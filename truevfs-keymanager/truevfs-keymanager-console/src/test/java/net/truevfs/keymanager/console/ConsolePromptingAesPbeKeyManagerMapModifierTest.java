/*
 * Copyright (C) 2005-2012 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.truevfs.keymanager.console;

import java.util.Arrays;
import net.truevfs.keymanager.spec.param.AesPbeParameters;
import net.truevfs.keymanager.spec.spi.KeyManagerMapModifier;
import net.truevfs.keymanager.spec.spi.KeyManagerMapModifierTestSuite;
import net.truevfs.keymanager.console.ConsolePromptingAesPbeKeyManagerMapModifier;

/**
 * @author Christian Schlichtherle
 */
public class ConsolePromptingAesPbeKeyManagerMapModifierTest
extends KeyManagerMapModifierTestSuite {
    @Override
    @SuppressWarnings("unchecked")
    protected Iterable<Class<?>> getClasses() {
        return (Iterable<Class<?>>) Arrays.asList((Class<?>) AesPbeParameters.class);
    }

    @Override
    protected KeyManagerMapModifier newModifier() {
        return new ConsolePromptingAesPbeKeyManagerMapModifier();
    }
}