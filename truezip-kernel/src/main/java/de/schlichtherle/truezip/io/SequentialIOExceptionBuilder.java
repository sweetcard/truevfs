/*
 * Copyright 2004-2012 Schlichtherle IT Services
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.schlichtherle.truezip.io;

import de.schlichtherle.truezip.util.AbstractExceptionBuilder;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import net.jcip.annotations.NotThreadSafe;

/**
 * Assembles a {@link SequentialIOException} from one or more
 * {@link Exception}s by
 * {@link SequentialIOException#initPredecessor(SequentialIOException) chaining}
 * them.
 * When the assembly is thrown or returned later, it is sorted by
 * {@link SequentialIOException#sortPriority() priority}.
 *
 * @param   <C> The type of the cause exceptions.
 * @param   <E> The type of the assembled exception.
 * @author  Christian Schlichtherle
 * @version $Id$
 */
@NotThreadSafe
@DefaultAnnotation(NonNull.class)
public class SequentialIOExceptionBuilder<  C extends Exception,
                                            E extends SequentialIOException>
extends AbstractExceptionBuilder<C, E> {

    private final Class<E> clazz;

    /**
     * Static constructor provided for comforting the most essential use case.
     */
    public static SequentialIOExceptionBuilder<Exception, SequentialIOException>
    create() {
        return new SequentialIOExceptionBuilder<Exception, SequentialIOException>(Exception.class, SequentialIOException.class);
    }

    public SequentialIOExceptionBuilder(Class<C> c, Class<E> e) {
        try {
            if (!e.isAssignableFrom(c))
                e.getConstructor(String.class).newInstance("test"); // fail-fast!
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex);
        }
        this.clazz = e;
    }

    /**
     * Chains the given exceptions and returns the result.
     *
     * @throws IllegalStateException if
     *         {@code cause.}{@link SequentialIOException#getPredecessor()} is
     *         already initialized by a previous call to
     *         {@link SequentialIOException#initPredecessor(SequentialIOException)}.
     */
    @SuppressWarnings("unchecked")
    @Override
    protected final E update(C cause, E previous) {
        final E next;
        try {
            next = clazz.isInstance(cause)
                    ? ((E) cause)
                    : clazz.getConstructor(String.class)
                        .newInstance(cause.toString());
        } catch (Exception ex) {
            ex.initCause(cause);
            throw new AssertionError(ex);
        }
        if (next != cause)
            next.initCause(cause);
        try {
            return (E) next.initPredecessor(previous);
        } catch (IllegalStateException ex) {
            if (previous != null)
                throw (IllegalStateException) ex.initCause(next);
            return next;
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sorts the given exception chain by
     * {@link SequentialIOException#sortPriority() priority}
     * and returns the result.
     */
    @SuppressWarnings("unchecked")
    @Override
    protected final E post(E assembly) {
        return (E) assembly.sortPriority();
    }
}
