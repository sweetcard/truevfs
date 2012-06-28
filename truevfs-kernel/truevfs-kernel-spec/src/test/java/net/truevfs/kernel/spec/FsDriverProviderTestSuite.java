/*
 * Copyright (C) 2005-2012 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.truevfs.kernel.spec;

import net.truevfs.kernel.spec.util.ExtensionSet;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Christian Schlichtherle
 */
public abstract class FsDriverProviderTestSuite {

    private FsDriverProvider provider;

    protected abstract String getExtensions();
    protected abstract FsDriverProvider newDriverProvider();

    @Before
    public void setUp() {
        provider = newDriverProvider();
    }

    @Test
    public void testGet() {
        for (final String extension : new ExtensionSet(getExtensions()))
            assertNotNull(provider.getDrivers().get(FsScheme.create(extension)));
    }

    @Test
    public void testImmutability() {
        for (final String extension : new ExtensionSet(getExtensions()) ) {
            try {
                provider.getDrivers().remove(FsScheme.create(extension));
                fail();
            } catch (UnsupportedOperationException expected) {
            }
        }
    }
}