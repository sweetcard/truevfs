/*
 * Copyright (C) 2005-2012 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.truevfs.driver.zip.raes;

import net.truevfs.kernel.spec.spi.FsDriverMapModifier;
import net.truevfs.kernel.spec.spi.FsDriverMapModifierTestSuite;

/**
 * @author Christian Schlichtherle
 */
public class ZipRaesDriverMapModifierTest
extends FsDriverMapModifierTestSuite {

    @Override
    protected String getExtensions() {
        return "tzp|zip.rae|zip.raes";
    }

    @Override
    protected FsDriverMapModifier newModifier() {
        return new ZipRaesDriverMapModifier();
    }
}