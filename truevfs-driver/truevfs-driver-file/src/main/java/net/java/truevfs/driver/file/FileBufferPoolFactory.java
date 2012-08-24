/*
 * Copyright (C) 2005-2012 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.java.truevfs.driver.file;

import javax.annotation.concurrent.Immutable;
import net.java.truevfs.kernel.spec.cio.IoBufferPool;
import net.java.truevfs.kernel.spec.spi.IoBufferPoolFactory;

/**
 * Creates {@linkplain FileBufferPool temp file based I/O buffer pools}.
 *
 * @author Christian Schlichtherle
 */
@Immutable
public final class FileBufferPoolFactory extends IoBufferPoolFactory {
    @Override
    public IoBufferPool get() {
        return new FileBufferPool();
    }
}