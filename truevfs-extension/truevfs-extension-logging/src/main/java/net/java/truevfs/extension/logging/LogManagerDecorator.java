/*
 * Copyright (C) 2005-2012 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.java.truevfs.extension.logging;

import javax.annotation.concurrent.Immutable;
import net.java.truevfs.kernel.spec.FsManager;
import net.java.truevfs.kernel.spec.spi.FsManagerDecorator;

/**
 * @author Christian Schlichtherle
 */
@Immutable
public final class LogManagerDecorator extends FsManagerDecorator {
    @Override
    public FsManager apply(FsManager manager) {
        return LogDirector.SINGLETON.instrument(manager);
    }

    /** Returns -100. */
    @Override
    public int getPriority() {
        return -100;
    }
}