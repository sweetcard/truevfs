/*
 * Copyright (C) 2005-2015 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.java.truevfs.kernel.impl

import java.util.concurrent.locks._
import javax.annotation.concurrent._

import net.java.truecommons.shed.Link.Type._
import net.java.truecommons.shed.{Filter, Link, Visitor}
import net.java.truevfs.kernel.impl.DefaultManager._
import net.java.truevfs.kernel.spec._

/** The default implementation of a file system manager.
  *
  * @author Christian Schlichtherle
  */
@ThreadSafe
private final class DefaultManager
extends FsAbstractManager
   with ReentrantReadWriteLockAspect { manager =>

  override val lock = new ReentrantReadWriteLock

  /**
   * The map of all schedulers for composite file system controllers,
   * keyed by the mount point of their respective file system model.
   */
  private[this] val controllers =
    new collection.mutable.WeakHashMap[FsMountPoint, Link[FsController]]

  private[this] val syncOnShutdown = ShutdownFuse(armed = false) {
    new FsSync()
      .manager(manager)
      .options(FsSyncOptions.UMOUNT)
      .run()
  }

  override def newModel(context: FsDriver, mountPoint: FsMountPoint, parent: FsModel): FsModel =
    context decorate new DefaultModel(mountPoint, parent)

  override def newController(context: AnyArchiveDriver, model: FsModel, parent: FsController): FsController = {
    assert(model.getParent == parent.getModel)
    assert(!model.isInstanceOf[ArchiveModel[_]])
    // HC SVNT DRACONES!
    // The FalsePositiveArchiveController decorates the FrontController
    // so that the decorated controller (chain) does not need to resolve
    // operations on false positive archive files.
    new FalsePositiveArchiveController(
      new FrontController(
        context decorate
          new ArchiveControllerAdapter(parent,
            new BackController(context, model, parent))))
  }

  override def controller(driver: FsCompositeDriver, mountPoint: FsMountPoint): FsController = {
    try {
      readLocked { controller0(driver, mountPoint) }
    } catch {
      case ex: NeedsWriteLockException =>
        if (readLockedByCurrentThread) {
          throw ex
        }
        writeLocked { controller0(driver, mountPoint) }
    }
  }

  private def controller0(driver: FsCompositeDriver, mountPoint: FsMountPoint): FsController = {
    controllers get mountPoint flatMap { l => Option(l.get) } match {
      case Some(c) => c
      case None =>
        checkWriteLockedByCurrentThread()
        val pc = Option(mountPoint.getParent) map { controller0(driver, _) }
        val pm = pc map { _.getModel }
        val m = new ManagedModel(driver newModel (this, mountPoint, pm.orNull))
        val c = driver newController (this, m, pc.orNull)
        m init c
        c
    }
  }

  override def accept[X <: Exception, V <: Visitor[_ >: FsController, X]](filter: Filter[_ >: FsController], visitor: V) = {
    var allUnmounted = true
    try {
      manager withFilter { controller =>
        val accepted = filter accept controller
        allUnmounted &= accepted
        accepted
      } accept { controller =>
        try {
          visitor visit controller
        } finally {
          allUnmounted &= !controller.getModel.isMounted
        }
      }
    } finally {
      if (allUnmounted)
        syncOnShutdown disarm ()
    }
    visitor
  }

  private case class withFilter(filter: FsController => Boolean) {
    def accept(visitor: FsController => Unit) {
      readLocked { controllers.values flatMap { link => Option(link.get) } }
        .filter(filter)
        .toIndexedSeq
        .sorted(ReverseControllerOrdering)
        .foreach(visitor)
    }
  }

  /** A model which schedules its controller for synchronization by observing
    * its property `mounted` - see method `sync(BitField)`.
    */
  private final class ManagedModel(model: FsModel)
  extends FsDecoratingModel(model) {

    private[this] var _controller: FsController = _

    def init(controller: FsController) {
      assert(null ne controller)
      assert(!model.isMounted)
      _controller = controller
      schedule(mandatory = false)
    }

    /**
     * Schedules the file system controller for synchronization according
     * to the given mount status.
     */
    override def setMounted(mounted: Boolean) {
      writeLocked {
        if (model.isMounted != mounted) {
          if (mounted) {
            syncOnShutdown arm ()
          }
          schedule(mandatory = mounted)
          model setMounted mounted
        }
      }
    }

    def schedule(mandatory: Boolean) {
      assert(writeLockedByCurrentThread)
      controllers += getMountPoint -> (
        (if (mandatory) STRONG else WEAK) newLink _controller
      )
    }
  } // ManagedModel
}

private object DefaultManager {

  private final class FrontController(c: FsController)
  extends FsDecoratingController(c)
     with FinalizeController

  // HC SVNT DRACONES!
  // The LockController extends the SyncController so that
  // the extended controller (chain) doesn't need to be thread safe.
  // The SyncController extends the CacheController because the
  // selective entry cache needs to get flushed on a NeedsSyncException.
  // The CacheController extends the ResourceController because the
  // cache entries terminate streams and channels and shall not stop the
  // extended controller (chain) from getting synced.
  // The ResourceController extends the TargetArchiveController so that
  // trying to sync the file system while any stream or channel to the
  // latter is open gets detected and properly dealt with.
  private final class BackController[E <: FsArchiveEntry](driver: FsArchiveDriver[E], model: FsModel, parent: FsController)
  extends TargetArchiveController[E](driver, model, parent)
     with ResourceController[E]
     with CacheController[E]
     with SyncController[E]
     with LockController[E] {
    override val pool = driver.getPool
    require(null ne pool)
  }

  private object ReverseControllerOrdering
  extends FsControllerComparator
     with Ordering[FsController]
}
