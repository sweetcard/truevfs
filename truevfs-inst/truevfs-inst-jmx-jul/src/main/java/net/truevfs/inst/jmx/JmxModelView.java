/*
 * Copyright (C) 2005-2012 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.truevfs.inst.jmx;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Date;
import javax.management.*;
import static net.truevfs.kernel.spec.FsAccessOptions.NONE;
import net.truevfs.kernel.spec.*;
import static net.truevfs.kernel.spec.FsSyncOption.CLEAR_CACHE;
import static net.truevfs.kernel.spec.cio.Entry.Access.*;
import static net.truevfs.kernel.spec.cio.Entry.Size.DATA;
import static net.truevfs.kernel.spec.cio.Entry.Size.STORAGE;
import static net.truevfs.kernel.spec.cio.Entry.UNKNOWN;
import net.truevfs.kernel.spec.sl.FsDriverLocator;
import net.truevfs.kernel.spec.sl.FsManagerLocator;
import net.truevfs.kernel.spec.util.BitField;

/**
 * The MXBean implementation for a {@link FsModel file system model}.
 *
 * @author  Christian Schlichtherle
 */
final class JmxModelView
extends StandardMBean
implements JmxModelViewMXBean {

    private static final MBeanServer
            mbs = ManagementFactory.getPlatformMBeanServer();
    private static final BitField<FsSyncOption>
            SYNC_OPTIONS = BitField.of(CLEAR_CACHE);
    private static final FsCompositeDriver
            DRIVER = new FsSimpleCompositeDriver(FsDriverLocator.SINGLETON);

    private final FsModel model;

    static JmxModelViewMXBean register(final FsModel model) {
        final ObjectName name = getObjectName(model);
        final JmxModelViewMXBean view = new JmxModelView(model);
        try {
            try {
                mbs.registerMBean(view, name);
                return view;
            } catch (InstanceAlreadyExistsException ignored) {
                return JMX.newMXBeanProxy(mbs, name, JmxModelViewMXBean.class);
            }
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    static void unregister(final FsModel model) {
        final ObjectName name = getObjectName(model);
        try {
            try {
                mbs.unregisterMBean(name);
            } catch (InstanceNotFoundException ignored) {
            }
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    private static ObjectName getObjectName(final FsModel model) {
        final String path = model.getMountPoint().toHierarchicalUri().toString();
        try {
            return new ObjectName(  FsModel.class.getName(),
                                    "path",
                                    ObjectName.quote(path));
        } catch (MalformedObjectNameException ex) {
            throw new AssertionError(ex);
        }
    }

    private JmxModelView(FsModel model) {
        super(JmxModelViewMXBean.class, true);
        this.model = model;
    }

    @Override
    public MBeanInfo getMBeanInfo() {
        MBeanInfo mbinfo = super.getMBeanInfo();
        return new MBeanInfo(mbinfo.getClassName(),
                mbinfo.getDescription(),
                mbinfo.getAttributes(),
                mbinfo.getConstructors(),
                mbinfo.getOperations(),
                getNotificationInfo());
    }

    public MBeanNotificationInfo[] getNotificationInfo() {
        return new MBeanNotificationInfo[]{};
    }

    @Override
    protected String getDescription(MBeanInfo info) {
        return "A managed federated file system.";
    }

    @Override
    protected String getDescription(MBeanAttributeInfo info) {
        String description = null;
        if (info.getName().equals("MountPoint")) {
            description = "The mount point URI of this managed federated file system.";
        } else if (info.getName().equals("Touched")) {
            description = "Whether or not this managed federated file system needs to get umount()ed.";
        } else if (info.getName().equals("ModelOfParent")) {
            description = "The enclosing managed federated file system.";
        } else if (info.getName().equals("MountPointOfParent")) {
            description = "The mount point URI of the parent file system.";
        } else if (info.getName().equals("SizeOfData")) {
            description = "The data size of this managed federated file system.";
        } else if (info.getName().equals("SizeOfStorage")) {
            description = "The storage size of this managed federated file system.";
        } else if (info.getName().equals("TimeWritten")) {
            description = "The last write time of this managed federated file system.";
        } else if (info.getName().equals("TimeRead")) {
            description = "The last read or access time of this managed federated file system.";
        } else if (info.getName().equals("TimeCreated")) {
            description = "The creation time of this managed federated file system.";
        }
        return description;
    }

    /**
     * Override customization hook:
     * You can supply a customized description for MBeanParameterInfo.getDescription()
     */
    @Override
    protected String getDescription(MBeanOperationInfo op, MBeanParameterInfo param, int sequence) {
        return null;
    }

    /**
     * Override customization hook:
     * You can supply a customized description for MBeanParameterInfo.getName()
     */
    @Override
    protected String getParameterName(MBeanOperationInfo op, MBeanParameterInfo param, int sequence) {
        return null;
    }

    /**
     * Override customization hook:
     * You can supply a customized description for MBeanOperationInfo.getDescription()
     */
    @Override
    protected String getDescription(MBeanOperationInfo info) {
        String description = null;
        if (info.getName().equals("umount")) {
            description = "Unmounts this managed federated file system and all enclosed managed federated file systems. If any managed federated file system is busy with I/O, an FsSyncException is thrown.";
        }
        return description;
    }

    @Override
    public String getMountPoint() {
        return model.getMountPoint().toString();
    }

    @Override
    public boolean isTouched() {
        return model.isTouched();
    }

    @Override
    public JmxModelViewMXBean getModelOfParent() {
        final FsModel parent = model.getParent();
        assert null != parent;
        return null == parent.getParent()
                ? null
                : JmxModelView.register(parent);
    }

    @Override
    public String getMountPointOfParent() {
        final FsModel parent = model.getParent();
        assert null != parent;
        return parent.getMountPoint().toString();
    }

    private volatile FsController<?> parentController;

    private FsController<?> getParentController() {
        final FsController<?> parentController = this.parentController;
        return null != parentController
                ? parentController
                : (this.parentController = FsManagerLocator.SINGLETON.getManager()
                    .controller(DRIVER, model.getMountPoint())
                    .getParent());
    }

    private volatile FsEntryName parentEntryName;

    private FsEntryName getParentEntryName() {
        final FsEntryName parentEntryName = this.parentEntryName;
        return null != parentEntryName
                ? parentEntryName
                : (this.parentEntryName = model.getMountPoint().getPath().getEntryName());
    }

    @Override
    public long getSizeOfData() {
        try {
            return getParentController()
                    .stat(NONE, getParentEntryName())
                    .getSize(DATA);
        } catch (IOException ex) {
            return UNKNOWN;
        }
    }

    @Override
    public long getSizeOfStorage() {
        try {
            return getParentController()
                    .stat(NONE, getParentEntryName())
                    .getSize(STORAGE);
        } catch (IOException ex) {
            return UNKNOWN;
        }
    }

    @Override
    public String getTimeWritten() {
        final long time;
        try {
            time = getParentController()
                        .stat(NONE, getParentEntryName())
                        .getTime(WRITE);
        } catch (IOException ex) {
            return null;
        }
        return UNKNOWN == time ? null : new Date(time).toString();
    }

    @Override
    public String getTimeRead() {
        final long time;
        try {
            time = getParentController()
                        .stat(NONE, getParentEntryName())
                        .getTime(READ);
        } catch (IOException ex) {
            return null;
        }
        return UNKNOWN == time ? null : new Date(time).toString();
    }

    @Override
    public String getTimeCreated() {
        final long time;
        try {
            time = getParentController()
                        .stat(NONE, getParentEntryName())
                        .getTime(CREATE);
        } catch (IOException ex) {
            return null;
        }
        return UNKNOWN == time ? null : new Date(time).toString();
    }

    @Override
    public void umount() throws FsSyncException {
        new FsFilteringManager( FsManagerLocator.SINGLETON.getManager(),
                                model.getMountPoint())
                .sync(SYNC_OPTIONS);
    }
}