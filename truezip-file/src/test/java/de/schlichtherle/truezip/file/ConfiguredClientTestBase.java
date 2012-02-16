/*
 * Copyright 2004-2012 Schlichtherle IT Services
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.schlichtherle.truezip.file;

import de.schlichtherle.truezip.fs.FsDefaultManager;
import de.schlichtherle.truezip.fs.FsManager;
import de.schlichtherle.truezip.fs.FsMountPoint;
import de.schlichtherle.truezip.fs.FsScheme;
import de.schlichtherle.truezip.fs.archive.FsArchiveDriver;
import de.schlichtherle.truezip.fs.archive.FsArchiveDriverTestBase;
import de.schlichtherle.truezip.util.SuffixSet;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.After;

/**
 * @param   <D> The type of the archive driver.
 * @author  Christian Schlichtherle
 * @version $Id$
 */
public abstract class ConfiguredClientTestBase<D extends FsArchiveDriver<?>>
extends FsArchiveDriverTestBase<D> {

    protected static final long TIMEOUT_MILLIS = 50;
    protected static final FsMountPoint
            ROOT_DIRECTORY = FsMountPoint.create(URI.create("file:/"));
    protected static final FsMountPoint
            CURRENT_DIRECTORY = FsMountPoint.create(new File("").toURI());
    protected static final String[] NO_STRINGS = new String[0];
    private static final String ARCHIVE_DETECTOR = "archiveDetector";

    private static final boolean FS_MANAGER_ISOLATE
            = Boolean.getBoolean(FsManager.class.getName() + ".isolate");
    static {
        Logger  .getLogger(ConfiguredClientTestBase.class.getName())
                .log(   Level.CONFIG,
                        "Isolate file system managers: {0}",
                        FS_MANAGER_ISOLATE);
    }

    private TArchiveDetector detector;
    private Map<String, ?> environment;

    @Override
    public void setUp() throws IOException {
        super.setUp();
        detector = new TArchiveDetector(getSuffixList(), getArchiveDriver());
        environment = Collections.singletonMap(ARCHIVE_DETECTOR, detector);
        final TConfig config = TConfig.push();
        // Using a private file system manager would normally violate the third
        // party access constraints, but in this context it's safe because no
        // two test methods should ever access the same archive file(s) except
        // when performing a sync of all mounted file systems.
        // Mind that a sync should always succeed (unless there's an issue in
        // the parent file system) und must not confuse other threads about the
        // state of the synced archive file(s).
        // So the default value 'false' helps to identify potential isolation
        // issues in case this invariant is not met.
        // See http://truezip.java.net/truezip-file/usage.html#Third_Party_Access
        if (FS_MANAGER_ISOLATE)
            config.setManager(new FsDefaultManager());
        config.setLenient(true);
        config.setArchiveDetector(detector);
    }

    @After
    public void tearDown() {
        TConfig.pop();
    }

    protected abstract String getSuffixList();

    protected final FsScheme getScheme() {
        return FsScheme.create(new SuffixSet(getSuffixList()).iterator().next());
    }

    protected final String getSuffix() {
        return "." + getScheme();
    }

    protected final TArchiveDetector getArchiveDetector() {
        return detector;
    }

    @SuppressWarnings("ReturnOfCollectionOrArrayField")
    protected final Map<String, ?> getEnvironment() {
        return environment;
    }
}