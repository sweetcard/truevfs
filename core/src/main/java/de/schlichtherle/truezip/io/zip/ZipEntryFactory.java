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

package de.schlichtherle.truezip.io.zip;

/**
 * A factory for {@link ZipEntry}s.
 *
 * @param <E> The type of the created ZIP entries.
 * @author Christian Schlichtherle
 * @version $Id$
 * @see BasicZipFile
 */
public interface ZipEntryFactory<E extends ZipEntry> {

    /**
     * Creates a new {@link ZipEntry} with the given entry {@code name}.
     *
     * @param  name the entry name.
     * @return A newly created {@link ZipEntry} with the given entry
     *         {@code name} - {@code null} is not permitted.
     */
    E newZipEntry(String name);
}
