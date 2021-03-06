/*
 * Copyright (C) 2005-2015 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.java.truevfs.access;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import net.java.truecommons.cio.Entry.Access;
import net.java.truecommons.cio.Entry.Size;
import net.java.truecommons.io.Streams;
import net.java.truecommons.shed.BitField;
import net.java.truecommons.shed.PathSplitter;
import net.java.truecommons.shed.Paths;
import net.java.truecommons.shed.UriBuilder;
import net.java.truevfs.kernel.spec.*;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.WillClose;
import javax.annotation.WillNotClose;
import javax.annotation.concurrent.Immutable;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.util.*;

import static net.java.truecommons.cio.Entry.Access.*;
import static net.java.truecommons.cio.Entry.Type.DIRECTORY;
import static net.java.truecommons.cio.Entry.Type.FILE;
import static net.java.truecommons.cio.Entry.UNKNOWN;
import static net.java.truevfs.access.ExpertFeature.Level.INTERMEDIATE;
import static net.java.truevfs.access.ExpertFeature.Reason.INJECTING_A_DIFFERENT_DETECTOR_FOR_THE_SAME_PATH_MAY_CORRUPT_DATA;
import static net.java.truevfs.kernel.spec.FsAccessOption.EXCLUSIVE;
import static net.java.truevfs.kernel.spec.FsAccessOption.GROW;
import static net.java.truevfs.kernel.spec.FsAssertion.Level.*;
import static net.java.truevfs.kernel.spec.FsNodeName.ROOT;
import static net.java.truevfs.kernel.spec.FsNodeName.SEPARATOR_CHAR;
import static net.java.truevfs.kernel.spec.FsUriModifier.CANONICALIZE;

/**
 * A replacement for the class {@link File} which provides transparent
 * read/write access to archive files and their entries as if they were
 * (virtual) directories and files.
 * Because this class actually extends the class {@link File} it can get used
 * polymorphic with the class {@link javax.swing.filechooser.FileSystemView}
 * or any other class which depends on the class {@link File}.
 *
 * <h3><a name="bulkIOMethods">Bulk I/O Methods</a></h3>
 * <p>
 * This class provides some convenient methods which use pooled buffers and
 * pooled threads in order to achieve superior performance as compared to the
 * naive read-then-write-in-a-loop approach.
 * These bulk I/O methods fall into the following categories:
 * <ol>
 * <li>The {@code cp(_p|_r|_rp)?} methods copy files or directory trees.
 *     The method names have been modeled after the Unix command line utility
 *     {@code cp} with its options.
 * <li>The {@code mv} methods move files or directory trees.
 *     The method names have been modeled after the Unix command line utility
 *     {@code mv}.
 * <li>The {@code rm(_r)?} methods remove files or directory trees.
 *     The method names have been modeled after the Unix command line utility
 *     {@code rm} with its options.
 * <li>The {@code input|output} methods copy the given streams to this file or
 *     vice versa.
 *     In contrast to the previous methods they never close their argument
 *     streams, so applications can call them multiple times on the same
 *     streams to con<em>cat</em>enate data.
 * <li>Finally, the {@link #cat(InputStream, OutputStream)}
 *     method is the core copy engine for all these methods.
 *     It performs the data transfer from an input stream to an output stream.
 *     When used with <em>unbuffered</em> input and output stream
 *     implementations, it delivers the same performance as the transfer
 *     method in the package {@code java.nio}.
 *     Its name is modeled after the Unix command line utility {@code cat}.
 * </ol>
 * <b>Important:</b> You must provide the <em>full path name</em> for both
 * source and destination parameters to any of these methods!
 * In particular, both the source and destination parameters must either
 * represent a file or a directory - mixing file and directory parameters will
 * not work.
 * This constraint is meant to prevent ambiguous method semantics.
 *
 * <h4><a name="directDataCopying"><a name="RDC">Raw Data Copying (RDC)</a></a></h4>
 * <p>
 * If data is copied from an archive file to another archive file of the
 * same type, some of the copy methods support a feature called <i>Raw Data
 * Copying</i> (RDC) to achieve best performance:
 * With RDC, the raw entry data is copied from the input entry to the
 * output entry without the need to temporarily reproduce, copy and
 * process the file entry data again.
 * <p>
 * The benefits of this feature are archive driver specific:
 * In case of ZIP files with compressed entries, RDC avoids inflating the
 * entry data when reading the input entry and immediately deflating it
 * again when writing the output entry.
 * In case of TAR files, RDC avoids the need to create an additional temporary
 * file, but shows no impact otherwise because the TAR file format doesn't
 * support compression.
 *
 * <h3><a name="traversal">Traversing Directory Trees</a></h3>
 * <p>
 * When traversing directory trees, e.g. when searching, copying or moving
 * them, it's important that all file objects use
 * {@linkplain TArchiveDetector#equals consistent} {@link TArchiveDetector}
 * objects for archive file detection in these directory trees.
 * This is required in order to make sure that the virtual file system state
 * which is managed by the TrueVFS Kernel does not get bypassed.
 * Otherwise, file system operations on archive files would yield inconsistent
 * results and may even cause <strong>loss of data</strong>!
 * <p>
 * By default, all file objects use {@link TArchiveDetector#ALL} in order to
 * detect all supported archive types (see {@link TConfig} for other options).
 * This is fine because it's fail-safe and performs reasonably well when
 * copying archive files (e.g. ZIP entries won't get recompressed thanks to
 * <a href="#RDC">RDC</a>).
 *
 * <h4><a name="verbatimCopy">Making Verbatim Copies of Directory Trees</a></h4>
 * <p>
 * Using the default {@code TArchiveDetector.ALL} results in <i>structural
 * copies</i> rather than <i>verbatim copies</i> (byte-by-byte copies) of any
 * archive files within the source directory tree.
 * This saves you from accidentally bypassing the virtual file system state
 * which is managed by the TrueVFS Kernel.
 * <p>
 * However, sometimes you may need verbatim copies, e.g. when comparing hash
 * sums for archive files.
 * In this case, you need to unmount any archive file in the source and
 * destination directory trees first, e.g. by calling {@link TVFS#umount()} or
 * {@link TVFS#umount(TFile)} before you can copy the directory trees.
 * For the subsequent traversal of the directory trees, you need use
 * {@code TFile} objects which do <em>not</em> recognize prospective archive
 * files.
 * For example, to make a recursive <em>verbatim</em> copy, you could use this:
 * <pre><code>
 * TFile src = ...; // any
 * TFile dst = ...; // dito
 * ... // any I/O operations
 * TVFS.umount(src); // unmount selectively
 * TVFS.umount(dst); // dito
 * src.toNonArchiveFile().cp_rp(dst.toNonArchiveFile());
 * </code></pre>
 * <p>
 * The calls to {@link TVFS#umount(TFile)} selectively unmount any archive
 * files within the source and destination directory trees and the calls to
 * {@link #toNonArchiveFile()} ensure that prospective archive file detection
 * is inhibited when recursively copying the source and destination directory
 * trees.
 *
 * <h3><a name="falsePositives">Detecting Archive Files and False Positives</a></h3>
 * <p>
 * Whenever an archive file extension is detected in a path, this class treats
 * the corresponding file or directory as a <i>prospective archive file</i>.
 * The word &quot;prospective&quot; suggests that just because a file is named
 * <i>archive.zip</i> it isn't necessarily a valid ZIP file.
 * In fact, it could be anything, even a plain directory in the platform file system!
 * <p>
 * Such an invalid archive file is called a <i>false positive</i> archive file.
 * TrueVFS correctly identifies all types of false positive archive files by
 * performing a recursive look up operation for the first parent file system
 * where the respective prospective archive file actually exists and treats it
 * according to its <i>true state</i>.
 * <p>
 * The following table shows how certain methods in this class behave,
 * depending upon an archive file's path and its <i>true state</i> in the
 * first parent file system where it actually exists.
 *
 * <table>
 * <caption>Test Methods</caption>
 * <thead>
 * <tr>
 *   <th>Path</th>
 *   <th>True State</th>
 *   <th>{@code isArchive()}<sup>1</sup></th>
 *   <th>{@code isDirectory()}</th>
 *   <th>{@code isFile()}</th>
 *   <th>{@code exists()}</th>
 *   <th>{@code length()}<sup>2</sup></th>
 * </tr>
 * </thead>
 * <tbody>
 * <tr>
 *   <td><i>archive.zip</i><sup>3</sup></td>
 *   <td>Valid ZIP file</td>
 *   <td>{@code true}</td>
 *   <td>{@code true}</td>
 *   <td>{@code false}</td>
 *   <td>{@code true}</td>
 *   <td>{@code 0}</td>
 * </tr>
 * <tr>
 *   <td><i>archive.zip</i></td>
 *   <td>False positive: Plain directory</td>
 *   <td>{@code true}</td>
 *   <td>{@code true}</td>
 *   <td>{@code false}</td>
 *   <td>{@code true}</td>
 *   <td><i>{@code ?}</i></td>
 * </tr>
 * <tr>
 *   <td><i>archive.zip</i></td>
 *   <td>False positive: Plain file</td>
 *   <td>{@code true}</td>
 *   <td>{@code false}</td>
 *   <td>{@code true}</td>
 *   <td>{@code true}</td>
 *   <td><i>{@code ?}</i></td>
 * </tr>
 * <tr>
 *   <td><i>archive.zip</i></td>
 *   <td>False positive: Regular special file</td>
 *   <td>{@code true}</td>
 *   <td>{@code false}</td>
 *   <td>{@code false}</td>
 *   <td>{@code true}</td>
 *   <td><i>{@code ?}</i></td>
 * </tr>
 * <tr>
 *   <td><i>archive.zip</i></td>
 *   <td>File or directory does not exist</td>
 *   <td>{@code true}</td>
 *   <td>{@code false}</td>
 *   <td>{@code false}</td>
 *   <td>{@code false}</td>
 *   <td>{@code 0}</td>
 * </tr>
 * <tr>
 *   <td colspan="7">&nbsp;</td>
 * </tr>
 * <tr>
 *   <td><i>archive.tzp</i><sup>4</sup></td>
 *   <td>Valid RAES encrypted ZIP file with valid key (e.g. password)</td>
 *   <td>{@code true}</td>
 *   <td>{@code true}</td>
 *   <td>{@code false}</td>
 *   <td>{@code true}</td>
 *   <td>{@code 0}</td>
 * </tr>
 * <tr>
 *   <td><i>archive.tzp</i></td>
 *   <td>Valid RAES encrypted ZIP file with unknown key<sup>5</sup></td>
 *   <td>{@code true}</td>
 *   <td>{@code false}</td>
 *   <td>{@code false}</td>
 *   <td>{@code true}</td>
 *   <td><i>{@code ?}</i></td>
 * </tr>
 * <tr>
 *   <td><i>archive.tzp</i></td>
 *   <td>False positive: Plain directory</td>
 *   <td>{@code true}</td>
 *   <td>{@code true}</td>
 *   <td>{@code false}</td>
 *   <td>{@code true}</td>
 *   <td><i>{@code ?}</i></td>
 * </tr>
 * <tr>
 *   <td><i>archive.tzp</i></td>
 *   <td>False positive: Plain file</td>
 *   <td>{@code true}</td>
 *   <td>{@code false}</td>
 *   <td>{@code true}</td>
 *   <td>{@code true}</td>
 *   <td><i>{@code ?}</i></td>
 * </tr>
 * <tr>
 *   <td><i>archive.tzp</i></td>
 *   <td>False positive: Regular special file</td>
 *   <td>{@code true}</td>
 *   <td>{@code false}</td>
 *   <td>{@code false}</td>
 *   <td>{@code true}</td>
 *   <td><i>{@code ?}</i></td>
 * </tr>
 * <tr>
 *   <td><i>archive.tzp</i></td>
 *   <td>File or directory does not exist</td>
 *   <td>{@code true}</td>
 *   <td>{@code false}</td>
 *   <td>{@code false}</td>
 *   <td>{@code false}</td>
 *   <td>{@code 0}</td>
 * </tr>
 * <tr>
 *   <td colspan="7">&nbsp;</td>
 * </tr>
 * <tr>
 *   <td><i>other</i></td>
 *   <td>Plain directory</td>
 *   <td>{@code false}</td>
 *   <td>{@code true}</td>
 *   <td>{@code false}</td>
 *   <td>{@code true}</td>
 *   <td><i>{@code ?}</i></td>
 * </tr>
 * <tr>
 *   <td><i>other</i></td>
 *   <td>Plain file</td>
 *   <td>{@code false}</td>
 *   <td>{@code false}</td>
 *   <td>{@code true}</td>
 *   <td>{@code true}</td>
 *   <td><i>{@code ?}</i></td>
 * </tr>
 * <tr>
 *   <td><i>other</i></td>
 *   <td>Regular special file</td>
 *   <td>{@code false}</td>
 *   <td>{@code false}</td>
 *   <td>{@code false}</td>
 *   <td>{@code true}</td>
 *   <td><i>{@code ?}</i></td>
 * </tr>
 * <tr>
 *   <td><i>other</i></td>
 *   <td>File or directory does not exist</td>
 *   <td>{@code false}</td>
 *   <td>{@code false}</td>
 *   <td>{@code false}</td>
 *   <td>{@code false}</td>
 *   <td>{@code 0}</td>
 * </tr>
 * </tbody>
 * </table>
 * <ol>
 * <li>{@link #isArchive} doesn't check the true state of the file - it just
 *     looks at its path: If the path ends with a configured archive file
 *     extension, {@code isArchive()} always returns {@code true}.
 * <li>{@link #length} always returns {@code 0} if the path denotes a
 *     valid archive file.
 *     Otherwise, the return value of {@code length()} depends on the
 *     platform file system, which is indicated by <i>{@code ?}</i>.
 *     For regular directories on Windows/NTFS for example, the return value
 *     would be {@code 0}.
 * <li>This example presumes that the JAR of the module
 *     TrueVFS&nbsp;Driver&nbsp;ZIP is present on the run time class path.</li>
 * <li>This example presumes that the JAR of the module
 *     TrueVFS&nbsp;Driver&nbsp;ZIP.RAES&nbsp;(TZP) is present on the run time
 *     class path.</li>
 * <li>The methods behave exactly the same for both <i>archive.zip</i> and
 *    <i>archive.tzp</i> with one exception: If the key for a RAES encrypted
 *    ZIP file remains unknown (e.g. because the user cancelled password
 *    prompting), then these methods behave as if the true state of the path
 *    were a special file, i.e. both {@link #isDirectory} and {@link #isFile}
 *    return {@code false}, while {@link #exists} returns {@code true}.</li>
 * </ol>
 *
 * @author Christian Schlichtherle
 */
@Immutable
public final class TFile extends File implements TRex {

    private static final long serialVersionUID = 3617072259051821745L;

    /** The prefix of a UNC (a Windows concept). */
    private static final String UNC_PREFIX = separator + separator;

    /** The file system roots. */
    private static final Set<File>
            ROOTS = Collections.unmodifiableSet(
                new TreeSet<>(Arrays.asList(listRoots())));

    private static final File CURRENT_DIRECTORY = new File(".");

    private static final BitField<Access> NO_ACCESS = BitField.noneOf(Access.class);
    private static final BitField<Access> READ_ACCESS = BitField.of(READ);
    private static final BitField<Access> WRITE_ACCESS = BitField.of(WRITE);
    private static final BitField<Access> EXECUTE_ACCESS = BitField.of(EXECUTE);

    /**
     * The decorated file is used to implement the behaviour of the file system
     * operations in case this instance represents neither an archive file
     * nor an entry in an archive file.
     * If this instance is constructed from another {@code File}
     * instance, then this field is initialized with that instance.
     * <p>
     * This enables federation of file system implementations and is essential
     * to enable the broken implementation in
     * {@link javax.swing.JFileChooser} to browse archive files.
     */
    private transient File file;

    /** The configuration to use. */
    private transient TArchiveDetector detector;

    private transient @CheckForNull TFile innerArchive;
    private transient @CheckForNull TFile enclArchive;
    private transient @CheckForNull FsNodeName nodeName;

    /**
     * This refers to the file system controller if and only if this file
     * refers to a prospective archive file, otherwise it's {@code null}.
     * This field should be considered to be {@code final}!
     *
     * @see #readObject
     */
    @SuppressFBWarnings("JCIP_FIELD_ISNT_FINAL_IN_IMMUTABLE_CLASS")
    private transient volatile @CheckForNull FsController controller;

    /**
     * Constructs a new {@code TFile} instance which wraps the given
     * {@code file}.
     * If {@code file} is an instance of this class, then this method behaves
     * like a copy constructor, otherwise the current archive detector gets
     * resolved by calling {@code TConfig.current().getArchiveDetector()} in order
     * to scan the entire path name for prospective archive files.
     *
     * @param file the file object to decorate.
     *        If this is an instance of this class, most of its fields current
     *        copied.
     */
    public TFile(File file) { this(file, (TArchiveDetector) null); }

    /**
     * Constructs a new {@code TFile} instance which uses the given archive
     * detector to scan its path name for prospective archive files.
     *
     * @param file the file object to decorate.
     *        If this is an instance of this class, most of its fields get
     *        copied.
     * @param detector the archive detector to use for scanning the path name
     *        for prospective archive files.
     *        If this parameter is {@code null} and {@code file} is
     *        an instance of this class, then its archive detector gets used.
     *        If this parameter is {@code null} and {@code file} is <em>not</em>
     *        an instance of this class, then the current archive detector gets
     *        resolved by calling {@code TConfig.current().getArchiveDetector()}.
     */
    @ExpertFeature(INJECTING_A_DIFFERENT_DETECTOR_FOR_THE_SAME_PATH_MAY_CORRUPT_DATA)
    @SuppressWarnings("AccessingNonPublicFieldOfAnotherObject")
    public TFile(final File file, final @CheckForNull TArchiveDetector detector) {
        super(file.getPath());
        if (file instanceof TFile) {
            final TFile that = (TFile) file;
            this.file = that.file;
            this.detector = that.detector;
            if (null == detector || this.detector.equals(detector)) {
                this.enclArchive = that.getEnclArchive();
                this.nodeName = that.nodeName;
                this.innerArchive = that.isArchive() ? this : that.innerArchive;
                this.controller = that.controller;
            } else {
                this.detector = detector;
                scan(null);
            }
        } else {
            this.file = file;
            this.detector = null != detector ? detector : TConfig.current().getArchiveDetector();
            scan(null);
        }
        assert invariants();
    }

    /**
     * Constructs a new {@code TFile} instance which scans its path name for
     * prospective archive files using the current archive detector by calling
     * {@code TConfig.current().getArchiveDetector()}.
     *
     * @param path the path name.
     */
    public TFile(String path) { this(path, (TArchiveDetector) null); }

    /**
     * Constructs a new {@code TFile} instance which uses the given archive
     * detector to scan its path name for prospective archive files.
     *
     * @param path the path name.
     * @param detector the archive detector to use for scanning the path name
     *        for prospective archive files.
     *        If this parameter is {@code null}, then the default archive
     *        detector gets resolved by calling
     *        {@code TConfig.current().getArchiveDetector()}.
     */
    @ExpertFeature(INJECTING_A_DIFFERENT_DETECTOR_FOR_THE_SAME_PATH_MAY_CORRUPT_DATA)
    public TFile(final String path, final @CheckForNull TArchiveDetector detector) {
        super(path);
        this.file = new File(path);
        this.detector = null != detector ? detector : TConfig.current().getArchiveDetector();
        scan(null);
        assert invariants();
    }

    /**
     * Constructs a new {@code TFile} instance which scans its path name for
     * prospective archive files using the current archive detector by calling
     * {@code TConfig.current().getArchiveDetector()}.
     *
     * @param parent the parent directory.
     *        If this is an instance of this class, then only the child path
     *        name gets scanned for prospective archive files, otherwise the
     *        entire path name.
     * @param child the child path name.
     */
    public TFile(@CheckForNull File parent, String child) {
        this(parent, child, null);
    }

    /**
     * Constructs a new {@code TFile} instance which uses the given archive
     * detector to scan its path name for prospective archive files.
     *
     * @param parent the parent directory.
     *        If this is an instance of this class, then only the child path
     *        name gets scanned for prospective archive files, otherwise the
     *        entire path name.
     * @param child the child path name.
     * @param detector the archive detector to use for scanning the path name
     *        for prospective archive files.
     *        If this parameter is {@code null}, then the default archive
     *        detector gets resolved by calling
     *        {@code TConfig.current().getArchiveDetector()}.
     */
    @ExpertFeature(INJECTING_A_DIFFERENT_DETECTOR_FOR_THE_SAME_PATH_MAY_CORRUPT_DATA)
    public TFile(
            final @CheckForNull File parent,
            final String child,
            final @CheckForNull TArchiveDetector detector) {
        super(parent, child);
        this.file = new File(parent, child);
        this.detector = null != detector ? detector : TConfig.current().getArchiveDetector();
        if (parent instanceof TFile) scan((TFile) parent);
        else scan(null);
        assert invariants();
    }

    /**
     * Constructs a new {@code TFile} instance which scans its path name for
     * prospective archive files using the current archive detector by calling
     * {@code TConfig.current().getArchiveDetector()}.
     *
     * @param parent the parent directory.
     *        If this is an instance of this class, then only the child path
     *        name gets scanned for prospective archive files, otherwise the
     *        entire path name.
     * @param child the child path name.
     */
    public TFile(@CheckForNull String parent, String child) {
        this(parent, child, null);
    }

    /**
     * Constructs a new {@code TFile} instance which uses the given archive
     * detector to scan its path name for prospective archive files.
     *
     * @param parent the parent directory.
     * @param child the child path name.
     * @param detector the archive detector to use for scanning the path name
     *        for prospective archive files.
     *        If this parameter is {@code null}, then the default archive
     *        detector gets resolved by calling
     *        {@code TConfig.current().getArchiveDetector()}.
     */
    @ExpertFeature(INJECTING_A_DIFFERENT_DETECTOR_FOR_THE_SAME_PATH_MAY_CORRUPT_DATA)
    public TFile(
            final @CheckForNull String parent,
            final String child,
            final @CheckForNull TArchiveDetector detector) {
        super(parent, child);
        this.file = new File(parent, child);
        this.detector = null != detector ? detector : TConfig.current().getArchiveDetector();
        scan(null);
        assert invariants();
    }

    /**
     * Constructs a new {@code TFile} instance for the given {@code uri}.
     * This constructor is equivalent to
     * <code>new {@link #TFile(FsNodePath, TArchiveDetector) TFile(FsNodePath.create(uri, CANONICALIZE), null))}</code>,
     *
     * @param  uri an absolute URI which has a scheme component which is
     *         known by the current archive detector
     *         {@code TConfig.current().getArchiveDetector()}.
     * @throws IllegalArgumentException if the given URI does not conform to
     *         the syntax constraints for {@link FsNodePath}s or
     *         {@link File#File(URI)}.
     * @see    #getUri()
     */
    public TFile(URI uri) {
        this(uri, null);
    }

    /**
     * Constructs a new {@code TFile} instance for the given {@code uri} and
     * {@code detector}.
     * This constructor is a super set of the super class constructor
     * {@link File#File(URI)} with the following additional features:
     * If the given URI is opaque, it must match the pattern
     * {@code <scheme>:<uri>!/<entry>}.
     * The constructed file object then parses the URI to address an entry in
     * a federated file system (i.e. prospective archive file) with the name
     * {@code <entry>} in the prospective archive file addressed by
     * {@code <uri>} which is of the type identified by {@code <scheme>}  .
     * This is recursively applied to access entries within other prospective
     * archive files until {@code <uri>} is a hierarchical URI.
     * Note that the scheme component of this hierarchical URI must be
     * {@code file} in order to maintain interoperability with the super class!
     *
     * @param  uri an absolute URI which has a scheme component which is
     *         known by the given {@code detector}.
     * @param  detector the archive detector to look up archive file system
     *         drivers for the named URI scheme components.
     *         If this parameter is {@code null}, then the default archive
     *         detector gets resolved by calling
     *         {@code TConfig.current().getArchiveDetector()}.
     * @throws IllegalArgumentException if the given URI does not conform to
     *         the syntax constraints for {@link File#File(URI)}.
     * @see    #getNodePath()
     */
    @ExpertFeature(INJECTING_A_DIFFERENT_DETECTOR_FOR_THE_SAME_PATH_MAY_CORRUPT_DATA)
    public TFile(URI uri, @CheckForNull TArchiveDetector detector) {
        this(FsNodePath.create(uri, CANONICALIZE), detector);
    }

    /**
     * Constructs a new {@code TFile} instance for the given {@code path}.
     * This constructor is equivalent to
     * <code>new {@link #TFile(FsNodePath, TArchiveDetector) TFile(path, null)}</code>
     *
     * @param  path a node path with an absolute
     *         {@link FsNodePath#toHierarchicalUri() hierarchical URI} which
     *         has a scheme component which is known by the current archive
     *         detector {@code TConfig.current().getArchiveDetector()}.
     * @throws IllegalArgumentException if the
     *         {@link FsNodePath#toHierarchicalUri() hierarchical URI} of the
     *         given node path does not conform to the syntax constraints for
     *         {@link File#File(URI)}.
     * @see    #getNodePath()
     */
    public TFile(FsNodePath path) { this(path, null); }

    /**
     * Constructs a new {@code TFile} instance for the given {@code path} and
     * {@code detector}.
     * This constructor is a super set of the super class constructor
     * {@link File#File(URI)} with the following additional features:
     * If the URI of the given node path is opaque, it must match the pattern
     * {@code <scheme>:<uri>!/<entry>}.
     * The constructed file object then parses the URI to address an entry in
     * a federated file system (i.e. prospective archive file) with the name
     * {@code <entry>} in the prospective archive file addressed by
     * {@code <uri>} which is of the type identified by {@code <scheme>}  .
     * This is recursively applied to access entries within other prospective
     * archive files until {@code <uri>} is a hierarchical URI.
     * Note that the scheme component of this hierarchical URI must be
     * {@code file} in order to maintain interoperability with the super class!
     *
     * @param  path a path with an absolute
     *         {@link FsNodePath#toHierarchicalUri() hierarchical URI} which
     *         has a scheme component which is known by the given
     *         {@code detector}.
     * @param  detector the archive detector to look up archive file system
     *         drivers for the named URI scheme components.
     *         If this parameter is {@code null}, then the default archive
     *         detector gets resolved by calling
     *         {@code TConfig.current().getArchiveDetector()}.
     * @throws IllegalArgumentException if the
     *         {@link FsNodePath#toHierarchicalUri() hierarchical URI} of the
     *         given node path does not conform to the syntax constraints for
     *         {@link File#File(URI)}.
     * @see    #getNodePath()
     */
    @ExpertFeature(INJECTING_A_DIFFERENT_DETECTOR_FOR_THE_SAME_PATH_MAY_CORRUPT_DATA)
    public TFile(final FsNodePath path, final @CheckForNull TArchiveDetector detector) {
        super(path.toHierarchicalUri());
        parse(path, null != detector ? detector : TConfig.current().getArchiveDetector());
    }

    private void parse(
            final FsNodePath path,
            final TArchiveDetector detector) {
        this.file = new File(super.getPath());
        this.detector = detector;
        final FsMountPoint mp = path.getMountPoint();
        final FsNodePath mpp = mp.getPath();
        final FsNodeName nn;
        if (null == mpp) {
            assert !path.getUri().isOpaque();
            this.enclArchive = null;
            this.nodeName = null;
            this.innerArchive = null;
        } else if ((nn = path.getNodeName()).isRoot()) {
            assert path.getUri().isOpaque();
            if (mpp.getUri().isOpaque()) {
                this.enclArchive = new TFile(mpp.getMountPoint(), detector);
                this.nodeName = mpp.getNodeName();
            } else {
                this.enclArchive = null;
                this.nodeName = null;
            }
            this.innerArchive = this;
            // See http://java.net/jira/browse/TRUEZIP-154 .
            this.controller = getController(mp);
        } else {
            assert path.getUri().isOpaque();
            this.enclArchive = new TFile(mp, detector);
            this.nodeName = nn;
            this.innerArchive = this.enclArchive;
        }

        assert invariants();
    }

    @SuppressWarnings("LeakingThisInConstructor")
    private TFile(
            final FsMountPoint mountPoint,
            final TArchiveDetector detector) {
        super(mountPoint.toHierarchicalUri());
        this.file = new File(super.getPath());
        this.detector = detector;
        final FsNodePath mpp = mountPoint.getPath();
        if (null == mpp) {
            assert !mountPoint.getUri().isOpaque();
            this.enclArchive = null;
            this.nodeName = null;
            this.innerArchive = null;
        } else {
            assert mountPoint.getUri().isOpaque();
            if (mpp.getUri().isOpaque()) {
                this.enclArchive = new TFile(mpp.getMountPoint(), detector);
                this.nodeName = mpp.getNodeName();
            } else {
                this.enclArchive = null;
                this.nodeName = null;
            }
            this.innerArchive = this;
            this.controller = getController(mountPoint);
        }
        assert invariants();
    }

    @SuppressWarnings({"LeakingThisInConstructor", "AccessingNonPublicFieldOfAnotherObject"})
    private TFile(
            final File file,
            final @CheckForNull TFile innerArchive,
            final TArchiveDetector detector) {
        super(file.getPath());
        this.file = file;
        final String path = file.getPath();
        if (null != innerArchive) {
            final int iapl = innerArchive.getPath().length();
            if (path.length() == iapl) {
                this.detector = innerArchive.detector;
                this.enclArchive = innerArchive.enclArchive;
                this.nodeName = innerArchive.nodeName;
                this.innerArchive = this;
                this.controller = innerArchive.controller;
            } else {
                this.detector = detector;
                this.innerArchive = this.enclArchive = innerArchive;
                try {
                    this.nodeName = new FsNodeName(
                            new UriBuilder()
                                .path(
                                    path.substring(iapl + 1) // cut off leading separatorChar
                                        .replace(separatorChar, SEPARATOR_CHAR))
                                .getUri(),
                            CANONICALIZE);
                } catch (URISyntaxException ex) {
                    throw new AssertionError(ex);
                }
            }
        } else {
            this.detector = detector;
        }
        assert invariants();
    }

    /**
     * Initialize this file object by scanning its path for archive
     * files, using the given {@code ancestor} file (i.e. a direct or
     * indirect parent file) if any.
     * {@code file} and {@code config} must already be initialized!
     * Must not be called to re-initialize this object!
     */
    private void scan(final @CheckForNull TFile ancestor) {
        final String path = super.getPath();
        assert ancestor == null || path.startsWith(ancestor.getPath());
        assert file.getPath().equals(path);
        assert null != detector;
        final StringBuilder nodeNameBuf = new StringBuilder(path.length());
        scan(detector, ancestor, 0, path, nodeNameBuf, new PathSplitter(separatorChar, false));
        try {
            nodeName = 0 >= nodeNameBuf.length()
                    ? null
                    : new FsNodeName(
                        new UriBuilder().path(nodeNameBuf.toString()).getUri(),
                        CANONICALIZE);
        } catch (URISyntaxException ex) {
            throw new AssertionError(ex);
        }
    }

    @SuppressWarnings("AccessingNonPublicFieldOfAnotherObject")
    private void scan(
            TArchiveDetector detector,
            @CheckForNull TFile ancestor,
            int skip,
            final String path,
            final StringBuilder enclEntryNameBuf,
            final PathSplitter splitter) {
        if (path == null) {
            assert null == enclArchive;
            enclEntryNameBuf.setLength(0);
            return;
        }

        splitter.split(path);
        final String parent = splitter.getParentPath();
        final String member = splitter.getMemberName();

        if (0 == member.length() || ".".equals(member)) {
            // Fall through.
        } else if ("..".equals(member)) {
            skip++;
        } else if (0 < skip) {
            skip--;
        } else {
            if (null != ancestor) {
                final int pathLen = path.length();
                final int ancestorPathLen = ancestor.getPath().length();
                if (pathLen == ancestorPathLen) {
                    // Found ancestor: Process it and stop.
                    // Using the following assertion would be wrong:
                    // enclEntryNameBuf may indeed be null if the full path
                    // ends with just a single dot after the last separator,
                    // i.e. the base name is ".", indicating the current
                    // directory.
                    // assert enclEntryNameBuf.getLength() > 0;
                    enclArchive = ancestor.innerArchive;
                    if (!ancestor.isArchive()) {
                        if (ancestor.isEntry()) {
                            assert null != ancestor.nodeName;
                            if (0 < enclEntryNameBuf.length()) {
                                enclEntryNameBuf.insert(0, '/');
                                enclEntryNameBuf.insert(0, ancestor.nodeName.getPath());
                            } else { // TODO: Simplify this!
                                // Example: new TFile(new TFile(new TFile("archive.zip"), "entry"), ".")
                                assert enclArchive == ancestor.enclArchive;
                                enclEntryNameBuf.append(ancestor.nodeName.getPath());
                            }
                        } else {
                            assert null == enclArchive;
                            enclEntryNameBuf.setLength(0);
                        }
                    } else if (0 >= enclEntryNameBuf.length()) { // TODO: Simplify this!
                        // Example: new TFile(new TFile("archive.zip"), ".")
                        assert enclArchive == ancestor;
                        innerArchive = this;
                        enclArchive = ancestor.enclArchive;
                        if (ancestor.nodeName != null)
                            enclEntryNameBuf.append(ancestor.nodeName.getPath());
                    }
                    if (this != innerArchive)
                        innerArchive = enclArchive;
                    return;
                } else if (pathLen < ancestorPathLen) {
                    detector = ancestor.getArchiveDetector();
                    ancestor = ancestor.enclArchive;
                }
            }

            final boolean isArchive = null != detector.scheme(path);
            if (0 < enclEntryNameBuf.length()) {
                if (isArchive) {
                    enclArchive = new TFile(path, detector); // use the same configuration for the parent directory
                    if (innerArchive != this)
                        innerArchive = enclArchive;
                    return;
                }
                enclEntryNameBuf.insert(0, '/');
                enclEntryNameBuf.insert(0, member);
            } else {
                if (isArchive)
                    innerArchive = this;
                enclEntryNameBuf.append(member);
            }
        }

        scan(detector, ancestor, skip, parent, enclEntryNameBuf, splitter);
    }

    private Object writeReplace() throws ObjectStreamException {
        return getAbsoluteFile();
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeObject(getUri());
    }

    private void readObject(ObjectInputStream in)
    throws IOException, ClassNotFoundException {
        parse(  FsNodePath.create((URI) in.readObject(), CANONICALIZE),
                TConfig.current().getArchiveDetector());
    }

    /**
     * Checks the invariants of this class and throws an AssertionError if
     * any is violated even if assertion checking is disabled.
     * <p>
     * The constructors call this method like this:
     * <pre>{@code assert invariants();}</pre>
     * This calls the method if and only if assertions are enabled in order
     * to assert that the instance invariants are properly obeyed.
     * If assertions are disabled, the call to this method is thrown away by
     * the HotSpot compiler, so there is no performance penalty.
     *
     * @throws AssertionError If assertions are enabled and any invariant is
     *         violated.
     * @return {@code true}
     */
    private boolean invariants() {
        // Thread-safe caching
        final File file = this.file;
        final TFile innerArchive = this.innerArchive;
        final TFile enclArchive = this.enclArchive;
        final FsNodeName nodeName = this.nodeName;

        assert null != file;
        assert !(file instanceof TFile);
        assert file.getPath().equals(super.getPath());
        assert null != detector;
        assert (null != innerArchive) == (getInnerEntryName() != null);
        assert (null != enclArchive) == (null != nodeName);
        assert this != enclArchive;
        assert (this == innerArchive)
                ^ (innerArchive == enclArchive && null == controller);
        assert null == enclArchive
                || Paths.contains(  enclArchive.getPath(),
                                    file.getParentFile().getPath(),
                                    separatorChar)
                    && !nodeName.toString().isEmpty();
        return true;
    }

    @Override
    public TArchiveDetector getArchiveDetector() { return detector; }

    /**
     * Returns a file object for the same path name, but does not detect any
     * archive file name patterns in the last path name segment.
     * The parent file object is unaffected by this transformation, so the
     * path name of this file object may address an entry in an archive file.
     *
     * @return A file object for the same path name, but does not detect any
     *         archive file name patterns in the last path name segment.
     * @see    TVFS#umount(TFile)
     */
    @ExpertFeature( level=INTERMEDIATE,
                    value=INJECTING_A_DIFFERENT_DETECTOR_FOR_THE_SAME_PATH_MAY_CORRUPT_DATA)
    public TFile toNonArchiveFile() {
        if (!isArchive()) return this;
        return new TFile(getParentFile(), getName(), TArchiveDetector.NULL);
    }

    /**
     * Returns the first parent directory (starting from this file) which is
     * <em>not</em> an archive file or a file located in an archive file.
     *
     * @return The first parent directory (starting from this file) which is
     *         <em>not</em> an archive file or a file located in an archive
     *         file.
     */
    public @Nullable TFile getNonArchivedParentFile() {
        final TFile enclArchive = this.enclArchive;
        return null != enclArchive
                ? enclArchive.getNonArchivedParentFile()
                : getParentFile();
    }

    @Override
    public @Nullable String getParent() {
        return file.getParent();
    }

    @Override
    public @Nullable TFile getParentFile() {
        final File parent = file.getParentFile();
        if (parent == null)
            return null;

        final TFile enclArchive = this.enclArchive;
        if (null != enclArchive
                && enclArchive.getPath().length() == parent.getPath().length()) {
            assert enclArchive.getPath().equals(parent.getPath());
            return enclArchive;
        }

        // This is not only called for performance reasons, but also in order
        // to prevent the parent path from being rescanned for archive files
        // with a different detector, which is nonsense.
        return new TFile(parent, enclArchive, getArchiveDetector());
    }

    @Override
    public TFile getAbsoluteFile() {
        final String p = getAbsolutePath();
        return p.equals(getPath()) ? this : new TFile(p, getArchiveDetector());
    }

    @Override
    public String getAbsolutePath() {
        return file.getAbsolutePath();
    }

    /**
     * Similar to {@link #getAbsoluteFile()}, but removes any
     * {@code "."} and {@code ".."} directories from the path name wherever
     * possible.
     * The result is similar to {@link #getCanonicalFile()}, but symbolic
     * links are not resolved.
     * This may be useful if {@code getCanonicalFile()} throws an
     * IOException.
     *
     * @return The normalized absolute file object denoting the same file or
     *         directory as this instance.
     * @see    #getCanonicalFile()
     * @see    #getNormalizedFile()
     */
    public TFile getNormalizedAbsoluteFile() {
        final String p = getNormalizedAbsolutePath();
        return p.equals(getPath()) ? this : new TFile(p, getArchiveDetector());
    }

    /**
     * Similar to {@link #getAbsolutePath()}, but removes any redundant
     * {@code "."} and {@code ".."} directories from the path name.
     * The result is similar to {@link #getCanonicalPath()}, but symbolic
     * links are not resolved.
     * This may be useful if {@code getCanonicalPath()} throws an
     * IOException.
     *
     * @return The normalized absolute path string denoting the same file or
     *         directory as this instance.
     * @see #getCanonicalPath()
     * @see #getNormalizedPath()
     */
    public String getNormalizedAbsolutePath() {
        return Paths.normalize(getAbsolutePath(), separatorChar);
    }

    /**
     * Removes any redundant {@code "."} and {@code ".."} directories from the
     * path name.
     *
     * @return The normalized file object denoting the same file or
     *         directory as this instance.
     */
    public TFile getNormalizedFile() {
        final String p = getNormalizedPath();
        return p.equals(getPath()) ? this : new TFile(p, getArchiveDetector());
    }

    /**
     * Removes any redundant {@code "."}, {@code ".."} directories from the
     * path name.
     *
     * @return The normalized path string denoting the same file or
     *         directory as this instance.
     */
    public String getNormalizedPath() {
        return Paths.normalize(getPath(), separatorChar);
    }

    @Override
    @FsAssertion(atomic=YES, consistent=YES, isolated=YES, durable=NOT_APPLICABLE)
    public TFile getCanonicalFile() throws IOException {
        final String p = getCanonicalPath();
        return p.equals(getPath()) ? this : new TFile(p, getArchiveDetector());
    }

    @Override
    @FsAssertion(atomic=YES, consistent=YES, isolated=YES, durable=NOT_APPLICABLE)
    public String getCanonicalPath() throws IOException {
        return file.getCanonicalPath();
    }

    /**
     * This convenience method simply returns the canonical form of this
     * abstract path or the normalized absolute form if resolving the
     * prior fails.
     *
     * @return The canonical or absolute path of this file as an
     *         instance of this class.
     */
    public TFile getCanOrAbsFile() {
        final String p = getCanOrAbsPath();
        return p.equals(getPath()) ? this : new TFile(p, getArchiveDetector());
    }

    /**
     * This convenience method simply returns the canonical form of this
     * abstract path or the normalized absolute form if resolving the
     * prior fails.
     *
     * @return The canonical or absolute path of this file as a
     *         {@code String} instance.
     */
    public String getCanOrAbsPath() {
        try {
            return getCanonicalPath();
        } catch (IOException ex) {
            return Paths.normalize(getAbsolutePath(), separatorChar);
        }
    }

    @Override
    public String getPath() {
        return file.getPath();
    }

    @Override
    public String getName() {
        return file.getName();
    }

    /**
     * Returns {@code true} if and only if this {@code TFile} addresses an
     * archive file.
     * Whether or not this is true solely depends on the
     * {@link TArchiveDetector} which was used to construct this {@code TFile}
     * - no file system tests are performed by this method!
     *
     * @return {@code true} if and only if this {@code TFile} addresses an
     *         archive file.
     * @see    #isEntry
     * @see    #isDirectory
     * @see    <a href="#falsePositives">Detecting Archive Paths and False Positives</a>
     */
    public boolean isArchive() {
        return this == innerArchive;
    }

    /**
     * Returns {@code true} if and only if this {@code TPath} addresses an
     * entry located within an archive file.
     * Whether or not this is true solely depends on the
     * {@link TArchiveDetector} which was used to construct this {@code TPath}
     * - no file system tests are performed by this method!
     *
     * @return {@code true} if and only if this {@code TPath} addresses an
     *         entry located within an archive file.
     * @see #isArchive
     * @see #isDirectory
     * @see <a href="#falsePositives">Detecting Archive Paths and False Positives</a>
     */
    public boolean isEntry() {
        return null != nodeName;
    }

    /**
     * Returns the innermost archive file object for this file object.
     * That is, if this object addresses an archive file, then this method
     * returns {@code this}.
     * If this object addresses an entry located within an archive file, then
     * this methods returns the file object representing the enclosing archive
     * file, or {@code null} otherwise.
     * <p>
     * This method always returns a normalized path, i.e. all occurences of
     * {@code "."} and {@code ".."} in the path name are removed according to
     * their meaning wherever possible.
     * <p>
     * In order to support unlimited nesting levels, this method returns
     * a {@code TFile} instance which may recursively address an entry within
     * another archive file.
     *
     * @return The innermost archive path object for this path object.
     */
    public @CheckForNull TFile getInnerArchive() {
        return innerArchive;
    }

    /**
     * Returns the entry name relative to the innermost archive file.
     * That is, if this object addresses an archive file, then this method
     * returns the empty string {@code ""}.
     * If this object addresses an entry located within an archive file,
     * then this method returns the relative path of the entry in the
     * enclosing archive file separated by the entry separator character
     * {@code '/'}, or {@code null} otherwise.
     * <p>
     * This method always returns an undotified path, i.e. all redundant
     * occurences of {@code "."} and {@code ".."} in the path are removed
     * wherever possible.
     *
     * @return The entry name relative to the innermost archive file.
     */
    public @Nullable String getInnerEntryName() {
        final FsNodeName nodeName;
        return this == innerArchive
                ? ROOT.getPath()
                : null == (nodeName = this.nodeName)
                    ? null
                    : nodeName.getPath();
    }

    /**
     * Returns the parent file system object for this file object.
     * That is, if this object addresses an entry located within an archive
     * file, then this method returns the file object representing the
     * enclosing archive file, or {@code null} otherwise.
     * <p>
     * This method always returns a normalized path, i.e. all occurences of
     * {@code "."} and {@code ".."} in the path name are removed according to
     * their meaning wherever possible.
     * <p>
     * In order to support unlimited nesting levels, this method returns
     * a {@code TFile} instance which may recursively address an entry within
     * another archive file.
     *
     * @return The enclosing archive file in this path.
     */
    public @CheckForNull TFile getEnclArchive() {
        return enclArchive;
    }

    /**
     * Returns the node name relative to the parent file system.
     * That is, if this object addresses an entry located within an archive
     * file, then this method returns the relative path of the entry in the
     * enclosing archive file separated by the entry separator character
     * {@code '/'}, or {@code null} otherwise.
     * <p>
     * This method always returns an undotified path, i.e. all redundant
     * occurences of {@code "."} and {@code ".."} in the path are removed
     * wherever possible.
     *
     * @return The entry name relative to the enclosing archive file.
     */
    public @Nullable String getEnclEntryName() {
        return null == nodeName ? null : nodeName.getPath();
    }

    /**
     * Returns {@code true} if and only if this file is a
     * {@linkplain #getTopLevelArchive() top level archive file}.
     *
     * @return {@code true} if and only if this file is a
     *         {@linkplain #getTopLevelArchive() top level archive file}.
     */
    public boolean isTopLevelArchive() {
        return getTopLevelArchive() == this;
    }

    /**
     * Returns the top level archive file in the path or {@code null} if this
     * file object does not name an archive file.
     * A top level archive is not enclosed in another archive.
     * If this method returns non-{@code null}, the value denotes the longest
     * part of the path which may (but does not need to) exist as a plain file
     * in the platform file system.
     *
     * @return The top level archive file in the path or {@code null} if this
     *         file object does not name an archive file.
     */
    public @Nullable TFile getTopLevelArchive() {
        final TFile enclArchive = this.enclArchive;
        return null != enclArchive
                ? enclArchive.getTopLevelArchive()
                : innerArchive;
    }

    /**
     * Returns the decorated file object.
     * <p>
     * If this file instance has been created from a
     * {@link javax.swing.filechooser.FileSystemView},
     * the decorated file object may be an instance of a sibling class, i.e.
     * another sub-class of {@link File}.
     *
     * @return     An instance of the {@link File File} class or any of its
     *             sub-classes, but never an instance of this class and never
     *             {@code null}.
     * @deprecated Using the resulting {@link File} object for file system
     *             access would compete with the TrueVFS Kernel for I/O and may
     *             easily corrupt the state of the (virtual) file system space,
     *             including loss of data!
     */
    @Deprecated
    public File getFile() { return file; }

    /**
     * Returns a file system controller if and only if the path denotes an
     * archive file, or {@code null} otherwise.
     * <p>
     * TODO: Consider making this public in order to enable applications to
     * get access to archive entry properties.
     *
     * @return A file system controller if and only if the path denotes an
     *         archive file, or {@code null} otherwise.
     */
    @Nullable FsController getController() {
        final FsController controller = this.controller;
        if (this != innerArchive || null != controller)
            return controller;
        final File file = this.file;
        final String path = Paths.normalize(file.getPath(), separatorChar);
        final FsScheme scheme = getArchiveDetector().scheme(path);
        // See http://java.net/jira/browse/TRUEZIP-154 .
        if (null == scheme)
            throw new ServiceConfigurationError(
                    "Unknown file system scheme for path \""
                    + path
                    + "\"! Check run-time class path configuration.");
        final FsMountPoint mountPoint;
        try {
            final TFile enclArchive = this.enclArchive;
            final FsNodeName nodeName = this.nodeName;
            assert (null != enclArchive) == (null != nodeName);
            mountPoint = new FsMountPoint(scheme, null == enclArchive
                    ? new FsNodePath(   file)
                    : new FsNodePath(   enclArchive .getController()
                                                .getModel()
                                                .getMountPoint(),
                                    nodeName));
        } catch (URISyntaxException ex) {
            throw new AssertionError(ex);
        }
        return this.controller = getController(mountPoint);
    }

    private FsController getController(FsMountPoint mountPoint) {
        return TConfig
                .current()
                .getManager()
                .controller(getArchiveDetector(), mountPoint);
    }

    /**
     * Returns {@code true} if and only if the path represented
     * by this instance is a direct or indirect parent of the path
     * represented by the given {@code file}.
     * <p>
     * Note:
     * <ul>
     * <li>This method uses the absolute path name of the given files.
     * <li>This method does <em>not</em> access the file system.
     *     It just tests the paths.
     * </ul>
     *
     * @param file The file object for the path to test for being a direct or
     *        indirect child of the path of this instance.
     * @return {@code true} if and only if the path represented
     *         by this instance is a direct or indirect parent of the path
     *         represented by the given {@code file}.
     */
    public boolean isParentOf(final File file) {
        final String a = this.getAbsolutePath();
        final String b = file.getAbsoluteFile().getParent();
        return b != null ? Paths.contains(a, b, separatorChar) : false;
    }

    @Override
    public boolean isAbsolute() {
        return file.isAbsolute();
    }

    @Override
    public boolean isHidden() {
        return file.isHidden();
    }

    /**
     * Returns {@code true} if and only if the path represented
     * by this instance contains the path represented by the given {@code file},
     * where a path is said to contain another path if and only
     * if it's equal or an ancestor of the other path.
     * <p>
     * Note:
     * <ul>
     * <li>This method uses the absolute path name of the given files.
     * <li>This method does <em>not</em> access the file system.
     *     It just tests the paths.
     * </ul>
     *
     * @param  file The file object for the path to test for being contained by
     *         the path of this instance.
     * @return {@code true} if and only if the path represented
     *         by this instance contains the path represented by the given
     *         {@code file}
     * @throws NullPointerException If the parameter is {@code null}.
     */
    public boolean contains(File file) { return contains(this, file); }

    /**
     * Returns {@code true} if and only if the path represented
     * by {@code a} contains the path represented by {@code b},
     * where a path is said to contain another path if and only
     * if it's equal or an ancestor of the other path.
     * <p>
     * Note:
     * <ul>
     * <li>This method uses the absolute path name of the given files.
     * <li>This method does <em>not</em> access the file system.
     *     It just tests the paths.
     * </ul>
     *
     * @param  a the file to test for containing {@code b}.
     * @param  b the file to test for being contained by {@code a}.
     * @return {@code true} if and only if the path represented
     *         by {@code a} contains the path represented by {@code b}.
     * @throws NullPointerException If any parameter is {@code null}.
     */
    public static boolean contains(File a, File b) {
        return Paths.contains(  a.getAbsolutePath(),
                                b.getAbsolutePath(),
                                separatorChar);
    }

    /**
     * Returns {@code true} if and only if this file denotes a file system
     * root or a UNC (if running on the Windows platform).
     *
     * @return {@code true} if and only if this file denotes a file system
     *         root or a UNC (if running on the Windows platform).
     */
    public boolean isFileSystemRoot() {
        final TFile canOrAbsFile = getCanOrAbsFile();
        return ROOTS.contains(canOrAbsFile) || isUNC(canOrAbsFile.getPath());
    }

    /**
     * Returns {@code true} if and only if this file denotes a UNC.
     * Note that this should be relevant on the Windows platform only.
     *
     * @return {@code true} if and only if this file denotes a UNC.
     */
    public boolean isUNC() { return isUNC(getCanOrAbsPath()); }

    /**
     * Returns {@code true} if and only if this file denotes a UNC.
     * Note that this may be only relevant on the Windows platform.
     *
     * @param  path a file path.
     * @return {@code true} if and only if {@code path} denotes a UNC.
     */
    private static boolean isUNC(String path) {
        return path.startsWith(UNC_PREFIX) && path.indexOf(separatorChar, 2) > 2;
    }

    /**
     * {@inheritDoc}
     * <p>
     * The implementation in the class {@link TFile} delegates the call to its
     * {@link #getFile() decorated file}.
     *
     * @see #equals(Object)
     */
    @Override
    public int hashCode() { return file.hashCode(); }

    /**
     * {@inheritDoc}
     * <p>
     * The implementation in the class {@link TFile} delegates the call to its
     * {@link #getFile() decorated file}.
     * This implies that only the hierarchicalized file system path
     * of this file instance is considered in the comparison.
     * E.g. {@code new TFile(FsNodePath.create("zip:file:/archive!/entry"))} and
     * {@code new TFile(FsNodePath.create("tar:file:/archive!/entry"))} would
     * compare equal because their hierarchicalized file system path is
     * {@code "file:/archive/entry"}.
     * <p>
     * More formally, let {@code a} and {@code b} be two TFile objects.
     * Then if the expression
     * {@code a.getNodePath().toHierarchicalUri().equals(b.getNodePath().toHierarchicalUri())}
     * is true, the expression {@code a.equals(b)} is also true.
     * <p>
     * Note that this does <em>not</em> work vice versa:
     * E.g. on Windows, the expression
     * {@code new TFile("file").equals(new TFile("FILE"))} is true, but
     * {@code new TFile("file").getNodePath().toHierarchicalUri().equals(new TFile("FILE").getNodePath().toHierarchicalUri())}
     * is false because {@link FsNodePath#equals(Object)} is case sensitive.
     *
     * @param that the object to get compared with this object
     * @see   #hashCode()
     * @see   #compareTo(File)
     */
    @Override
    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    public boolean equals(Object that) { return file.equals(that); }

    /**
     * {@inheritDoc }
     * <p>
     * The implementation in the class {@link TFile} delegates the call to its
     * {@link #getFile() decorated file} object.
     * This implies that only the hierarchicalized file system path
     * of this file instance is considered in the comparison.
     * E.g. {@code new TFile(FsNodePath.create("zip:file:/archive!/entry"))} and
     * {@code new TFile(FsNodePath.create("tar:file:/archive!/entry"))} would
     * compare equal because their hierarchicalized file system path is
     * {@code "file:/archive/entry"}.
     * <p>
     * More formally, let {@code a} and {@code b} be two TFile objects.
     * Now if the expression
     * {@code a.getNodePath().toHierarchicalUri().compareTo(b.getNodePath().toHierarchicalUri()) == 0}
     * is true, then the expression {@code a.compareTo(b) == 0} is also true.
     * <p>
     * Note that this does <em>not</em> work vice versa:
     * E.g. on Windows, the expression
     * {@code new TFile("file").compareTo(new TFile("FILE")) == 0} is true, but
     * {@code new TFile("file").getNodePath().toHierarchicalUri().compareTo(new TFile("FILE").getNodePath().toHierarchicalUri()) == 0}
     * is false because {@link FsNodePath#equals(Object)} is case sensitive.
     *
     * @param that the file object to get compared with this object
     * @see   #equals(Object)
     */
    @Override
    public int compareTo(File that) { return file.compareTo(that); }

    /**
     * Returns a file system node path which is consistent with {@link #toURI()}.
     *
     * @return A file system node path which is consistent with {@link #toURI()}.
     * @see    #TFile(FsNodePath)
     * @see    #toURI()
     */
    @Override
    public FsNodePath getNodePath() {
        try {
            if (this == innerArchive) {
                final FsScheme scheme = getScheme();
                if (null != enclArchive) {
                    assert null != nodeName;
                    return new FsNodePath(
                            new FsMountPoint(
                                scheme,
                                new FsNodePath(
                                    new FsMountPoint(enclArchive.toURI(), CANONICALIZE),
                                    nodeName)),
                            ROOT);
                } else {
                    return new FsNodePath(
                            new FsMountPoint(scheme, new FsNodePath(file)),
                            ROOT);
                }
            } else if (null != enclArchive) {
                assert null != nodeName;
                return new FsNodePath(
                        new FsMountPoint(enclArchive.toURI(), CANONICALIZE),
                        nodeName);
            } else {
                return new FsNodePath(file);
            }
        } catch (URISyntaxException ex) {
            throw new AssertionError(ex);
        }
    }

    @Override
    public FsMountPoint getMountPoint() {
        // TODO: Optimize this.
        return getNodePath().getMountPoint();
    }

    @Override
    public FsNodeName getNodeName() {
        return this == innerArchive ? ROOT : nodeName;
    }

    private @Nullable FsScheme getScheme() {
        if (this != innerArchive) return null;
        final FsController controller = this.controller;
        if (null != controller)
            return controller.getModel().getMountPoint().getScheme();
        return getArchiveDetector().scheme(file.getPath());
    }

    /**
     * In case no prospective archive file has been detected in the path name
     * at construction time, this method behaves like the super class method
     * {@link File#toURI()}.
     * <p>
     * Otherwise, an opaque URI of the form {@code <scheme>:<uri>!/<entry>} is
     * returned, where {@code <scheme>} is the URI scheme component identifying
     * a file system driver, {@code <uri>} is the URI returned by this method
     * for the innermost archive file and {@code <entry>} is the relative path
     * name of the the entry addressed by this file object in the innermost
     * archive file.
     * If this file object addresses an archive file, then {@code <uri>} is the
     * URI which would have been returned by this method if the file name of
     * the archive file had not been detected as a prospective archive file
     * and {@code entry} is an empty string.
     *
     * <h3><a name="exampleUris">Example URIs</a></h3>
     *
     * <ul>
     * <li>{@code file:/foo} addresses a regular file</li>
     * <li>{@code war:file:/foo.war!/} addresses the root entry of the WAR file
     *     addressed by {@code file:/foo.war}.</li>
     * <li>{@code war:file:/foo.war!/META-INF/MANIFEST.MF} addresses the entry
     *     {@code META-INF/MANIFEST.MF} in the WAR file addressed by
     *     {@code file:/foo.war}.</li>
     * <li>{@code jar:war:file:/foo.war!/WEB-INF/lib/bar.jar!/META-INF/MANIFEST.MF}
     *     addresses the entry {@code META-INF/MANIFEST.MF} in the JAR file
     *     addressed by {@code war:file:/foo.war!/WEB-INF/lib/bar.jar}, which
     *     recursively addresses the entry {@code WEB-INF/lib/bar.jar} in the
     *     WAR file addressed by {@code file:/foo.war}.</li>
     * </ul>
     *
     * @return A URI for this file object.
     * @see    #getNodePath()
     */
    @Override
    public URI getUri() {
        try {
            if (this == innerArchive) {
                final FsScheme scheme = getScheme();
                if (null != enclArchive) {
                    assert null != nodeName;
                    return new FsMountPoint(
                            scheme,
                            new FsNodePath(
                                new FsMountPoint(enclArchive.toURI(), CANONICALIZE),
                                nodeName)).getUri();
                } else {
                    return new FsMountPoint(scheme, new FsNodePath(file)).getUri();
                }
            } else if (null != enclArchive) {
                assert null != nodeName;
                return new FsNodePath(
                        new FsMountPoint(enclArchive.toURI(), CANONICALIZE),
                        nodeName).getUri();
            } else {
                return file.toURI();
            }
        } catch (URISyntaxException ex) {
            throw new AssertionError(ex);
        }
    }

    @Override
    public URI toURI() { return getUri(); }

    @Deprecated
    @Override
    public URL toURL() throws MalformedURLException {
        return null != innerArchive ? toURI().toURL() : file.toURL();
    }

    @Override
    public TFile toFile() { return this; }

    @Override
    public TPath toPath() { return new TPath(this); }

    @Override
    public String toString() { return file.toString(); }

    private static BitField<FsAccessOption> getAccessPreferences() {
        return TConfig.current().getAccessPreferences();
    }

    /**
     * {@inheritDoc}
     *
     * @see <a href="#falsePositives">Detecting Archive Paths and False Positives</a>
     */
    @Override
    @FsAssertion(atomic=YES, consistent=YES, isolated=YES, durable=NOT_APPLICABLE)
    public boolean exists() {
        // DONT test existance of getNodeName() in enclArchive because
        // it doesn't need to exist - see
        // http://java.net/jira/browse/TRUEZIP-136 .
        if (null != innerArchive) {
            try {
                innerArchive.getController().checkAccess(
                        getAccessPreferences(), getNodeName(),
                        NO_ACCESS);
                return true;
            } catch (IOException ex) {
                return false;
            }
        }
        return file.exists();
    }

    /**
     * Similar to its super class implementation, but returns
     * {@code false} for a valid archive file, too.
     * <p>
     * For archive file validation its virtual file system gets mounted.
     * In case a RAES encrypted ZIP file gets mounted, the user gets prompted
     * for its password unless the default configuration for key management
     * hasn't been overridden.
     *
     * @see <a href="#falsePositives">Detecting Archive Paths and False Positives</a>
     */
    @Override
    @FsAssertion(atomic=YES, consistent=YES, isolated=YES, durable=NOT_APPLICABLE)
    public boolean isFile() {
        if (null != innerArchive) {
            try {
                final FsNode entry = innerArchive.getController()
                        .node(getAccessPreferences(), getNodeName());
                return null != entry && entry.isType(FILE);
            } catch (IOException ex) {
                return false;
            }
        }
        return file.isFile();
    }

    /**
     * Similar to its super class implementation, but returns
     * {@code true} for a valid archive file, too.
     * <p>
     * For archive file validation its virtual file system gets mounted.
     * In case a RAES encrypted ZIP file gets mounted, the user gets prompted
     * for its password unless the default configuration for key management
     * hasn't been overridden.
     *
     * @see <a href="#falsePositives">Detecting Archive Paths and False Positives</a>
     * @see #isArchive
     * @see #isEntry
     */
    @Override
    @FsAssertion(atomic=YES, consistent=YES, isolated=YES, durable=NOT_APPLICABLE)
    public boolean isDirectory() {
        if (null != innerArchive) {
            try {
                final FsNode entry = innerArchive.getController()
                        .node(getAccessPreferences(), getNodeName());
                return null != entry && entry.isType(DIRECTORY);
            } catch (IOException ex) {
                return false;
            }
        }
        return file.isDirectory();
    }

    @Override
    @FsAssertion(atomic=YES, consistent=YES, isolated=YES, durable=NOT_APPLICABLE)
    public boolean canRead() {
        if (null != innerArchive) {
            try {
                innerArchive.getController().checkAccess(
                        getAccessPreferences(), getNodeName(),
                        READ_ACCESS);
                return true;
            } catch (IOException ex) {
                return false;
            }
        }
        return file.canRead();
    }

    @Override
    @FsAssertion(atomic=YES, consistent=YES, isolated=YES, durable=NOT_APPLICABLE)
    public boolean canWrite() {
        if (null != innerArchive) {
            try {
                innerArchive.getController().checkAccess(
                        getAccessPreferences(), getNodeName(),
                        WRITE_ACCESS);
                return true;
            } catch (IOException ex) {
                return false;
            }
        }
        return file.canWrite();
    }

    @Override
    @FsAssertion(atomic=YES, consistent=YES, isolated=YES, durable=NOT_APPLICABLE)
    public boolean canExecute() {
        if (null != innerArchive) {
            try {
                innerArchive.getController().checkAccess(
                        getAccessPreferences(), getNodeName(),
                        EXECUTE_ACCESS);
                return true;
            } catch (IOException ex) {
                return false;
            }
        }
        return file.canExecute();
    }

    /**
     * Like the super class implementation, but is aware of archive
     * files in its path.
     * For entries in a archive file, this is effectively a no-op:
     * The method will only return {@code true} if the entry isExisting and the
     * archive file was mounted read only.
     */
    @Override
    @FsAssertion(atomic=YES, consistent=YES, isolated=YES)
    public boolean setReadOnly() {
        if (null != innerArchive) {
            try {
                innerArchive.getController()
                        .setReadOnly(getAccessPreferences(), getNodeName());
                return true;
            } catch (IOException ex) {
                return false;
            }
        }
        return file.setReadOnly();
    }

    /**
     * Returns the (uncompressed) length of the file.
     * The length returned of a valid archive file is {@code 0} in order
     * to properly emulate virtual directories across all platforms.
     * <p>
     * For archive file validation its virtual file system gets mounted.
     * In case a RAES encrypted ZIP file gets mounted, the user gets prompted
     * for its password unless the default configuration for key management
     * hasn't been overridden.
     *
     * @see <a href="#falsePositives">Detecting Archive Paths and False Positives</a>
     */
    @Override
    @FsAssertion(atomic=YES, consistent=YES, isolated=YES, durable=NOT_APPLICABLE)
    public long length() {
        if (null != innerArchive) {
            final FsNode entry;
            try {
                entry = innerArchive.getController()
                        .node(getAccessPreferences(), getNodeName());
            } catch (final IOException ex) {
                return 0;
            }
            if (null == entry)
                return 0;
            final long size = entry.getSize(Size.DATA);
            return UNKNOWN != size ? size : 0;
        }
        return file.length();
    }

    /**
     * Returns a {@code long} value representing the time this file was
     * last modified, measured in milliseconds since the epoch (00:00:00 GMT,
     * January 1, 1970), or {@code 0L} if the file does not exist or if an
     * I/O error occurs or if this is a ghost directory in an archive file.
     *
     * @see <a href="package.html">Package description for more information
     *      about ghost directories</a>
     */
    @Override
    @FsAssertion(atomic=YES, consistent=YES, isolated=YES, durable=NOT_APPLICABLE)
    public long lastModified() {
        if (null != innerArchive) {
            final FsNode entry;
            try {
                entry = innerArchive.getController()
                        .node(getAccessPreferences(), getNodeName());
            } catch (final IOException ex) {
                return 0;
            }
            if (null == entry)
                return 0;
            final long time = entry.getTime(Access.WRITE);
            return UNKNOWN != time ? time : 0;
        }
        return file.lastModified();
    }

    /**
     * Sets the last modification of this file or (virtual) directory.
     * If this is a ghost directory within an archive file, it's reincarnated
     * as a plain directory within the archive file.
     * <p>
     * Note that calling this method may incur a severe performance penalty
     * if the file is an entry in an archive file which has just been written
     * (such as after a normal copy operation).
     * If you want to copy a file's contents as well as its last modification
     * time, use {@link #cp_p(File, File)} instead.
     *
     * @see #cp_p(File, File)
     * @see <a href="package.html">Package description for more information
     *      about ghost directories</a>
     */
    @Override
    @FsAssertion(atomic=YES, consistent=YES, isolated=YES)
    public boolean setLastModified(final long time) {
        if (null != innerArchive) {
            try {
                innerArchive.getController().setTime(
                        getAccessPreferences(), getNodeName(),
                        WRITE_ACCESS,
                        time);
                return true;
            } catch (IOException ex) {
                return false;
            }
        }
        return file.setLastModified(time);
    }

    /**
     * Returns the names of the members in this (virtual) directory in a newly
     * created array.
     * The returned array is <em>not</em> sorted.
     * This is the most efficient list method.
     * <p>
     * <b>Note:</b> Archive entries with absolute paths are ignored by
     * this method and are never returned.
     *
     * @return A possibly empty array with the members of this (virtual)
     *         directory or {@code null} if this instance does not refer to a
     *         (virtual) directory or if the virtual directory is inaccessible
     *         due to an I/O error.
     */
    @Override
    @FsAssertion(atomic=YES, consistent=YES, isolated=YES, durable=NOT_APPLICABLE)
    public @Nullable String[] list() {
        if (null != innerArchive) {
            final FsNode entry;
            try {
                entry = innerArchive.getController()
                        .node(getAccessPreferences(), getNodeName());
            } catch (IOException ex) {
                return null;
            }
            if (null == entry) return null;
            final Set<String> members = entry.getMembers();
            return null == members ? null : members.toArray(new String[members.size()]);
        }
        return file.list();
    }

    /**
     * Returns the names of the members in this directory which are
     * accepted by {@code filenameFilter} in a newly created array.
     * The returned array is <em>not</em> sorted.
     * <p>
     * <b>Note:</b> Archive entries with absolute paths are ignored by
     * this method and are never returned.
     *
     * @return A possibly empty array with the members of this (virtual)
     *         directory or {@code null} if this instance does not refer to a
     *         (virtual) directory or if the virtual directory is inaccessible
     *         due to an I/O error.
     */
    @Override
    @FsAssertion(atomic=YES, consistent=YES, isolated=YES, durable=NOT_APPLICABLE)
    public @Nullable String[] list(final @CheckForNull FilenameFilter filter) {
        if (null != innerArchive) {
            final FsNode entry;
            try {
                entry = innerArchive.getController()
                        .node(getAccessPreferences(), getNodeName());
            } catch (IOException ex) {
                return null;
            }
            final Set<String> members = members(entry);
            if (null == members)
                return null;
            if (null == filter)
                return members.toArray(new String[members.size()]);
            final Collection<String> accepted = new ArrayList<>(members.size());
            for (final String member : members)
                if (filter.accept(this, member))
                    accepted.add(member);
            return accepted.toArray(new String[accepted.size()]);
        }
        return file.list(filter);
    }

    @Override
    @FsAssertion(atomic=YES, consistent=YES, isolated=YES, durable=NOT_APPLICABLE)
    public @Nullable TFile[] listFiles() {
        return listFiles((FilenameFilter) null);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Note that archive entries with absolute paths are ignored by this
     * method and are never returned.
     */
    @Override
    @FsAssertion(atomic=YES, consistent=YES, isolated=YES, durable=NOT_APPLICABLE)
    public @Nullable TFile[] listFiles(
            final @CheckForNull FilenameFilter filter) {
        if (null != innerArchive) {
            final FsNode entry;
            try {
                entry = innerArchive.getController()
                        .node(getAccessPreferences(), getNodeName());
            } catch (IOException ex) {
                return null;
            }
            return filter(members(entry), filter);
        } else {
            return filter(list(file.list(filter)), (FilenameFilter) null);
        }
    }

    private static @CheckForNull Set<String> members(@CheckForNull FsNode entry) {
        return null == entry ? null : entry.getMembers();
    }

    private static @CheckForNull List<String> list(@CheckForNull String[] list) {
        return null == list ? null : Arrays.asList(list);
    }

    private @Nullable TFile[] filter(
            final @CheckForNull Collection<String> members,
            final @CheckForNull FilenameFilter filter) {
        if (null == members) return null;
        final TArchiveDetector detector = TConfig.current().getArchiveDetector();
        if (null != filter) {
            final Collection<TFile> accepted = new ArrayList<>(members.size());
            for (final String member : members)
                if (filter.accept(this, member))
                    accepted.add(new TFile(this, member, detector));
            return accepted.toArray(new TFile[accepted.size()]);
        } else {
            final TFile[] accepted = new TFile[members.size()];
            int i = 0;
            for (final String member : members)
                accepted[i++] = new TFile(this, member, detector);
            return accepted;
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Note that archive entries with absolute paths are ignored by this
     * method and are never returned.
     */
    @Override
    @FsAssertion(atomic=YES, consistent=YES, isolated=YES, durable=NOT_APPLICABLE)
    public @Nullable TFile[] listFiles(final @CheckForNull FileFilter filter) {
        if (null != innerArchive) {
            final FsNode entry;
            try {
                entry = innerArchive.getController()
                        .node(getAccessPreferences(), getNodeName());
            } catch (IOException ex) {
                return null;
            }
            return filter(members(entry), filter);
        } else {
            return filter(list(file.list()), filter);
        }
    }

    private @Nullable TFile[] filter(
            final @CheckForNull Collection<String> members,
            final @CheckForNull FileFilter filter) {
        if (null == members) return null;
        final TArchiveDetector detector = TConfig.current().getArchiveDetector();
        if (null != filter) {
            final Collection<TFile> accepted = new ArrayList<>(members.size());
            for (final String member : members) {
                final TFile file = new TFile(this, member, detector);
                if (filter.accept(file))
                    accepted.add(file);
            }
            return accepted.toArray(new TFile[accepted.size()]);
        } else {
            final TFile[] accepted = new TFile[members.size()];
            int i = 0;
            for (final String member : members)
                accepted[i++] = new TFile(this, member, detector);
            return accepted;
        }
    }

    /**
     * Creates a new, empty file similar to its superclass implementation.
     * Note that this method doesn't create archive files because archive
     * files are virtual directories, not files!
     *
     * @see #mkdir
     */
    @Override
    @FsAssertion(consistent=YES, isolated=NO)
    public boolean createNewFile() throws IOException {
        if (null != innerArchive) {
            try {
                innerArchive.getController().make(
                        getAccessPreferences().set(EXCLUSIVE), getNodeName(),
                        FILE,
                        null);
                return true;
            } catch (final FileAlreadyExistsException ex) {
                return false;
            }
        }
        return file.createNewFile();
    }

    @Override
    @FsAssertion(consistent=YES, isolated=NO)
    public boolean mkdirs() {
        if (null == innerArchive) return file.mkdirs();

        final TFile parent = getParentFile();
        if (null != parent && !parent.exists()) parent.mkdirs();

        // TODO: Profile: return parent.isDirectory() && mkdir();
        // May perform better in certain situations where (probably false
        // positive) archive files are involved.
        return mkdir();
    }

    /**
     * Creates a new, empty (virtual) directory similar to its superclass
     * implementation.
     * This method creates an archive file if {@link #isArchive} returns
     * {@code true}.
     * Example:
     * {@code new TFile("archive.zip").mkdir();}
     * <p>
     * Alternatively, archive files get created automatically by simply
     * creating their entries.
     * Example:
     * {@code new TFileOutputStream("archive.zip/README");}
     * This assumes the default configuration where
     * {@link TConfig#isLenient TConfig.current().isLenient()} is true.
     */
    @Override
    @FsAssertion(atomic=YES, consistent=YES, isolated=YES)
    public boolean mkdir() {
        if (null != innerArchive) {
            try {
                innerArchive.getController().make(
                        getAccessPreferences(), getNodeName(),
                        DIRECTORY,
                        null);
                return true;
            } catch (IOException ex) {
                return false;
            }
        }
        return file.mkdir();
    }

    /**
     * Ensures that a (virtual) directory with {@link #getPath() this path name}
     * exists in the (federated) file system.
     *
     * @param  recursive whether or not any missing ancestor directories shall
     *         get created if required.
     * @return {@code this}
     * @throws IOException if any I/O error occurs.
     */
    @FsAssertion(consistent=YES, isolated=NO)
    public TFile mkdir(final boolean recursive) throws IOException {
        final TFile innerArchive = this.innerArchive;
        if (null != innerArchive) {
            if (recursive) {
                final TFile parent = getParentFile();
                if (null != parent && !parent.exists())
                    parent.mkdir(recursive);
            }
            final FsController controller = innerArchive.getController();
            final FsNodeName innerEntryName = getNodeName();
            try {
                controller.make(    getAccessPreferences(), innerEntryName,
                                    DIRECTORY, null);
            } catch (IOException ex) {
                final FsNode entry = controller
                        .node(getAccessPreferences(), innerEntryName);
                if (null == entry || !entry.isType(DIRECTORY))
                    throw ex;
            }
        } else {
            final File dir = file;
            if (!(recursive ? dir.mkdirs() : dir.mkdir()) && !dir.isDirectory())
                throw new FileSystemException(dir.getPath(), null, "Cannot create directory!");
        }
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated This method just returns a boolean value to indicate failure,
     *             which is hard to analyze.
     * @see #rm()
     */
    @Deprecated
    @Override
    @FsAssertion(atomic=YES, consistent=YES, isolated=YES)
    public boolean delete() {
        try {
            rm(this);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    /**
     * Equivalent to {@link #rm(File) rm(this)}.
     *
     * @return {@code this}
     * @throws IOException if any I/O error occurs.
     * @see    <a href="#bulkIOMethods">Bulk I/O Methods</a>
     */
    @FsAssertion(atomic=YES, consistent=YES, isolated=YES)
    public TFile rm() throws IOException {
        rm(this);
        return this;
    }

    /**
     * Deletes the given file or directory.
     * If the file is a directory, it must be empty.
     *
     * @param  file the file or directory.
     *         Note that although this just needs to be a plain {@code File}
     *         object, archive files and entries are only supported for
     *         instances of {@code TFile}.
     * @throws IOException if any I/O error occurs.
     * @see    <a href="#bulkIOMethods">Bulk I/O Methods</a>
     */
    @FsAssertion(atomic=YES, consistent=YES, isolated=YES)
    @SuppressWarnings("AccessingNonPublicFieldOfAnotherObject")
    public static void rm(File file) throws IOException {
        if (file instanceof TFile) {
            TFile tfile = (TFile) file;
            if (null != tfile.innerArchive) {
                tfile.innerArchive.getController().unlink(
                        getAccessPreferences(), tfile.getNodeName());
                return;
            }
            file = tfile.file;
        }
        if (!file.delete())
            throw new FileSystemException(file.getPath(), null, "Cannot delete!");
    }

    /**
     * Equivalent to {@link #rm_r(File) rm_r(this)}.
     *
     * @return {@code this}
     * @throws IOException if any I/O error occurs.
     * @see    <a href="#bulkIOMethods">Bulk I/O Methods</a>
     * @see    <a href="#traversal">Traversing Directory Trees</a>
     */
    @FsAssertion(consistent=YES)
    public TFile rm_r() throws IOException {
        TBIO.rm_r(this, getArchiveDetector());
        return this;
    }

    /**
     * Recursively deletes the given file or directory tree.
     * <p>
     * If {@code node} is an instance of this
     * class, its {@link #getArchiveDetector() archive detector}
     * is used to detect prospective archive files in the directory tree.
     * Otherwise,
     * {@link TArchiveDetector#NULL}
     * is used to detect prospective archive files in the directory tree.
     * <p>
     * This file system operation is <em>not</em> atomic.
     *
     * @param  file the file or directory tree.
     *         Note that although this just needs to be a plain {@code File},
     *         archive files and entries are only supported for instances of
     *         this class.
     * @throws IOException if any I/O error occurs.
     * @see    <a href="#bulkIOMethods">Bulk I/O Methods</a>
     * @see    <a href="#traversal">Traversing Directory Trees</a>
     */
    @FsAssertion(consistent=YES)
    public static void rm_r(File file) throws IOException {
        TBIO.rm_r(file,
                file instanceof TFile
                    ? ((TFile) file).getArchiveDetector()
                    : TArchiveDetector.NULL);
    }

    @Override
    @FsAssertion(atomic=YES, consistent=YES, isolated=YES, durable=YES)
    public void deleteOnExit() {
        if (innerArchive != null) {
            // Support for this operation for archive files and entries has been
            // removed because using a shutdown hook uncautiously introduces a
            // memory leak in dynamic class loader environments.
            throw new UnsupportedOperationException();
        }
        file.deleteOnExit();
    }

    /**
     * {@inheritDoc}
     *
     * @param  dst the destination file or directory tree.
     *         Note that although this just needs to be a plain {@code File},
     *         archive files and entries are only supported for instances of
     *         this class.
     * @return {@code true} on success or {@code false} otherwise.
     * @deprecated This method just returns a boolean value to indicate failure,
     *             which is hard to analyze.
     * @see #mv(File)
     */
    @Deprecated
    @Override
    @FsAssertion(consistent=YES)
    public boolean renameTo(final File dst) {
        try {
            mv(this, dst, getArchiveDetector());
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    /**
     * Equivalent to {@link #mv(File, File, TArchiveDetector) mv(this, dst, detector)},
     * where {@code detector} is {@code TConfig.current().getArchiveDetector()}.
     *
     * @param  dst the destination file or directory tree.
     *         Note that although this just needs to be a plain {@code File},
     *         archive files and entries are only supported for instances of
     *         this class.
     * @return {@code this}
     * @throws IOException if any I/O error occurs.
     * @see    <a href="#bulkIOMethods">Bulk I/O Methods</a>
     * @see    <a href="#traversal">Traversing Directory Trees</a>
     */
    @FsAssertion(consistent=YES)
    public TFile mv(File dst) throws IOException {
        mv(this, dst, TConfig.current().getArchiveDetector());
        return this;
    }

    /**
     * Moves the given source file or directory to the given destination file
     * or directory.
     * <p>
     * In certain cases, this method might perform a recursive copy-then-delete
     * operation rather than an atomic move operation.
     * In these cases, an attempt is made to copy all attributes of each
     * source file to the destination file, too.
     * Which attributes are actually copied is specific to the destination
     * file system driver implementation, but the minimum guarantee is to
     * copy the last modification time.
     *
     * @param  src the source file or directory tree.
     *         Note that although this just needs to be a plain {@code File},
     *         archive files and entries are only supported for instances of
     *         this class.
     * @param  dst the destination file or directory tree.
     *         Note that although this just needs to be a plain {@code File},
     *         archive files and entries are only supported for instances of
     *         this class.
     * @param  detector the archive detector to use for detecting any archive
     *         files <em>within</em> the source and destination directory
     *         trees.
     * @throws IOException if any I/O error occurs.
     * @see    <a href="#bulkIOMethods">Bulk I/O Methods</a>
     * @see    <a href="#traversal">Traversing Directory Trees</a>
     */
    @ExpertFeature( level=INTERMEDIATE,
                    value=INJECTING_A_DIFFERENT_DETECTOR_FOR_THE_SAME_PATH_MAY_CORRUPT_DATA)
    @FsAssertion(consistent=YES)
    public static void mv(
            final File src,
            final File dst,
            final TArchiveDetector detector)
    throws IOException {
        final boolean srcArchived;
        final File srcDelegate;
        if (src instanceof TFile) {
            final TFile srcFile = (TFile) src;
            srcArchived = null != srcFile.getInnerArchive();
            srcDelegate = srcFile.getFile();
        } else {
            srcArchived = false;
            srcDelegate = src;
        }
        final boolean dstArchived;
        final File dstDelegate;
        if (dst instanceof TFile) {
            final TFile dstFile = (TFile) dst;
            dstArchived = null != dstFile.getInnerArchive();
            dstDelegate = dstFile.getFile();
        } else {
            dstArchived = false;
            dstDelegate = dst;
        }
        if (!srcArchived && !dstArchived) {
            if (srcDelegate.renameTo(dstDelegate)) return;
            throw new FileSystemException(src.getPath(), dst.getPath(), "Cannot rename.");
        }
        TBIO.mv(src, dst, detector);
    }

    /**
     * Copies the data from the input stream {@code in} to the output stream
     * {@code out} and closes both streams - even if an exception occurs.
     * <p>
     * This is a high performance implementation which uses a pooled background
     * thread to fill a FIFO of pooled buffers which is concurrently flushed by
     * the current thread.
     * It performs best when used with <em>unbuffered</em> streams.
     *
     * <table>
     * <caption>Feature Matrix</caption>
     * <thead>
     * <tr>
     *   <th>Feature</th>
     *   <th>Supported</th>
     * </tr>
     * </thead>
     * <tbody>
     * <tr>
     *   <td>Preserves file attributes</td>
     *   <td>n/a</td>
     * </tr>
     * <tr>
     *   <td>Copies directories recursively</td>
     *   <td>n/a</td>
     * </tr>
     * <tr>
     *   <td>Reads and overwrites special files</td>
     *   <td>n/a</td>
     * </tr>
     * <tr>
     *   <td>Closes parameter stream(s)</td>
     *   <td>Always</td>
     * </tr>
     * <tr>
     *   <td><a href="#RDC">Raw Data Copying (RDC)</a></td>
     *   <td>n/a</td>
     * </tr>
     * <tr>
     *   <td>Deletes partial written files on failure</td>
     *   <td>n/a</td>
     * </tr>
     * <tr>
     *   <td>Deletes partial written directories on failure</td>
     *   <td>n/a</td>
     * </tr>
     * </tbody>
     * </table>
     *
     * @param  in the input stream.
     * @param  out the output stream.
     * @throws IOException if any I/O error occurs.
     * @see    #cat(InputStream, OutputStream)
     * @see    <a href="#bulkIOMethods">Bulk I/O Methods</a>
     */
    @FsAssertion(atomic=NO, consistent=YES, isolated=NO)
    public static void cp(  final @WillClose InputStream in,
                            final @WillClose OutputStream out)
    throws IOException {
        Streams.copy(in, out);
    }

    /**
     * Copies the input stream {@code in} to the file {@code dst} and
     * closes the stream - even if an exception occurs.
     *
     * <table>
     * <caption>Feature Matrix</caption>
     * <thead>
     * <tr>
     *   <th>Feature</th>
     *   <th>Supported</th>
     * </tr>
     * </thead>
     * <tbody>
     * <tr>
     *   <td>Preserves file attributes</td>
     *   <td>None</td>
     * </tr>
     * <tr>
     *   <td>Copies directories recursively</td>
     *   <td>No</td>
     * </tr>
     * <tr>
     *   <td>Reads and overwrites special files</td>
     *   <td>Yes</td>
     * </tr>
     * <tr>
     *   <td>Closes parameter stream(s)</td>
     *   <td>Always</td>
     * </tr>
     * <tr>
     *   <td><a href="#RDC">Raw Data Copying (RDC)</a></td>
     *   <td>No</td>
     * </tr>
     * <tr>
     *   <td>Deletes partial written files on failure</td>
     *   <td>Yes</td>
     * </tr>
     * <tr>
     *   <td>Deletes partial written directories on failure</td>
     *   <td>n/a</td>
     * </tr>
     * </tbody>
     * </table>
     *
     * @param  in the input stream.
     * @param  dst the destination file.
     *         Note that although this just needs to be a plain {@code File},
     *         archive files and entries are only supported for instances of
     *         this class.
     * @throws IOException if any I/O error occurs.
     * @see    <a href="#bulkIOMethods">Bulk I/O Methods</a>
     */
    @FsAssertion(atomic=NO, consistent=YES, isolated=NO)
    public static void cp(final @WillClose InputStream in, final File dst)
    throws IOException {
        Objects.requireNonNull(in);

        @WillClose TFileOutputStream out = null;
        try {
            out = new TFileOutputStream(dst);
        } finally {
            if (null == out) // exception?
                in.close();
        }

        try {
            cp(in, out);
        } catch (final IOException ex) {
            rm(dst);
            throw ex;
        }
    }

    /**
     * Copies the file {@code src} to the output stream {@code out} and
     * closes the stream - even if an exception occurs.
     *
     * <table>
     * <caption>Feature Matrix</caption>
     * <thead>
     * <tr>
     *   <th>Feature</th>
     *   <th>Supported</th>
     * </tr>
     * </thead>
     * <tbody>
     * <tr>
     *   <td>Preserves file attributes</td>
     *   <td>None</td>
     * </tr>
     * <tr>
     *   <td>Copies directories recursively</td>
     *   <td>No</td>
     * </tr>
     * <tr>
     *   <td>Reads and overwrites special files</td>
     *   <td>Yes</td>
     * </tr>
     * <tr>
     *   <td>Closes parameter stream(s)</td>
     *   <td>Always</td>
     * </tr>
     * <tr>
     *   <td><a href="#RDC">Raw Data Copying (RDC)</a></td>
     *   <td>No</td>
     * </tr>
     * <tr>
     *   <td>Deletes partial written files on failure</td>
     *   <td>n/a</td>
     * </tr>
     * <tr>
     *   <td>Deletes partial written directories on failure</td>
     *   <td>n/a</td>
     * </tr>
     * </tbody>
     * </table>
     *
     * @param  src the source file.
     *         Note that although this just needs to be a plain {@code File},
     *         archive files and entries are only supported for instances of
     *         this class.
     * @param  out the output stream.
     * @throws IOException if any I/O error occurs.
     * @see    <a href="#bulkIOMethods">Bulk I/O Methods</a>
     */
    @FsAssertion(atomic=NO, consistent=YES, isolated=NO)
    public static void cp(final File src, final @WillClose OutputStream out)
    throws IOException {
        Objects.requireNonNull(out);

        @WillClose TFileInputStream in = null;
        try {
            in = new TFileInputStream(src);
        } finally {
            if (null == in) // exception?
                out.close();
        }
        cp(in, out);
    }

    /**
     * Equivalent to {@link #cp(File, File) cp(this, dst)}.
     *
     * @param  dst the destination file.
     *         Note that although this just needs to be a plain {@code File},
     *         archive files and entries are only supported for instances of
     *         this class.
     * @return {@code this}
     * @throws IOException if any I/O error occurs.
     */
    @FsAssertion(atomic=NO, consistent=YES, isolated=NO)
    public TFile cp(File dst) throws IOException {
        TBIO.cp(false, this, dst);
        return this;
    }

    /**
     * Copies the file {@code src} to the file {@code dst}.
     *
     * <table>
     * <caption>Feature Matrix</caption>
     * <thead>
     * <tr>
     *   <th>Feature</th>
     *   <th>Supported</th>
     * </tr>
     * </thead>
     * <tbody>
     * <tr>
     *   <td>Preserves file attributes</td>
     *   <td>None</td>
     * </tr>
     * <tr>
     *   <td>Copies directories recursively</td>
     *   <td>No</td>
     * </tr>
     * <tr>
     *   <td>Reads and overwrites special files</td>
     *   <td>Yes</td>
     * </tr>
     * <tr>
     *   <td>Closes parameter stream(s)</td>
     *   <td>n/a</td>
     * </tr>
     * <tr>
     *   <td><a href="#RDC">Raw Data Copying (RDC)</a></td>
     *   <td>Yes</td>
     * </tr>
     * <tr>
     *   <td>Deletes partial written files on failure</td>
     *   <td>Yes</td>
     * </tr>
     * <tr>
     *   <td>Deletes partial written directories on failure</td>
     *   <td>n/a</td>
     * </tr>
     * </tbody>
     * </table>
     *
     * @param  src the source file.
     *         Note that although this just needs to be a plain {@code File},
     *         archive files and entries are only supported for instances of
     *         this class.
     * @param  dst the destination file.
     *         Note that although this just needs to be a plain {@code File},
     *         archive files and entries are only supported for instances of
     *         this class.
     * @throws IOException if any I/O error occurs.
     * @see    <a href="#bulkIOMethods">Bulk I/O Methods</a>
     */
    @FsAssertion(atomic=NO, consistent=YES, isolated=NO)
    public static void cp(File src, File dst) throws IOException {
        TBIO.cp(false, src, dst);
    }

    /**
     * Equivalent to {@link #cp_p(File, File) cp_p(this, dst)}.
     *
     * @param  dst the destination file.
     *         Note that although this just needs to be a plain {@code File},
     *         archive files and entries are only supported for instances of
     *         this class.
     * @return {@code this}
     * @throws IOException if any I/O error occurs.
     */
    @FsAssertion(atomic=NO, consistent=YES, isolated=NO)
    public TFile cp_p(File dst) throws IOException {
        TBIO.cp(true, this, dst);
        return this;
    }

    /**
     * Copies the file {@code src} to the file {@code dst} and attempts to
     * copy all attributes of the source file to the destination file, too.
     * Which attributes are actually copied is specific to the source and
     * destination file system driver implementations, but the minimum
     * guarantee is to copy the last modification time.
     *
     * <table>
     * <caption>Feature Matrix</caption>
     * <thead>
     * <tr>
     *   <th>Feature</th>
     *   <th>Supported</th>
     * </tr>
     * </thead>
     * <tbody>
     * <tr>
     *   <td>Preserves file attributes</td>
     *   <td>Best effort</td>
     * </tr>
     * <tr>
     *   <td>Copies directories recursively</td>
     *   <td>No</td>
     * </tr>
     * <tr>
     *   <td>Reads and overwrites special files</td>
     *   <td>Yes</td>
     * </tr>
     * <tr>
     *   <td>Closes parameter stream(s)</td>
     *   <td>n/a</td>
     * </tr>
     * <tr>
     *   <td><a href="#RDC">Raw Data Copying (RDC)</a></td>
     *   <td>Yes</td>
     * </tr>
     * <tr>
     *   <td>Deletes partial written files on failure</td>
     *   <td>Yes</td>
     * </tr>
     * <tr>
     *   <td>Deletes partial written directories on failure</td>
     *   <td>n/a</td>
     * </tr>
     * </tbody>
     * </table>
     *
     * @param  src the source file.
     *         Note that although this just needs to be a plain {@code File},
     *         archive files and entries are only supported for instances of
     *         this class.
     * @param  dst the destination file.
     *         Note that although this just needs to be a plain {@code File},
     *         archive files and entries are only supported for instances of
     *         this class.
     * @throws IOException if any I/O error occurs.
     * @see    <a href="#bulkIOMethods">Bulk I/O Methods</a>
     */
    @FsAssertion(atomic=NO, consistent=YES, isolated=NO)
    public static void cp_p(File src, File dst) throws IOException {
        TBIO.cp(true, src, dst);
    }

    /**
     * Equivalent to
     * {@link #cp_r(File, File, TArchiveDetector, TArchiveDetector) cp_r(this, dst, detector, detector)},
     * where {@code detector} is {@code TConfig.current().getArchiveDetector()}.
     *
     * @param  dst the destination file or directory tree.
     *         Note that although this just needs to be a plain {@code File},
     *         archive files and entries are only supported for instances of
     *         this class.
     * @return {@code this}
     * @throws IOException if any I/O error occurs.
     * @see    <a href="#bulkIOMethods">Bulk I/O Methods</a>
     * @see    <a href="#traversal">Traversing Directory Trees</a>
     */
    @FsAssertion(atomic=NO, consistent=YES, isolated=NO)
    public TFile cp_r(File dst) throws IOException {
        final TArchiveDetector detector = TConfig.current().getArchiveDetector();
        TBIO.cp_r(false, this, dst, detector, detector);
        return this;
    }

    /**
     * Equivalent to
     * {@link #cp_r(File, File, TArchiveDetector, TArchiveDetector) cp_r(this, dst, detector, detector)}.
     *
     * @param  src the source file or directory tree.
     *         Note that although this just needs to be a plain {@code File},
     *         archive files and entries are only supported for instances of
     *         this class.
     * @param  dst the destination file or directory tree.
     *         Note that although this just needs to be a plain {@code File},
     *         archive files and entries are only supported for instances of
     *         this class.
     * @param  detector the archive detector to use for detecting any archive
     *         files <em>within</em> the source and destination directory
     *         trees.
     * @throws IOException if any I/O error occurs.
     * @since  TrueZIP 7.2
     * @see    <a href="#bulkIOMethods">Bulk I/O Methods</a>
     * @see    <a href="#traversal">Traversing Directory Trees</a>
     */
    @ExpertFeature( level=INTERMEDIATE,
                    value=INJECTING_A_DIFFERENT_DETECTOR_FOR_THE_SAME_PATH_MAY_CORRUPT_DATA)
    @FsAssertion(atomic=NO, consistent=YES, isolated=NO)
    public static void cp_r(File src, File dst, TArchiveDetector detector)
    throws IOException {
        TBIO.cp_r(false, src, dst, detector, detector);
    }

    /**
     * Recursively copies the file or directory {@code src}
     * to the file or directory {@code dst}.
     *
     * <table>
     * <caption>Feature Matrix</caption>
     * <thead>
     * <tr>
     *   <th>Feature</th>
     *   <th>Supported</th>
     * </tr>
     * </thead>
     * <tbody>
     * <tr>
     *   <td>Preserves file attributes</td>
     *   <td>None</td>
     * </tr>
     * <tr>
     *   <td>Copies directories recursively</td>
     *   <td>Yes</td>
     * </tr>
     * <tr>
     *   <td>Reads and overwrites special files</td>
     *   <td>No</td>
     * </tr>
     * <tr>
     *   <td>Closes parameter stream(s)</td>
     *   <td>n/a</td>
     * </tr>
     * <tr>
     *   <td><a href="#RDC">Raw Data Copying (RDC)</a></td>
     *   <td>Yes</td>
     * </tr>
     * <tr>
     *   <td>Deletes partial written files on failure</td>
     *   <td>Yes</td>
     * </tr>
     * <tr>
     *   <td>Deletes partial written directories on failure</td>
     *   <td>No</td>
     * </tr>
     * </tbody>
     * </table>
     *
     * @param  src the source file or directory tree.
     *         Note that although this just needs to be a plain {@code File},
     *         archive files and entries are only supported for instances of
     *         this class.
     * @param  dst the destination file or directory tree.
     *         Note that although this just needs to be a plain {@code File},
     *         archive files and entries are only supported for instances of
     *         this class.
     * @param  srcDetector the archive detector to use for detecting any
     *         archive files <em>within</em> the source directory tree.
     * @param  dstDetector the archive detector to use for detecting any
     *         archive files <em>within</em> the destination directory tree.
     * @throws IOException if any I/O error occurs.
     * @see    <a href="#bulkIOMethods">Bulk I/O Methods</a>
     * @see    <a href="#traversal">Traversing Directory Trees</a>
     */
    @ExpertFeature( level=INTERMEDIATE,
                    value=INJECTING_A_DIFFERENT_DETECTOR_FOR_THE_SAME_PATH_MAY_CORRUPT_DATA)
    @FsAssertion(atomic=NO, consistent=YES, isolated=NO)
    public static void cp_r(
            File src,
            File dst,
            TArchiveDetector srcDetector,
            TArchiveDetector dstDetector)
    throws IOException {
        TBIO.cp_r(false, src, dst, srcDetector, dstDetector);
    }

    /**
     * Equivalent to
     * {@link #cp_rp(File, File, TArchiveDetector, TArchiveDetector) cp_rp(this, dst, detector, detector)},
     * where {@code detector} is {@code TConfig.current().getArchiveDetector()}.
     *
     * @param  dst the destination file or directory tree.
     *         Note that although this just needs to be a plain {@code File},
     *         archive files and entries are only supported for instances of
     *         this class.
     * @return {@code this}
     * @throws IOException if any I/O error occurs.
     * @see    <a href="#bulkIOMethods">Bulk I/O Methods</a>
     * @see    <a href="#traversal">Traversing Directory Trees</a>
     */
    @FsAssertion(atomic=NO, consistent=YES, isolated=NO)
    public TFile cp_rp(File dst) throws IOException {
        final TArchiveDetector detector = TConfig.current().getArchiveDetector();
        TBIO.cp_r(true, this, dst, detector, detector);
        return this;
    }

    /**
     * Equivalent to
     * {@link #cp_rp(File, File, TArchiveDetector, TArchiveDetector) cp_rp(this, dst, detector, detector)}.
     *
     * @param  src the source file or directory tree.
     *         Note that although this just needs to be a plain {@code File},
     *         archive files and entries are only supported for instances of
     *         this class.
     * @param  dst the destination file or directory tree.
     *         Note that although this just needs to be a plain {@code File},
     *         archive files and entries are only supported for instances of
     *         this class.
     * @param  detector the archive detector to use for detecting any archive
     *         files <em>within</em> the source and destination directory
     *         trees.
     * @throws IOException if any I/O error occurs.
     * @since  TrueZIP 7.2
     * @see    <a href="#bulkIOMethods">Bulk I/O Methods</a>
     * @see    <a href="#traversal">Traversing Directory Trees</a>
     */
    @ExpertFeature( level=INTERMEDIATE,
                    value=INJECTING_A_DIFFERENT_DETECTOR_FOR_THE_SAME_PATH_MAY_CORRUPT_DATA)
    @FsAssertion(atomic=NO, consistent=YES, isolated=NO)
    public static void cp_rp(File src, File dst, TArchiveDetector detector)
    throws IOException {
        TBIO.cp_r(true, src, dst, detector, detector);
    }

    /**
     * Recursively copies the file or directory {@code src} to the file or
     * directory {@code dst} and attempts to copy all attributes of each
     * source file to the destination file, too.
     * Which attributes are actually copied is specific to the source and
     * destination file system driver implementations, but the minimum
     * guarantee is to copy the last modification time - except for archive
     * files.
     *
     * <table>
     * <caption>Feature Matrix</caption>
     * <thead>
     * <tr>
     *   <th>Feature</th>
     *   <th>Supported</th>
     * </tr>
     * </thead>
     * <tbody>
     * <tr>
     *   <td>Preserves file attributes</td>
     *   <td>Best effort</td>
     * </tr>
     * <tr>
     *   <td>Copies directories recursively</td>
     *   <td>Yes</td>
     * </tr>
     * <tr>
     *   <td>Reads and overwrites special files</td>
     *   <td>No</td>
     * </tr>
     * <tr>
     *   <td>Closes parameter stream(s)</td>
     *   <td>n/a</td>
     * </tr>
     * <tr>
     *   <td><a href="#RDC">Raw Data Copying (RDC)</a></td>
     *   <td>Yes</td>
     * </tr>
     * <tr>
     *   <td>Deletes partial written files on failure</td>
     *   <td>Yes</td>
     * </tr>
     * <tr>
     *   <td>Deletes partial written directories on failure</td>
     *   <td>No</td>
     * </tr>
     * </tbody>
     * </table>
     *
     * @param  src the source file or directory tree.
     *         Note that although this just needs to be a plain {@code File},
     *         archive files and entries are only supported for instances of
     *         this class.
     * @param  dst the destination file or directory tree.
     *         Note that although this just needs to be a plain {@code File},
     *         archive files and entries are only supported for instances of
     *         this class.
     * @param  srcDetector the archive detector to use for detecting any
     *         archive files <em>within</em> the source directory tree.
     * @param  dstDetector the archive detector to use for detecting any
     *         archive files <em>within</em> the destination directory tree.
     * @throws IOException if any I/O error occurs.
     * @see    <a href="#bulkIOMethods">Bulk I/O Methods</a>
     * @see    <a href="#traversal">Traversing Directory Trees</a>
     */
    @ExpertFeature( level=INTERMEDIATE,
                    value=INJECTING_A_DIFFERENT_DETECTOR_FOR_THE_SAME_PATH_MAY_CORRUPT_DATA)
    @FsAssertion(atomic=NO, consistent=YES, isolated=NO)
    public static void cp_rp(
            File src,
            File dst,
            TArchiveDetector srcDetector,
            TArchiveDetector dstDetector)
    throws IOException {
        TBIO.cp_r(true, src, dst, srcDetector, dstDetector);
    }

    /**
     * Copies the input stream {@code in} to this file or entry in an archive
     * file
     * <em>without</em> closing the stream.
     *
     * <table>
     * <caption>Feature Matrix</caption>
     * <thead>
     * <tr>
     *   <th>Feature</th>
     *   <th>Supported</th>
     * </tr>
     * </thead>
     * <tbody>
     * <tr>
     *   <td>Preserves file attributes</td>
     *   <td>n/a</td>
     * </tr>
     * <tr>
     *   <td>Copies directories recursively</td>
     *   <td>n/a</td>
     * </tr>
     * <tr>
     *   <td>Reads and overwrites special files</td>
     *   <td>Yes</td>
     * </tr>
     * <tr>
     *   <td>Closes parameter stream(s)</td>
     *   <td>Never</td>
     * </tr>
     * <tr>
     *   <td><a href="#RDC">Raw Data Copying (RDC)</a></td>
     *   <td>n/a</td>
     * </tr>
     * <tr>
     *   <td>Deletes partial written files on failure</td>
     *   <td>Yes</td>
     * </tr>
     * <tr>
     *   <td>Deletes partial written directories on failure</td>
     *   <td>n/a</td>
     * </tr>
     * </tbody>
     * </table>
     *
     * @param  in the input stream.
     * @throws IOException if any I/O error occurs.
     * @see    <a href="#bulkIOMethods">Bulk I/O Methods</a>
     */
    @FsAssertion(atomic=NO, consistent=YES, isolated=NO)
    public void input(final @WillNotClose InputStream in) throws IOException {
        Objects.requireNonNull(in);
        try {
            try (TFileOutputStream out = new TFileOutputStream(this)) {
                Streams.cat(in, out);
            }
        } catch (final IOException ex) {
            rm(this);
            throw ex;
        }
    }

    /**
     * Copies this file or entry in an archive file to the output stream
     * {@code out}
     * <em>without</em> closing the stream.
     *
     * <table>
     * <caption>Feature Matrix</caption>
     * <thead>
     * <tr>
     *   <th>Feature</th>
     *   <th>Supported</th>
     * </tr>
     * </thead>
     * <tbody>
     * <tr>
     *   <td>Preserves file attributes</td>
     *   <td>n/a</td>
     * </tr>
     * <tr>
     *   <td>Copies directories recursively</td>
     *   <td>n/a</td>
     * </tr>
     * <tr>
     *   <td>Reads and overwrites special files</td>
     *   <td>Yes</td>
     * </tr>
     * <tr>
     *   <td>Closes parameter stream(s)</td>
     *   <td>Never</td>
     * </tr>
     * <tr>
     *   <td><a href="#RDC">Raw Data Copying (RDC)</a></td>
     *   <td>n/a</td>
     * </tr>
     * <tr>
     *   <td>Deletes partial written files on failure</td>
     *   <td>n/a</td>
     * </tr>
     * <tr>
     *   <td>Deletes partial written directories on failure</td>
     *   <td>n/a</td>
     * </tr>
     * </tbody>
     * </table>
     *
     * @param  out the output stream.
     * @throws IOException if any I/O error occurs.
     * @see    <a href="#bulkIOMethods">Bulk I/O Methods</a>
     */
    @FsAssertion(atomic=NO, consistent=YES, isolated=NO, durable=NOT_APPLICABLE)
    public void output(final @WillNotClose OutputStream out) throws IOException {
        Objects.requireNonNull(out);
        try (TFileInputStream in = new TFileInputStream(this)) {
            Streams.cat(in, out);
        }
    }

    /**
     * Copies the data from the given input stream to the given output stream
     * <em>without</em> closing them.
     * The name of this method is inspired by the Unix command line utility
     * {@code cat} because you could use it to con<em>cat</em>enate the
     * contents of multiple streams.
     * <p>
     * This is a high performance implementation which uses a pooled background
     * thread to fill a FIFO of data buffers which is concurrently flushed by
     * the current thread.
     * It performs best when used with <em>unbuffered</em> streams.
     *
     * <table>
     * <caption>Feature Matrix</caption>
     * <thead>
     * <tr>
     *   <th>Feature</th>
     *   <th>Supported</th>
     * </tr>
     * </thead>
     * <tbody>
     * <tr>
     *   <td>Preserves file attributes</td>
     *   <td>n/a</td>
     * </tr>
     * <tr>
     *   <td>Copies directories recursively</td>
     *   <td>n/a</td>
     * </tr>
     * <tr>
     *   <td>Reads and overwrites special files</td>
     *   <td>n/a</td>
     * </tr>
     * <tr>
     *   <td>Closes parameter stream(s)</td>
     *   <td>Never</td>
     * </tr>
     * <tr>
     *   <td><a href="#RDC">Raw Data Copying (RDC)</a></td>
     *   <td>n/a</td>
     * </tr>
     * <tr>
     *   <td>Deletes partial written files on failure</td>
     *   <td>n/a</td>
     * </tr>
     * <tr>
     *   <td>Deletes partial written directories on failure</td>
     *   <td>n/a</td>
     * </tr>
     * </tbody>
     * </table>
     *
     * @param  in the input stream.
     * @param  out the output stream.
     * @throws IOException if any I/O error occurs.
     * @see    #cp(InputStream, OutputStream)
     * @see    <a href="#bulkIOMethods">Bulk I/O Methods</a>
     */
    @FsAssertion(atomic=NO, consistent=YES, isolated=NO)
    public static void cat( @WillNotClose InputStream in,
                            @WillNotClose OutputStream out)
    throws IOException {
        Streams.cat(in, out);
    }

    /**
     * Compacts this archive file by removing any redundant archive entry
     * contents and meta data, including central directories.
     * If this file isn't a
     * {@linkplain #isTopLevelArchive() top level archive file},
     * then this operation does nothing and returns immediately.
     * <p>
     * This operation is intended to compact archive files which have been
     * frequently updated with {@link FsAccessOption#GROW} or similar means.
     * If this access option is set and an archive file is updated frequently,
     * then over time a lot of redundant artifacts such as archive entry
     * contents and meta data, including central directories may be physically
     * present in the archive file, even if all its entries have been deleted.
     * This operation should then get used to remove any redundant artifacts
     * and reclaim the storage space.
     * <p>
     * Mind that this operation has no means to detect if there is actually any
     * redundant data present in this archive file.
     * Any invocation will perform exactly the same steps, so if this archive
     * file is already compact, then this will just waste time and temporary
     * space in the platform file system.
     * <p>
     * Note that this operation is not thread-safe and hence not isolated, so
     * you should not concurrently access this archive file or any of its
     * entries!
     * <p>
     * This operation performs in the order of <i>O(s)</i>, where <i>s</i> is
     * the total size of the archive file either before (worst case) or after
     * (best case) compacting it.
     * If this archive file has already been mounted, then <i>s</i> is the
     * total size of the archive file after compacting it (best case).
     * Otherwise, the definition of <i>s</i> is specific to the archive file
     * system driver.
     * Usually, if the archive file contains a central directory, you could
     * expect the best case, otherwise the worst case, but this information
     * is given without warranty.
     * <p>
     * If this archive file has been successfully compacted, then it's left
     * unmounted, so any subsequent operation will mount it again, which
     * requires additional time.
     *
     * @return this
     * @throws IOException On any I/O error.
     * @see    FsAccessOption#GROW
     */
    @FsAssertion(atomic=YES, consistent=YES, isolated=NO, durable=YES)
    public TFile compact() throws IOException {
        // See http://java.net/jira/browse/TRUEZIP-205 .
        if (isTopLevelArchive()) compact(this);
        return this;
    }

    private static void compact(TFile grown) throws IOException {
        grown = grown.getNormalizedFile();
        assert grown.isArchive();

        final File dir = parent(grown);
        final @Nullable String extension = extension(grown);
        try (final TConfig config = TConfig.open()) {
            config.setAccessPreference(GROW, false);

            // Create temp file.
            final TFile compact = new TFile(createTempFile("tzp", extension, dir));
            compact.rm();
            try {
                // Make a structural copy of the grown archive file, thereby
                // compacting it.
                grown.cp_rp(compact);

                // Unmount both archive files so we can delete and move them
                // safely and fast like regular files.
                TVFS.umount(grown);
                TVFS.umount(compact);

                // Move the compacted archive file over to the grown archive
                // file like a regular file.
                if (!move(compact.toNonArchiveFile(), grown.toNonArchiveFile()))
                    throw new FileSystemException(compact.getPath(), grown.getPath(), "Cannot move!");
            } catch (final IOException ex) {
                try {
                    compact.rm();
                } catch (final IOException ex2) {
                    ex.addSuppressed(ex2);
                }
                throw ex;
            }
        }
    }

    private static File parent(final File file) {
        final File parent = file.getParentFile();
        return null != parent ? parent : CURRENT_DIRECTORY;
    }

    private static @Nullable String extension(final TFile file) {
        final FsScheme scheme = file.getScheme();
        return null != scheme ? "." + scheme : null;
    }

    private static boolean move(File src, File dst) {
        return src.exists()
                && (!dst.exists() || dst.delete())
                && src.renameTo(dst);
    }
}
