/*
 * Copyright (C) 2005-2012 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.truevfs.driver.file;

import net.truevfs.kernel.cio.IOPool;
import net.truevfs.kernel.spi.IOPoolService;
import javax.annotation.concurrent.Immutable;

/**
 * Provides {@link TempFilePool#INSTANCE}.
 *
 * @author Christian Schlichtherle
 */
@Immutable
public final class TempFilePoolService extends IOPoolService {

    @Override
    public IOPool<?> getIOPool() {
        return TempFilePool.INSTANCE;
    }

    /** @return -100 */
    @Override
    public int getPriority() {
        return -100;
    }
}