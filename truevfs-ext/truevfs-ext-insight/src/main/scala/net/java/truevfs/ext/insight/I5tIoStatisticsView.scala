/*
 * Copyright (C) 2005-2015 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.java.truevfs.ext.insight

import javax.annotation.concurrent._
import javax.management._

import net.java.truevfs.ext.insight.stats.FsStatistics

/**
 * A view for [[net.java.truevfs.ext.insight.stats.IoStatistics]].
 *
 * @author Christian Schlichtherle
 */
@ThreadSafe
private final class I5tIoStatisticsView(controller: I5tStatistics)
extends I5tStatisticsView(classOf[I5tIoStatisticsMXBean], true)
with I5tIoStatisticsMXBean {

  assert(null ne controller)

  protected override def getDescription(info: MBeanInfo): String = "A log of I/O statistics."

  override def stats: FsStatistics = controller.stats
  override def getSubject: String = controller.subject
  override def rotate(): Unit = { controller.rotate() }
}
