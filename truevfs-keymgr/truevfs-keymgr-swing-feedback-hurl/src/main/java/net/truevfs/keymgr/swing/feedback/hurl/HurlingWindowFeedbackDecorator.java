/*
 * Copyright (C) 2005-2012 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.truevfs.keymgr.swing.feedback.hurl;

import javax.annotation.concurrent.Immutable;
import net.truevfs.keymgr.swing.feedback.Feedback;
import net.truevfs.keymgr.swing.spi.InvalidKeyFeedbackDecorator;

/**
 * Decorates any given feedback with a
 * {@link HurlingWindowFeedback}.
 * 
 * @author Christian Schlichtherle
 */
@Immutable
public final class HurlingWindowFeedbackDecorator
extends InvalidKeyFeedbackDecorator {
    @Override
    public Feedback apply(Feedback feedback) {
        return new HurlingWindowFeedback(feedback);
    }

    /** Returns -100. */
    @Override
    public int getPriority() {
        return -100;
    }
}
