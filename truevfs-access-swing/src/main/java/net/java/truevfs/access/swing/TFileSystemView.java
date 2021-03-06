/*
 * Copyright (C) 2005-2015 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.java.truevfs.access.swing;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.text.MessageFormat;
import javax.annotation.CheckForNull;
import javax.swing.Icon;
import javax.swing.JFileChooser;
import javax.swing.UIManager;
import javax.swing.filechooser.FileSystemView;
import net.java.truevfs.access.TArchiveDetector;
import net.java.truevfs.access.TFile;

/**
 * A custom file system fsv required to browse archive files like (virtual)
 * directories with a {@link JFileChooser}.
 * This class is used by {@link TFileTreeCellRenderer}
 * to render files and directories in a {@link TFileTree}.
 *
 * @author Christian Schlichtherle
 */
//
// Unfortunately this is a pretty ugly piece of code.
// The reason for this is the completely broken design of the genuine
// JFileChooser, FileSystemView, FileView, ShellFolder and BasicFileChooserUI
// classes.
// The FileSystemView uses a lot of "instanceof" run time type detections
// in conjunction with Sun's proprietory (and platform dependent) ShellFolder
// class, which subclasses File.
// Other classes like BasicFileChooserUI also rely on the use of the
// ShellFolder class, which they really shouldn't.
// The use of the ShellFolder class is also the sole reason for the existence
// of the method {@link net.truevfs.file.TFile#getFile()}.
// For many methods in this class, we need to pass in the delegate to the
// superclass implementation in order for the JFileChooser to work as expected.
//
public class TFileSystemView extends TDecoratingFileSystemView {

    /** Maybe null - uses default then. **/
    private @CheckForNull TArchiveDetector detector;

    public TFileSystemView() {
        this(FileSystemView.getFileSystemView(), null);
    }

    public TFileSystemView(FileSystemView fileSystemView) {
        this(fileSystemView, null);
    }

    public TFileSystemView( final FileSystemView fileSystemView,
                            final @CheckForNull TArchiveDetector detector) {
        super(fileSystemView);
        this.detector = detector;
    }

    /**
     * Returns the nullable archive detector to use.
     * 
     * @return The nullable archive detector to use.
     */
    public @CheckForNull TArchiveDetector getDetector() { return detector; }

    /**
     * Sets the archive detector to use.
     *
     * @param detector the archive detector to use.
     */
    public void setDetector(@CheckForNull TArchiveDetector detector) {
        this.detector = detector;
    }

    /**
     * Ensures that the returned file object is an instance of {@link TFile}.
     * 
     * @param  file the file to wrap.
     * @return {@code file} if it's already a {@link TFile} or a new
     *         {@code TFile} which wraps it.
     */
    protected TFile wrap(final File file) {
        return file instanceof TFile
                ? (TFile) file
                : new TFile(file, getDetector());
    }

    /**
     * Ensures that the returned file object is an instance of {@link File},
     * not {@link TFile}.
     * 
     * @param  file the file to unwrap.
     * @return the originally wrapped file if {@code file} is an instance of
     *         {@code TFile} or {@code file} otherwise.
     */
    @SuppressWarnings("deprecation")
    protected File unwrap(File file) {
        return file instanceof TFile ? ((TFile) file).getFile() : file;
    }

    //
    // Overridden methods:
    //

    @Override
    public boolean isRoot(File file) { return fsv.isRoot(unwrap(file)); }

    @Override
    public Boolean isTraversable(File file) { return wrap(file).isDirectory(); }

    @Override
    public String getSystemDisplayName(File file) {
        final TFile tfile = wrap(file);
        if (tfile.isArchive() || tfile.isEntry())
            return tfile.getName();
        return fsv.getSystemDisplayName(unwrap(file));
    }

    @Override
    public String getSystemTypeDescription(File file) {
        final TFile tfile = wrap(file);
        final String typeDescription = TFileView.typeDescription(tfile);
        if (typeDescription != null)
            return typeDescription;
        return fsv.getSystemTypeDescription(unwrap(file));
    }

    @Override
    public Icon getSystemIcon(File file) {
        final TFile tfile = wrap(file);
        final Icon icon = TFileView.icon(tfile);
        if (null != icon)
            return icon;
        final File uFile = unwrap(file);
        return uFile.exists()
            ? fsv.getSystemIcon(uFile)
            : null;
    }

    @Override
    public boolean isParent(File folder, File file) {
        return fsv.isParent(wrap(folder), wrap(file))
            || fsv.isParent(unwrap(folder), unwrap(file));
    }

    @Override
    public File getChild(File parent, String child) {
        final TFile wParent = wrap(parent);
        if (wParent.isArchive() || wParent.isEntry())
            return createFileObject(fsv.getChild(wParent, child));
        return createFileObject(fsv.getChild(unwrap(parent), child));
    }

    @Override
    public boolean isFileSystem(File file) {
        return fsv.isFileSystem(unwrap(file));
    }

    @Override
    public File createNewFolder(final File parent)
    throws IOException {
        final TFile wParent = wrap(parent);
        if (wParent.isArchive() || wParent.isEntry()) {
            TFile folder = new TFile(wParent,
                    UIManager.getString(TFile.separatorChar == '\\'
                            ? "FileChooser.win32.newFolder"
                            : "FileChooser.other.newFolder"),
                    getDetector());

            for (int i = 2; !folder.mkdirs(); i++) {
                if (i > 100)
                    throw new IOException(wParent + ": Could not create new directory entry!");
                folder = new TFile(wParent,
                        MessageFormat.format(
                            UIManager.getString(TFile.separatorChar == '\\'
                                ? "FileChooser.win32.newFolder.subsequent"
                                : "FileChooser.other.newFolder.subsequent"),
                            new Object[] { Integer.valueOf(i) }),
                        getDetector());
            }

            return folder;
        }
        return createFileObject(fsv.createNewFolder(unwrap(parent)));
    }

    @Override
    public boolean isHiddenFile(File file) {
        return fsv.isHiddenFile(unwrap(file));
    }

    @Override
    public boolean isFileSystemRoot(File file) {
        return fsv.isFileSystemRoot(unwrap(file));
    }

    @Override
    public boolean isDrive(File file) {
        return fsv.isDrive(unwrap(file));
    }

    @Override
    public boolean isFloppyDrive(File file) {
        return fsv.isFloppyDrive(unwrap(file));
    }

    @Override
    public boolean isComputerNode(File file) {
        return fsv.isComputerNode(unwrap(file));
    }

    @Override
    public File createFileObject(File dir, String filename) {
        return createFileObject(fsv.createFileObject(dir, filename));
    }

    @Override
    public File createFileObject(String path) {
        return createFileObject(fsv.createFileObject(path));
    }

    /**
     * Wraps the given {@code file} in a {@link TFile} if its an archive file
     * or an archive entry.
     * Otherwise returns the given {@code file}.
     * 
     * @param  file the file to wrap.
     * @return {@code file} if it's not an archive file and not an archive
     *         entry, otherwise a {@link TFile} which wraps the given
     *         {@code file}.
     */
    public File createFileObject(final File file) {
        final TFile tfile = wrap(file);
        return tfile.isArchive() || tfile.isEntry() ? tfile : unwrap(file);
    }

    @Override
    public File[] getFiles(final File dir, final boolean useFileHiding) {
        final TFile smartDir = wrap(dir);
        if (smartDir.isArchive() || smartDir.isEntry()) {
            // dir is a ZIP file or an entry in a ZIP file.
            class Filter implements FileFilter {
                @Override
                public boolean accept(File file) {
                    return !useFileHiding || !isHiddenFile(file);
                }
            } // class Filter
            final File files[] = smartDir.listFiles(new Filter());
            return null == files ? new TFile[0] : files;
        } else {
            final File files[] = fsv.getFiles(unwrap(dir), useFileHiding);
            for (int i = files.length; --i >= 0; )
                files[i] = createFileObject(files[i]);
            return files;
        }
    }

    @Override
    public File getParentDirectory(File file) {
        final TFile tfile = wrap(file);
        if (tfile.isEntry()) return createFileObject(tfile.getParentFile());
        final @CheckForNull File dir = fsv.getParentDirectory(unwrap(file));
        return null == dir ? dir : createFileObject(dir);
    }
}
