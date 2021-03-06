/*
 * Copyright (C) 2005-2015 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.java.truevfs.ext.logging

import java.io._
import net.java.truecommons.io._
import net.java.truecommons.cio._
import org.slf4j._

/**
 * @author Christian Schlichtherle
 */
private final class LogInputStream(
  override val context: InputSocket[_ <: Entry],
  in: InputStream
) extends DecoratingInputStream(in) with LogCloseable with Immutable {
  override def logger = LogInputStream.logger
}

private object LogInputStream {
  private val logger = LoggerFactory.getLogger(classOf[LogInputStream])
}
