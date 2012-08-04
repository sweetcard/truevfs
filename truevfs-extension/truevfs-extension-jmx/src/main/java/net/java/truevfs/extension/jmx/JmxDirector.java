/*
 * Copyright (C) 2005-2012 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.java.truevfs.extension.jmx;

import java.lang.management.ManagementFactory;
import javax.annotation.concurrent.ThreadSafe;
import javax.management.*;
import net.java.truevfs.component.instrumentation.*;
import net.java.truevfs.kernel.spec.FsController;
import net.java.truevfs.kernel.spec.FsManager;
import net.java.truevfs.kernel.spec.FsModel;
import net.java.truevfs.kernel.spec.cio.*;

/**
 * @author Christian Schlichtherle
 */
@ThreadSafe
public class JmxDirector extends InstrumentingDirector<JmxDirector> {

    private static final String APPLICATION_IO_STATISTICS = "Application I/O Statistics";
    private static final String KERNEL_IO_STATISTICS = "Kernel I/O Statistics";
    private static final String BUFFER_IO_STATISTICS = "Buffer I/O Statistics";
    private static final MBeanServer
            mbs = ManagementFactory.getPlatformMBeanServer();
    public static final JmxDirector SINGLETON = new JmxDirector();

    private JmxDirector() { }

    private volatile JmxIoStatistics application;

    JmxIoStatistics getApplicationIoStatistics() {
        final JmxIoStatistics stats = application;
        assert null != stats;
        return stats;
    }

    void setApplicationIoStatistics(final JmxIoStatistics stats) {
        assert null != stats;
        this.application = stats;
        JmxIoStatisticsView.register(stats, APPLICATION_IO_STATISTICS);
    }

    private volatile JmxIoStatistics kernel;

    JmxIoStatistics getKernelIoStatistics() {
        final JmxIoStatistics stats = kernel;
        assert null != stats;
        return stats;
    }

    void setKernelIoStatistics(final JmxIoStatistics stats) {
        assert null != stats;
        this.kernel = stats;
        JmxIoStatisticsView.register(stats, KERNEL_IO_STATISTICS);
    }

    private volatile JmxIoStatistics temp;

    JmxIoStatistics getTempIoStatistics() {
        final JmxIoStatistics stats = temp;
        assert null != stats;
        return stats;
    }

    void setTempIoStatistics(final JmxIoStatistics stats) {
        assert null != stats;
        this.temp = stats;
        JmxIoStatisticsView.register(stats, BUFFER_IO_STATISTICS);
    }

    void clearStatistics() {
        for (final Object[] params : new Object[][] {
            { APPLICATION_IO_STATISTICS, application, },
            { KERNEL_IO_STATISTICS, kernel, },
            { BUFFER_IO_STATISTICS, temp, },
        }) {
            final ObjectName pattern;
            try {
                pattern = new ObjectName(FsManager.class.getName() + ":type=" + params[0] + ",name=*");
            } catch (MalformedObjectNameException ex) {
                throw new AssertionError();
            }
            for (final ObjectName found : mbs.queryNames(pattern, null)) {
                final JmxIoStatisticsMXBean proxy = JMX.newMXBeanProxy(
                        mbs, found, JmxIoStatisticsMXBean.class);
                if (((JmxIoStatistics) params[1]).getTimeCreatedMillis()
                        == proxy.getTimeCreatedMillis())
                    continue;
                try {
                    mbs.unregisterMBean(found);
                } catch (InstanceNotFoundException ex) {
                    throw new AssertionError();
                } catch (MBeanRegistrationException ex) {
                    throw new AssertionError(ex);
                }
            }
        }
    }

    @Override
    public FsManager instrument(FsManager manager) {
        return new JmxManager(this, manager);
    }

    @Override
    public <B extends IoBuffer<B>> IoBufferPool<B> instrument(IoBufferPool<B> pool) {
        return new JmxIoBufferPool<>(this, pool);
    }

    @Override
    public FsModel instrument(
            FsModel model,
            InstrumentingCompositeDriver<JmxDirector> context) {
        return new JmxModel(this, model);
    }

    @Override
    public FsController instrument(
            FsController controller,
            InstrumentingManager<JmxDirector> context) {
        return new JmxApplicationController(this, controller);
    }

    @Override
    public FsController instrument(
            FsController controller,
            InstrumentingCompositeDriver<JmxDirector> context) {
        return new JmxKernelController(this, controller);
    }

    @Override
    public <B extends IoBuffer<B>> InputSocket<B> instrument(
            InputSocket<B> input,
            InstrumentingIoBuffer<JmxDirector, B> context) {
        return new JmxInputSocket<>(this, input, temp);
    }

    @Override
    public <E extends Entry> InputSocket<E> instrument(
            InputSocket<E> input,
            InstrumentingController<JmxDirector> context) {
        return new JmxInputSocket<>(this, input,
                JmxController.class.cast(context).getIOStatistics());
    }

    @Override
    public <B extends IoBuffer<B>> OutputSocket<B> instrument(
            OutputSocket<B> output,
            InstrumentingIoBuffer<JmxDirector, B> context) {
        return new JmxOutputSocket<>(this, output, temp);
    }

    @Override
    public <E extends Entry> OutputSocket<E> instrument(
            OutputSocket<E> output,
            InstrumentingController<JmxDirector> context) {
        return new JmxOutputSocket<>(this, output,
                JmxController.class.cast(context).getIOStatistics());
    }
}
