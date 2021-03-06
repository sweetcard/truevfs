/*
 * Copyright (C) 2005-2015 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.java.truevfs.kernel.spec;

import java.net.URI;
import javax.annotation.CheckForNull;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * @author Christian Schlichtherle
 */
public class FsModelTest {

    protected FsModel newModel( FsMountPoint mountPoint,
                                @CheckForNull FsModel parent) {
        return new FsTestModel(mountPoint, parent);
    }

    private FsModel newModel(final FsMountPoint mountPoint) {
        return newModel(mountPoint,
                        null == mountPoint.getParent()
                            ? null
                            : newModel(mountPoint.getParent()));
    }

    @Test
    @SuppressWarnings("ResultOfObjectAllocationIgnored")
    public void testConstructorWithNull() {
        try {
            newModel(null, null);
            fail();
        } catch (NullPointerException expected) {
        }
    }

    @Test
    @SuppressWarnings("ResultOfObjectAllocationIgnored")
    public void testConstructorWithMountPoint() {
        for (final String[] params : new String[][] {
            { "foo:/bar/" },
        }) {
            final FsMountPoint mountPoint = FsMountPoint.create(URI.create(params[0]));
            final FsModel model = newModel(mountPoint, null);
            assertThat(model.getMountPoint(), sameInstance(mountPoint));
            assertThat(model.getMountPoint().getPath(), nullValue());
            assertThat(model.getParent(), nullValue());
            assertThat(model.isMounted(), is(false));
        }
    }

    @Test
    @SuppressWarnings("ResultOfObjectAllocationIgnored")
    public void testConstructorWithMountPointAndParent() {
        for (final String[] params : new String[][] {
            { "foo:/bar/baz/", "foo:/bar/" },
            { "foo:/bar/", "foo:/baz/" },
        }) {
            final FsMountPoint mountPoint = FsMountPoint.create(URI.create(params[0]));
            final FsMountPoint parentMountPoint = FsMountPoint.create(URI.create(params[1]));
            final FsModel parent = newModel(parentMountPoint, null);
            try {
                newModel(mountPoint, parent);
                fail(params[0]);
            } catch (RuntimeException expected) {
            }
        }

        for (final String[] params : new String[][] {
            //{ "foo:bar:baz:/boom!/bang!/", "bar:baz:/boom!/", "plonk/", "bang/plonk/", "foo:bar:baz:/boom!/bang!/plonk/" },
            { "foo:bar:baz:/boom!/bang!/", "bar:baz:/boom!/", "plonk", "bang/plonk", "foo:bar:baz:/boom!/bang!/plonk" },
            //{ "foo:bar:/baz!/", "bar:/", "boom/", "baz/boom/", "foo:bar:/baz!/boom/" },
            { "foo:bar:/baz!/", "bar:/", "boom", "baz/boom", "foo:bar:/baz!/boom" },
        }) {
            final FsMountPoint mountPoint = FsMountPoint.create(URI.create(params[0]));
            final FsMountPoint parentMountPoint = FsMountPoint.create(URI.create(params[1]));
            final FsNodeName entryName = FsNodeName.create(URI.create(params[2]));
            final FsNodeName parentEntryName = FsNodeName.create(URI.create(params[3]));
            final FsNodePath path = FsNodePath.create(URI.create(params[4]));
            FsModel parent = newModel(parentMountPoint);
            FsModel model = newModel(mountPoint, parent);

            assertThat(model.getMountPoint(), sameInstance(mountPoint));
            assertThat(model.getParent(), sameInstance(parent));
            assertThat(model.getMountPoint().getPath().resolve(entryName).getNodeName(), equalTo(parentEntryName));
            assertThat(model.getMountPoint().resolve(entryName), equalTo(path));
            assertThat(model.isMounted(), is(false));
        }
    }
}