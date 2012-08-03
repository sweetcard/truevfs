/*
 * Copyright (C) 2005-2012 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.java.truevfs.kernel.spec.cio;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;

/**
 * A pool of byte array I/O buffers.
 *
 * @author Christian Schlichtherle
 */
@ThreadSafe
public final class ByteArrayIoBufferPool implements IoBufferPool<ByteArrayIoBuffer> {

    private static final String BUFFER_NAME = "buffer-";

    private final int initialCapacity;
    private final AtomicInteger total = new AtomicInteger();
    private final AtomicInteger active = new AtomicInteger();

    /**
     * Constructs a new byte array I/O pool.
     *
     * @param initialCapacity the initial capacity of the array to use for
     *        writing to an allocated I/O entry.
     */
    public ByteArrayIoBufferPool(final int initialCapacity) {
        if (0 > initialCapacity)
            throw new IllegalArgumentException("Negative initial capacity: " + initialCapacity);
        this.initialCapacity = initialCapacity;
    }

    @Override
    public IoBuffer<ByteArrayIoBuffer> allocate() {
        final ByteBuffer buffer = new ByteBuffer(total.getAndIncrement());
        active.getAndIncrement();
        return buffer;
    }

    @Override
    public void release(IoBuffer<ByteArrayIoBuffer> buffer) throws IOException {
        buffer.release();
    }

    /**
     * Returns the number of I/O entries allocated but not yet released from
     * this pool.
     *
     * @return The number of I/O entries allocated but not yet released from
     *         this pool.
     */
    public int size() {
        return active.get();
    }

    @NotThreadSafe
    private final class ByteBuffer extends ByteArrayIoBuffer {
        private boolean released;

        ByteBuffer(int i) {
            super(BUFFER_NAME + i, ByteArrayIoBufferPool.this.initialCapacity);
        }

        @Override
        public void release() throws IOException {
            if (released)
                throw new IllegalStateException("entry has already been released!");
            active.getAndDecrement();
            setData(null);
            released = true;
        }
    }
}