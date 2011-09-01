/*
 * Copyright (C) 2011 Schlichtherle IT Services
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.schlichtherle.truezip.fs;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import net.jcip.annotations.ThreadSafe;

/**
 * The default implementation of a file system model.
 *
 * @author  Christian Schlichtherle
 * @version $Id$
 */
@ThreadSafe
@DefaultAnnotation(NonNull.class)
public class FsDefaultModel extends FsModel {

    private final FsMountPoint mountPoint;
    private final @CheckForNull FsModel parent;
    private volatile boolean touched;

    public FsDefaultModel(  final FsMountPoint mountPoint,
                            final @CheckForNull FsModel parent) {
        if (!equals(mountPoint.getParent(),
                    (null == parent ? null : parent.getMountPoint())))
            throw new IllegalArgumentException("Parent/Member mismatch!");
        this.mountPoint = mountPoint;
        this.parent = parent;
    }

    private static boolean equals(@CheckForNull Object o1, @CheckForNull Object o2) {
        return o1 == o2 || null != o1 && o1.equals(o2);
    }

    @Override
    public final FsMountPoint getMountPoint() {
        return mountPoint;
    }

    @Override
    public final FsModel getParent() {
        return parent;
    }

    @Override
    public boolean isTouched() {
        return touched;
    }

    @Override
    public void setTouched(boolean touched) {
        this.touched = touched;
    }
}
