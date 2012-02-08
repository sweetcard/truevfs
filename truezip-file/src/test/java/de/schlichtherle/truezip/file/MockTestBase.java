/*
 * Copyright 2004-2012 Schlichtherle IT Services
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.schlichtherle.truezip.file;

import de.schlichtherle.truezip.fs.archive.mock.MockArchiveDriver;
import de.schlichtherle.truezip.socket.IOPoolProvider;

/**
 * @author  Christian Schlichtherle
 * @version $Id$
 */
public abstract class MockTestBase extends TestBase<MockArchiveDriver> {

    @Override
    protected final String getSuffixList() {
        return "mok|mok1|mok2";
    }

    @Override
    protected final MockArchiveDriver newArchiveDriver(IOPoolProvider provider) {
        return new MockArchiveDriver();
    }
}
