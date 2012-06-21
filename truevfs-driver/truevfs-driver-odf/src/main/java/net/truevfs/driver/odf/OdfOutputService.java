/*
 * Copyright (C) 2005-2012 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.truevfs.driver.odf;

import edu.umd.cs.findbugs.annotations.DischargesObligation;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import javax.annotation.WillCloseWhenClosed;
import javax.annotation.concurrent.NotThreadSafe;
import net.truevfs.driver.zip.ZipDriverEntry;
import net.truevfs.driver.zip.ZipOutputService;
import static net.truevfs.driver.zip.io.ZipEntry.STORED;
import static net.truevfs.kernel.cio.Entry.UNKNOWN;
import net.truevfs.kernel.cio.*;

/**
 * Created by {@link OdfDriver} to meet the special requirements of
 * OpenDocument Format (ODF) files.
 *
 * @author Christian Schlichtherle
 */
@NotThreadSafe
public class OdfOutputService extends MultiplexingOutputService<ZipDriverEntry> {

    /** The name of the entry to receive tender, loving care. */
    private static final String MIMETYPE = "mimetype";

    /** Whether we have started to write the <i>mimetype</i> entry or not. */
    private boolean mimetype;

    /**
     * Constructs a new ODF output service.
     * 
     * @param output the decorated output service.
     * @param pool the pool for buffering entry data.
     */
    public OdfOutputService(
            IoPool<?> pool,
            @WillCloseWhenClosed ZipOutputService output) {
        super(pool, output);
    }

    @Override
    public OutputSocket<ZipDriverEntry> output(final ZipDriverEntry entry) {
        Objects.requireNonNull(entry);

        final class Output extends DecoratingOutputSocket<ZipDriverEntry> {
            Output() { super(OdfOutputService.super.output(entry)); }

            @Override
            public ZipDriverEntry target() throws IOException {
                return entry;
            }

            @Override
            public OutputStream stream(InputSocket<? extends Entry> peer)
            throws IOException {
                if (MIMETYPE.equals(entry.getName())) {
                    mimetype = true;
                    if (UNKNOWN == entry.getMethod())
                        entry.setMethod(STORED);
                }
                return socket().stream(peer);
            }
        } // Output

        return new Output();
    }

    @Override
    public boolean isBusy() {
        return !mimetype || super.isBusy();
    }

    @Override
    @DischargesObligation
    public void close() throws IOException {
        mimetype = true; // trigger writing temps
        super.close();
    }
}