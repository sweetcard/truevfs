/*
 * Copyright (C) 2005-2012 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.truevfs.extension.jmxjul.jul;

import java.io.IOException;
import java.io.OutputStream;
import javax.annotation.concurrent.Immutable;
import net.truevfs.extension.jmxjul.InstrumentingOutputSocket;
import net.truevfs.kernel.spec.cio.Entry;
import net.truevfs.kernel.spec.cio.InputSocket;
import net.truevfs.kernel.spec.cio.OutputSocket;

/**
 * @author  Christian Schlichtherle
 */
@Immutable
final class JulOutputSocket<E extends Entry>
extends InstrumentingOutputSocket<E> {

    JulOutputSocket(JulDirector director, OutputSocket<? extends E> model) {
        super(director, model);
    }

    @Override
    public OutputStream stream(InputSocket<? extends Entry> peer)
    throws IOException {
        return new JulOutputStream(socket(), peer);
    }
}
