/*
 * Copyright (C) 2005-2015 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.java.truevfs.driver.tar.gzip;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import javax.annotation.concurrent.Immutable;
import net.java.truecommons.io.AbstractSink;
import net.java.truecommons.io.AbstractSource;
import net.java.truecommons.io.Streams;
import net.java.truecommons.shed.BitField;
import net.java.truevfs.comp.tardriver.TarDriver;
import net.java.truevfs.comp.tardriver.TarDriverEntry;
import net.java.truevfs.comp.tardriver.TarInputService;
import net.java.truevfs.comp.tardriver.TarOutputService;
import net.java.truevfs.kernel.spec.FsAccessOption;
import static net.java.truevfs.kernel.spec.FsAccessOption.STORE;
import net.java.truevfs.kernel.spec.FsController;
import net.java.truevfs.kernel.spec.FsInputSocketSource;
import net.java.truevfs.kernel.spec.FsModel;
import net.java.truevfs.kernel.spec.FsNodeName;
import net.java.truevfs.kernel.spec.FsOutputSocketSink;
import net.java.truecommons.cio.InputService;
import net.java.truevfs.kernel.spec.cio.MultiplexingOutputService;
import net.java.truecommons.cio.OutputService;

/**
 * An archive driver for GZIP compressed TAR files (TAR.GZIP).
 * <p>
 * Subclasses must be thread-safe and should be immutable!
 *
 * @author Christian Schlichtherle
 */
@Immutable
public class TarGZipDriver extends TarDriver {

    /**
     * Returns the size of the I/O buffer.
     * <p>
     * The implementation in the class {@link TarGZipDriver} returns
     * {@link Streams#BUFFER_SIZE}.
     *
     * @return The size of the I/O buffer.
     */
    public int getBufferSize() {
        return Streams.BUFFER_SIZE;
    }

    /**
     * Returns the compression level to use when writing a GZIP sink stream.
     * <p>
     * The implementation in the class {@link TarGZipDriver} returns
     * {@link Deflater#BEST_COMPRESSION}.
     *
     * @return The compression level to use when writing a GZIP sink stream.
     */
    public int getLevel() {
        return Deflater.BEST_COMPRESSION;
    }

    @Override
    protected InputService<TarDriverEntry> newInput(
            final FsModel model,
            final FsInputSocketSource source)
    throws IOException {
        final class Source extends AbstractSource {
            @Override
            public InputStream stream() throws IOException {
                final InputStream in = source.stream();
                try {
                    return new GZIPInputStream(in, getBufferSize());
                } catch(final Throwable ex) {
                    try {
                        in.close();
                    } catch (final Throwable ex2) {
                        ex.addSuppressed(ex2);
                    }
                    throw ex;
                }
            }
        } // Source

        return new TarInputService(model, new Source(), this);
    }

    @Override
    protected OutputService<TarDriverEntry> newOutput(
            final FsModel model,
            final FsOutputSocketSink sink,
            final InputService<TarDriverEntry> input)
    throws IOException {
        final class Sink extends AbstractSink {
            @Override
            public OutputStream stream() throws IOException {
                final OutputStream out = sink.stream();
                try {
                    return new GZIPOutputStream(out, getBufferSize(), getLevel());
                } catch(final Throwable ex) {
                    try {
                        out.close();
                    } catch (final Throwable ex2) {
                        ex.addSuppressed(ex2);
                    }
                    throw ex;
                }
            }
        } // Sink

        return new MultiplexingOutputService<>(getPool(),
                new TarOutputService(model, new Sink(), this));
    }

    /**
     * Sets {@link FsAccessOption#STORE} in {@code options} before
     * forwarding the call to {@code controller}.
     */
    @Override
    protected FsOutputSocketSink sink(
            BitField<FsAccessOption> options,
            final FsController controller,
            final FsNodeName name) {
        // Leave FsAccessOption.COMPRESS untouched - the driver shall be given
        // opportunity to apply its own preferences to sort out such a conflict.
        options = options.set(STORE);
        return new FsOutputSocketSink(options,
                controller.output(options, name, null));
    }

    /** Extends its super class to set the deflater level. */
    private static final class GZIPOutputStream
    extends java.util.zip.GZIPOutputStream {
        GZIPOutputStream(OutputStream out, int size, int level)
        throws IOException {
            super(out, size);
            def.setLevel(level);
        }
    } // GZIPOutputStream
}
