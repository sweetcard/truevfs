/*
 * Copyright (C) 2005-2012 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package de.schlichtherle.truevfs.kernel

import net.truevfs.kernel._
import javax.annotation.concurrent._

/** A shutdown hook singleton which `sync`s a `register`ed file system manager
  * when it's `run`.
  * This is to protect an application from loss of data if the manager isn't
  * explicitly asked to `sync` before the JVM terminates.
  * 
  * @author Christian Schlichtherle
  */
@ThreadSafe
private object SyncShutdownHook extends Thread {
  setPriority(Thread.MAX_PRIORITY)

  @volatile
  private var _manager: Option[FsManager] = None

  /** Registers the given file system `manager` for `sync`hronization when the
    * shutdown hook is `run`.
   * 
   * @param manager the file system manager to `sync` when the shutdown hook is
   *        `run`.
   * @see   #cancel
   */
  def register(manager: FsManager) {
    if (_manager.orNull != manager) {
      synchronized {
        if (_manager.orNull != manager) {
          Runtime.getRuntime addShutdownHook this
          _manager = Option(manager)
        }
      }
    }
  }

  /** De-registers any previously registered file system manager.
    * 
    * @see #register
    */
  def cancel() {
    if (_manager isDefined) {
      synchronized {
        if (_manager isDefined) {
          // Prevent memory leak in dynamic class loader environments.
          Runtime.getRuntime removeShutdownHook this
          _manager = None
        }
      }
    }
  }

  /** `sync`s any `register`ed file system manager.
    * 
    * If any exception occurs within the shutdown hook, its stacktrace gets
    * printed to standard error because logging doesn't work in a shutdown
    * hook.
    * 
    * @deprecated Do '''not''' call this method directly!
    * @see #register
    */
  override def run() {
    // HC SVNT DRACONES!
    _manager foreach { manager =>
      _manager = None // MUST reset to void calls to cancel() during the sync()!
      try {
        manager sync FsSyncOptions.UMOUNT // may call cancel()!
      } catch {
        // Logging doesn't work in a shutdown hook!
        case ex => ex printStackTrace()
      }
    }
  }
}