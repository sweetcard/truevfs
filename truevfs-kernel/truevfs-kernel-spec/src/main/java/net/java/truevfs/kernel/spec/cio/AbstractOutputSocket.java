/*
 * Copyright (C) 2005-2012 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.java.truevfs.kernel.spec.cio;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import javax.annotation.CheckForNull;
import javax.annotation.concurrent.Immutable;
import net.java.truecommons.io.ChannelOutputStream;

/**
 * Abstract base class for output sockets.
 *
 * @param  <E> the type of the {@linkplain #target() target entry} for I/O
 *         operations.
 * @see    AbstractInputSocket
 * @author Christian Schlichtherle
 */
@Immutable
public abstract class AbstractOutputSocket<E extends Entry>
extends AbstractIoSocket<E> implements OutputSocket<E> {

    /**
     * Returns the target of the given nullable peer socket or null.
     * This method is provided for convenience.
     * 
     * @param  <E> the type of the peer socket's local target.
     * @param  peer the nullable peer socket.
     * @return {@code null} if the given reference is null or the target of the
     *         given peer socket otherwise.
     * @throws IOException if resolving the peer's local target fails.
     */
    protected final @CheckForNull <E extends Entry> E target(
            @CheckForNull InputSocket<E> peer)
    throws IOException {
        return null != peer ? peer.target() : null;
    }

    /**
     * {@inheritDoc}
     * <p>
     * The implementation in the class {@link OutputSocket} calls
     * {@link #channel()} and wraps the result in a
     * {@link ChannelOutputStream} adapter.
     * Note that this violates the contract for this method unless you
     * override either this method or {@link #channel()} with a valid
     * implementation.
     */
    @Override
    public OutputStream stream(InputSocket<? extends Entry> peer)
    throws IOException {
        return new ChannelOutputStream(channel(peer));
    }

    /**
     * {@inheritDoc}
     * 
     * @throws UnsupportedOperationException the implementation in the class
     *         {@link OutputSocket} <em>always</em> throws an exception of
     *         this type.
     */
    @Override
    public SeekableByteChannel channel(InputSocket<? extends Entry> peer)
    throws IOException {
        throw new UnsupportedOperationException();
    }
}