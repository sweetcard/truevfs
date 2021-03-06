/*
 * Copyright (C) 2005-2015 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.java.truevfs.comp.zip;

import java.nio.charset.Charset;

/**
 * Defines the default character set for accessing ZIP files.
 * 
 * @author Christian Schlichtherle
 */
public interface ZipCharsetParameters extends ZipParameters {

    /**
     * Returns the default character set for comments and entry names in a ZIP
     * file.
     * When reading a ZIP file, this is used to decode comments and entry names
     * in a ZIP file unless an entry has bit 11 set in its General Purpose Bit
     * Flags.
     * In this case, the character set is ignored and "UTF-8" is used for
     * decoding the entry.
     * This is in accordance to Appendix D of PKWARE's
     * <a href="http://www.pkware.com/documents/casestudies/APPNOTE.TXT">ZIP File Format Specification</a>,
     * version 6.3.0 and later.
     * <p>
     * This is an immutable property - multiple calls must return the same
     * object.
     * 
     * @return The default character set for comments and entry names in a ZIP
     *         file.
     */
    Charset getCharset();
}
