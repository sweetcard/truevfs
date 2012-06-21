/*
 * Copyright (C) 2005-2012 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package de.schlichtherle.truevfs.kernel

import java.util.concurrent.locks._
import javax.annotation.concurrent._
import net.truevfs.kernel._

/** A file system model which supports multiple concurrent reader threads.
  *
  * @see    LockController
  * @author Christian Schlichtherle
  */
@ThreadSafe
private final class LockModel(model: FsModel)
extends FsDecoratingModel[FsModel](model) with LockModelLike {
  val lock = new ReentrantReadWriteLock
}