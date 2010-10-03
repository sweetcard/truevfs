/*
 * Copyright (C) 2007-2010 Schlichtherle IT Services
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
package de.schlichtherle.truezip.io.file;

import de.schlichtherle.truezip.io.socket.entry.CommonEntry.Access;
import de.schlichtherle.truezip.io.socket.file.FileEntry;
import de.schlichtherle.truezip.io.socket.output.CommonOutputSocket;
import de.schlichtherle.truezip.io.socket.entry.CommonEntry;
import de.schlichtherle.truezip.io.socket.input.CommonInputSocket;
import de.schlichtherle.truezip.io.FileBusyException;
import java.net.URI;
import de.schlichtherle.truezip.io.archive.controller.FalsePositiveEntryException;
import de.schlichtherle.truezip.io.archive.controller.ArchiveControllers;
import de.schlichtherle.truezip.io.archive.controller.ArchiveController;
import de.schlichtherle.truezip.io.archive.controller.ArchiveBusyException;
import de.schlichtherle.truezip.io.archive.controller.FalsePositiveEnclosedEntryException;
import de.schlichtherle.truezip.io.archive.filesystem.ArchiveFileSystemException;
import de.schlichtherle.truezip.io.InputException;
import de.schlichtherle.truezip.io.Streams;
import de.schlichtherle.truezip.io.socket.IOSocket;
import de.schlichtherle.truezip.io.socket.file.FileSocketFactory;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import static de.schlichtherle.truezip.io.archive.entry.ArchiveEntry.SEPARATOR_CHAR;
import static de.schlichtherle.truezip.io.Files.contains;

/**
 * Provides static utility methods for {@link File}s.
 * This class cannot get instantiated outside its package.
 * <p>
 * Note that in contrast to the {@link File} class, the methods in this
 * class accept and return plain {@code java.io.File} instances.
 * Full advantage is taken if a parameter is actually an instance of the
 * {@code File} class in this package, however.
 *
 * @author Christian Schlichtherle
 * @version $Id$
 */
class Files {

    Files() {
    }

    /**
     * Moves the source to the destination by recursively copying and deleting
     * its files and directories.
     * Hence, this file system operation works even with archive files or
     * entries within archive files, but is <em>not</em> atomic.
     *
     * @param src The source file or directory.
     *            This must exist.
     * @param dst The destination file or directory.
     *            This may or may not exist.
     *            If it does, its contents are overwritten.
     * @param detector The object used to detect any archive
     *        files in the path and configure their parameters.
     * @return Whether the operation succeeded or not.
     *         If it fails, the source and destination may contain only a
     *         subset of the source before this operation.
     *         However, each file has either been completely moved or not.
     * @see File#renameTo(java.io.File, ArchiveDetector)
     * @see <a href="package-summary.html#third_parties">Third Party
     *      Access using different Archive Detectors</a>
     */
    static boolean move(
            final java.io.File src,
            final java.io.File dst,
            final ArchiveDetector detector) {
        return !contains(src, dst) && move0(src, dst, detector);
    }

    private static boolean move0(
            final java.io.File src,
            final java.io.File dst,
            final ArchiveDetector detector) {
        boolean ok = true;
        if (src.isDirectory()) {
            final long srcLastModified = src.lastModified();
            final boolean srcIsArchived = src instanceof File
                    && ((File) src).getInnerArchive() != null;
            final boolean dstIsArchived = dst instanceof File
                    && ((File) dst).getInnerArchive() != null;
            final boolean srcIsGhost = srcIsArchived
                    && srcLastModified <= 0;
            if (!srcIsGhost || !dstIsArchived || !File.isLenient())
                dst.mkdir();
            final String[] members = src.list();
            if (!srcIsArchived && dstIsArchived) {
                // Create sorted entries if writing a new archive file.
                // This is courtesy only, so natural order is sufficient.
                Arrays.sort(members);
            }
            for (int i = 0, l = members.length; i < l; i++) {
                final String member = members[i];
                ok &= move0(  detector.createFile(src, member),
                            detector.createFile(dst,  member),
                            detector);
            }
            if (!srcIsGhost)
                ok &= dst.setLastModified(srcLastModified);
        } else if (src.isFile()) { // !isDirectory()
            try {
                copy(true, src, dst);
            } catch (IOException ex) {
                ok = false;
            }
        } else {
            ok = false; // don't move special files!
        }
        return ok && src.delete(); // only unlink if ok!
    }

    /**
     * Performs a recursive copy operation.
     *
     * @see File#copyAllTo(java.io.File, ArchiveDetector, ArchiveDetector)
     * @see File#archiveCopyAllTo(java.io.File, ArchiveDetector, ArchiveDetector)
     * @see <a href="package-summary.html#third_parties">Third Party
     *      Access using different Archive Detectors</a>
     */
    static void copyAll(
            final boolean preserve,
            final java.io.File src,
            final java.io.File dst,
            final ArchiveDetector srcDetector,
            final ArchiveDetector dstDetector)
    throws IOException {
        if (contains(src, dst))
            throw new ContainsFileException(src, dst);
        copyAll0(preserve, src, dst, srcDetector, dstDetector);
    }

    /**
     * Unchecked parameters version.
     */
    private static void copyAll0(
            final boolean preserve,
            final java.io.File src,
            final java.io.File dst,
            final ArchiveDetector srcDetector,
            final ArchiveDetector dstDetector)
    throws IOException {
        if (src.isDirectory()) {
            final long srcLastModified = src.lastModified();
            final boolean srcIsArchived = src instanceof File
                    && ((File) src).getInnerArchive() != null;
            final boolean dstIsArchived = dst instanceof File
                    && ((File) dst).getInnerArchive() != null;
            final boolean srcIsGhost = srcIsArchived
                    && srcLastModified <= 0;
            if (!srcIsGhost || !dstIsArchived || !File.isLenient())
                if (!dst.mkdir() && !dst.isDirectory())
                    throw new IOException("destination is not a directory");
            final String[] members = src.list();
            if (!srcIsArchived && dstIsArchived) {
                // Create sorted entries if writing a new archive.
                // This is a courtesy only, so natural order is sufficient.
                Arrays.sort(members);
            }
            for (int i = 0, l = members.length; i < l; i++) {
                final String member = members[i];
                copyAll0(  preserve,
                        srcDetector.createFile(src, member),
                        dstDetector.createFile(dst, member),
                        srcDetector, dstDetector);
            }
            if (preserve && !srcIsGhost)
                if (!dst.setLastModified(srcLastModified))
                    throw new IOException("cannot set last modification time");
        } else if (src.isFile() && (!dst.exists() || dst.isFile())) {
            copy0(preserve, src, dst);
        } else {
            throw new IOException("cannot copy non-existent or special files");
        }
    }

    /**
     * The name of this method is inspired by the Unix command line utility
     * {@code copy}.
     *
     * @see File#cp(java.io.File, java.io.File)
     * @see File#cp_p(java.io.File, java.io.File)
     * @see <a href="package-summary.html#third_parties">Third Party
     *      Access using different Archive Detectors</a>
     */
    static void copy(
            final boolean preserve,
            final java.io.File src,
            final java.io.File dst)
    throws IOException {
        if (contains(src, dst))
            throw new ContainsFileException(src, dst);
        copy0(preserve, src, dst);
    }

    /**
     * Unchecked parameters version.
     */
    private static void copy0(
            final boolean preserve,
            final java.io.File src,
            final java.io.File dst)
    throws IOException {
        assert src != null;
        assert dst != null;

        try {
            try {
                if (src instanceof File) {
                    final File srcFile = (File) src;
                    final File srcArchive = srcFile.getInnerArchive();
                    if (srcArchive != null) {
                        final String srcPath = srcFile.getInnerEntryName();
                        assert srcPath != null;
                        copy0(  preserve,
                                srcArchive.getArchiveController(),
                                srcPath,
                                dst);
                        return;
                    }
                }
            } catch (FalsePositiveEntryException isNotArchive) {
                assert !(isNotArchive instanceof FalsePositiveEnclosedEntryException)
                        : "must be handled in try-block!";
                // Fall through!
            }

            // Treat the source like a regular file.
            final InputStream in = new java.io.FileInputStream(src);
            try {
                copy0(preserve, src, in, dst);
            } finally {
                try {
                    in.close();
                } catch (IOException ex) {
                    throw new InputException(ex);
                }
            }
        } catch (FileNotFoundException ex) {
            throw ex;
        } catch (ArchiveBusyException ex) {
            throw new FileBusyException(ex);
        } catch (ArchiveFileSystemException afse) {
            final FileNotFoundException fnfe = new FileNotFoundException(afse.toString());
            fnfe.initCause(afse);
            throw fnfe;
        } catch (IOException ex) {
            dst.delete();
            throw ex;
        }
    }

    /**
     * Copies a source file to a destination file, optionally preserving the
     * source's last modification time.
     * We already have an input stream to read the source file,
     * but we know nothing about the destination file yet.
     * Note that this method <em>never</em> closes the given input stream!
     *
     * @throws FileNotFoundException If either the source or the destination
     *         cannot get accessed.
     * @throws InputException If copying the data fails because of an
     *         IOException in the source.
     * @throws IOException If copying the data fails because of an
     *         IOException in the destination.
     */
    private static void copy0(
            final boolean preserve,
            final java.io.File src,
            final InputStream in,
            final java.io.File dst)
    throws IOException {
        try {
            if (dst instanceof File) {
                final File dstFile = (File) dst;
                final File dstArchive = dstFile.getInnerArchive();
                if (dstArchive != null) {
                    final String dstPath = dstFile.getInnerEntryName();
                    assert dstPath != null;
                    ArchiveControllers.copy(
                            preserve, File.isLenient(),
                            src,
                            in,
                            dstArchive.getArchiveController(),
                            dstPath);
                    return;
                }
            }
        } catch (FalsePositiveEntryException isNotArchive) {
            assert !(isNotArchive instanceof FalsePositiveEnclosedEntryException)
                    : "must be handled in try-block!";
            // Fall through!
        }

        // Treat the destination like a regular file.
        final OutputStream out = new java.io.FileOutputStream(dst);
        try {
            Streams.cat(in, out);
        } finally {
            out.close();
        }
        if (preserve && !dst.setLastModified(src.lastModified()))
            throw new IOException(dst.getPath()
                    + " (cannot preserve last modification time)");
    }

    /**
     * Copies a source file to a destination file, optionally preserving the
     * source's last modification time.
     * We know that the source file appears to be an entry in an archive
     * file, but we know nothing about the destination file yet.
     * <p>
     * Note that this method synchronizes on the class object in order
     * to prevent dead locks by two threads copying archive entries to the
     * other's source archive concurrently!
     *
     * @throws FalsePositiveEntryException If the source or the destination is a
     *         false positive and the exception
     *         cannot get resolved within this method.
     * @throws InputException If copying the data fails because of an
     *         IOException in the source.
     * @throws IOException If copying the data fails because of an
     *         IOException in the destination.
     */
    private static void copy0(
            final boolean preserve,
            final ArchiveController srcController,
            final String srcPath,
            final java.io.File dst)
    throws FalsePositiveEntryException, IOException {
        // Do not assume anything about the lock status of the controller:
        // This method may be called from a subclass while a lock is acquired!
        //assert !srcController.readLock().isLocked();
        //assert !srcController.writeLock().isLocked();

        try {
            try {
                if (dst instanceof File) {
                    final File dstFile = (File) dst;
                    final File dstArchive = dstFile.getInnerArchive();
                    if (dstArchive != null) {
                        final String dstPath = dstFile.getInnerEntryName();
                        assert dstPath != null;
                        ArchiveControllers.copy(
                                preserve, File.isLenient(),
                                srcController,
                                srcPath,
                                dstArchive.getArchiveController(),
                                dstPath);
                        return;
                    }
                }
            } catch (FalsePositiveEnclosedEntryException ex) {
                throw ex;
            } catch (FalsePositiveEntryException ex) {
                // Both the source and/or the destination may be false positives,
                // so we need to use the exception's additional information to
                // find out which controller actually detected the false positive.
                if (srcController.getMountPoint().equals(ex.getMountPoint()))
                    throw ex; // not my job - pass on!
            }

            final CommonInputSocket<? extends CommonEntry> input
                    = srcController.newInputSocket(srcPath);
            final CommonOutputSocket<? extends CommonEntry> output
                    = FileSocketFactory.get().newOutputSocket(new FileEntry(dst));
            IOSocket.copy(input, output);
            if (preserve && !dst.setLastModified(input.getTarget().getTime(Access.WRITE)))
                throw new IOException(dst.getPath()
                        + " (cannot preserve last modification time)");
        } catch (FalsePositiveEnclosedEntryException ex) {
            final URI enclMountPoint = ex.getMountPoint();
            final ArchiveController enclController
                    = ArchiveControllers.getController(enclMountPoint); // FIXME: Redesign delegation strategy!
            final String enclPath = enclMountPoint.relativize(
                    enclMountPoint
                    .resolve(ex.getPath() + SEPARATOR_CHAR)
                    .resolve(srcPath)).toString();
            // Reroute call to the source's enclosing archive controller.
            copy0(preserve, enclController, enclPath, dst);
        }
    }

    /**
     * Deletes the entire directory tree represented by the parameter,
     * regardless whether it's a file or directory, whether the directory
     * is empty.
     * <p>
     * This file system operation is <em>not</em> atomic.
     *
     * @return Whether or not the entire directory tree was successfully
     *         removed.
     */
    static boolean deleteAll(final java.io.File file) {
        boolean ok = true;
        if (file.isDirectory()) {
            // If the directory is an archive file, one may be tempted to delete it
            // directly (using e.g. java.io.File.delete()).
            // However, this would bypass the ArchiveController's state and cause
            // subsequent mayhem.
            // So we play it safe despite the fact that this procedure is comparably
            // much slower.
            java.io.File[] members = file.listFiles();
            for (int i = members.length; --i >= 0;)
                ok &= deleteAll(members[i]);
        }
        return ok && file.delete();
    }
}
