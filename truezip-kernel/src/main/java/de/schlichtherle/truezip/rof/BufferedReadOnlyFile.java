/*
 * Copyright (C) 2006-2011 Schlichtherle IT Services
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

package de.schlichtherle.truezip.rof;

import de.schlichtherle.truezip.io.Streams;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import net.jcip.annotations.NotThreadSafe;

/**
 * A {@link ReadOnlyFile} implementation which provides buffered random read
 * only access to another {@code ReadOnlyFile}.
 * <p>
 * Note that this class implements a virtual file pointer.
 * Thus, if you would like to use the decorated read only file again after
 * you have finished using the decorating read only file, then you should
 * synchronize their file pointers using the following idiom:
 * <pre>
 *     ReadOnlyFile rof = new DefaultReadOnlyFile(new File("HelloWorld.java"));
 *     try {
 *         ReadOnlyFile brof = new BufferedReadOnlyFile(rof);
 *         try {
 *             // Do any file input on frof here...
 *             brof.seek(1);
 *         } finally {
 *             // Synchronize the file pointers.
 *             rof.seek(brof.getFilePointer());
 *         }
 *         // This assertion would fail if we hadn't done the file pointer
 *         // synchronization!
 *         assert rof.getFilePointer() == 1;
 *     } finally {
 *         rof.close();
 *     }
 * </pre>
 *
 * @author  Christian Schlichtherle
 * @version $Id$
 */
@NotThreadSafe
@DefaultAnnotation(NonNull.class)
public class BufferedReadOnlyFile extends DecoratingReadOnlyFile {

    /** The default buffer length of the window to the file. */
    public static final int WINDOW_LEN = Streams.BUFFER_SIZE;

    /** Returns the smaller parameter. */
    protected static long min(long a, long b) {
        return a < b ? a : b;
    }

    /** Returns the greater parameter. */
    protected static long max(long a, long b) {
        return a < b ? b : a;
    }

    private long length;

    /**
     * The virtual file pointer in the file data.
     * This is relative to the start of the file.
     */
    private long fp;

    /**
     * The current offset in the read only file where the buffer window starts.
     * This is always a multiple of the buffer window size.
     */
    private long windowOff;

    /** The buffer window to the file data. */
    private final byte[] window;

    /**
     * Constructs a new buffered read only file.
     *
     * @param  file The file to read.
     * @throws FileNotFoundException If the file cannot get opened for reading.
     * @throws IOException On any other I/O related issue.
     */
    public BufferedReadOnlyFile(File file) throws IOException {
        this(null, file, WINDOW_LEN);
    }

    /**
     * Constructs a new buffered read only file.
     *
     * @param  file The file to read.
     * @param  windowLen The size of the buffer window in bytes.
     * @throws FileNotFoundException If the file cannot get opened for reading.
     * @throws IOException On any other I/O related issue.
     */
    public BufferedReadOnlyFile(
            final File file,
            final int windowLen)
    throws IOException {
        this(null, file, windowLen);
    }

    /**
     * Constructs a new buffered read only file.
     *
     * @param rof The read only file to read.
     * @throws FileNotFoundException If the file cannot get opened for reading.
     * @throws IOException On any other I/O related issue.
     */
    public BufferedReadOnlyFile(
            final ReadOnlyFile rof)
    throws IOException {
        this(rof, null, WINDOW_LEN);
    }

    /**
     * Constructs a new buffered read only file.
     *
     * @param rof The read only file to read.
     * @param windowLen The size of the buffer window in bytes.
     * @throws FileNotFoundException If the file cannot get opened for reading.
     * @throws IOException On any other I/O related issue.
     */
    public BufferedReadOnlyFile(
            final ReadOnlyFile rof,
            final int windowLen)
    throws IOException {
        this(rof, null, windowLen);
    }

    private BufferedReadOnlyFile(
            @CheckForNull ReadOnlyFile rof,
            final @CheckForNull File file,
            final int windowLen)
    throws IOException {
        super(rof);

        // Check parameters (fail fast).
        if (null == rof) {
            rof = new DefaultReadOnlyFile(file);
        } else { // null != rof
            assert null == file;
        }
        if (windowLen <= 0)
            throw new IllegalArgumentException();

        super.delegate = rof;
        //this.length = rof.length();
        this.fp = rof.getFilePointer();
        this.window = new byte[windowLen];
        invalidateWindow();

        assert 0 < this.window.length;
    }

    /**
     * Asserts that this file is open.
     *
     * @throws IOException If the preconditions do not hold.
     */
    protected final void assertOpen() throws IOException {
        if (null == delegate)
            throw new IOException("File is closed!");
    }

    @Override
    public long length() throws IOException {
        // Check state.
        assertOpen();

        final long length = delegate.length();
        if (length != this.length) {
            this.length = length;
            invalidateWindow();
        }
        return length;
    }

    @Override
    public long getFilePointer()
    throws IOException {
        // Check state.
        assertOpen();

        return fp;
    }

    @Override
    public void seek(final long fp)
    throws IOException {
        // Check state.
        assertOpen();

        if (fp < 0)
            throw new IOException("File pointer must not be negative!");
        final long length = length();
        if (fp > length)
            throw new IOException("File pointer (" + fp
                    + ") is larger than file length (" + length + ")!");

        this.fp = fp;
    }

    @Override
    public int read()
    throws IOException {
        // Check state.
        assertOpen();
        if (fp >= length())
            return -1;

        // Position window and return its data.
        positionWindow();
        return window[(int) (fp++ % window.length)] & 0xff;
    }

    @Override
    public int read(final byte[] buf, final int off, final int len)
    throws IOException {
        if (len == 0)
            return 0; // be fault-tolerant and compatible to RandomAccessFile

        // Check state.
        assertOpen();
        final long length = length();
        if (fp >= length)
            return -1;

        // Check parameters.
        if (0 > (off | len | buf.length - off - len))
	    throw new IndexOutOfBoundsException();

        // Setup.
        final int windowLen = window.length;
        int read = 0; // amount of read data copied to buf

        {
            // Partial read of window data at the start.
            final int o = (int) (fp % windowLen);
            if (o != 0) {
                // The file pointer is not on a window boundary.
                positionWindow();
                read = (int) min(len, windowLen - o);
                read = (int) min(read, length - fp);
                System.arraycopy(window, o, buf, off, read);
                fp += read;
            }
        }

        {
            // Full read of window data in the middle.
            while (read + windowLen < len && fp + windowLen <= length) {
                // The file pointer is starting and ending on window boundaries.
                positionWindow();
                System.arraycopy(window, 0, buf, off + read, windowLen);
                read += windowLen;
                fp += windowLen;
            }
        }

        // Partial read of window data at the end.
        if (read < len && fp < length) {
            // The file pointer is not on a window boundary.
            positionWindow();
            final int n = (int) min(len - read, length - fp);
            System.arraycopy(window, 0, buf, off + read, n);
            read += n;
            fp += n;
        }

        // Assert that at least one byte has been read if len isn't zero.
        // Note that EOF has been tested before.
        assert read > 0;
        return read;
    }

    /**
     * Closes this read only file.
     * As a side effect, this will set the reference to the decorated read
     * only file ({@link #delegate} to {@code null}.
     */
    @Override
    public void close()
    throws IOException {
        // Order is important here!
        if (null == delegate)
            return;
        try {
            delegate.close();
        } finally {
            delegate = null;
        }
    }

    //
    // Buffer window operations.
    //

    /**
     * Positions the window so that the block containing the current virtual
     * file pointer in the encrypted file is entirely contained in it.
     *
     * @throws IOException On any I/O related issue.
     *         The window is invalidated in this case.
     */
    private void positionWindow()
    throws IOException {
        // Check window position.
        final long fp = this.fp;
        final int windowLen = window.length;
        final long nextWindowOff = windowOff + windowLen;
        if (windowOff <= fp && fp < nextWindowOff)
            return;

        try {
            // Move window in the buffered file.
            windowOff = (fp / windowLen) * windowLen; // round down to multiple of window size
            if (windowOff != nextWindowOff)
                delegate.seek(windowOff);

            // Fill window until end of file or buffer.
            // This should normally complete in one loop cycle, but we do not
            // depend on this as it would be a violation of ReadOnlyFile's
            // contract.
            int n = 0;
            do {
                int read = delegate.read(window, n, windowLen - n);
                if (read < 0)
                    break;
                n += read;
            } while (n < windowLen);
        } catch (IOException ioe) {
            windowOff = -windowLen - 1; // force seek() at next positionWindow()
            throw ioe;
        }
    }

    /**
     * Forces the window to be reloaded on the next call to
     * {@link #positionWindow()}.
     */
    private void invalidateWindow() {
        windowOff = Long.MIN_VALUE;
    }
}
