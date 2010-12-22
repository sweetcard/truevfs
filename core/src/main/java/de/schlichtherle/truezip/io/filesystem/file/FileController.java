/*
 * Copyright 2010 Schlichtherle IT Services
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
package de.schlichtherle.truezip.io.filesystem.file;

import de.schlichtherle.truezip.io.filesystem.FileSystemEntryName;
import de.schlichtherle.truezip.io.filesystem.FileSystemException;
import de.schlichtherle.truezip.io.filesystem.FileSystemController;
import java.net.URI;
import de.schlichtherle.truezip.io.entry.Entry;
import de.schlichtherle.truezip.io.entry.Entry.Access;
import de.schlichtherle.truezip.io.entry.Entry.Type;
import de.schlichtherle.truezip.io.filesystem.FileSystemModel;
import de.schlichtherle.truezip.io.filesystem.SyncException;
import de.schlichtherle.truezip.io.filesystem.SyncOption;
import de.schlichtherle.truezip.io.filesystem.InputOption;
import de.schlichtherle.truezip.io.filesystem.OutputOption;
import de.schlichtherle.truezip.io.socket.InputSocket;
import de.schlichtherle.truezip.io.socket.OutputSocket;
import de.schlichtherle.truezip.util.BitField;
import de.schlichtherle.truezip.util.ExceptionBuilder;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import javax.swing.Icon;
import net.jcip.annotations.ThreadSafe;

import static de.schlichtherle.truezip.io.Files.*;
import static de.schlichtherle.truezip.io.entry.Entry.Access.*;
import static de.schlichtherle.truezip.io.filesystem.FileSystemEntryName.*;
import static java.io.File.separatorChar;

/**
 * @author Christian Schlichtherle
 * @version $Id$
 */
@ThreadSafe
final class FileController extends FileSystemController<FileSystemModel>  {

    private final FileSystemModel model;
    private final File target;

    FileController(final FileSystemModel model) {
        if (null != model.getParent())
            throw new IllegalArgumentException();
        URI uri = model.getMountPoint().getUri();
        // Postfix: Move Windows UNC host from authority to path.
        if ('\\' == separatorChar && null != uri.getRawAuthority()) {
            try {
                uri = new URI(  uri.getScheme(), "",
                                SEPARATOR + SEPARATOR + uri.getAuthority() + uri.getPath(),
                                uri.getQuery(), uri.getFragment());
            } catch (URISyntaxException ex) {
                throw new AssertionError(ex);
            }
        }
        this.model = model;
        this.target = new File(uri);
    }

    @Override
    public FileSystemModel getModel() {
        return model;
    }

    @Override
    public FileSystemController<?> getParent() {
        return null;
    }

    @Override
    public Icon getOpenIcon() throws IOException {
        return null;
    }

    @Override
    public Icon getClosedIcon() throws IOException {
        return null;
    }

    @Override
    public boolean isReadOnly() throws IOException {
        return false;
    }

    @Override
    public FileEntry getEntry(FileSystemEntryName name) throws IOException {
        final FileEntry entry = new FileEntry(target, name);
        return entry.getFile().exists() ? entry : null;
    }

    @Override
    public boolean isReadable(FileSystemEntryName name) throws IOException {
        final File file = new File(target, name.getPath());
        return file.canRead();
    }

    @Override
    public boolean isWritable(FileSystemEntryName name) throws IOException {
        final File file = new File(target, name.getPath());
        return isCreatableOrWritable(file);
    }

    @Override
    public void setReadOnly(FileSystemEntryName name) throws IOException {
        final File file = new File(target, name.getPath());
        if (!file.setReadOnly())
            throw new IOException();
    }

    @Override
    public boolean setTime(FileSystemEntryName name, BitField<Access> types, long value)
    throws IOException {
        final File file = new File(target, name.getPath());
        boolean ok = true;
        for (final Access type : types)
            ok &= WRITE == type ? file.setLastModified(value) : false;
        return ok;
    }

    @Override
    public InputSocket<?> getInputSocket(
            FileSystemEntryName name,
            BitField<InputOption> options) {
        return FileInputSocket.get( new FileEntry(target, name),
                                    options.clear(InputOption.BUFFER));
    }

    @Override
    public OutputSocket<?> getOutputSocket(
            FileSystemEntryName name,
            BitField<OutputOption> options,
            Entry template) {
        return FileOutputSocket.get(new FileEntry(target, name), options, template);
    }

    @Override
    public boolean mknod(   FileSystemEntryName name,
                            Type type,
                            BitField<OutputOption> options,
                            Entry template)
    throws IOException {
        final File file = new File(target, name.getPath());
        switch (type) {
            case FILE:
                return file.createNewFile();

            case DIRECTORY:
                return file.mkdir();

            default:
                throw new IOException(file.getPath() + " (entry type not supported: " + type + ")");
        }
    }

    @Override
    public void unlink(FileSystemEntryName name)
    throws IOException {
        final File file = new File(target, name.getPath());
        if (!file.delete())
            throw new IOException(file.getPath() + " (cannot delete)");
    }

    @Override
    public <X extends IOException>
    void sync(  final BitField<SyncOption> options, final ExceptionBuilder<? super SyncException, X> builder)
    throws X, FileSystemException {
    }
}
