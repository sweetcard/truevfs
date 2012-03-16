/*
 * Copyright (C) 2004-2012 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package de.schlichtherle.truezip.fs.archive;

import de.schlichtherle.truezip.fs.*;
import static de.schlichtherle.truezip.fs.FsSyncOptions.RESET;
import de.schlichtherle.truezip.util.BitField;
import java.io.IOException;
import javax.annotation.concurrent.Immutable;

/**
 * Resets the decorated file system controller once the virtual root directory
 * of the file system has been successfully unlinked.
 * 
 * @since  TrueZIP 7.5
 * @author Christian Schlichtherle
 */
@Immutable
final class FsResetController
extends FsLockModelDecoratingController<FsController<? extends FsLockModel>> {

    /**
     * Constructs a new file system unlink controller.
     *
     * @param controller the decorated file system controller.
     */
    FsResetController(FsController<? extends FsLockModel> controller) {
        super(controller);
    }

    @Override
    public void unlink( final FsEntryName name,
                        final BitField<FsOutputOption> options)
    throws IOException {
        boolean checkRoot = true;
        try {
            delegate.unlink(name, options);
        } catch (final FsFalsePositiveException ex) {
            throw ex;
        } catch (final IOException ex) {
            checkRoot = false;
            throw ex;
        } finally {
            if (checkRoot && name.isRoot()) {
                // Either the virtual root directory has been successfully
                // removed or it's a false positive archive file.
                // In either case the selective cache needs to get cleared now
                // without flushing it.
                // For a false positive archive file, the effect will be that
                // the mount state gets reset so that the file system can get
                // subsequently mounted if the target archive file has been
                // modified to be a true archive file meanwhile.
                delegate.sync(RESET);
            }
        }
    }
}