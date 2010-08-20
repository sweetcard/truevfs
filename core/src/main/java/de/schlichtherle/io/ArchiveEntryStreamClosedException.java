/*
 * Copyright (C) 2006-2010 Schlichtherle IT Services
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

package de.schlichtherle.io;

import java.io.IOException;

/**
 * Thrown if an input or output stream for an archive entry has been forced to
 * close when the archive file was (explicitly or implicitly) unmounted.
 *
 * @see <a href="package-summary.html#streams">Using Archive Entry Streams</a>
 * @see File#umount
 * @see File#update
 * @author Christian Schlichtherle
 * @version $Id$
 * @since TrueZIP 6.0
 */
public class ArchiveEntryStreamClosedException extends IOException {
    private static final long serialVersionUID = 4563928734723923649L;
    
    // TODO: Make this package private!
    public ArchiveEntryStreamClosedException() {
        super("entry stream has been forced to close() when the archive file was unmounted");
    }
}
