/*
 * Copyright (C) 2010 Schlichtherle IT Services
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
package de.schlichtherle.truezip.io.archive.controller;

import de.schlichtherle.truezip.io.archive.entry.ArchiveEntry;
import de.schlichtherle.truezip.io.archive.filesystem.ArchiveFileSystemEntry;
import de.schlichtherle.truezip.io.archive.model.ArchiveModel;
import de.schlichtherle.truezip.io.entry.Entry;
import de.schlichtherle.truezip.io.filesystem.FileSystemEntryName;
import de.schlichtherle.truezip.io.filesystem.FilterFileSystemController;
import de.schlichtherle.truezip.io.socket.InputOption;
import de.schlichtherle.truezip.io.socket.InputSocket;
import de.schlichtherle.truezip.io.socket.OutputOption;
import de.schlichtherle.truezip.io.socket.OutputSocket;
import de.schlichtherle.truezip.util.BitField;
import java.io.IOException;

/**
 * @param   <E> The type of the archive entries.
 * @author  Christian Schlichtherle
 * @version $Id$
 */
public abstract class FilterArchiveController<
        E extends ArchiveEntry,
        C extends ArchiveController<? extends E>>
extends FilterFileSystemController<C>
implements ArchiveController<E> {

    /**
     * Constructs a new filter archive controller.
     *
     * @param controller the non-{@code null} archive controller.
     */
    protected FilterArchiveController(final C controller) {
        super(controller);
    }

    private volatile ArchiveModel model;

    @Override
    public final ArchiveModel getModel() {
        return null != model ? model : (model = controller.getModel());
    }

    @Override
    public ArchiveFileSystemEntry<? extends E> getEntry(FileSystemEntryName name)
    throws IOException {
        return controller.getEntry(name);
    }

    @Override
    public InputSocket<? extends E> getInputSocket(
            FileSystemEntryName name,
            BitField<InputOption> options) {
        return controller.getInputSocket(name, options);
    }

    @Override
    public OutputSocket<? extends E> getOutputSocket(
            FileSystemEntryName name,
            BitField<OutputOption> options,
            Entry template) {
        return controller.getOutputSocket(name, options, template);
    }
}
