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
package de.schlichtherle.truezip.io.archive.filesystem;

import de.schlichtherle.truezip.io.archive.driver.zip.ZipDriver;
import de.schlichtherle.truezip.io.archive.entry.ArchiveEntry;
import de.schlichtherle.truezip.util.BitField;
import org.junit.Test;

import static de.schlichtherle.truezip.io.entry.Entry.Access.WRITE;
import static de.schlichtherle.truezip.io.entry.Entry.ROOT;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

/**
 * @author Christian Schlichtherle
 * @version $Id$
 */
public class ArchiveFileSystemModelTest {

    @Test
    public void testAddRemoveArchiveFileSystemListeners() {
        final ArchiveFileSystem<?> model
                = ArchiveFileSystem.newArchiveFileSystem(new ZipDriver());

        try {
            model.addArchiveFileSystemListener(null);
        } catch (NullPointerException expected) {
        }
        assertThat(model.getArchiveFileSystemListeners(), notNullValue());
        assertThat(model.getArchiveFileSystemListeners().size(), is(0));

        final Listener listener1 = new Listener(model);
        model.addArchiveFileSystemListener(listener1);
        assertThat(model.getArchiveFileSystemListeners().size(), is(1));

        final Listener listener2 = new Listener(model);
        model.addArchiveFileSystemListener(listener2);
        assertThat(model.getArchiveFileSystemListeners().size(), is(2));

        model.getArchiveFileSystemListeners().clear();
        assertThat(model.getArchiveFileSystemListeners().size(), is(2));

        try {
            model.removeArchiveFileSystemListener(null);
        } catch (NullPointerException expected) {
        }
        assertThat(model.getArchiveFileSystemListeners().size(), is(2));

        model.removeArchiveFileSystemListener(listener1);
        model.removeArchiveFileSystemListener(listener1);
        assertThat(model.getArchiveFileSystemListeners().size(), is(1));

        model.removeArchiveFileSystemListener(listener2);
        model.removeArchiveFileSystemListener(listener2);
        assertThat(model.getArchiveFileSystemListeners().size(), is(0));
    }

    /*@Test
    public void testNotifyArchiveFileSystemListeners()
    throws ArchiveFileSystemException {
        final ArchiveFileSystem<?> model
                = ArchiveFileSystem.newArchiveFileSystem(new ZipDriver());
        final Listener listener1 = new Listener(model);
        final Listener listener2 = new Listener(model);

        model.setTime(ROOT, BitField.of(WRITE), System.currentTimeMillis());
        assertThat(listener1.before, is(1));
        assertThat(listener2.before, is(1));
        assertThat(listener1.after, is(1));
        assertThat(listener2.after, is(1));

        model.setTime(ROOT, BitField.of(WRITE), System.currentTimeMillis());
        assertThat(listener1.before, is(1));
        assertThat(listener2.before, is(1));
        assertThat(listener1.after, is(1));
        assertThat(listener2.after, is(1));
    }*/

    private static class Listener
    implements ArchiveFileSystemListener<ArchiveEntry> {
        final ArchiveFileSystem<?> model;
        int before;
        int after;

        @SuppressWarnings("LeakingThisInConstructor")
        Listener(final ArchiveFileSystem<?> model) {
            this.model = model;
            model.addArchiveFileSystemListener(this);
        }

        @Override
        public void beforeTouch(ArchiveFileSystemEvent<?> event) {
            assertThat(event, notNullValue());
            assertThat(event.getSource(), sameInstance((Object) model));
            before++;
        }

        @Override
        public void afterTouch(ArchiveFileSystemEvent<?> event) {
            assertThat(event, notNullValue());
            assertThat(event.getSource(), sameInstance((Object) model));
            after++;
        }
    }
}
