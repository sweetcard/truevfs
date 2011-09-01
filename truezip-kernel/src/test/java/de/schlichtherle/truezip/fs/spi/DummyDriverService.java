/*
 * Copyright 2011 Schlichtherle IT Services
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.schlichtherle.truezip.fs.spi;

import de.schlichtherle.truezip.fs.DummyDriver;
import de.schlichtherle.truezip.fs.FsDriver;
import de.schlichtherle.truezip.fs.FsScheme;
import java.util.Map;

/**
 * A service for the dummy driver.
 *
 * @author  Christian Schlichtherle
 * @version $Id$
 */
public final class DummyDriverService extends FsDriverService {

    private final Map<FsScheme, FsDriver> drivers;

    public DummyDriverService(String suffixes) {
        this.drivers = newMap(new Object[][] {
            { suffixes, new DummyDriver() },
        });
    }

    @Override
    public Map<FsScheme, FsDriver> get() {
        return drivers;
    }
}
