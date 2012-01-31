/*
 * Copyright 2004-2012 Schlichtherle IT Services
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.schlichtherle.truezip.file;

import static de.schlichtherle.truezip.fs.FsInputOptions.INPUT_PREFERENCES_MASK;
import static de.schlichtherle.truezip.fs.FsInputOptions.NO_INPUT_OPTIONS;
import static de.schlichtherle.truezip.fs.FsOutputOption.*;
import de.schlichtherle.truezip.fs.*;
import static de.schlichtherle.truezip.fs.FsOutputOptions.OUTPUT_PREFERENCES_MASK;
import de.schlichtherle.truezip.fs.sl.FsManagerLocator;
import de.schlichtherle.truezip.util.BitField;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.Closeable;
import java.util.Deque;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import net.jcip.annotations.ThreadSafe;

/**
 * A container for configuration options with global or inheritable thread
 * local scope.
 * <p>
 * A client application can use {@link #push()} to create a new
 * <i>inheritable thread local configuration</i> by copying the
 * <i>current configuration</i> and pushing the copy on top of the inheritable
 * thread local stack.
 * <p>
 * Later, the client application can use {@link #pop()} or {@link #close()} to
 * pop the current configuration or {@code this} configuration respectively
 * off the top of the inheritable thread local stack.
 * <p>
 * Finally, a client application can use {@link #get()} to get access to the
 * current configuration.
 * If no configuration has been pushed on the inheritable thread local stack
 * before, the <i>global configuration</i> is returned.
 * <p>
 * Whenever a child thread is started, it shares the current configuration
 * with its parent thread.
 * Mind that access to the global configuration is <em>not</em> synchronized.
 * 
 * <a name="examples"/><h3>Examples</h3>
 *
 * <a name="global"/><h4>Changing The Global Configuration</h4>
 * <p>
 * If the thread local configuration stack is empty, i.e. no {@link #push()}
 * without a corresponding {@link #close()} or {@link #pop()} has been called
 * before, then the {@link #get()} method will return the global configuration.
 * This feature is intended to get used during the application setup to change
 * some configuration options with global scope like this:
 * <pre>{@code
class MyApplication extends TApplication<IOException> {

    \@Override
    protected void setup() {
        // This should obtain the global configuration.
        TConfig config = TConfig.get();
        // Configure custom application file format.
        config.setArchiveDetector(new TArchiveDetector("aff",
                new JarDriver(IOPoolLocator.SINGLETON)));
        // Set FsOutputOption.GROW for appending-to rather than reassembling
        // existing archive files.
        config.setOutputPreferences(
                config.getOutputPreferences.set(FsOutputOption.GROW));
    }

    ...
}
 * }</pre>
 * 
 * <a name="local"/><h4>Setting The Default Archive Detector In The Current Thread</h4>
 * <p>
 * If an application needs to change the configuration of just the current
 * thread rather than changing the global configuration, then the
 * {@link #push()} method needs to get called like this:
 * <pre>{@code
TFile file1 = new TFile("file.aff");
assert !file1.isArchive();

// First, push a new current configuration onto the inheritable thread local
// stack.
TConfig config = TConfig.push();
try {
    // Configure custom application file format "aff".
    config.setArchiveDetector(new TArchiveDetector("aff",
            new JarDriver(IOPoolLocator.SINGLETON)));

    // Now use the current configuration.
    TFile file2 = new TFile("file.aff");
    assert file2.isArchive();
    // Do some I/O here.
    ...
} finally {
    // Pop the current configuration off the inheritable thread local stack.
    config.close();
}
 * }</pre>
 * <p>
 * Using try-with-resources in JSE&nbsp;7, this can get shortened to:
 * <pre>{@code
TFile file1 = new TFile("file.aff");
assert !file1.isArchive();

// First, push a new current configuration onto the inheritable thread local
// stack.
try (TConfig config = TConfig.push()) {
    // Configure custom application file format "aff".
    config.setArchiveDetector(new TArchiveDetector("aff",
            new JarDriver(IOPoolLocator.SINGLETON)));

    // Now use the current configuration.
    TFile file2 = new TFile("file.aff");
    assert file2.isArchive();
    // Do some I/O here.
    ...
}
 * }</pre>
 *
 * <a name="appending"/><h4>Appending To Archive Files In The Current Thread</h4>
 * <p>
 * By default, TrueZIP is configured to produce the smallest possible archive
 * files.
 * This is achieved by selecting the maximum compression ratio in the archive
 * drivers and by performing an archive update whenever an existing archive
 * entry is going to get overwritten with new contents or updated with new meta
 * data in order to avoid the writing of redundant data to the resulting
 * archive file.
 * An archive update is basically a copy operation where all archive entries
 * which haven't been written yet get copied from the input archive file to the
 * output archive file.
 * However, while this strategy produces the smallest possible archive files,
 * it may yield bad performance if the number and contents of the archive
 * entries to create or update are pretty small compared to the total size of
 * the resulting archive file.
 * <p>
 * Therefore, you can change this strategy by setting the
 * {@link FsOutputOption#GROW} output option preference when writing archive
 * entry contents or updating their meta data.
 * When set, this output option allows archive files to grow by appending new
 * or updated archive entries to their end and inhibiting archive update
 * operations.
 * You can set this preference in the global configuration as shown above or
 * you can set it on a case-by-case basis as follows:
 * <pre>{@code
// We are going to append "entry" to "archive.zip".
TFile file = new TFile("archive.zip/entry");

// First, push a new current configuration on the inheritable thread local
// stack.
TConfig config = TConfig.push();
try {
    // Set FsOutputOption.GROW for appending-to rather than reassembling
    // existing archive files.
    config.setOutputPreferences(
            config.getOutputPreferences.set(FsOutputOption.GROW));

    // Now use the current configuration and append the entry to the archive
    // file even if it's already present.
    TFileOutputStream out = new TFileOutputStream(file);
    try {
        // Do some output here.
        ...
    } finally {
        out.close();
    }
} finally {
    // Pop the current configuration off the inheritable thread local stack.
    config.close();
}
 * }</pre>
 * <p>
 * Note that it's specific to the archive file system driver if this output
 * option preference is supported or not.
 * If it's not supported, then it gets silently ignored, thereby falling back
 * to the default strategy of performing a full archive update whenever
 * required to avoid writing redundant archive entry data.
 * <p>
 * As of TrueZIP 7.5, the support is like this:
 * <ul>
 * <li>The drivers of the module TrueZIP Driver ZIP fully support this output
 *     option preference, so it's available for EAR, JAR, WAR, ZIP etc.</li>
 * <li>The drivers of the module TrueZIP Driver ZIP.RAES only allow redundant
 *     archive entry contents and meta data.
 *     You cannot append to an existing ZIP.RAES file, however.</li>
 * <li>The drivers of the module TrueZIP Driver TAR only allow redundant
 *     archive entry contents.
 *     You cannot append to an existing TAR file, however.</li>
 * </ul>
 * 
 * <a name="unit-testing"/><h4>Unit Testing</h4>
 * <p>
 * Using the thread local inheritable configuration stack comes in handy when
 * unit testing, e.g. with JUnit. Consider this pattern:
 * <pre>{@code
public class AppTest {

    \@Before
    public void setUp() {
        TConfig config = TConfig.push();
        // Let's just recognize ZIP files.
        config.setArchiveDetector(new TArchiveDetector("zip"));
    }

    \@After
    public void shutDown() {
        TConfig.pop();
    }

    \@Test
    public void testMethod() {
        // Test accessing some ZIP files here.
        ...
    }
}
 * }</pre>
 * <p>
 * Note that it's not necessary to save the reference to the new pushed
 * configuration in {@code setUp()}.
 * {@code shutDown()} will just pop the top configuration off the inheritable
 * thread local configuration stack.
 * <p>
 * The most important feature of this code is that it's thread-safe, which
 * enables you to run your unit tests in parallel!
 * 
 * @since   TrueZIP 7.2
 * @author  Christian Schlichtherle
 * @version $Id$
 */
@ThreadSafe
@DefaultAnnotation(NonNull.class)
public final class TConfig implements Closeable {

    /**
     * The default value of the
     * {@link #getInputPreferences input preferences} property, which is
     * {@link FsInputOptions#NO_INPUT_OPTIONS}.
     * 
     * @since TrueZIP 7.3
     */
    public static final BitField<FsInputOption>
            DEFAULT_INPUT_PREFERENCES = NO_INPUT_OPTIONS;

    private static final BitField<FsInputOption>
            INPUT_PREFERENCES_COMPLEMENT_MASK = INPUT_PREFERENCES_MASK.not();

    /**
     * The default value of the
     * {@link #getOutputPreferences output preferences} property, which is
     * <code>{@link BitField}.of({@link FsOutputOption#CREATE_PARENTS})</code>.
     * 
     * @since TrueZIP 7.3
     */
    public static final BitField<FsOutputOption>
            DEFAULT_OUTPUT_PREFERENCES = BitField.of(CREATE_PARENTS);

    private static final BitField<FsOutputOption>
            OUTPUT_PREFERENCES_COMPLEMENT_MASK = OUTPUT_PREFERENCES_MASK.not();

    private static final InheritableThreadLocalConfigStack configs
            = new InheritableThreadLocalConfigStack();

    private static final TConfig GLOBAL = new TConfig();

    // I don't think these fields should be volatile.
    // This would make a difference if and only if two threads were changing
    // the GLOBAL configuration concurrently, which is discouraged.
    // Instead, the global configuration should only changed once at
    // application startup and then each thread should modify only its thread
    // local configuration which has been obtained by a call to TConfig.push().
    private FsManager manager;
    private TArchiveDetector detector;
    private BitField<FsInputOption> inputPreferences;
    private BitField<FsOutputOption> outputPreferences;

    /**
     * Returns the current configuration.
     * First, this method peeks the inheritable thread local configuration stack.
     * If no configuration has been {@link #push() pushed} yet, the global
     * configuration is returned.
     * Mind that the global configuration is shared by all threads.
     * 
     * @return The current configuration.
     * @see    #push()
     */
    public static TConfig get() {
        final TConfig config = configs.get().peek();
        return null != config ? config : GLOBAL;
    }

    /**
     * Creates a new current configuration by copying the current configuration
     * and pushing the copy onto the inheritable thread local stack.
     * 
     * @return The new current configuration.
     * @see    #get()
     */
    public static TConfig push() {
        final TConfig config = new TConfig(get());
        configs.get().push(config);
        return config;
    }

    /**
     * Pops the {@link #get() current configuration} off the inheritable thread
     * local stack.
     * 
     * @throws IllegalStateException If the {@link #get() current configuration}
     *         is the global configuration.
     */
    public static void pop() {
        get().close();
    }

    /** Default constructor for the global configuration. */
    private TConfig() {
        this.manager = FsManagerLocator.SINGLETON.get();
        this.detector = TArchiveDetector.ALL;
        this.inputPreferences = DEFAULT_INPUT_PREFERENCES;
        this.outputPreferences = DEFAULT_OUTPUT_PREFERENCES;
    }

    /** Copy constructor for inheritable thread local configurations. */
    private TConfig(final TConfig template) {
        this.manager = template.getManager();
        this.detector = template.getArchiveDetector();
        this.inputPreferences = template.getInputPreferences();
        this.outputPreferences = template.getOutputPreferences();
    }

    /**
     * Returns the file system manager of the current configuration.
     * 
     * @return     The file system manager of the current configuration.
     * @deprecated This public class property is solely intended to be used by
     *             the TrueZIP&nbsp;Path module and should never be used by
     *             client applications.
     * @since      TrueZIP 7.5
     */
    @Deprecated
    public static FsManager getCurrentManager() {
        return get().getManager();
    }

    /**
     * Returns the file system manager.
     * 
     * @return The file system manager.
     */
    FsManager getManager() {
        return this.manager;
    }

    /**
     * Sets the file system manager.
     * 
     * @param manager The file system manager.
     */
    void setManager(final FsManager manager) {
        if (null == manager)
            throw new NullPointerException();
        this.manager = manager;
    }

    /**
     * Returns the value of the property {@code lenient}.
     *
     * @return The value of the property {@code lenient}.
     * @see    #setLenient(boolean)
     */
    public boolean isLenient() {
        return this.outputPreferences.get(CREATE_PARENTS);
    }

    /**
     * Sets the value of the property {@code lenient}.
     * This property controls whether archive files and their member
     * directories get automatically created whenever required.
     * By default, the value of this class property is {@code true}!
     * <p>
     * Consider the following path: {@code a/outer.zip/b/inner.zip/c}.
     * Now let's assume that {@code a} exists as a plain directory in the
     * platform file system, while all other segments of this path don't, and
     * that the module TrueZIP Driver ZIP is present on the run-time class path
     * in order to detect {@code outer.zip} and {@code inner.zip} as ZIP files
     * according to the initial setup.
     * <p>
     * Now, if this property is set to {@code false}, then an application
     * needs to call {@code new TFile("a/outer.zip/b/inner.zip").mkdirs()}
     * before it can actually create the innermost {@code c} entry as a file
     * or directory.
     * <p>
     * More formally, before an application can access an entry in a federated
     * file system, all its parent directories need to exist, including archive
     * files.
     * This emulates the behaviour of the platform file system.
     * <p>
     * If this property is set to {@code true} however, then any missing
     * parent directories (including archive files) up to the outermost archive
     * file {@code outer.zip} get automatically created when using operations
     * to create the innermost element of the path {@code c}.
     * <p>
     * This enables applications to succeed with doing this:
     * {@code new TFile("a/outer.zip/b/inner.zip/c").createNewFile()},
     * or that:
     * {@code new TFileOutputStream("a/outer.zip/b/inner.zip/c")}.
     * <p>
     * Note that in either case the parent directory of the outermost archive
     * file {@code a} must exist - TrueZIP does not automatically create
     * directories in the platform file system!
     *
     * @param lenient the value of the property {@code lenient}.
     * @see   #isLenient()
     */
    public void setLenient(final boolean lenient) {
        this.outputPreferences = this.outputPreferences
                .set(CREATE_PARENTS, lenient);
    }

    /**
     * Returns the default {@link TArchiveDetector} to use for scanning path
     * names for prospective archive files if no {@code TArchiveDetector} has
     * been explicitly provided to a constructor.
     *
     * @return The default {@link TArchiveDetector} to use for scanning
     *         path names for prospective archive files.
     * @see #setArchiveDetector
     */
    public TArchiveDetector getArchiveDetector() {
        return this.detector;
    }

    /**
     * Sets the default {@link TArchiveDetector} to use for scanning path
     * names for prospective archive files if no {@code TArchiveDetector} has
     * been explicitly provided to a {@link TFile} constructor.
     * Because the {@code TFile} class is immutable, changing the value of this
     * property will affect the scanning of path names of subsequently
     * constructed {@code TFile} objects only - existing {@code TFile} objects
     * will <em>not</em> be affected.
     *
     * @param detector the default {@link TArchiveDetector} to use for scanning
     *        path names for prospective archive files.
     * @see   #getArchiveDetector()
     */
    public void setArchiveDetector(final TArchiveDetector detector) {
        if (null == detector)
            throw new NullPointerException();
        this.detector = detector;
    }

    /**
     * Returns the input preferences.
     * 
     * @return The input preferences.
     * @since  TrueZIP 7.3
     */
    public BitField<FsInputOption> getInputPreferences() {
        return this.inputPreferences;
    }

    /**
     * Sets the input preferences.
     * These preferences are usually not cached, so changing them should take
     * effect immediately.
     * 
     * @param  preferences the input preferences.
     * @throws IllegalArgumentException if an option is present in
     *         {@code preferences} which is not present in
     *         {@link FsInputOptions#INPUT_PREFERENCES_MASK}.
     * @since  TrueZIP 7.3
     */
    public void setInputPreferences(final BitField<FsInputOption> preferences) {
        final BitField<FsInputOption> illegal = preferences
                .and(INPUT_PREFERENCES_COMPLEMENT_MASK);
        if (!illegal.isEmpty())
            throw new IllegalArgumentException(preferences + " (illegal input preferences)");
        this.inputPreferences = preferences;
    }

    /**
     * Returns the output preferences.
     * 
     * @return The output preferences.
     * @since  TrueZIP 7.3
     */
    public BitField<FsOutputOption> getOutputPreferences() {
        return this.outputPreferences;
    }

    /**
     * Sets the output preferences.
     * These preferences are usually not cached, so changing them should take
     * effect immediately.
     * 
     * @param  preferences the output preferences.
     * @throws IllegalArgumentException if an option is present in
     *         {@code preferences} which is not present in
     *         {@link FsOutputOptions#OUTPUT_PREFERENCES_MASK} or if both
     *         {@link FsOutputOption#STORE} and
     *         {@link FsOutputOption#COMPRESS} have been set.
     * @since  TrueZIP 7.3
     */
    public void setOutputPreferences(final BitField<FsOutputOption> preferences) {
        final BitField<FsOutputOption> illegal = preferences
                .and(OUTPUT_PREFERENCES_COMPLEMENT_MASK);
        if (!illegal.isEmpty())
            throw new IllegalArgumentException(preferences + " (illegal output preferences)");
        if (preferences.get(STORE) && preferences.get(COMPRESS))
            throw new IllegalArgumentException(preferences + " (either STORE or COMPRESS may be set, but not both)");
        this.outputPreferences = preferences;
    }

    /**
     * Pops this configuration off the inheritable thread local stack.
     * 
     * @throws IllegalStateException If this configuration is not the top
     *         element of the inheritable thread local stack.
     */
    @Override
    public void close() {
        final InheritableThreadLocalConfigStack configs = TConfig.configs;
        final Deque<TConfig> stack = configs.get();
        final TConfig expected;
        try {
            expected = stack.pop();
        } catch (NoSuchElementException ex) {
            throw new IllegalStateException("The inheritable thread local configuration stack is empty!", ex);
        }
        if (expected != this) {
            stack.push(expected);
            throw new IllegalStateException("This configuration is not the top element of the inheritable thread local configuration stack!");
        }
        if (stack.isEmpty())
            configs.remove();
    }

    /**
     * An inheritable thread local configuration stack.
     * Whenever a child thread is started, it will share the current
     * configuration with its parent thread by creating a new inheritable
     * thread local stack with the parent thread's current configuration as the
     * only element.
     */
    private static final class InheritableThreadLocalConfigStack
    extends InheritableThreadLocal<Deque<TConfig>> {
        @Override
        protected Deque<TConfig> initialValue() {
            return new LinkedList<TConfig>();
        }

        @Override
        protected Deque<TConfig> childValue(final Deque<TConfig> parent) {
            final Deque<TConfig> child = new LinkedList<TConfig>();
            final TConfig element = parent.peek();
            if (null != element)
                child.push(element);
            return child;
        }
    } // InheritableThreadLocalConfigStack
}
