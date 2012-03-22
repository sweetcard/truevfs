/*
 * Copyright (C) 2005-2012 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package de.schlichtherle.truezip.fs.inst;

import de.schlichtherle.truezip.fs.FsDecoratingModel;
import de.schlichtherle.truezip.fs.FsModel;
import javax.annotation.concurrent.Immutable;

/**
 * @author  Christian Schlichtherle
 */
@Immutable
public abstract class InstrumentingModel extends FsDecoratingModel<FsModel> {

    protected final InstrumentingDirector director;

    @SuppressWarnings("LeakingThisInConstructor")
    protected InstrumentingModel(FsModel model, InstrumentingDirector director) {
        super(model);
        if (null == director)
            throw new NullPointerException();
        this.director = director;
    }
}