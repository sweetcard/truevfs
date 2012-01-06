/*
 * Copyright 2004-2012 Schlichtherle IT Services
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.schlichtherle.truezip.fs;

import de.schlichtherle.truezip.util.BitField;
import de.schlichtherle.truezip.util.Link;
import de.schlichtherle.truezip.util.Link.Type;
import static de.schlichtherle.truezip.util.Link.Type.*;
import de.schlichtherle.truezip.util.Links;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
import net.jcip.annotations.ThreadSafe;

/**
 * The default implementation of a file system manager.
 *
 * @author  Christian Schlichtherle
 * @version $Id$
 */
@ThreadSafe
@DefaultAnnotation(NonNull.class)
public final class FsDefaultManager extends FsManager {

    /**
     * The map of all schedulers for composite file system controllers,
     * keyed by the mount point of their respective file system model.
     * All access to this map must be externally synchronized!
     */
    private final Map<FsMountPoint, Link<FsFederatingController>> schedulers
            = new WeakHashMap<FsMountPoint, Link<FsFederatingController>>();

    private final Type optionalScheduleType;

    public FsDefaultManager() {
        this(WEAK);
    }

    /** Provided for unit testing. */
    FsDefaultManager(final Type optionalScheduleType) {
        assert null != optionalScheduleType;
        this.optionalScheduleType = optionalScheduleType;
    }

    @Override
    public synchronized FsController<?>
    getController(  FsMountPoint mountPoint,
                    FsCompositeDriver driver) {
        return getController(mountPoint, null, driver);
    }

    private FsController<?>
    getController(  final FsMountPoint mountPoint,
                    @CheckForNull FsController<?> parent,
                    final FsCompositeDriver driver) {
        if (null == mountPoint.getParent()) {
            if (null != parent)
                throw new IllegalArgumentException("Parent/member mismatch!");
            final FsModel model = new FsDefaultModel(mountPoint, null);
            return driver.newController(model, null);
        }
        FsFederatingController controller = Links.getTarget(schedulers.get(
                mountPoint));
        if (null == controller) {
            if (null == parent)
                parent = getController(mountPoint.getParent(), null, driver);
            final ScheduledModel model = new ScheduledModel(
                    mountPoint, parent.getModel());
            model.setController(controller = new FsFederatingController(
                    driver.newController(model, parent)));
        }
        return controller;
    }

    /**
     * A model which schedules its controller for
     * {@link #sync(BitField) synchronization} by &quot;observing&quot; its
     * {@code touched} property.
     * Extending its sub-class to register for updates to the {@code touched}
     * property is simpler, faster and requires a smaller memory footprint than
     * the alternative observer pattern.
     */
    private final class ScheduledModel extends FsDefaultModel {
        FsFederatingController controller;
        volatile boolean touched;

        ScheduledModel(FsMountPoint mountPoint, FsModel parent) {
            super(mountPoint, parent);
        }

        void setController(final FsFederatingController controller) {
            assert null != controller;
            assert !touched;
            this.controller = controller;
            schedule(false);
        }

        @Override
        public boolean isTouched() {
            return touched;
        }

        /**
         * Schedules the file system controller for synchronization according
         * to the given touch status.
         */
        @Override
        public void setTouched(final boolean touched) {
            if (touched == this.touched)
                return;
            this.touched = touched;
            schedule(touched);
        }

        void schedule(boolean mandatory) {
            synchronized (FsDefaultManager.this) {
                schedulers.put(getMountPoint(),
                        (mandatory ? STRONG : optionalScheduleType)
                            .newLink(controller));
            }
        }
    } // ScheduledModel

    @Override
    public synchronized int getSize() {
        return schedulers.size();
    }

    @Override
    public synchronized Iterator<FsController<?>> iterator() {
        return getControllers().iterator();
    }

    private Set<FsController<?>> getControllers() {
        final Set<FsController<?>> snapshot
                = new TreeSet<FsController<?>>(FsControllerComparator.REVERSE);
        for (final Link<FsFederatingController> link : schedulers.values()) {
            final FsController<?> controller = Links.getTarget(link);
            if (null != controller)
                snapshot.add(controller);
        }
        return snapshot;
    }

    /**
     * Orders file system controllers so that all file systems appear before
     * any of their parent file systems.
     */
    private static final class FsControllerComparator
    implements Comparator<FsController<?>> {
        static final FsControllerComparator REVERSE
                = new FsControllerComparator();

        @Override
        public int compare( FsController<?> l,
                            FsController<?> r) {
            return r.getModel().getMountPoint().toHierarchicalUri()
                    .compareTo(l.getModel().getMountPoint().toHierarchicalUri());
        }
    } // FsControllerComparator
}
