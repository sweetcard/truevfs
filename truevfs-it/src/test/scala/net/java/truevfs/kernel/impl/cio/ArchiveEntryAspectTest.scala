/*
 * Copyright (C) 2005-2015 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.java.truevfs.kernel.impl.cio

import net.java.truecommons.cio.Entry.Access._
import net.java.truecommons.cio.Entry.PosixEntity._
import net.java.truecommons.cio.Entry.Size._
import net.java.truecommons.cio.Entry.Type._
import net.java.truecommons.cio.Entry._
import net.java.truevfs.kernel.spec._
import net.java.truevfs.kernel.spec.mock.MockArchiveDriverEntry
import org.junit.runner._
import org.scalatest.Matchers._
import org.scalatest._
import org.scalatest.junit._
import org.scalatest.prop.PropertyChecks._
import org.scalatest.prop._

/**
  * @author Christian Schlichtherle
  */
@RunWith(classOf[JUnitRunner])
class ArchiveEntryAspectTest extends WordSpec {

  private def forAllNameAndType(test: (FsArchiveEntry, ArchiveEntryAspect[_]) => Unit) {
    forAll { name: String =>
      whenever (null ne name) {
        forAll(Table("type", FILE, DIRECTORY)) { tµpe =>
          val e = new MockArchiveDriverEntry(name, tµpe)
          val a = ArchiveEntryAspect(e)
          test(e, a)
        }
      }
    }
  }

  "An entry aspect" should {
    "have the same name and type than its associated entry" in {
      forAllNameAndType { (e, a) =>
        a.name should be (e.getName)
        a.tµpe should be (e.getType)
      }
    }

    "properly decorate its associated entry" in {
      forAllNameAndType { (e, a) =>
        a.dataSize = UNKNOWN
        a.dataSize should be (UNKNOWN)
        a.dataSize = 0
        a.dataSize should be (0)
        a.dataSize should be (e.getSize(DATA))

        a.storageSize = UNKNOWN
        a.storageSize should be (UNKNOWN)
        a.storageSize = 0
        a.storageSize should be (0)
        a.storageSize should be (e.getSize(STORAGE))

        a.createTime = UNKNOWN
        a.createTime should be (UNKNOWN)
        a.createTime = 0
        a.createTime should be (0)
        a.createTime should be (e.getTime(CREATE))

        a.readTime = UNKNOWN
        a.readTime should be (UNKNOWN)
        a.readTime = 0
        a.readTime should be (0)
        a.readTime should be (e.getTime(READ))

        a.writeTime = UNKNOWN
        a.writeTime should be (UNKNOWN)
        a.writeTime = 0
        a.writeTime should be (0)
        a.writeTime should be (e.getTime(WRITE))

        a.executeTime = UNKNOWN
        a.executeTime should be (UNKNOWN)
        a.executeTime = 0
        a.executeTime should be (0)
        a.executeTime should be (e.getTime(EXECUTE))

        forAll(Table("PosixEntity", USER, GROUP, OTHER)) { entity =>
          a.createPermission(entity) = None // unknown
          a.createPermission(entity) should be (None)
          a.createPermission(entity) = Option(false) // not permitted
          a.createPermission(entity) should be (Option(false))
          a.createPermission(entity) = Option(true) // permitted
          a.createPermission(entity) should be (Option(true))
          a.createPermission(entity) should be (Option(e.isPermitted(CREATE, entity)))

          a.readPermission(entity) = None // unknown
          a.readPermission(entity) should be (None)
          a.readPermission(entity) = Option(false) // not permitted
          a.readPermission(entity) should be (Option(false))
          a.readPermission(entity) = Option(true) // permitted
          a.readPermission(entity) should be (Option(true))
          a.readPermission(entity) should be (Option(e.isPermitted(READ, entity)))

          a.writePermission(entity) = None // unknown
          a.writePermission(entity) should be (None)
          a.writePermission(entity) = Option(false) // not permitted
          a.writePermission(entity) should be (Option(false))
          a.writePermission(entity) = Option(true) // permitted
          a.writePermission(entity) should be (Option(true))
          a.writePermission(entity) should be (Option(e.isPermitted(WRITE, entity)))

          a.executePermission(entity) = None // unknown
          a.executePermission(entity) should be (None)
          a.executePermission(entity) = Option(false) // not permitted
          a.executePermission(entity) should be (Option(false))
          a.executePermission(entity) = Option(true) // permitted
          a.executePermission(entity) should be (Option(true))
          a.executePermission(entity) should be (Option(e.isPermitted(EXECUTE, entity)))

          a.deletePermission(entity) = None // unknown
          a.deletePermission(entity) should be (None)
          a.deletePermission(entity) = Option(false) // not permitted
          a.deletePermission(entity) should be (Option(false))
          a.deletePermission(entity) = Option(true) // permitted
          a.deletePermission(entity) should be (Option(true))
          a.deletePermission(entity) should be (Option(e.isPermitted(DELETE, entity)))
        }
      }
    }
  }
}
