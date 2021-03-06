/*
 * Copyright (C) 2005-2015 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.java.truevfs.kernel.impl

import java.io._
import java.nio.channels.SeekableByteChannel
import java.nio.file._
import javax.annotation.concurrent._

import net.java.truecommons.cio.Entry.Access._
import net.java.truecommons.cio.Entry.Size._
import net.java.truecommons.cio.Entry.Type._
import net.java.truecommons.cio.Entry._
import net.java.truecommons.cio._
import net.java.truecommons.io._
import net.java.truecommons.shed._
import net.java.truevfs.kernel.impl.TargetArchiveController._
import net.java.truevfs.kernel.spec.FsAccessOption._
import net.java.truevfs.kernel.spec.FsAccessOptions._
import net.java.truevfs.kernel.spec.FsSyncOption._
import net.java.truevfs.kernel.spec._

import scala.{None, Option, Some}
import scala.reflect.ClassTag

/** Manages I/O to the entry which represents the target archive file in its
  * parent file system, detects archive entry collisions and implements a sync
  * of the target archive file.
  *
  * This controller is an emitter of
  * [[net.java.truecommons.shed.ControlFlowException]]s, for example
  * when
  * [[net.java.truevfs.kernel.impl.FalsePositiveArchiveException detecting a false positive archive file]], or
  * [[net.java.truevfs.kernel.impl.NeedsSyncException requiring a sync]].
  *
  * @tparam E the type of the archive entries.
  * @author Christian Schlichtherle
  */
@NotThreadSafe
private abstract class TargetArchiveController[E <: FsArchiveEntry]
(_driver: FsArchiveDriver[E], _model: FsModel, parent: FsController)
extends FileSystemArchiveController[E] with ArchiveModelAspect[E] {

  assert(null ne parent)

  final override val model: ArchiveModel[E] =
    new TargetArchiveModel(_driver, _model)
  require(model.getParent eq parent.getModel, "Parent/member mismatch!")

  /** The entry name of the target archive file in the parent file system. */
  private[this] val name = mountPoint.getPath.getNodeName
  assert(null ne name)

  /**
   * The (possibly cached) [[InputArchive]] which is used to mount the
   * (virtual) archive file system and read the entries from the target
   * archive file.
   */
  private[this] var _inputArchive: Option[InputArchive[E]] = None

  /**
   * The (possibly cached) [[OutputArchive]] which is used to write the
   * entries to the target archive file.
   */
  private[this] var _outputArchive: Option[OutputArchive[E]] = None

  assert(invariants)

  private def invariants = {
    val fs = fileSystem
    assert(_inputArchive.isEmpty || fs.isDefined)
    assert(_outputArchive.isEmpty || fs.isDefined)
    assert(fs.isEmpty || _inputArchive.isDefined || _outputArchive.isDefined)
    // This is effectively the same than the last three assertions, but is
    // harder to trace in the field on failure.
    //assert null != fs == (null != ia || null != oa);
    true
  }

  private def inputArchive = {
    _inputArchive match {
      case Some(ia) if !ia.clutch.isOpen => throw NeedsSyncException()
      case x => x
    }
  }

  private def inputArchive_=(ia: Option[InputArchive[E]]) {
    assert(ia.isEmpty || _inputArchive.isEmpty)
    ia foreach { _ => mounted = true }
    _inputArchive = ia
  }

  private def outputArchive = {
    _outputArchive match {
      case Some(oa) if !oa.clutch.isOpen => throw NeedsSyncException()
      case x => x
    }
  }

  private def outputArchive_=(oa: Option[OutputArchive[E]]) {
    assert(oa.isEmpty || _outputArchive.isEmpty)
    oa foreach { _ => mounted = true }
    _outputArchive = oa
  }

  def mount(options: AccessOptions, autoCreate: Boolean) {
    try {
      mount0(options, autoCreate)
    } finally {
      assert(invariants)
    }
  }

  private def mount0(options: AccessOptions, autoCreate: Boolean) {
    // HC SVNT DRACONES!

    // Check parent file system node.
    val pn = {
      try {
        parent node (options, name)
      } catch {
        case ex: FalsePositiveArchiveException =>
          throw new AssertionError(ex)
        case inaccessibleEntry: IOException =>
          if (autoCreate) throw inaccessibleEntry
          throw new FalsePositiveArchiveException(inaccessibleEntry)
      }
    }

    // Obtain file system by creating or loading it from the parent node.
    val fs = {
      if (null eq pn) {
        if (autoCreate) {
          // This may fail e.g. if the container file is an RAES
          // encrypted ZIP file and the user cancels password prompting.
          outputArchive(options)
          ArchiveFileSystem(model)
        } else {
          throw new FalsePositiveArchiveException(
            new NoSuchFileException(name.toString))
        }
      } else {
        // ro must be init first because the parent archive
        // controller could be a FileController and on Windows this
        // property changes to TRUE once a file is opened for reading!
        val ro = optionalReadOnlyCause()
        val is = {
          try {
            driver newInput (model, MOUNT_OPTIONS, parent, name)
          } catch {
            case ex: FalsePositiveArchiveException =>
              throw new AssertionError(ex)
            case ex: IOException =>
              if (pn isType SPECIAL) throw new FalsePositiveArchiveException(ex)
              throw new PersistentFalsePositiveArchiveException(ex)
          }
        }
        val fs = ArchiveFileSystem(model, is, pn, ro)
        inputArchive = Some(new InputArchive(is))
        assert(mounted)
        fs
      }
    }

    // Register file system.
    fileSystem = Some(fs)
  }

  private def optionalReadOnlyCause() = {
    try {
      parent checkAccess (MOUNT_OPTIONS, name, WRITE_ACCESS)
      None
    } catch {
      case cause: FalsePositiveArchiveException => throw new AssertionError(cause)
      case cause: IOException => Some(cause)
    }
  }

  /**
   * Ensures that `outputArchive` does not return `None`.
   *
   * @return The output archive.
   */
  private def outputArchive(options: AccessOptions): OutputArchive[E] = {
    outputArchive foreach { oa => assert(mounted); return oa }
    val is = inputArchive match {
      case Some(ia) => ia.driverProduct
      case _ => null
    }
    val os = {
      try {
        driver newOutput (model,
                          options and ACCESS_PREFERENCES_MASK set CACHE,
                          parent, name, is)
      } catch {
        case ex: FalsePositiveArchiveException =>
          throw new AssertionError(ex)
        case ex: ControlFlowException =>
          assert(ex.isInstanceOf[NeedsLockRetryException], ex)
          throw ex
      }
    }
    val oa = new OutputArchive(os)
    outputArchive = Some(oa)
    assert(mounted)
    oa
  }

  def input(name: String): InputSocket[E] = {
    final class Input extends AbstractInputSocket[E] {
      lazy val socket: InputSocket[E] = inputArchive.get input name

      def target(): E = syncOn[ClosedInputException] { socket target () }

      override def stream(peer: AnyOutputSocket): InputStream =
        syncOn[ClosedInputException] { socket stream peer }

      override def channel(peer: AnyOutputSocket): SeekableByteChannel =
        syncOn[ClosedInputException] { socket channel peer }
    }
    new Input
  }

  def output(options: AccessOptions, entry: E): OutputSocket[E] = {
    final class Output extends AbstractOutputSocket[E] {
      lazy val socket: OutputSocket[E] = outputArchive(options) output entry

      def target: E = entry

      override def stream(peer: AnyInputSocket): OutputStream =
        syncOn[ClosedOutputException] { socket stream peer }

      override def channel(peer: AnyInputSocket): SeekableByteChannel =
        syncOn[ClosedOutputException] { socket channel peer }
    }
    new Output
  }

  private def syncOn[X <: IOException] = new SyncOn

  private class SyncOn[X <: IOException] {
    def apply[A](operation: => A)(implicit mf: ClassTag[X]): A = {
      try { operation }
      catch { case x: X => throw NeedsSyncException() }
    }
  }

  def sync(options: SyncOptions) {
    try {
      val builder = new FsSyncExceptionBuilder
      if (!(options get ABORT_CHANGES)) copy(builder)
      close(options, builder)
      builder check ()
    } finally {
      assert(invariants)
    }
  }

  /**
   * Synchronizes all entries in the (virtual) archive file system with the
   * (temporary) output archive file.
   *
   * @param handler the strategy for assembling sync exceptions.
   */
  private def copy(handler: FsSyncExceptionBuilder) {
    // Skip (In|Out)putArchive for better performance.
    // This is safe because the ResourceController has already shut down
    // all concurrent access by closing the respective resources (streams,
    // channels etc).
    // The Disconnecting(In|Out)putService should not get skipped however:
    // If these would throw an (In|Out)putClosedException, then this would
    // be an artifact of a bug.
    val is = _inputArchive match {
      case Some(ia) =>
        val clutch = ia.clutch
        if (!clutch.isOpen) return
        clutch
      case _ =>
        new DummyInputService[E]
    }

    val os = _outputArchive match {
      case Some(oa) =>
        val clutch = oa.clutch
        if (!clutch.isOpen) return
        clutch
      case _ =>
        return
    }

    for (cn <- fileSystem.get) {
      for (ae <- cn.getEntries) {
        val aen = ae.getName
        if (null eq (os entry aen)) {
          try {
            if (DIRECTORY eq ae.getType) {
              if (!cn.isRoot) // never output the root directory!
                if (UNKNOWN != ae.getTime(WRITE)) // never output a ghost directory!
                  os.output(ae).stream(null).close()
            } else if (null ne is.entry(aen)) {
              IoSockets.copy(is.input(aen), os.output(ae))
            } else {
              // The file system entry is a newly created
              // non-directory entry which hasn't received any
              // content yet, e.g. as a result of make()
              // => output an empty file system entry.
              for (size <- ALL_SIZES)
                ae.setSize(size, UNKNOWN)
              ae.setSize(DATA, 0)
              os.output(ae).stream(null).close()
            }
          } catch {
            case ex: IOException =>
              throw handler fail new FsSyncException(mountPoint, ex)
          }
        }
      }
    }
  }

  /**
   * Discards the file system, closes the input archive and finally the
   * output archive.
   * Note that this order is critical: The parent file system controller is
   * expected to replace the entry for the target archive file with the
   * output archive when it gets closed, so this must be done last.
   * Using a finally block ensures that this is done even in the unlikely
   * event of an exception when closing the input archive.
   * Note that in this case closing the output archive is likely to fail and
   * override the IOException thrown by this method, too.
   *
   * @param handler the strategy for assembling sync exceptions.
   */
  private def close(options: SyncOptions, handler: FsSyncExceptionBuilder) {
    // HC SVNT DRACONES!
    _inputArchive.foreach { ia =>
      try {
        ia.close()
      } catch {
        case ex: ControlFlowException =>
          assert(ex.isInstanceOf[NeedsLockRetryException], ex)
          throw ex
        case ex: IOException =>
          handler warn new FsSyncWarningException(mountPoint, ex)
      }
      inputArchive = None
    }
    _outputArchive.foreach { oa =>
      try {
        oa close ()
      } catch {
        case ex: ControlFlowException =>
          assert(ex.isInstanceOf[NeedsLockRetryException], ex)
          throw ex
        case ex: IOException =>
          handler warn new FsSyncException(mountPoint, ex)
      }
      outputArchive = None
    }
    fileSystem = None
    if (options get ABORT_CHANGES) mounted = false
  }

  def checkSync(options: AccessOptions, name: FsNodeName, intention: Access) {
    // HC SVNT DRACONES!

    // If no file system exists then pass the test.
    val fs = fileSystem match {
      case Some(fs) => fs
      case _        => return
    }

    // If GROWing and the driver supports the respective access method,
    // then pass the test.
    if (options.get(GROW)) {
      intention match {
        case READ  =>
        case WRITE =>
          if (driver.getRedundantContentSupport) {
            outputArchive // side-effect!
            return
          }
        case _     =>
          if (driver.getRedundantMetaDataSupport) return
      }
    }

    // If the file system does not contain an entry with the given name,
    // then pass the test.
    val cn = fs node (options, name) match {
      case Some(cn) => cn
      case _ => return
    }

    // If the entry name addresses the file system root, then pass the test
    // because the root entry cannot get input or output anyway.
    if (name.isRoot) return

    // Check if the entry is already written to the output archive.
    outputArchive match {
      case Some(oa) =>
        val aen = cn.getEntry.getName
        if (null ne (oa entry aen)) throw NeedsSyncException()
      case _ =>
    }

    // If our intention is reading the entry then check if it's present in the
    // input archive.
    if (intention eq READ) inputArchive match {
      case Some(ia) =>
        val aen = cn.getEntry.getName
        if (null eq (ia entry aen)) throw NeedsSyncException()
      case _ =>
        throw NeedsSyncException()
    }
  }

  private class TargetArchiveModel(driver: FsArchiveDriver[E], model: FsModel)
  extends ArchiveModel(driver, model) {
    override def touch(options: AccessOptions) { outputArchive(options) }
  }
} // TargetArchiveController

private object TargetArchiveController {
  private val MOUNT_OPTIONS = BitField.of(CACHE)
  private val WRITE_ACCESS = BitField.of(WRITE)

  private final class InputArchive[E <: FsArchiveEntry]
  (val driverProduct: InputService[E])
  extends LockInputService(new DisconnectingInputService(driverProduct)) {
    def clutch: DisconnectingInputService[E] = container.asInstanceOf[DisconnectingInputService[E]]
  }

  private final class OutputArchive[E <: FsArchiveEntry]
  (driverProduct: OutputService[E])
  extends LockOutputService(new DisconnectingOutputService(driverProduct)) {
    def clutch: DisconnectingOutputService[E] = container.asInstanceOf[DisconnectingOutputService[E]]
  }

  /** A dummy input archive to substitute for `None` when copying.
    *
    * @tparam E the type of the entries.
    */
  private final class DummyInputService[E <: Entry] extends InputService[E] {
    override def size = 0
    override def iterator: java.util.Iterator[E] = java.util.Collections.emptyList[E].iterator
    override def entry(name: String): E = null.asInstanceOf[E]
    override def input(name: String): InputSocket[E] = throw new AssertionError
    override def close(): Unit = throw new AssertionError
  }
} // TargetArchiveController
