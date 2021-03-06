/*
 * Copyright (C) 2005-2015 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.java.truevfs.kernel

import net.java.truecommons.shed._
import net.java.truevfs.kernel.spec._
import net.java.truecommons.cio._
import net.java.truecommons.cio.Entry._
import java.{lang => jl}
import java.{util => ju}
import language.implicitConversions

/** Implements the Kernel API.
  *
  * @author Christian Schlichtherle
  */
package object impl {

  private[impl] type AccessOptions = BitField[FsAccessOption]
  private[impl] type SyncOptions = BitField[FsSyncOption]
  private[impl] type AnyArchiveDriver = FsArchiveDriver[_ <: FsArchiveEntry]
  private[impl] type AnyIoSocket = IoSocket[_ <: Entry]
  private[impl] type AnyInputSocket = InputSocket[_ <: Entry]
  private[impl] type AnyOutputSocket = OutputSocket[_ <: Entry]

  // Used for looping through BitField, Container etc.
  implicit private[impl] def asScalaIterable[E](i: jl.Iterable[E]): Iterable[E] = {
    import collection.JavaConverters._
    i.asScala
  }

  implicit private[impl] def asScalaMapFromAccessToLong(input: ju.Map[Access, jl.Long]): Map[Access, Long] = {
    var output = Map[Access, Long]()
    for (entry <- input.entrySet)
      output += entry.getKey -> Long.unbox(entry.getValue)
    output
  }

  implicit private[impl] def asJavaMapFromAccessToLong(input: Map[Access, Long]): ju.Map[Access, jl.Long] = {
    val output = new ju.HashMap[Access, jl.Long]()
    for ((key, value) <- input)
      output.put(key, Long.box(value))
    output
  }
}
