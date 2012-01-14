/*
 * Copyright 2004-2012 Schlichtherle IT Services
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.schlichtherle.truezip.socket;

import de.schlichtherle.truezip.util.Pool;
import java.io.IOException;

/**
 * A pool for allocating I/O buffers, which are used as a volatile storage for
 * bulk data.
 * Typical implementations may use temporary files for big data or byte arrays
 * for small data.
 * The I/O buffers are referred to by {@link IOEntry}s.
 * <p>
 * Implementations must be thread-safe.
 * However, this does not necessarily apply to the implementation of its
 * managed resources, i.e. the {@code IOEntry}s.
 *
 * @param   <E> the type of the entries for the I/O buffers.
 * @author  Christian Schlichtherle
 * @version $Id$
 */
public interface IOPool<E extends IOEntry<E>>
extends Pool<IOPool.Entry<E>, IOException> {

    /**
     * A releasable I/O entry.
     * TODO for TrueZIP 8: This should be named "IOBuffer".
     * 
     * @param <E> the type of the I/O entries.
     */
    interface Entry<E extends IOEntry<E>>
    extends IOEntry<E>, Pool.Releasable<IOException> {
    }
}
