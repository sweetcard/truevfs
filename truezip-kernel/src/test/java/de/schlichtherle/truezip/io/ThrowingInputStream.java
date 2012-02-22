/*
 * Copyright 2004-2012 Schlichtherle IT Services
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.schlichtherle.truezip.io;

import de.schlichtherle.truezip.test.TestConfig;
import de.schlichtherle.truezip.test.ThrowControl;
import edu.umd.cs.findbugs.annotations.CreatesObligation;
import java.io.IOException;
import java.io.InputStream;
import javax.annotation.CheckForNull;
import javax.annotation.WillCloseWhenClosed;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * A decorating input stream which supports throwing exceptions according to
 * {@link TestConfig}.
 * 
 * @see     ThrowingOutputStream
 * @author  Christian Schlichtherle
 * @version $Id$
 */
@NotThreadSafe
public final class ThrowingInputStream extends DecoratingInputStream {

    private final ThrowControl control;

    @edu.umd.cs.findbugs.annotations.SuppressWarnings("OBL_UNSATISFIED_OBLIGATION")
    @CreatesObligation
    public ThrowingInputStream(@WillCloseWhenClosed InputStream in) {
        this(in, null);
    }

    @edu.umd.cs.findbugs.annotations.SuppressWarnings("OBL_UNSATISFIED_OBLIGATION")
    @CreatesObligation
    public ThrowingInputStream( final @WillCloseWhenClosed InputStream in,
                                final @CheckForNull ThrowControl control) {
        super(in);
        if (null == in)
            throw new NullPointerException();
        this.control = null != control
                ? control
                : TestConfig.get().getThrowControl();
    }

    private void checkAllExceptions() throws IOException {
        control.check(this, IOException.class);
        checkUndeclaredExceptions();
    }

    private void checkUndeclaredExceptions() {
        control.check(this, RuntimeException.class);
        control.check(this, Error.class);
    }

    @Override
    public int read() throws IOException {
        checkAllExceptions();
        return delegate.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        checkAllExceptions();
        return delegate.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        checkAllExceptions();
        return delegate.skip(n);
    }

    @Override
    public int available() throws IOException {
        checkAllExceptions();
        return delegate.available();
    }

    @Override
    public void close() throws IOException {
        checkAllExceptions();
        delegate.close();
    }

    @Override
    public void mark(int readlimit) {
        checkUndeclaredExceptions();
        delegate.mark(readlimit);
    }

    @Override
    public void reset() throws IOException {
        checkAllExceptions();
        delegate.reset();
    }

    @Override
    public boolean markSupported() {
        checkUndeclaredExceptions();
        return delegate.markSupported();
    }
}