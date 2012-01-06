/*
 * Copyright (C) 2004-2011 Schlichtherle IT Services
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.schlichtherle.truezip.fs;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import net.jcip.annotations.ThreadSafe;

/**
 * Indicates that a file system is a false positive file system.
 * This exception type is reserved for use within the TrueZIP Kernel in order
 * to reroute file system operations to the parent file system of a false
 * positive federated file system, i.e. a false positive archive file.
 * Unless there is a bug, an exception of this type <em>never</em> pops up to
 * a TrueZIP application and is <em>always</em> associated with another
 * {@link IOException} as its {@link #getCause()}.
 *
 * @see     FsFederatingController
 * @author  Christian Schlichtherle
 * @version $Id$
 */
@ThreadSafe
@SuppressWarnings("serial") // serializing an exception for a temporary event is nonsense!
@DefaultAnnotation(NonNull.class)
public class FsFalsePositiveException extends FsException {

    public FsFalsePositiveException(IOException cause) {
        super(cause);
        assert null != cause;
        assert !(cause instanceof FsException);
    }

    @Override
    public @NonNull IOException getCause() {
        return (IOException) super.getCause();
    }

    @Override
    @edu.umd.cs.findbugs.annotations.SuppressWarnings("BC_UNCONFIRMED_CAST")
    public final FsFalsePositiveException initCause(Throwable cause) {
        assert super.getCause() instanceof IOException;
        super.initCause((IOException) cause);
        return this;
    }
}
