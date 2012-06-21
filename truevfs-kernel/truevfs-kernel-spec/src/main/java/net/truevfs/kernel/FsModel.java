/*
 * Copyright (C) 2005-2012 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.truevfs.kernel;

import java.util.Objects;
import javax.annotation.CheckForNull;
import javax.annotation.concurrent.ThreadSafe;
import net.truevfs.kernel.util.UniqueObject;

/**
 * Defines the common properties of a file system.
 * <p>
 * Sub-classes must be thread-safe, too.
 *
 * @see    FsController
 * @author Christian Schlichtherle
 */
@ThreadSafe
public class FsModel extends UniqueObject {

    private final FsMountPoint mountPoint;
    private @CheckForNull final FsModel parent;

    public FsModel( final FsMountPoint mountPoint,
                    final @CheckForNull FsModel parent) {
        if (!Objects.equals(mountPoint.getParent(),
                    (null == parent ? null : parent.getMountPoint())))
            throw new IllegalArgumentException("Parent/Member mismatch!");
        this.mountPoint = mountPoint;
        this.parent = parent;
    }

    /**
     * Returns the mount point of the file system.
     * The mount point may be used to construct error messages or to locate
     * and access file system meta data which is stored outside the file system,
     * e.g. passwords for RAES encrypted ZIP files.
     *
     * @return The mount point of the file system.
     */
    public final FsMountPoint getMountPoint() {
        return mountPoint;
    }

    /**
     * Returns the parent file system model or {@code null} if and only if the
     * file system is not federated, i.e. if it's not a member of a parent file
     * system.
     *
     * @return The nullable parent file system model.
     */
    public final @CheckForNull FsModel getParent() {
        return parent;
    }

    /**
     * Returns {@code true} if and only if some state associated with the
     * federated file system has been modified so that the
     * corresponding {@link FsController} must not get discarded until
     * the next call to {@link FsController#sync sync}.
     * <p>
     * The implementation in the class {@link FsModel} always
     * returns {@code false}.
     * 
     * @return {@code true} if and only if some state associated with the
     *         federated file system has been modified so that the
     *         corresponding {@link FsController} must not get discarded until
     *         the next {@link FsController#sync sync}.
     */
    public boolean isTouched() {
        return false;
    }

    /**
     * <b>Optional operation:</b> Sets the value of the property
     * {@link #isTouched() touched}.
     * <p>
     * The implementation in the class {@link FsModel} always
     * throws an {@link UnsupportedOperationException}.
     *
     * @param  touched the new value of this property.
     * @throws UnsupportedOperationException At the discretion of the
     *         implementation, e.g. if the file system type does not support
     *         {@link FsController#sync syncing}.
     */
    public void setTouched(boolean touched) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns a string representation of this object for debugging and logging
     * purposes.
     */
    @Override
    public String toString() {
        return String.format("%s[mountPoint=%s, parent=%s, touched=%b]",
                getClass().getName(),
                getMountPoint(),
                getParent(),
                isTouched());
    }
}