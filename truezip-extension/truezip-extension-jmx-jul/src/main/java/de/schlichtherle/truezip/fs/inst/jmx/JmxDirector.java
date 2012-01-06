/*
 * Copyright 2011 Schlichtherle IT Services
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.schlichtherle.truezip.fs.inst.jmx;

import de.schlichtherle.truezip.entry.Entry;
import de.schlichtherle.truezip.fs.FsController;
import de.schlichtherle.truezip.fs.FsManager;
import de.schlichtherle.truezip.fs.FsModel;
import de.schlichtherle.truezip.fs.inst.InstrumentingCompositeDriver;
import de.schlichtherle.truezip.fs.inst.InstrumentingController;
import de.schlichtherle.truezip.fs.inst.InstrumentingDirector;
import de.schlichtherle.truezip.fs.inst.InstrumentingIOPool;
import de.schlichtherle.truezip.fs.inst.InstrumentingManager;
import de.schlichtherle.truezip.socket.IOPool;
import de.schlichtherle.truezip.socket.InputSocket;
import de.schlichtherle.truezip.socket.OutputSocket;
import de.schlichtherle.truezip.util.JSE7;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.management.ManagementFactory;
import javax.management.InstanceNotFoundException;
import javax.management.JMX;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import net.jcip.annotations.ThreadSafe;

/**
 * @author  Christian Schlichtherle
 * @version $Id$
 */
@ThreadSafe
@DefaultAnnotation(NonNull.class)
public class JmxDirector extends InstrumentingDirector {

    private static final String APPLICATION_IO_STATISTICS = "ApplicationIOStatistics";
    private static final String KERNEL_IO_STATISTICS = "KernelIOStatistics";
    private static final String TEMP_IO_STATISTICS = "TempIOStatistics";
    private static final MBeanServer
            mbs = ManagementFactory.getPlatformMBeanServer();
    public static final JmxDirector SINGLETON = JSE7.AVAILABLE
            ? new JmxNio2Director()
            : new JmxDirector();

    private JmxDirector() {
    }

    private volatile JmxIOStatistics application;

    JmxIOStatistics getApplicationIOStatistics() {
        final JmxIOStatistics stats = application;
        assert null != stats;
        return stats;
    }

    void setApplicationIOStatistics(final JmxIOStatistics stats) {
        assert null != stats;
        this.application = stats;
        JmxIOStatisticsView.register(stats, APPLICATION_IO_STATISTICS);
    }

    private volatile JmxIOStatistics kernel;

    JmxIOStatistics getKernelIOStatistics() {
        final JmxIOStatistics stats = kernel;
        assert null != stats;
        return stats;
    }

    void setKernelIOStatistics(final JmxIOStatistics stats) {
        assert null != stats;
        this.kernel = stats;
        JmxIOStatisticsView.register(stats, KERNEL_IO_STATISTICS);
    }

    private volatile JmxIOStatistics temp;

    JmxIOStatistics getTempIOStatistics() {
        final JmxIOStatistics stats = temp;
        assert null != stats;
        return stats;
    }

    void setTempIOStatistics(final JmxIOStatistics stats) {
        assert null != stats;
        this.temp = stats;
        JmxIOStatisticsView.register(stats, TEMP_IO_STATISTICS);
    }

    void clearStatistics() {
        for (final Object[] params : new Object[][] {
            { APPLICATION_IO_STATISTICS, application, },
            { KERNEL_IO_STATISTICS, kernel, },
            { TEMP_IO_STATISTICS, temp, },
        }) {
            final ObjectName pattern;
            try {
                pattern = new ObjectName(FsManager.class.getName() + ":type=" + params[0] + ",name=*");
            } catch (MalformedObjectNameException ex) {
                throw new AssertionError();
            }
            for (final ObjectName found : mbs.queryNames(pattern, null)) {
                final JmxIOStatisticsMXBean proxy = JMX.newMXBeanProxy(
                        mbs, found, JmxIOStatisticsMXBean.class);
                if (((JmxIOStatistics) params[1]).getTimeCreatedMillis()
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
    public <E extends IOPool.Entry<E>> IOPool<E> instrument(IOPool<E> pool) {
        return new JmxIOPool<E>(pool, this);
    }

    @Override
    public FsManager instrument(FsManager manager) {
        return new JmxManager(manager, this);
    }

    @Override
    public FsModel instrument(FsModel model, InstrumentingCompositeDriver context) {
        return new JmxModel(model, this);
    }

    @Override
    public <M extends FsModel> FsController<M> instrument(FsController<M> controller, InstrumentingManager context) {
        return new JmxApplicationController<M>(controller, this);
    }

    @Override
    public <M extends FsModel> FsController<M> instrument(FsController<M> controller, InstrumentingCompositeDriver context) {
        return new JmxKernelController<M>(controller, this);
    }

    @Override
    public <E extends IOPool.Entry<E>> InputSocket<E> instrument(InputSocket<E> input, InstrumentingIOPool<E>.InstrumentingEntry context) {
        return new JmxInputSocket<E>(input, this, temp);
    }

    @edu.umd.cs.findbugs.annotations.SuppressWarnings("BC_UNCONFIRMED_CAST")
    @Override
    public <E extends Entry, M extends FsModel> InputSocket<E> instrument(InputSocket<E> input, InstrumentingController<M> context) {
        return new JmxInputSocket<E>(input, this, ((JmxController) context).getIOStatistics());
    }

    @Override
    public <E extends IOPool.Entry<E>> OutputSocket<E> instrument(OutputSocket<E> output, InstrumentingIOPool<E>.InstrumentingEntry context) {
        return new JmxOutputSocket<E>(output, this, temp);
    }

    @edu.umd.cs.findbugs.annotations.SuppressWarnings("BC_UNCONFIRMED_CAST")
    @Override
    public <E extends Entry, M extends FsModel> OutputSocket<E> instrument(OutputSocket<E> output, InstrumentingController<M> context) {
        return new JmxOutputSocket<E>(output, this, ((JmxController) context).getIOStatistics());
    }

    private static final class JmxNio2Director extends JmxDirector {
        @Override
        public <E extends IOPool.Entry<E>> InputSocket<E> instrument(InputSocket<E> input, InstrumentingIOPool<E>.InstrumentingEntry context) {
            return new JmxNio2InputSocket<E>(input, this, super.temp);
        }

        @edu.umd.cs.findbugs.annotations.SuppressWarnings("BC_UNCONFIRMED_CAST")
        @Override
        public <E extends Entry, M extends FsModel> InputSocket<E> instrument(InputSocket<E> input, InstrumentingController<M> context) {
            return new JmxNio2InputSocket<E>(input, this, ((JmxController) context).getIOStatistics());
        }

        @Override
        public <E extends IOPool.Entry<E>> OutputSocket<E> instrument(OutputSocket<E> output, InstrumentingIOPool<E>.InstrumentingEntry context) {
            return new JmxNio2OutputSocket<E>(output, this, super.temp);
        }

        @edu.umd.cs.findbugs.annotations.SuppressWarnings("BC_UNCONFIRMED_CAST")
        @Override
        public <E extends Entry, M extends FsModel> OutputSocket<E> instrument(OutputSocket<E> output, InstrumentingController<M> context) {
            return new JmxNio2OutputSocket<E>(output, this, ((JmxController) context).getIOStatistics());
        }
    }
}
