/*
 * Copyright (C) 2005-2012 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.java.truevfs.kernel.spec;

import java.util.Iterator;
import net.java.truecommons.shed.Stream;

/**
 * An ordered stream of file system controllers.
 * The controllers are sorted in reverse order of the
 * {@linkplain FsMountPoint#getHierarchicalUri() hierarchical URI}
 * of the {@linkplain FsModel#getMountPoint() mount point} of their
 * {@linkplain FsController#getModel() file system model}.
 * 
 * @see    FsControllerComparator
 * @author Christian Schlichtherle
 */
public interface FsControllerStream extends Stream<FsController> {

    /**
     * Returns the number of file system controllers in this stream.
     * 
     * @return The number of file system controllers in this stream.
     */
    int size();

    /**
     * Returns a new iterator which enumerates the file system controllers in
     * reverse order of the
     * {@linkplain FsMountPoint#getHierarchicalUri() hierarchical URI} of the
     * {@linkplain FsModel#getMountPoint() mount point} of their
     * {@linkplain FsController#getModel() file system model}.
     * 
     * @return A new iterator which enumerates the file system controllers in
     *         reverse order of the
     *         {@linkplain FsMountPoint#getHierarchicalUri() hierarchical URI}
     *         of the {@linkplain FsModel#getMountPoint() mount point} of their
     *         {@linkplain FsController#getModel() file system model}.
     */
    @Override Iterator<FsController> iterator();

    /**
     * Closes this stream.
     * It is an error to call any other method once this method has terminated
     * without an exception and any violation may result in a
     * {@link RuntimeException}.
     * <p>
     * Implementations are strongly encouraged to clear their references to the
     * controllers enumerated by this stream in order to support garbage
     * collection.
     */
    @Override void close();
}