/*
 * Copyright (C) 2005-2015 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.java.truevfs.kernel.spec;

import net.java.truevfs.kernel.spec.FsAccessOptions;
import net.java.truevfs.kernel.spec.FsAccessOption;
import static net.java.truevfs.kernel.spec.FsAccessOption.*;
import static net.java.truevfs.kernel.spec.FsAccessOptions.ACCESS_PREFERENCES_MASK;
import static net.java.truevfs.kernel.spec.FsAccessOptions.NONE;
import net.java.truecommons.shed.BitField;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * @author Christian Schlichtherle
 */
public class FsAccessOptionsTest {

    @Test
    public void testOf() {
        for (final Object[] params : new Object[][] {
            // { $array, $bits }
            { new FsAccessOption[0], NONE },
            { new FsAccessOption[] { CACHE, CREATE_PARENTS, STORE, COMPRESS, GROW, ENCRYPT }, ACCESS_PREFERENCES_MASK },
        }) {
            final FsAccessOption[] array = (FsAccessOption[]) params[0];
            final BitField<?> bits = (BitField<?>) params[1];
            assertEquals(FsAccessOptions.of(array), bits);
        }
    }
}
