/*
 * Copyright 2004-2012 Schlichtherle IT Services
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.schlichtherle.truezip.fs.archive;

import static de.schlichtherle.truezip.entry.Entry.UNKNOWN;
import static de.schlichtherle.truezip.entry.Entry.ALL_ACCESS_SET;
import static de.schlichtherle.truezip.entry.Entry.ALL_SIZE_SET;
import de.schlichtherle.truezip.entry.Entry.Access;
import de.schlichtherle.truezip.entry.Entry.Size;
import java.util.Formatter;

/**
 * Provides static utility methods for {@link FsArchiveEntry} objects.
 * 
 * @author  Christian Schlichtherle
 * @version $Id$
 */
public final class FsArchiveEntries {

    /** You cannot instantiate this class. */
    private FsArchiveEntries() { }

    /**
     * Returns a string representation of this object for debugging and logging
     * purposes.
     * 
     * @param  e the archive entry
     * @return A string representation of this object for debugging and logging
     *         purposes.
     */
    public static String toString(final FsArchiveEntry e) {
        final StringBuilder s = new StringBuilder(256);
        final Formatter f = new Formatter(s)
                .format("%s[name=%s, type=%s",
                    e.getClass().getName(), e.getName(), e.getType());
        for (Size type : ALL_SIZE_SET) {
            final long size = e.getSize(type);
            if (UNKNOWN != size)
                f.format(", size(%s)=%d", type, size);
        }
        for (Access type : ALL_ACCESS_SET) {
            final long time = e.getTime(type);
            if (UNKNOWN != time)
                f.format(", time(%s)=%tc", type, time);
        }
        return s.append("]").toString();
    }
}