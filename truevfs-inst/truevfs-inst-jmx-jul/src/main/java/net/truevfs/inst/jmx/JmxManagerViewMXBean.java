/*
 * Copyright (C) 2005-2012 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.truevfs.inst.jmx;

import net.truevfs.kernel.spec.FsManager;
import net.truevfs.kernel.spec.FsSyncException;

/**
 * The MXBean interface for a {@link FsManager file system manager}.
 *
 * @author Christian Schlichtherle
 */
public interface JmxManagerViewMXBean {

    /**
     * Returns a new array of all managed federated file systems.
     * 
     * @return A new array of all managed federated file systems.
     */
    JmxModelViewMXBean[] getFederatedFileSystems();

    /**
     * Returns the total number of managed federated file systems.
     */
    int getFileSystemsTotal();

    /**
     * Returns the number of managed federated file systems which have been
     * touched and need synchronization by calling {@link FsManager#sync}.
     * <p>
     * Note that you should <em>not</em> use the returned value to synchronize
     * conditionally - this is unreliable!
     */
    int getFileSystemsTouched();

    /**
     * Returns the total number of managed <em>top level</em> federated file
     * systems.
     */
    int getTopLevelFileSystemsTotal();

    /**
     * Returns the number of managed <em>top level</em> federated file systems
     * which have been touched and need synchronization by calling
     * {@link FsManager#sync}.
     */
    int getTopLevelFileSystemsTouched();

    /**
     * Synchronizes all managed federated file systems.
     * 
     * @throws FsSyncException If any managed federated file system is busy
     *         with I/O.
     */
    void sync() throws FsSyncException;
    
    /**
     * Clears all but the last I/O statistics.
     */
    void clearStatistics();
}