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
import static de.schlichtherle.truezip.entry.Entry.ALL_SIZE_SET;
import de.schlichtherle.truezip.entry.Entry.Access;
import de.schlichtherle.truezip.entry.Entry.Size;
import de.schlichtherle.truezip.entry.EntryContainer;
import de.schlichtherle.truezip.io.DecoratingOutputStream;
import de.schlichtherle.truezip.rof.ReadOnlyFile;
import de.schlichtherle.truezip.socket.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * @author  Christian Schlichtherle
 * @version $Id$
 */
@NotThreadSafe
public class MockArchiveDriverEntryContainer
implements EntryContainer<MockArchiveDriverEntry> {

    final IOPool<?> pool;
    final Map<String, MockArchiveDriverEntry> entries;

    public static MockArchiveDriverEntryContainer create(
            final IOPoolProvider provider) {
        return create(provider, 32);
    }

    public static MockArchiveDriverEntryContainer create(
            final IOPoolProvider provider,
            final int initialCapacity) {
        final IOPool<?> pool = provider.get();
        if (null == pool)
            throw new NullPointerException();
        return new MockArchiveDriverEntryContainer(
                pool,
                new LinkedHashMap<String, MockArchiveDriverEntry>(initialCapacity));
    }

    private MockArchiveDriverEntryContainer(
            final IOPool<?> pool,
            final Map<String, MockArchiveDriverEntry> entries) {
        this.pool = pool;
        this.entries = entries;
    }

    @Override
    public int getSize() {
        return entries.size();
    }

    @Override
    public Iterator<MockArchiveDriverEntry> iterator() {
        return Collections.unmodifiableCollection(entries.values()).iterator();
    }

    @Override
    public MockArchiveDriverEntry getEntry(String name) {
        return entries.get(name);
    }

    public InputShop<MockArchiveDriverEntry> newInputShop() {
        return new DisconnectingInputShop<MockArchiveDriverEntry>(
                new MockInputShop(pool, entries));
    }

    private static final class MockInputShop
    extends MockArchiveDriverEntryContainer
    implements InputShop<MockArchiveDriverEntry> {

        MockInputShop(  IOPool<?> pool,
                        Map<String, MockArchiveDriverEntry> entries) {
            super(pool, entries);
        }

        @Override
        public InputSocket<? extends MockArchiveDriverEntry> getInputSocket(
                final String name) {
            if (null == name)
                throw new NullPointerException();

            class Input extends InputSocket<MockArchiveDriverEntry> {
                @Override
                public MockArchiveDriverEntry getLocalTarget()
                throws IOException {
                    final MockArchiveDriverEntry entry = entries.get(name);
                    if (null == entry)
                        throw new FileNotFoundException(name + " (entry not found)");
                    return entry;
                }

                @Override
                public SeekableByteChannel newSeekableByteChannel()
                throws IOException {
                    return getBufferInputSocket().newSeekableByteChannel();
                }

                @Override
                public ReadOnlyFile newReadOnlyFile() throws IOException {
                    return getBufferInputSocket().newReadOnlyFile();
                }

                @Override
                public InputStream newInputStream() throws IOException {
                    return getBufferInputSocket().newInputStream();
                }

                InputSocket<? extends IOEntry<?>>
                getBufferInputSocket() throws IOException {
                    return getLocalTarget().getBuffer(pool).getInputSocket();
                }
            } // Input

            return new Input();
        }

        @Override
        public void close() { }
    } // MockInputShop

    public OutputShop<MockArchiveDriverEntry> newOutputShop() {
        return new DisconnectingOutputShop<MockArchiveDriverEntry>(
                new MockOutputShop(pool, entries));
    }

    private static final class MockOutputShop
    extends MockArchiveDriverEntryContainer
    implements OutputShop<MockArchiveDriverEntry> {

        MockOutputShop( IOPool<?> pool,
                        Map<String, MockArchiveDriverEntry> entries) {
            super(pool, entries);
        }

        @Override
        public OutputSocket<? extends MockArchiveDriverEntry> getOutputSocket(
                final MockArchiveDriverEntry entry) {
            if (null == entry)
                throw new NullPointerException();

            class Output extends OutputSocket<MockArchiveDriverEntry> {
                @Override
                public MockArchiveDriverEntry getLocalTarget() {
                    return entry;
                }

                @Override
                public SeekableByteChannel newSeekableByteChannel()
                throws IOException {
                    return getBufferOutputSocket().newSeekableByteChannel();
                }

                @Override
                public OutputStream newOutputStream()
                throws IOException {
                    class MockOutputStream extends DecoratingOutputStream {
                        MockOutputStream() throws IOException {
                            super(getBufferOutputSocket().newOutputStream());
                        }

                        @Override
                        public void close() throws IOException {
                            delegate.close();
                            copyProperties();
                        }
                    } // MockOutputStream

                    return new MockOutputStream();
                }

                OutputSocket<? extends IOEntry<?>>
                getBufferOutputSocket() throws IOException {
                    entries.put(entry.getName(), entry);
                    return getLocalTarget().getBuffer(pool).getOutputSocket();
                }

                void copyProperties() {
                    final MockArchiveDriverEntry dst = getLocalTarget();
                    final IOPool.Entry<?> src;
                    try {
                        src = dst.getBuffer(pool);
                    } catch (IOException ex) {
                        throw new AssertionError(ex);
                    }
                    for (final Size type : ALL_SIZE_SET)
                        dst.setSize(type, src.getSize(type));
                    for (final Access type : ALL_ACCESS_SET)
                        dst.setTime(type, src.getTime(type));
                }
            } // Output

            return new Output();
        }

        @Override
        public void close() { }
    } // MockOutputShop
}