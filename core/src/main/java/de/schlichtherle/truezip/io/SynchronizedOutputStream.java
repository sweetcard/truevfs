/*
 * Copyright (C) 2006-2010 Schlichtherle IT Services
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
package de.schlichtherle.truezip.io;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A decorator which synchronizes all access to an {@link OutputStream}
 * via an object provided to its constructor.
 *
 * @see SynchronizedInputStream
 * @author Christian Schlichtherle
 * @version $Id$
 */
public class SynchronizedOutputStream extends OutputStream {

    /** The object to synchronize on - never {@code null}. */
    protected final Object lock;

    /** The decorated output stream. */
    protected OutputStream target;

    /**
     * Constructs a new synchronized output stream.
     * This object will synchronize on itself.
     *
     * @param target The output stream to wrap in this decorator.
     */
    public SynchronizedOutputStream(final OutputStream target) {
    	this(target, null);
    }

    /**
     * Constructs a new synchronized output stream.
     *
     * @param target The output stream to wrap in this decorator.
     * @param lock The object to synchronize on.
     *        If {@code null}, then this object is used, not the stream.
     */
    public SynchronizedOutputStream(final OutputStream target, final Object lock) {
        this.target = target;
        this.lock = null == lock ? this : lock;
    }

    public void write(int b) throws IOException {
        synchronized (lock) {
            target.write(b);
        }
    }

    @Override
    public void write(byte[] b) throws IOException {
        synchronized (lock) {
            write(b, 0, b.length);
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        synchronized (lock) {
            target.write(b, off, len);
        }
    }

    /** Synchronizes on the {@link #lock} and calls {@link #doFlush}. */
    @Override
    public void flush() throws IOException {
        synchronized (lock) {
            doFlush();
        }
    }

    /**
     * Flushes the underlying stream.
     * This method is <em>not</em> synchronized!
     */
    protected void doFlush() throws IOException {
        target.flush();
    }

    /** Synchronizes on the {@link #lock} and calls {@link #doClose}. */
    @Override
    public void close() throws IOException {
        synchronized (lock) {
                doClose();
        }
    }

    /**
     * Calls {@link #doFlush} and finally closes the underlying stream.
     * This method is not synchronized!
     */
    protected void doClose() throws IOException {
        try {
            doFlush();
        } finally {
            target.close();
        }
    }
}
