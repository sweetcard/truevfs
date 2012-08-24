/*
 * Copyright (C) 2005-2012 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.java.truevfs.comp.inst;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.util.Objects;
import javax.annotation.CheckForNull;
import javax.annotation.concurrent.Immutable;
import net.java.truevfs.kernel.spec.cio.DecoratingOutputSocket;
import net.java.truevfs.kernel.spec.cio.Entry;
import net.java.truevfs.kernel.spec.cio.InputSocket;
import net.java.truevfs.kernel.spec.cio.OutputSocket;

/**
 * @param  <M> the type of the mediator.
 * @param  <E> the type of the {@linkplain #target() target entry} for I/O
 *         operations.
 * @see    InstrumentingInputSocket
 * @author Christian Schlichtherle
 */
@Immutable
public class InstrumentingOutputSocket<
        M extends Mediator<M>,
        E extends Entry>
extends DecoratingOutputSocket<E> {

    protected final M mediator;

    public InstrumentingOutputSocket(
            final M mediator,
            final OutputSocket<? extends E> socket) {
        super(socket);
        this.mediator = Objects.requireNonNull(mediator);
    }

    @Override
    public OutputStream stream(@CheckForNull InputSocket<? extends Entry> peer)
    throws IOException {
        return mediator.instrument(this, socket.stream(peer));
    }

    @Override
    public SeekableByteChannel channel(
            @CheckForNull InputSocket<? extends Entry> peer)
    throws IOException {
        return mediator.instrument(this, socket.channel(peer));
    }
}