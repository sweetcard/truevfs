/*
 * Copyright (C) 2005-2012 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.truevfs.kernel.util;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

/**
 * A link has a nullable {@link #get() target} property.
 * This interface is useful if a class is decorating or adapting another class
 * and access to the decorated or adapted object should be provided as part of
 * the public API of the decorating or adapting class.
 *
 * @param   <T> The type of the target.
 * @author  Christian Schlichtherle
 */

public interface Link<T> {

    /**
     * Returns the target of this link.
     * <p>
     * The returned object reference may be {@code null} subject to the terms
     * and conditions of sub-interfaces or implementations.
     * 
     * @return The target of this link.
     */
    @Nullable T get();

    /**
     * A factory for links which defines the terms and conditions for clearing
     * their target.
     */
    @SuppressWarnings("PublicInnerClass")
    enum Type {

        /** This reference type never clears the target of a link. */
        STRONG {
            @Override
            <T> Link<T> newLink(@CheckForNull T target, ReferenceQueue<? super T> queue) {
                return new Strong<>(target);
            }
        },

        /**
         * This reference type clears the target of a link according to the
         * terms and conditions for a {@link SoftReference}.
         */
        SOFT {
            @Override
            <T> Link<T> newLink(@CheckForNull T target, ReferenceQueue<? super T> queue) {
                return new Soft<>(target, queue);
            }
        },

        /**
         * This reference type clears the target of a link according to the
         * terms and conditions for a {@link WeakReference}.
         */
        WEAK {
            @Override
            <T> Link<T> newLink(@CheckForNull T target, ReferenceQueue<? super T> queue) {
                return new Weak<>(target, queue);
            }
        },

        /**
         * This reference type clears the target of a link according to the
         * terms and conditions for a {@link PhantomReference}.
         */
        PHANTOM {
            @Override
            public <T> Link<T> newLink(@CheckForNull T target, ReferenceQueue<? super T> queue) {
                return new Phantom<>(target, queue);
            }
        };

        /** Returns a new typed link to the given nullable target. */
        abstract <T> Link<T> newLink(@CheckForNull T target, @CheckForNull ReferenceQueue<? super T> queue);

        /** Returns a new typed link to the given nullable target. */
        public <T> Link<T> newLink(@CheckForNull T target) {
            return newLink(target, null);
        }

        /** A strong reference. */
        private static final class Strong<T> implements Link<T> {
            private final @CheckForNull T target;

            Strong(final @CheckForNull T target) {
                this.target = target;
            }

            @Override
            public T get() {
                return target;
            }

            @Override
            public String toString() {
                return String.format("%s[target=%s]",
                        getClass().getName(),
                        get());
            }
        }

        /** Adapts its subclass to the {@link Link} interface. */
        private static final class Soft<T> extends SoftReference<T>
        implements Link<T> {
            Soft(T target, ReferenceQueue<? super T> queue) {
                super(target, queue);
            }

            @Override
            public String toString() {
                return String.format("%s[target=%s]",
                        getClass().getName(),
                        get());
            }
        }

        /** Adapts its subclass to the {@link Link} interface. */
        private static final class Weak<T> extends WeakReference<T>
        implements Link<T> {
            Weak(T target, ReferenceQueue<? super T> queue) {
                super(target, queue);
            }

            @Override
            public String toString() {
                return String.format("%s[target=%s]",
                        getClass().getName(),
                        get());
            }
        }

        /** Adapts its subclass to the {@link Link} interface. */
        private static final class Phantom<T> extends PhantomReference<T>
        implements Link<T> {
            Phantom(T target, ReferenceQueue<? super T> queue) {
                super(target, queue);
            }

            @Override
            public String toString() {
                return String.format("%s[target=%s]",
                        getClass().getName(),
                        get());
            }
        }
    }
}