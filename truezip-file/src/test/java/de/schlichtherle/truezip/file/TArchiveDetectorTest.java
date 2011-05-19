/*
 * Copyright (C) 2005-2011 Schlichtherle IT Services
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.schlichtherle.truezip.file;

import de.schlichtherle.truezip.fs.FsDriver;
import de.schlichtherle.truezip.fs.FsScheme;
import de.schlichtherle.truezip.fs.archive.MockArchiveDriver;
import de.schlichtherle.truezip.fs.archive.FsArchiveDriver;
import java.util.Locale;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static de.schlichtherle.truezip.file.TArchiveDetector.NULL;

/**
 * @author Christian Schlichtherle
 * @version $Id$
 */
public class TArchiveDetectorTest {

    private static final FsArchiveDriver<?> DRIVER = new MockArchiveDriver();
    private TArchiveDetector detector;

    @Before
    public void setUp() {
        detector = new TArchiveDetector(
                "ear|exe|jar|odb|odf|odg|odm|odp|ods|odt|otg|oth|otp|ots|ott|tar|tar.bz2|tar.gz|tbz2|tgz|tzp|war|zip|zip.rae|zip.raes",
                DRIVER);
    }

    @Test
    public void testIllegalConstructors() {
        testIllegalConstructors(NullPointerException.class,
                new Object[][] {
                    { null, null },
                    { null, DRIVER },
                    //{ "xyz", null },
                    { null, null, null },
                    { null, null, DRIVER },
                    { null, "xyz", null },
                    { null, "xyz", DRIVER },
                    { NULL, null, null },
                    { NULL, null, DRIVER },
                    //{ TArchiveDetector.NULL, "xyz", null },
                    { null, new Object[][] {{ "xyz", MockArchiveDriver.class }} },
                    { NULL, null },
                    { NULL, new Object[][] {{ null, null }} },
                    { NULL, new Object[][] {{ null, "" }} },
                    { NULL, new Object[][] {{ null, "xyz" }} },
                    //{ TArchiveDetector.NULL, new Object[] { "xyz", null } },
               });

        testIllegalConstructors(IllegalArgumentException.class,
                new Object[][] {
                    { "DRIVER" },
                    { "DEFAULT" },
                    { "NULL" },
                    { "ALL" },
                    { "unknownSuffix" },
                    { "", DRIVER }, // empty suffix set
                    { ".", DRIVER }, // empty suffix set
                    { "|", DRIVER }, // empty suffix set
                    { "|.", DRIVER }, // empty suffix set
                    { "||", DRIVER }, // empty suffix set
                    { "||.", DRIVER }, // empty suffix set
                    { "|.|", DRIVER }, // empty suffix set
                    { "|.|.", DRIVER }, // empty suffix set
                    { NULL, "", DRIVER }, // empty suffix set
                    { NULL, ".", DRIVER }, // empty suffix set
                    { NULL, "|", DRIVER }, // empty suffix set
                    { NULL, "|.", DRIVER }, // empty suffix set
                    { NULL, "||", DRIVER }, // empty suffix set
                    { NULL, "||.", DRIVER }, // empty suffix set
                    { NULL, "|.|", DRIVER }, // empty suffix set
                    { NULL, "|.|.", DRIVER }, // empty suffix set
                    { NULL, new Object[][] {{ "", DRIVER }} }, // empty suffix set
                    { NULL, new Object[][] {{ ".", DRIVER }} }, // empty suffix set
                    { NULL, new Object[][] {{ "|", DRIVER }} }, // empty suffix set
                    { NULL, new Object[][] {{ "|.", DRIVER }} }, // empty suffix set
                    { NULL, new Object[][] {{ "||", DRIVER }} }, // empty suffix set
                    { NULL, new Object[][] {{ "||.", DRIVER }} }, // empty suffix set
                    { NULL, new Object[][] {{ "|.|", DRIVER }} }, // empty suffix set
                    { NULL, new Object[][] {{ "|.|.", DRIVER }} }, // empty suffix set
                    { NULL, new Object[][] {{ "anySuffix", "" }} }, // empty class name
                    { NULL, new Object[][] {{ "anySuffix", "xyz" }} }, // not a class name
                    { NULL, new Object[][] {{ MockArchiveDriver.class, DRIVER }} }, // not a suffix list
                    { NULL, new Object[][] {{ DRIVER, DRIVER }} }, // not a suffix list
                    { NULL, new Object[][] {{ "anySuffix", new Object() }} }, // not an archive driver
                    { NULL, new Object[][] {{ "anySuffix", Object.class }} }, // not an archive driver class
                });
    }

    @SuppressWarnings({"unchecked", "ResultOfObjectAllocationIgnored"})
    private void testIllegalConstructors(
            final Class<? extends Throwable> expected,
            final Object[][] list) {
        for (int i = 0; i < list.length; i++) {
            final Object[] args = list[i];
            Object arg0 = args[0];
            Object arg1 = null;
            Object arg2 = null;
            try {
                switch (args.length) {
                    case 1:
                        new TArchiveDetector((String) arg0);
                        fail("Index " + i);
                        break;

                    case 2:
                        arg1 = args[1];
                        if (arg0 != null) {
                            if (arg1 != null) {
                                if (arg0 instanceof String)
                                    new TArchiveDetector((String) arg0, (FsArchiveDriver<?>) arg1);
                                else if (arg1 instanceof Object[][])
                                    new TArchiveDetector((TArchiveDetector) arg0, (Object[][]) arg1);
                                else
                                    new TArchiveDetector((TArchiveDetector) arg0, (Map<FsScheme, FsDriver>) arg1);
                                fail("Index " + i);
                            } else {
                                assert arg0 != null;
                                assert arg1 == null;
                                if (arg0 instanceof String) {
                                    new TArchiveDetector((String) arg0, null);
                                    fail("Index " + i);
                                } else {
                                    try {
                                        new TArchiveDetector((TArchiveDetector) arg0, (Object[][]) null);
                                        fail("Index " + i);
                                    } catch (Throwable failure) {
                                        assertTrue(expected.isAssignableFrom(failure.getClass()));
                                    }
                                    try {
                                        new TArchiveDetector((TArchiveDetector) arg0, (Map<FsScheme, FsDriver>) null);
                                        fail("Index " + i);
                                    } catch (Throwable failure) {
                                        assertTrue(expected.isAssignableFrom(failure.getClass()));
                                    }
                                }
                            }
                        } else {
                            assert arg0 == null;
                            if (arg1 != null) {
                                if (arg1 instanceof FsArchiveDriver<?>)
                                    new TArchiveDetector(null, (FsArchiveDriver<?>) arg1);
                                else if (arg1 instanceof Object[][])
                                    new TArchiveDetector(null, (Object[][]) arg1);
                                else
                                    new TArchiveDetector(null, (Map<FsScheme, FsDriver>) arg1);
                                fail("Index " + i);
                            } else {
                                assert arg0 == null;
                                assert arg1 == null;
                                try {
                                    new TArchiveDetector((String) null, (FsArchiveDriver<?>) null);
                                    fail("Index " + i);
                                } catch (Throwable failure) {
                                    assertTrue(expected.isAssignableFrom(failure.getClass()));
                                }
                                try {
                                    new TArchiveDetector((TArchiveDetector) null, (Object[][]) null);
                                    fail("Index " + i);
                                } catch (Throwable failure) {
                                    assertTrue(expected.isAssignableFrom(failure.getClass()));
                                }
                                try {
                                    new TArchiveDetector((TArchiveDetector) null, (Map<FsScheme, FsDriver>) null);
                                    fail("Index " + i);
                                } catch (Throwable failure) {
                                    assertTrue(expected.isAssignableFrom(failure.getClass()));
                                }
                            }
                        }
                        break;

                    case 3:
                        arg1 = args[1];
                        arg2 = args[2];
                        new TArchiveDetector((TArchiveDetector) arg0, (String) arg1, (FsArchiveDriver<?>) arg2);
                        fail("Index " + i);
                        break;

                    default:
                        throw new AssertionError();
                }
            } catch (Throwable ex) {
                assertTrue(expected.isAssignableFrom(ex.getClass()));
            }
        }
    }

    @Test
    public void testGetSuffixes() {
        assertSuffixes(new String[] {
            "zip", "zip",
            "zip", ".zip",
            "zip", "|zip",
            "zip", "zip|",
            "zip", "zip|zip",
            "zip", "zip|.zip",
            "zip", "zip||zip",
            "zip", "zip|zip|",
            "zip", ".zip|",
            "zip", ".zip|zip",
            "zip", ".zip|.zip",
            "zip", ".zip||zip",
            "zip", ".zip|zip|",
            "zip", "|zip|",
            "zip", "|zip|zip",
            "zip", "|zip|.zip",
            "zip", "|zip||zip",
            "zip", "|zip|zip|",

            "zip", "ZIP",
            "zip", ".ZIP",
            "zip", "|ZIP",
            "zip", "ZIP|",
            "zip", "ZIP|ZIP",
            "zip", "ZIP|.ZIP",
            "zip", "ZIP||ZIP",
            "zip", "ZIP|ZIP|",
            "zip", ".ZIP|",
            "zip", ".ZIP|ZIP",
            "zip", ".ZIP|.ZIP",
            "zip", ".ZIP||ZIP",
            "zip", ".ZIP|ZIP|",
            "zip", "|ZIP|",
            "zip", "|ZIP|ZIP",
            "zip", "|ZIP|.ZIP",
            "zip", "|ZIP||ZIP",
            "zip", "|ZIP|ZIP|",

            "jar|zip", "JAR|ZIP",
            "jar|zip", "ZIP|JAR",
            "jar|zip", "|ZIP|JAR",
            "jar|zip", "ZIP|JAR|",
            "jar|zip", "|ZIP|JAR|",
            "jar|zip", "||ZIP|JAR|",
            "jar|zip", "|ZIP||JAR|",
            "jar|zip", "|ZIP|JAR||",

            "jar|zip", ".JAR|.ZIP",
            "jar|zip", ".ZIP|.JAR",
            "jar|zip", "|.ZIP|.JAR",
            "jar|zip", ".ZIP|.JAR|",
            "jar|zip", "|.ZIP|.JAR|",
            "jar|zip", "||.ZIP|.JAR|",
            "jar|zip", "|.ZIP||.JAR|",
            "jar|zip", "|.ZIP|.JAR||",
        });
    }

    private void assertSuffixes(final String[] args) {
        for (int i = 0; i < args.length; i++) {
            final String result = args[i++];
            final String suffixes = args[i];
            TArchiveDetector
            detector = new TArchiveDetector(suffixes, DRIVER);
            assertEquals(result, detector.toString());
            detector = new TArchiveDetector(NULL, suffixes, DRIVER);
            assertEquals(result, detector.toString());
            detector = new TArchiveDetector(NULL, new Object[][] {{ suffixes, DRIVER }});
            assertEquals(result, detector.toString());
        }
    }

    @Test
    public void testGetSuffixesForNullDrivers() {
        TArchiveDetector detector = new TArchiveDetector(
                NULL, "zip", null); // remove zip suffix
        assertEquals("", detector.toString());
        detector = new TArchiveDetector(
                NULL, ".ZIP", null); // remove zip suffix
        assertEquals("", detector.toString());
    }

    @Test
    public void testGetArchiveDriver() {
        assertDefaultArchiveDetector(NULL, new Object[] {
            null, "",
            null, ".",
            null, ".all",
            null, ".default",
            null, ".ear",
            null, ".exe",
            null, ".jar",
            null, ".null",
            null, ".tar",
            null, ".tar.bz2",
            null, ".tar.gz",
            null, ".tbz2",
            null, ".tgz",
            null, ".tzp",
            null, ".war",
            null, ".z",
            null, ".zip",
            null, ".zip.rae",
            null, ".zip.raes",
            null, "test",
            null, "test.",
            null, "test.all",
            null, "test.default",
            null, "test.ear",
            null, "test.exe",
            null, "test.jar",
            null, "test.null",
            null, "test.tar",
            null, "test.tar.bz2",
            null, "test.tar.gz",
            null, "test.tbz2",
            null, "test.tgz",
            null, "test.tzp",
            null, "test.war",
            null, "test.z",
            null, "test.zip",
            null, "test.zip.rae",
            null, "test.zip.raes",
        });

        assertDefaultArchiveDetector(detector, new Object[] {
            null, "",
            null, ".",
            null, ".all",
            null, ".default",
            DRIVER, ".ear",
            DRIVER, ".exe",
            DRIVER, ".jar",
            null, ".null",
            DRIVER, ".tar",
            DRIVER, ".tar.bz2",
            DRIVER, ".tar.gz",
            DRIVER, ".tbz2",
            DRIVER, ".tgz",
            DRIVER, ".tzp",
            DRIVER, ".war",
            null, ".z",
            DRIVER, ".zip",
            DRIVER, ".zip.rae",
            DRIVER, ".zip.raes",
            null, "test",
            null, "test.",
            null, "test.all",
            null, "test.default",
            DRIVER, "test.ear",
            DRIVER, "test.exe",
            DRIVER, "test.jar",
            null, "test.null",
            DRIVER, "test.tar",
            DRIVER, "test.tar.bz2",
            DRIVER, "test.tar.gz",
            DRIVER, "test.tbz2",
            DRIVER, "test.tgz",
            DRIVER, "test.tzp",
            DRIVER, "test.war",
            null, "test.z",
            DRIVER, "test.zip",
            DRIVER, "test.zip.rae",
            DRIVER, "test.zip.raes",
        });
    }

    private void assertDefaultArchiveDetector(
            TArchiveDetector detector,
            final Object[] args) {
        try {
            detector.getScheme(null);
            fail("Expected NullPointerException!");
        } catch (NullPointerException expected) {
        }

        try {
            detector = new TArchiveDetector(detector, new Object[][] {
                { "foo", "java.lang.Object", },
                { "bar", "java.io.FilterInputStream", },
            });
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }

        for (int i = 0; i < args.length; i++) {
            final FsArchiveDriver<?> result = (FsArchiveDriver<?>) args[i++];
            final String path = (String) args[i];
            assertDefaultArchiveDetector(detector, result, path);

            // Add level of indirection in order to test caching.
            detector = new TArchiveDetector(detector, new Object[0][0]);
            assertDefaultArchiveDetector(detector, result, path);
        }
    }

    private void assertDefaultArchiveDetector(
            final TArchiveDetector detector,
            final FsDriver result,
            final String path) {
        final String lpath = path.toLowerCase(Locale.ENGLISH);
        final String upath = path.toUpperCase(Locale.ENGLISH);

        if (null != result) {
            assertThat(detector.getScheme(lpath), equalTo(detector.getScheme(upath)));
            assertThat(detector.getDriver(detector.getScheme(lpath)),
                    sameInstance(result));
            assertThat(detector.getDriver(detector.getScheme(upath)),
                    sameInstance(result));
        } else {
            assertThat(detector.getScheme(lpath), nullValue());
            assertThat(detector.getScheme(upath), nullValue());
        }
    }
}
