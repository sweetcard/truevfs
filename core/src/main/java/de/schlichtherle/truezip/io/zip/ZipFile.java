/*
 * Copyright (C) 2005-2010 Schlichtherle IT Services
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

package de.schlichtherle.truezip.io.zip;

import de.schlichtherle.truezip.io.rof.ReadOnlyFile;
import de.schlichtherle.truezip.io.rof.SimpleReadOnlyFile;
import de.schlichtherle.truezip.io.util.SynchronizedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.zip.ZipException;

/**
 * Drop-in replacement for {@link java.util.zip.ZipFile java.util.zip.ZipFile}.
 * <p>
 * Where the constructors of this class accept a {@code charset}
 * parameter, this is used to decode comments and entry names in the ZIP file.
 * However, if an entry has bit 11 set in its General Purpose Bit Flag,
 * then this parameter is ignored and "UTF-8" is used for this entry.
 * This is in accordance to Appendix D of PKWARE's ZIP File Format
 * Specification, version 6.3.0 and later.
 * <p>
 * This class is able to skip a preamble like the one found in self extracting
 * archives.
 * <p>
 * Note that the entries returned by this class are instances of
 * {@code de.schlichtherle.truezip.io.zip.ZipEntry} instead of
 * {@code java.util.zip.ZipEntry}.
 * <p>
 * This class is thread-safe.
 *
 * @author Christian Schlichtherle
 * @version $Id$
 * @see ZipOutputStream
 */
public class ZipFile extends BasicZipFile<ZipEntry> {

    private final String name;

    /**
     * Equivalent to {@link #ZipFile(String, String, boolean, boolean)
     * ZipFile(name, DEFAULT_CHARSET, true, false)}
     */
    public ZipFile(String name)
    throws  NullPointerException,
            FileNotFoundException,
            ZipException,
            IOException {
        this(name, DEFAULT_CHARSET, true, false);
    }

    /**
     * Equivalent to {@link #ZipFile(String, String, boolean, boolean)
     * ZipFile(name, charset, true, false)}
     */
    public ZipFile(String name, String charset)
    throws  NullPointerException,
            UnsupportedEncodingException,
            FileNotFoundException,
            ZipException,
            IOException {
        this(name, charset, true, false);
    }

    /**
     * Opens the ZIP file identified by the given path name for reading its
     * entries.
     *
     * @param name The path name of the file.
     * @param charset The charset to use for decoding entry names and ZIP file
     *        comment.
     * @param preambled If this is {@code true}, then the ZIP file may have a
     *        preamble.
     *        Otherwise, the ZIP file must start with either a Local File
     *        Header (LFH) signature or an End Of Central Directory (EOCD)
     *        Header, causing this constructor to fail if the file is actually
     *        a false positive ZIP file, i.e. not compatible to the ZIP File
     *        Format Specification.
     *        This may be useful to read Self Extracting ZIP files (SFX), which
     *        usually contain the application code required for extraction in
     *        the preamble.
     * @param postambled If this is {@code true}, then the ZIP file may have a
     *        postamble of arbitrary length.
     *        Otherwise, the ZIP file must not have a postamble which exceeds
     *        64KB size, including the End Of Central Directory record
     *        (i.e. including the ZIP file comment), causing this constructor
     *        to fail if the file is actually a false positive ZIP file, i.e.
     *        not compatible to the ZIP File Format Specification.
     *        This may be useful to read Self Extracting ZIP files (SFX) with
     *        large postambles.
     * @throws NullPointerException If {@code name} or {@code charset} is
     *         {@code null}.
     * @throws UnsupportedEncodingException If charset is not supported by
     *         this JVM.
     * @throws FileNotFoundException If the file cannot get opened for reading.
     * @throws ZipException If the file is not compatible with the ZIP File
     *         Format Specification.
     * @throws IOException On any other I/O related issue.
     */
    public ZipFile(
            final String name,
            final String charset,
            final boolean preambled,
            final boolean postambled)
    throws  NullPointerException,
            UnsupportedEncodingException,
            FileNotFoundException,
            ZipException,
            IOException {
        super(  new SimpleReadOnlyFileSource(name),
                charset, DefaultZipEntryFactory.SINGLETON,
                preambled, postambled);
        this.name = name;
    }

    /**
     * Equivalent to {@link #ZipFile(File, String, boolean, boolean)
     * ZipFile(file, DEFAULT_CHARSET, true, false)}
     */
    public ZipFile(File file)
    throws  NullPointerException,
            FileNotFoundException,
            ZipException,
            IOException {
        this(file, DEFAULT_CHARSET, true, false);
    }

    /**
     * Equivalent to {@link #ZipFile(File, String, boolean, boolean)
     * ZipFile(file, charset, true, false)}
     */
    public ZipFile(File file, String charset)
    throws  NullPointerException,
            UnsupportedEncodingException,
            FileNotFoundException,
            ZipException,
            IOException {
        this(file, charset, true, false);
    }

    /**
     * Opens the given {@link File} for reading its entries.
     *
     * @param file The file.
     * @param charset The charset to use for decoding entry names and ZIP file
     *        comment.
     * @param preambled If this is {@code true}, then the ZIP file may have a
     *        preamble.
     *        Otherwise, the ZIP file must start with either a Local File
     *        Header (LFH) signature or an End Of Central Directory (EOCD)
     *        Header, causing this constructor to fail if the file is actually
     *        a false positive ZIP file, i.e. not compatible to the ZIP File
     *        Format Specification.
     *        This may be useful to read Self Extracting ZIP files (SFX), which
     *        usually contain the application code required for extraction in
     *        the preamble.
     * @param postambled If this is {@code true}, then the ZIP file may have a
     *        postamble of arbitrary length.
     *        Otherwise, the ZIP file must not have a postamble which exceeds
     *        64KB size, including the End Of Central Directory record
     *        (i.e. including the ZIP file comment), causing this constructor
     *        to fail if the file is actually a false positive ZIP file, i.e.
     *        not compatible to the ZIP File Format Specification.
     *        This may be useful to read Self Extracting ZIP files (SFX) with
     *        large postambles.
     * @throws NullPointerException If {@code file} or {@code charset} is
     *         {@code null}.
     * @throws UnsupportedEncodingException If charset is not supported by
     *         this JVM.
     * @throws FileNotFoundException If the file cannot get opened for reading.
     * @throws ZipException If the file is not compatible with the ZIP File
     *         Format Specification.
     * @throws IOException On any other I/O related issue.
     */
    public ZipFile(
            final File file,
            final String charset,
            final boolean preambled,
            final boolean postambled)
    throws  NullPointerException,
            UnsupportedEncodingException,
            FileNotFoundException,
            ZipException,
            IOException {
        super(  new SimpleReadOnlyFileSource(file),
                charset, DefaultZipEntryFactory.SINGLETON,
                preambled, postambled);
        this.name = file.getPath();
    }

    /**
     * Equivalent to {@link #ZipFile(ReadOnlyFile, String, boolean, boolean)
     * ZipFile(rof, DEFAULT_CHARSET, true, false)}
     */
    public ZipFile(ReadOnlyFile rof)
    throws  NullPointerException,
            FileNotFoundException,
            ZipException,
            IOException {
        this(rof, DEFAULT_CHARSET, true, false);
    }

    /**
     * Equivalent to {@link #ZipFile(ReadOnlyFile, String, boolean, boolean)
     * ZipFile(rof, charset, true, false)}
     */
    public ZipFile(ReadOnlyFile rof, String charset)
    throws  NullPointerException,
            UnsupportedEncodingException,
            FileNotFoundException,
            ZipException,
            IOException {
        this(rof, charset, true, false);
    }

    /**
     * Opens the given {@link ReadOnlyFile} for reading its entries.
     *
     * @param rof The random access read only file.
     * @param charset The charset to use for decoding entry names and ZIP file
     *        comment.
     * @param preambled If this is {@code true}, then the ZIP file may have a
     *        preamble.
     *        Otherwise, the ZIP file must start with either a Local File
     *        Header (LFH) signature or an End Of Central Directory (EOCD)
     *        Header, causing this constructor to fail if the file is actually
     *        a false positive ZIP file, i.e. not compatible to the ZIP File
     *        Format Specification.
     *        This may be useful to read Self Extracting ZIP files (SFX), which
     *        usually contain the application code required for extraction in
     *        the preamble.
     * @param postambled If this is {@code true}, then the ZIP file may have a
     *        postamble of arbitrary length.
     *        Otherwise, the ZIP file must not have a postamble which exceeds
     *        64KB size, including the End Of Central Directory record
     *        (i.e. including the ZIP file comment), causing this constructor
     *        to fail if the file is actually a false positive ZIP file, i.e.
     *        not compatible to the ZIP File Format Specification.
     *        This may be useful to read Self Extracting ZIP files (SFX) with
     *        large postambles.
     * @throws NullPointerException If {@code rof} or {@code charset} is
     *         {@code null}.
     * @throws UnsupportedEncodingException If charset is not supported by
     *         this JVM.
     * @throws FileNotFoundException If the file cannot get opened for reading.
     * @throws ZipException If the file is not compatible with the ZIP File
     *         Format Specification.
     * @throws IOException On any other I/O related issue.
     */
    public ZipFile(
            ReadOnlyFile rof,
            String charset,
            boolean preambled,
            boolean postambled)
    throws  NullPointerException,
            UnsupportedEncodingException,
            FileNotFoundException,
            ZipException,
            IOException {
        super(  rof, charset, preambled, postambled,
                DefaultZipEntryFactory.SINGLETON);
        this.name = null;
    }

    private static class SimpleReadOnlyFileSource
    implements ReadOnlyFileSource {
        final File file;

        public SimpleReadOnlyFileSource(File file) {
            this.file = file;
        }

        public SimpleReadOnlyFileSource(String name) {
            this.file = new File(name);
        }

        public ReadOnlyFile fetch() throws IOException {
            return new SimpleReadOnlyFile(file);
        }

        public void release(ReadOnlyFile rof) throws IOException {
            rof.close();
        }
    }

    /**
     * Returns the path name of the ZIP file or {@code null} if this object
     * was created with a {@link ReadOnlyFile}.
     */
    public String getName() {
        return name;
    }

    /**
     * Enumerates clones of all entries in this ZIP file.
     *
     * @deprecated Use {@link #iterator()} instead.
     */
    @Override
    public synchronized Enumeration<? extends ZipEntry> entries() {
        class CloneEnumeration implements Enumeration<ZipEntry> {
            final Enumeration<? extends ZipEntry> e = ZipFile.super.entries();

            public boolean hasMoreElements() {
                return e.hasMoreElements();
            }

            public ZipEntry nextElement() {
                return e.nextElement().clone();
            }
        }
        return new CloneEnumeration();
    }

    /**
     * Iterates through clones for all entries in this ZIP file.
     * The iteration does not support element removal.
     */
    @Override
    public synchronized Iterator<ZipEntry> iterator() {
        class EntryIterator implements Iterator<ZipEntry> {
            private final Iterator<ZipEntry> i = ZipFile.super.iterator();

            public boolean hasNext() {
                return i.hasNext();
            }

            public ZipEntry next() {
                return i.next().clone();
            }

            public void remove() {
                throw new UnsupportedOperationException("read-only iterator");
            }
        }
        return new EntryIterator();
    }

    /**
     * Returns a clone of the entry for the given name or {@code null} if no
     * entry with this name exists.
     *
     * @param name the name of the ZIP entry.
     */
    @Override
    public synchronized ZipEntry getEntry(String name) {
        final ZipEntry ze = super.getEntry(name);
        return ze != null ? ze.clone() : null;
    }

    @Override
    public synchronized InputStream getPreambleInputStream() throws IOException {
        return new SynchronizedInputStream(
                super.getPreambleInputStream(),
                this);
    }

    @Override
    public synchronized InputStream getPostambleInputStream() throws IOException {
        return new SynchronizedInputStream(
                super.getPostambleInputStream(),
                this);
    }

    @Override
    public synchronized boolean busy() {
        return super.busy();
    }

    @Override
    protected synchronized InputStream getInputStream(
            String name, boolean check, boolean inflate)
    throws  IOException {
        final InputStream in = super.getInputStream(name, check, inflate);
        return in != null ? new SynchronizedInputStream(in, this) : null;
    }

    @Override
    public synchronized void close() throws IOException {
        super.close();
    }
}
