/*
 * Copyright (C) 2005-2012 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
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
import de.schlichtherle.truezip.test.TestConfig;
import de.schlichtherle.truezip.test.ThrowControl;
import de.schlichtherle.truezip.util.HashMaps;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * @author  Christian Schlichtherle
 */
@NotThreadSafe
public class MockArchiveDriverEntryContainer
implements EntryContainer<MockArchiveDriverEntry> {

    final Map<String, MockArchiveDriverEntry> entries;
    private final TestConfig config;
    private @CheckForNull ThrowControl control;

    public static MockArchiveDriverEntryContainer create(
            @CheckForNull TestConfig config) {
        if (null == config)
            config = TestConfig.get();
        return new MockArchiveDriverEntryContainer(
                new LinkedHashMap<String, MockArchiveDriverEntry>(
                    HashMaps.initialCapacity(config.getNumEntries())),
                config);
    }

    private MockArchiveDriverEntryContainer(
            final Map<String, MockArchiveDriverEntry> entries,
            final TestConfig config) {
        this.entries = entries;
        this.config = config;
    }

    private ThrowControl getThrowControl() {
        final ThrowControl control = this.control;
        return null != control ? control : (this.control = config.getThrowControl());
    }

    private void checkUndeclaredExceptions() {
        getThrowControl().check(this, RuntimeException.class);
        getThrowControl().check(this, Error.class);
    }

    private IOPoolProvider getIOPoolProvider() {
        return config.getIOPoolProvider();
    }

    final IOPool<?> getIOPool() {
        return getIOPoolProvider().get();
    }

    @Override
    public int getSize() {
        checkUndeclaredExceptions();
        return entries.size();
    }

    @Override
    public Iterator<MockArchiveDriverEntry> iterator() {
        checkUndeclaredExceptions();
        return Collections.unmodifiableCollection(entries.values()).iterator();
    }

    @Override
    public MockArchiveDriverEntry getEntry(String name) {
        checkUndeclaredExceptions();
        return entries.get(name);
    }

    public InputShop<MockArchiveDriverEntry> newInputShop() {
        checkUndeclaredExceptions();
        return new ThrowingInputShop<MockArchiveDriverEntry>(
                new DisconnectingInputShop<MockArchiveDriverEntry>(
                    new MockInputShop(entries, config)),
                config);
    }

    public OutputShop<MockArchiveDriverEntry> newOutputShop() {
        checkUndeclaredExceptions();
        return new ThrowingOutputShop<MockArchiveDriverEntry>(
                new DisconnectingOutputShop<MockArchiveDriverEntry>(
                    new MockOutputShop(entries, config)),
                config);
    }

    private static final class MockInputShop
    extends MockArchiveDriverEntryContainer
    implements InputShop<MockArchiveDriverEntry> {

        MockInputShop(  Map<String, MockArchiveDriverEntry> entries,
                        TestConfig config) {
            super(entries, config);
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
                    return getLocalTarget()
                            .getBuffer(getIOPool())
                            .getInputSocket();
                }
            } // Input

            return new Input();
        }

        @Override
        public void close() { }
    } // MockInputShop

    private static final class MockOutputShop
    extends MockArchiveDriverEntryContainer
    implements OutputShop<MockArchiveDriverEntry> {

        MockOutputShop( Map<String, MockArchiveDriverEntry> entries,
                        TestConfig config) {
            super(entries, config);
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
                    return getLocalTarget()
                            .getBuffer(getIOPool())
                            .getOutputSocket();
                }

                void copyProperties() {
                    final MockArchiveDriverEntry dst = getLocalTarget();
                    final IOPool.Entry<?> src;
                    try {
                        src = dst.getBuffer(getIOPool());
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