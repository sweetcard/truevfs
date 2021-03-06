/*
 * Copyright (C) 2005-2015 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.java.truevfs.kernel.impl

import java.io._
import java.nio.channels._
import javax.annotation.concurrent._

import net.java.truecommons.cio._
import net.java.truecommons.io._
import net.java.truecommons.shed._
import net.java.truevfs.kernel.spec.FsSyncOption._
import net.java.truevfs.kernel.spec._

import scala.Option

private object ResourceController {
  private val waitTimeoutMillis = LockingStrategy.acquireTimeoutMillis
}

/** Accounts input and output resources returned by its decorated controller.
  *
  * @see    ResourceManager
  * @author Christian Schlichtherle
  */
@NotThreadSafe
private trait ResourceController[E <: FsArchiveEntry]
extends ArchiveController[E] {
  controller: ArchiveModelAspect[E] =>

  import ResourceController._

  private[this] val accountant = new ResourceAccountant(writeLock)

  abstract override def input(options: AccessOptions, name: FsNodeName) = {
    final class Input extends DelegatingInputSocket[Entry] {
      val socket = ResourceController.super.input(options, name)

      override def stream(peer: AnyOutputSocket) =
        new ResourceInputStream(socket.stream(peer))

      override def channel(peer: AnyOutputSocket) =
        new ResourceSeekableChannel(socket.channel(peer))
    }
    new Input
  }: AnyInputSocket

  abstract override def output(options: AccessOptions, name: FsNodeName, template: Option[Entry]) = {
    final class Output extends DelegatingOutputSocket[Entry] {
      val socket = ResourceController.super.output(options, name, template)

      override def stream(peer: AnyInputSocket) =
        new ResourceOutputStream(socket.stream(peer))

      override def channel(peer: AnyInputSocket) =
        new ResourceSeekableChannel(socket.channel(peer))
    }
    new Output
  }: AnyOutputSocket

  abstract override def sync(options: SyncOptions) {
    assert(writeLockedByCurrentThread)
    assert(!readLockedByCurrentThread)

    // HC SVNT DRACONES!
    val beforeWait = accountant.resources
    if (0 == beforeWait.total) {
      super.sync(options)
      return
    }

    val builder = new FsSyncExceptionBuilder
    try {
      if (0 != beforeWait.local && !(options get FORCE_CLOSE_IO))
        throw new FsOpenResourceException(beforeWait.local, beforeWait.total)
      accountant awaitClosingOfOtherThreadsResources
      (if (options get WAIT_CLOSE_IO) 0 else waitTimeoutMillis)
      val afterWait = accountant.resources
      if (0 != afterWait.total)
        throw new FsOpenResourceException(afterWait.local, afterWait.total)
    } catch {
      case e: FsOpenResourceException =>
        if (!(options get FORCE_CLOSE_IO))
          throw builder fail new FsSyncException(mountPoint, e)
        builder warn new FsSyncWarningException(mountPoint, e)
    }
    closeResources(builder)
    if (beforeWait.needsWaiting) {
      // awaitClosingOfOtherThreadsResources(*) has temporarily released
      // the write lock, so the state of the virtual file system may have
      // completely changed and thus we need to restart the sync
      // operation unless an exception occured.
      builder check ()
      throw NeedsSyncException()
    }
    try { super.sync(options) }
    catch { case ex: FsSyncException => throw builder fail ex }
    builder check ()
  }

  /** Closes and disconnects all entry streams of the output and input archive.
    *
    * @param builder the exception handling strategy.
    */
  private def closeResources(builder: FsSyncExceptionBuilder) {

    final class IOExceptionHandler
    extends ExceptionHandler[IOException, RuntimeException] {
      def fail(e: IOException) = throw new AssertionError(e)
      def warn(e: IOException) {
        builder warn new FsSyncWarningException(mountPoint, e)
      }
    } // IOExceptionHandler

    accountant closeAllResources new IOExceptionHandler
  }

  private class ResourceInputStream(in: InputStream)
  extends DecoratingInputStream(in) with Resource

  private class ResourceOutputStream(out: OutputStream)
  extends DecoratingOutputStream(out) with Resource

  private class ResourceSeekableChannel(channel: SeekableByteChannel)
  extends DecoratingSeekableChannel(channel) with Resource

  private trait Resource extends Closeable {
    accountant startAccountingFor this

    /**
      * Close()s this resource and finally stops accounting for it unless a
      * [[ControlFlowException]] is thrown.
      *
      * @see http://java.net/jira/browse/TRUEZIP-279 .
      */
    abstract override def close() {
      var cfe = false
      try     { super.close() }
      catch   { case e: ControlFlowException => cfe = true; throw e }
      finally { if (!cfe) accountant stopAccountingFor this }
    }
  }
}
