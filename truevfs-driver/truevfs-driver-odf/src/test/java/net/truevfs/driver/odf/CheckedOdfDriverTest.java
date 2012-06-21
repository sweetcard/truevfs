/*
 * Copyright (C) 2005-2012 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.truevfs.driver.odf;

import net.truevfs.driver.zip.ZipDriverEntry;
import net.truevfs.kernel.FsArchiveDriverTestSuite;
import net.truevfs.kernel.cio.IoPool;

/**
 * @author Christian Schlichtherle
 */
public final class CheckedOdfDriverTest
extends FsArchiveDriverTestSuite<ZipDriverEntry, CheckedOdfDriver> {

    @Override
    protected CheckedOdfDriver newArchiveDriver() {
        return new CheckedOdfDriver() {
            @Override
            public IoPool<?> getIoPool() {
                return getTestConfig().getIoPoolProvider().getIoPool();
            }
        };
    }

    @Override
    protected String getUnencodableName() {
        return null;
    }
}