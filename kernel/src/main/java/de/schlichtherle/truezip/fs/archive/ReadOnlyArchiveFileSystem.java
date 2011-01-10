/*
 * Copyright (C) 2011 Schlichtherle IT Services
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
package de.schlichtherle.truezip.fs.archive;

import de.schlichtherle.truezip.entry.Entry.Type;
import de.schlichtherle.truezip.entry.EntryContainer;
import de.schlichtherle.truezip.entry.EntryFactory;
import de.schlichtherle.truezip.entry.Entry;
import de.schlichtherle.truezip.entry.Entry.Access;
import de.schlichtherle.truezip.fs.FsEntryName;
import de.schlichtherle.truezip.fs.FsOutputOption;
import de.schlichtherle.truezip.util.BitField;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A read-only archive file system.
 * <p>
 * All modifying methods throw a {@link ReadOnlyArchiveFileSystemException}.
 *
 * @param   <E> The type of the archive entries.
 * @author  Christian Schlichtherle
 * @version $Id$
 */
final class ReadOnlyArchiveFileSystem<E extends ArchiveEntry>
extends ArchiveFileSystem<E> {

    ReadOnlyArchiveFileSystem(  final @NonNull EntryContainer<E> container,
                                final @NonNull EntryFactory<E> factory,
                                final @CheckForNull Entry rootTemplate) {
        super(factory, container, rootTemplate);
    }

    /** The implementation in this class returns {@code true}. */
    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public boolean isTouched() {
        assert !super.isTouched();
        return false;
    }

    @Override
    @NonNull
    public ArchiveFileSystemOperation<E> mknod(
            @NonNull FsEntryName name,
            @NonNull Type type,
            @NonNull BitField<FsOutputOption> options,
            @CheckForNull Entry template)
    throws ArchiveFileSystemException {
        throw new ReadOnlyArchiveFileSystemException();
    }

    @Override
    public void unlink(FsEntryName path)
    throws ArchiveFileSystemException {
        throw new ReadOnlyArchiveFileSystemException();
    }

    @Override
    public boolean setTime(FsEntryName path, BitField<Access> types, long value)
    throws ArchiveFileSystemException {
        throw new ReadOnlyArchiveFileSystemException();
    }
}
