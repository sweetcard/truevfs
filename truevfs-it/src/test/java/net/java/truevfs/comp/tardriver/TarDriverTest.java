/*
 * Copyright (C) 2005-2015 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.java.truevfs.comp.tardriver;

import net.java.truevfs.kernel.spec.FsArchiveDriverTestSuite;
import net.java.truevfs.kernel.spec.FsTestConfig;
import net.java.truecommons.cio.IoBufferPool;

/**
 * @author Christian Schlichtherle
 */
public final class TarDriverTest
extends FsArchiveDriverTestSuite<TarDriverEntry, TarDriver> {

    @Override
    protected TarDriver newArchiveDriver() {
        return new TarDriver() {
            @Override
            public IoBufferPool getPool() {
                return FsTestConfig.get().getPool();
            }
        };
    }

    @Override
    protected String getUnencodableName() {
        return null;
    }
}
