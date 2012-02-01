/*
 * Copyright 2004-2012 Schlichtherle IT Services
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.schlichtherle.truezip.fs.archive.mock;

import static de.schlichtherle.truezip.entry.Entry.ALL_ACCESS_SET;
import de.schlichtherle.truezip.entry.Entry.Access;
import static de.schlichtherle.truezip.entry.Entry.Size.DATA;
import de.schlichtherle.truezip.entry.EntryContainer;
import de.schlichtherle.truezip.io.DecoratingOutputStream;
import de.schlichtherle.truezip.rof.ReadOnlyFile;
import de.schlichtherle.truezip.socket.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author  Christian Schlichtherle
 * @version $Id$
 */
public class MockArchiveEntryContainer
implements EntryContainer<MockArchiveEntry> {

    private final Map<String, MockArchiveEntry>
            map = new LinkedHashMap<String, MockArchiveEntry>();
    private final IOPool<?> pool = new ByteArrayIOPool(2048);

    @Override
    public int getSize() {
        return map.size();
    }

    @Override
    public Iterator<MockArchiveEntry> iterator() {
        return Collections.unmodifiableCollection(map.values()).iterator();
    }

    @Override
    public MockArchiveEntry getEntry(String name) {
        return map.get(name);
    }

    public final class InputArchive
    extends MockArchiveEntryContainer
    implements InputShop<MockArchiveEntry> {
        private boolean closed;

        @Override
        public InputSocket<? extends MockArchiveEntry> getInputSocket(
                final String name) {
            if (null == name)
                throw new NullPointerException();

            class Input extends InputSocket<MockArchiveEntry> {
                @Override
                public MockArchiveEntry getLocalTarget() throws IOException {
                    final MockArchiveEntry entry = map.get(name);
                    if (null == entry)
                        throw new FileNotFoundException(name + " (entry not found)");
                    return entry;
                }

                @Override
                public ReadOnlyFile newReadOnlyFile() throws IOException {
                    if (closed)
                        throw new IOException("Input shop closed!");
                    return getLocalTarget()
                            .io
                            .getInputSocket()
                            .newReadOnlyFile();
                }

                @Override
                public InputStream newInputStream() throws IOException {
                    if (closed)
                        throw new IOException("Input shop closed!");
                    return getLocalTarget()
                            .io
                            .getInputSocket()
                            .newInputStream();
                }
            } // Input

            return new Input();
        }

        @Override
        public void close() {
            closed = true;
        }
    } // class InputArchive

    public final class OutputArchive
    extends MockArchiveEntryContainer
    implements OutputShop<MockArchiveEntry> {
        private boolean closed;

        @Override
        public OutputSocket<? extends MockArchiveEntry> getOutputSocket(
                final MockArchiveEntry entry) {
            if (null == entry)
                throw new NullPointerException();

            class Output extends OutputSocket<MockArchiveEntry> {
                @Override
                public MockArchiveEntry getLocalTarget() {
                    return entry;
                }

                @Override
                public OutputStream newOutputStream()
                throws IOException {
                    if (closed)
                        throw new IOException("Output shop closed!");
                    map.put(entry.getName(), entry);
                    IOPool.Entry<?> _io = entry.io;
                    if (null == _io)
                        entry.io = _io = pool.allocate(); // note no call to io.release()!
                    final IOPool.Entry<?> io = _io;

                    // TODO: Add check if the output shop has been closed on
                    // each method call!
                    class Stream extends DecoratingOutputStream {
                        Stream() throws IOException {
                            super(io.getOutputSocket().newOutputStream());
                        }

                        @Override
                        public void close() throws IOException {
                            try {
                                delegate.close();
                            } finally {
                                // Never copy anything but the DATA size!
                                entry.setSize(DATA, io.getSize(DATA));
                                for (Access type : ALL_ACCESS_SET)
                                    entry.setTime(type, io.getTime(type));
                            }
                        }
                    } // Stream

                    return new Stream();
                }
            } // Output

            return new Output();
        }

        @Override
        public void close() {
            closed = true;
        }
    } // class OutputArchive
}
