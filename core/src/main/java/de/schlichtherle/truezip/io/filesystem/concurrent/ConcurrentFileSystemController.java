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
package de.schlichtherle.truezip.io.filesystem.concurrent;

import de.schlichtherle.truezip.io.filesystem.SyncException;
import de.schlichtherle.truezip.io.filesystem.SyncOption;
import de.schlichtherle.truezip.io.filesystem.OutputOption;
import de.schlichtherle.truezip.io.filesystem.InputOption;
import de.schlichtherle.truezip.io.entry.Entry;
import de.schlichtherle.truezip.io.entry.Entry.Type;
import de.schlichtherle.truezip.io.entry.Entry.Access;
import de.schlichtherle.truezip.io.filesystem.FileSystemController;
import de.schlichtherle.truezip.io.filesystem.FileSystemEntry;
import de.schlichtherle.truezip.io.filesystem.FileSystemEntryName;
import de.schlichtherle.truezip.io.filesystem.FileSystemException;
import de.schlichtherle.truezip.io.filesystem.DecoratingFileSystemController;
import de.schlichtherle.truezip.io.socket.OutputSocket;
import de.schlichtherle.truezip.io.socket.InputSocket;
import de.schlichtherle.truezip.io.rof.ReadOnlyFile;
import de.schlichtherle.truezip.io.socket.DecoratingInputSocket;
import de.schlichtherle.truezip.io.socket.DecoratingOutputSocket;
import de.schlichtherle.truezip.util.BitField;
import de.schlichtherle.truezip.util.ExceptionHandler;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import javax.swing.Icon;
import net.jcip.annotations.ThreadSafe;

/**
 * Supports multiple concurrent reader threads.
 * 
 * @see     ConcurrentFileSystemModel
 * @author  Christian Schlichtherle
 * @version $Id$
 */
@ThreadSafe
public final class ConcurrentFileSystemController<
        M extends ConcurrentFileSystemModel,
        C extends FileSystemController<? extends M>>
extends DecoratingFileSystemController<M, C> {

    private volatile ReadLock readLock;
    private volatile WriteLock writeLock;

    /**
     * Constructs a new concurrent file system controller.
     *
     * @param controller the decorated file system controller.
     */
    public ConcurrentFileSystemController(@NonNull C controller) {
        super(controller);
    }

    private ReadLock readLock() {
        return null != readLock ? readLock : (readLock = getModel().readLock());
    }

    private WriteLock writeLock() {
        return null != writeLock ? writeLock : (writeLock = getModel().writeLock());
    }

    private void assertNotReadLockedByCurrentThread(NotWriteLockedException ex)
    throws NotWriteLockedException {
        getModel().assertNotReadLockedByCurrentThread(ex);
    }

    @Override
    public Icon getOpenIcon() throws IOException {
        try {
            readLock().lock();
            try {
                return delegate.getOpenIcon();
            } finally {
                readLock().unlock();
            }
        } catch (NotWriteLockedException ex) {
            assertNotReadLockedByCurrentThread(ex);
            writeLock().lock();
            try {
                return delegate.getOpenIcon();
            } finally {
                writeLock().unlock();
            }
        }
    }

    @Override
    public Icon getClosedIcon() throws IOException {
        try {
            readLock().lock();
            try {
                return delegate.getClosedIcon();
            } finally {
                readLock().unlock();
            }
        } catch (NotWriteLockedException ex) {
            assertNotReadLockedByCurrentThread(ex);
            writeLock().lock();
            try {
                return delegate.getClosedIcon();
            } finally {
                writeLock().unlock();
            }
        }
    }

    @Override
    public boolean isReadOnly() throws IOException {
        try {
            readLock().lock();
            try {
                return delegate.isReadOnly();
            } finally {
                readLock().unlock();
            }
        } catch (NotWriteLockedException ex) {
            assertNotReadLockedByCurrentThread(ex);
            writeLock().lock();
            try {
                return delegate.isReadOnly();
            } finally {
                writeLock().unlock();
            }
        }
    }

    @Override
    public FileSystemEntry getEntry(FileSystemEntryName name)
    throws IOException {
        try {
            readLock().lock();
            try {
                return delegate.getEntry(name);
            } finally {
                readLock().unlock();
            }
        } catch (NotWriteLockedException ex) {
            assertNotReadLockedByCurrentThread(ex);
            writeLock().lock();
            try {
                return delegate.getEntry(name);
            } finally {
                writeLock().unlock();
            }
        }
    }

    @Override
    public boolean isReadable(FileSystemEntryName name) throws IOException {
        try {
            readLock().lock();
            try {
                return delegate.isReadable(name);
            } finally {
                readLock().unlock();
            }
        } catch (NotWriteLockedException ex) {
            assertNotReadLockedByCurrentThread(ex);
            writeLock().lock();
            try {
                return delegate.isReadable(name);
            } finally {
                writeLock().unlock();
            }
        }
    }

    @Override
    public boolean isWritable(FileSystemEntryName name) throws IOException {
        try {
            readLock().lock();
            try {
                return delegate.isWritable(name);
            } finally {
                readLock().unlock();
            }
        } catch (NotWriteLockedException ex) {
            assertNotReadLockedByCurrentThread(ex);
            writeLock().lock();
            try {
                return delegate.isWritable(name);
            } finally {
                writeLock().unlock();
            }
        }
    }

    @Override
    public void setReadOnly(FileSystemEntryName name) throws IOException {
        assertNotReadLockedByCurrentThread(null);
        writeLock().lock();
        try {
            delegate.setReadOnly(name);
        } finally {
            writeLock().unlock();
        }
    }

    @Override
    public boolean setTime(FileSystemEntryName name, BitField<Access> types, long value)
    throws IOException {
        assertNotReadLockedByCurrentThread(null);
        writeLock().lock();
        try {
            return delegate.setTime(name, types, value);
        } finally {
            writeLock().unlock();
        }
    }

    @Override
    public InputSocket<?> getInputSocket(   FileSystemEntryName name,
                                            BitField<InputOption> options) {
        return new Input(delegate.getInputSocket(name, options));
    }

    private class Input extends DecoratingInputSocket<Entry> {
        Input(InputSocket<?> input) {
            super(input);
        }

        @Override
        public Entry getLocalTarget() throws IOException {
            try {
                readLock().lock();
                try {
                    return getBoundSocket().getLocalTarget();
                } finally {
                    readLock().unlock();
                }
            } catch (NotWriteLockedException ex) {
                assertNotReadLockedByCurrentThread(ex);
                writeLock().lock();
                try {
                    return getBoundSocket().getLocalTarget();
                } finally {
                    writeLock().unlock();
                }
            }
        }

        @Override
        public ReadOnlyFile newReadOnlyFile() throws IOException {
            try {
                readLock().lock();
                try {
                    return getBoundSocket().newReadOnlyFile();
                } finally {
                    readLock().unlock();
                }
            } catch (NotWriteLockedException ex) {
                assertNotReadLockedByCurrentThread(ex);
                writeLock().lock();
                try {
                    return getBoundSocket().newReadOnlyFile();
                } finally {
                    writeLock().unlock();
                }
            }
        }

        @Override
        public InputStream newInputStream() throws IOException {
            try {
                readLock().lock();
                try {
                    return getBoundSocket().newInputStream();
                } finally {
                    readLock().unlock();
                }
            } catch (NotWriteLockedException ex) {
                assertNotReadLockedByCurrentThread(ex);
                writeLock().lock();
                try {
                    return getBoundSocket().newInputStream();
                } finally {
                    writeLock().unlock();
                }
            }
        }
    } // class Input

    @Override
    public OutputSocket<?> getOutputSocket( FileSystemEntryName name,
                                            BitField<OutputOption> options,
                                            Entry template) {
        return new Output(delegate.getOutputSocket(name, options, template));
    }

    private class Output extends DecoratingOutputSocket<Entry> {
        Output(OutputSocket<?> output) {
            super(output);
        }

        @Override
        public Entry getLocalTarget() throws IOException {
            assertNotReadLockedByCurrentThread(null);
            writeLock().lock();
            try {
                return getBoundSocket().getLocalTarget();
            } finally {
                writeLock().unlock();
            }
        }

        @Override
        public OutputStream newOutputStream() throws IOException {
            assertNotReadLockedByCurrentThread(null);
            writeLock().lock();
            try {
                return getBoundSocket().newOutputStream();
            } finally {
                writeLock().unlock();
            }
        }
    } // class Output

    @Override
    public void mknod(
            @NonNull FileSystemEntryName name,
            @NonNull Type type,
            @NonNull BitField<OutputOption> options,
            @CheckForNull Entry template)
    throws IOException {
        assertNotReadLockedByCurrentThread(null);
        writeLock().lock();
        try {
            delegate.mknod(name, type, options, template);
        } finally {
            writeLock().unlock();
        }
    }

    @Override
    public void unlink(FileSystemEntryName name)
    throws IOException {
        assertNotReadLockedByCurrentThread(null);
        writeLock().lock();
        try {
            delegate.unlink(name);
        } finally {
            writeLock().unlock();
        }
    }

    @Override
    public <X extends IOException>
    void sync(
            @NonNull final BitField<SyncOption> options,
            @NonNull final ExceptionHandler<? super SyncException, X> handler)
    throws X, FileSystemException {
        assertNotReadLockedByCurrentThread(null);
        writeLock().lock();
        try {
            delegate.sync(options, handler);
        } finally {
            writeLock().unlock();
        }
    }
}
