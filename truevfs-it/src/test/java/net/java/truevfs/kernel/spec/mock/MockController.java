/*
 * Copyright (C) 2005-2015 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.java.truevfs.kernel.spec.mock;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.NoSuchFileException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import net.java.truecommons.cio.*;
import net.java.truecommons.cio.Entry.Access;
import net.java.truecommons.cio.Entry.Type;
import net.java.truecommons.shed.BitField;
import net.java.truevfs.kernel.spec.*;
import net.java.truevfs.kernel.spec.io.ThrowingInputStream;
import net.java.truevfs.kernel.spec.io.ThrowingOutputStream;
import net.java.truevfs.kernel.spec.io.ThrowingSeekableChannel;

/**
 * @author Christian Schlichtherle
 */
@ThreadSafe
public class MockController
extends FsAbstractController {

    private final @Nullable FsController parent;
    @SuppressWarnings("CollectionWithoutInitialCapacity")
    private final ConcurrentMap<FsNodeName, IoEntry<?>>
            map = new ConcurrentHashMap<>();
    private final FsTestConfig config;
    private volatile @CheckForNull FsThrowManager control;

    public MockController(FsModel model, @CheckForNull FsController parent) {
        this(model, parent, null);
    }

    /**
     * Constructs a new mock controller.
     *
     * @param model The file system model.
     * @param parent The parent file system controller.
     * @param config The mocking configuration.
     */
    public MockController(  final FsModel model,
                            final @CheckForNull FsController parent,
                            final @CheckForNull FsTestConfig config) {
        super(model);
        assert null == model.getParent()
                ? null == parent
                : model.getParent().equals(parent.getModel());
        this.parent = parent;
        this.config = null != config ? config : FsTestConfig.get();
    }

    private FsThrowManager getThrowControl() {
        final FsThrowManager control = this.control;
        return null != control ? control : (this.control = config.getThrowControl());
    }

    private void checkAllExceptions(final Object thiz) throws IOException {
        getThrowControl().check(thiz, IOException.class);
        checkUndeclaredExceptions(this);
    }

    private void checkUndeclaredExceptions(final Object thiz) {
        getThrowControl().check(thiz, RuntimeException.class);
        getThrowControl().check(thiz, Error.class);
    }

    @Override
    public FsController getParent() {
        checkUndeclaredExceptions(this);
        return parent;
    }

    @Override
    public FsNode node(
            final BitField<FsAccessOption> options,
            final FsNodeName name)
    throws IOException {
        checkAllExceptions(this);
        assert null != name;
        assert null != options;
        throw new UnsupportedOperationException();
    }

    @Override
    public void checkAccess(
            final BitField<FsAccessOption> options,
            final FsNodeName name,
            final BitField<Access> types)
    throws IOException {
        checkAllExceptions(this);
        assert null != name;
        assert null != options;
        assert null != types;
        throw new UnsupportedOperationException();
    }

    @Override
    public void setReadOnly(
            final BitField<FsAccessOption> options,
            final FsNodeName name)
    throws IOException {
        checkAllExceptions(this);
        assert null != name;
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean setTime(
            final BitField<FsAccessOption> options,
            final FsNodeName name,
            final Map<Access, Long> times)
    throws IOException {
        checkAllExceptions(this);
        assert null != name;
        assert null != times;
        assert null != options;
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean setTime(
            final BitField<FsAccessOption> options,
            final FsNodeName name,
            final BitField<Access> types, long value)
    throws IOException {
        checkAllExceptions(this);
        assert null != name;
        assert null != types;
        assert null != options;
        throw new UnsupportedOperationException();
    }

    @Override
    public InputSocket<?> input(
            final BitField<FsAccessOption> options,
            final FsNodeName name) {
        checkUndeclaredExceptions(this);
        assert null != name;
        assert null != options;

        class Input extends DelegatingInputSocket<Entry> {
            @Override
            protected InputSocket<? extends Entry> socket()
            throws IOException {
                checkAllExceptions(this);
                final IoEntry<?> buffer = map.get(name);
                if (null == buffer)
                    throw new NoSuchFileException(name.toString());
                return buffer.input();
            }

            @Override
            public SeekableByteChannel channel(OutputSocket<? extends Entry> peer)
            throws IOException {
                return new ThrowingSeekableChannel(socket().channel(peer),
                        config.getThrowControl());
            }

            @Override
            public InputStream stream(OutputSocket<? extends Entry> peer)
            throws IOException {
                return new ThrowingInputStream(socket().stream(peer),
                        config.getThrowControl());
            }
        } // Input

        return new Input();
    }

    @Override
    public OutputSocket<?> output(
            final BitField<FsAccessOption> options,
            final FsNodeName name,
            final @CheckForNull Entry template) {
        checkUndeclaredExceptions(this);
        assert null != name;
        assert null != options;

        class Output extends DelegatingOutputSocket<Entry> {
            @Override
            protected OutputSocket<? extends Entry> socket()
            throws IOException {
                checkAllExceptions(this);
                final IoEntry<?> n = new MemoryBuffer(
                        name.toString(), config.getDataSize());
                IoEntry<?> o = map.get(name);
                if (null == o)
                    o = map.putIfAbsent(name, n);
                return (null != o ? o : n).output();
            }

            @Override
            public SeekableByteChannel channel(InputSocket<? extends Entry> peer)
            throws IOException {
                return new ThrowingSeekableChannel(socket().channel(peer),
                        config.getThrowControl());
            }

            @Override
            public OutputStream stream(InputSocket<? extends Entry> peer)
            throws IOException {
                return new ThrowingOutputStream(socket().stream(peer),
                        config.getThrowControl());
            }
        } // Output

        return new Output();
    }

    @Override
    public void make(
            final BitField<FsAccessOption> options,
            final FsNodeName name,
            final Type type,
            final Entry template)
    throws IOException {
        checkAllExceptions(this);
        assert null != name;
        assert null != type;
        assert null != options;
        throw new UnsupportedOperationException();
    }

    @Override
    public void unlink(
            final BitField<FsAccessOption> options,
            final FsNodeName name)
    throws IOException {
        checkAllExceptions(this);
        assert null != name;
        assert null != options;
        throw new UnsupportedOperationException();
    }

    @Override
    public void sync(final BitField<FsSyncOption> options) throws FsSyncException {
        getThrowControl().check(this, FsSyncException.class);
        checkUndeclaredExceptions(this);
        assert null != options;
    }
}
