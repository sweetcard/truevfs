/*
 * Copyright (C) 2005-2015 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.java.truevfs.kernel.impl.util

import java.util.Comparator
import java.{util => ju}

import net.java.truevfs.kernel.impl.util.FileSystem._

import scala.collection.JavaConverters._
import scala.collection._

/**
  * A (virtual) file system is a mutable map from decomposable keys, named
  * ''paths'', to arbitrary values, named ''entries''.
  * The standard use case for this class is for mapping path name strings which
  * can get decomposed into segments by splitting them with a separator
  * character such as `'/'`.
  * However, this class works with any generic decomposable key type for which
  * a [[net.java.truevfs.kernel.impl.util.FileSystem.Composition]] and a
  * [[net.java.truevfs.kernel.impl.util.FileSystem.DirectoryFactory]] exist.
  *
  * Using this class helps to save some heap space if the paths address deeply
  * nested directory trees where many path segments can get shared between
  * mapped entries &mdash; provided that no other references to the paths are
  * held!
  *
  * This class supports both `null` paths and entries.
  *
  * This class is ''not'' thread-safe!
  *
  * @tparam K the type of the paths (keys) in this (virtual) file system (map).
  * @tparam V the type of the entries (values) in this (virtual) file system
  *         (map).
  * @author Christian Schlichtherle
  */
final class FileSystem[K >: Null, V](
  implicit val composition: Composition[K],
  val directoryFactory: DirectoryFactory[K]
) extends mutable.Map[K, V] with mutable.MapLike[K, V, FileSystem[K, V]] {

  private[this] var _root: INode[K, V] = _
  private var _size: Int = _

  reset()

  implicit private def self: FileSystem[K, V] = this

  private def reset() { _root = new Root; _size = 0}

  override def size = _size

  override def clear() = reset()

  override def empty = new FileSystem[K, V]

  override def iterator = iterator(None, _root)

  private def iterator(path: Option[K], node: Node[K, V]): Iterator[(K, V)] = {
    node.entry.map(path.orNull -> _).iterator ++ node.iterator.flatMap {
      case (matchedSegment, matchedNode) => iterator(Some(composition(path, matchedSegment)), matchedNode)
    }
  }

  def list(path: K): Option[Iterator[(K, V)]] = list(Option(path))

  private def list(path: Option[K]) = {
    node(path) map (_.iterator flatMap {
      case (segment, node) => node.entry map (composition(path, segment) -> _)
    })
  }

  override def get(path: K) = node(path) flatMap (_.entry)

  def node(path: K): Option[Node[K, V]] = node(Option(path))

  private def node(optPath: Option[K]): Option[INode[K, V]] = {
    optPath match {
      case Some(path) =>
        path match {
          case composition(parent, segment) =>
            node(parent) flatMap (_ get segment)
        }
      case None =>
        Some(_root)
    }
  }

  override def +=(kv: (K, V)) = { link(kv._1, kv._2); this }

  def link(path: K, entry: V): Node[K, V] = link(Option(path), Some(entry))

  private def link(optPath: Option[K], optEntry: Option[V]): INode[K, V] = {
    optPath match {
      case Some(path) =>
        path match {
          case composition(parent, segment) =>
            link(parent, None) link (segment, optEntry)
        }
      case None =>
        if (optEntry.isDefined) _root.entry = optEntry
        _root
    }
  }

  override def -=(path: K) = { unlink(path); this }

  def unlink(path: K): Unit = unlink(Option(path))

  private def unlink(optPath: Option[K]) {
    optPath match {
      case Some(path) =>
        path match {
          case composition(parent, segment) =>
            node(parent) foreach { node =>
              node unlink segment
              if (node.isDead) unlink(parent)
            }
        }
      case None =>
        _root.entry = None
    }
  }

  override def stringPrefix = "FileSystem"
}

/**
  * @author Christian Schlichtherle
  */
object FileSystem {

  def apply[K >: Null, V](
    composition: Composition[K],
    directoryFactory: DirectoryFactory[K]
  ) = new FileSystem[K, V]()(composition, directoryFactory)

  def apply[V](
    separator: Char,
    directoryFactory: DirectoryFactory[String] = new SortedDirectoryFactory
  ) = apply[String, V](new StringComposition(separator), directoryFactory)

  /** A file system node. */
  sealed abstract class Node[K >: Null, +V] extends Iterable[(K, Node[K, V])] {
    def path: Option[K]
    final def isRoot = path.isEmpty
    def entry: Option[V]
    final def isGhost = entry.isEmpty
    final def isLeaf = isEmpty
    final override def stringPrefix = "Node"
    final override def toString() = stringPrefix + "(path=" + path + ", isLeaf=" + isLeaf + ", entry=" + entry + ")"
  }

  private class INode[K >: Null, V] protected (
    private[this] val parent: Option[(INode[K, V], K)],
    private[this] var _entry: Option[V]
  ) (implicit fs: FileSystem[K, V])
  extends Node[K, V] {

    private[this] val _members = fs.directoryFactory.create[INode[K, V]]

    if (_entry.isDefined) fs._size += 1

    override def iterator = _members.iterator
    override def foreach[U](f: ((K, Node[K, V])) => U): Unit = _members foreach f
    override def size = _members.size

    override def path = address._2

    def address: (FileSystem[K, V], Option[K]) = {
      val (node, segment) = parent.get
      val (fs, path) = node.address
      fs -> Some(fs composition (path, segment))
    }

    override def entry = _entry

    def entry_=(entry: Option[V])(implicit fs: FileSystem[K, V]) {
      // HC SVNT DRACONES!
      if (_entry.isDefined) {
        if (entry.isEmpty)
          fs._size -= 1
      } else {
        if (entry.isDefined)
          fs._size += 1
      }
      _entry = entry
    }

    def get(segment: K) = _members get segment

    def link(segment: K, entry: Option[V])(implicit fs: FileSystem[K, V]) = {
      _members get segment match {
        case Some(node) =>
          if (entry.isDefined) node.entry = entry
          node
        case None =>
          val node = new INode[K, V](Some(this, segment), entry)
          _members += segment -> node
          node
      }
    }

    def unlink(segment: K)(implicit fs: FileSystem[K, V]) {
      _members get segment foreach { node =>
        node.entry = None
        if (node.isLeaf) _members -= segment
      }
    }

    def isDead = isGhost && isLeaf
  }

  private final class Root[K >: Null, V](implicit fs: FileSystem[K, V])
  extends INode[K, V](None, None) {
    override def address = fs -> None
  }

  trait Composition[K] extends ((Option[K], K) => K) {
    /** The composition method for injection. */
    override def apply(parent: Option[K], segment: K): K

    /**
     * The decomposition method for extraction.
     * Note that the second element of the returned tuple should not share any
     * memory with the given path - otherwise you might not achieve any heap
     * space savings!
     */
    def unapply(path: K): Some[(Option[K], K)]
  }

  final class StringComposition(separator: Char)
  extends Composition[String] {
    override def apply(optParent: Option[String], segment: String) = {
      optParent match {
        case Some(parent) => parent + segment
        case None => segment
      }
    }

    override def unapply(path: String) = {
      val i = path.lastIndexOf(separator)
      if (0 <= i) {
        // Don't share sub-strings with the FileSystem!
        Some(Some(path substring (0, i)) -> new String(path substring i))
      } else {
        // There's no point in copying here!
        Some(None, path)
      }
    }
  }

  trait DirectoryFactory[K] {
    def create[V]: mutable.Map[K, V]
  }

  final class HashedDirectoryFactory[K] extends DirectoryFactory[K] {
    def create[V] = new mutable.HashMap
  }

  final class SortedDirectoryFactory[K : Ordering] extends DirectoryFactory[K] {
    def create[V] = new ju.TreeMap[K, V](implicitly[Comparator[K]]).asScala
  }

  final class LinkedDirectoryFactory[K] extends DirectoryFactory[K] {
    def create[V] = new mutable.LinkedHashMap
  }
}
