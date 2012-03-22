/*
 * Copyright (C) 2005-2012 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package de.schlichtherle.truezip.fs;

import de.schlichtherle.truezip.io.SequentialIOException;
import java.io.IOException;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Indicates an exceptional condition when synchronizing the changes in a
 * federated file system to its parent file system.
 * Unless this is an instance of the sub-class {@link FsSyncWarningException},
 * an exception of this class implies that some or all
 * of the data of the federated file system has been lost.
 *
 * @author  Christian Schlichtherle
 */
@ThreadSafe
public class FsSyncException extends SequentialIOException {

    private static final long serialVersionUID = 4893219420357369739L;

    /**
     * This constructor is for exclusive use by {@link FsSyncExceptionBuilder}.
     *
     * @deprecated This method is only public in order to allow reflective
     *             access - do <em>not</em> call it directly!
     */
    @Deprecated
    public FsSyncException(@CheckForNull String message) {
        super(message);
    }

    public FsSyncException(FsModel model, IOException cause) {
        this(model, cause, 0);
    }

    FsSyncException(FsModel model, IOException cause, int priority) {
        super(model.getMountPoint().toString(), cause, priority);
    }

    @Override
    public @Nullable IOException getCause() {
        return (IOException) super.getCause();
    }

    @Override
    @edu.umd.cs.findbugs.annotations.SuppressWarnings("BC_UNCONFIRMED_CAST")
    public final FsSyncException initCause(final @CheckForNull Throwable cause) {
        //assert super.getCause() instanceof IOException;
        super.initCause((IOException) cause);
        return this;
    }
}