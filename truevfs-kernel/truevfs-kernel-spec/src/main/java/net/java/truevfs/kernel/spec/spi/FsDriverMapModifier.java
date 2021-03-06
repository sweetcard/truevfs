/*
 * Copyright (C) 2005-2015 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.java.truevfs.kernel.spec.spi;

import java.util.Map;
import net.java.truecommons.annotations.ServiceSpecification;
import net.java.truecommons.services.LocatableModifier;
import net.java.truevfs.kernel.spec.FsDriver;
import net.java.truevfs.kernel.spec.FsScheme;
import net.java.truevfs.kernel.spec.sl.FsDriverMapLocator;

/**
 * An abstract service for modifying maps of file system schemes to file
 * system drivers.
 * Modifier services are subject to service location by the
 * {@link FsDriverMapLocator#SINGLETON}.
 * <p>
 * If multiple modifier services are locatable on the class path at run
 * time, they are applied in ascending order of their
 * {@linkplain #getPriority() priority} so that the result of the modifier
 * service with the greatest number becomes the result of the entire
 * modifier chain.
 *
 * @author Christian Schlichtherle
 */
@ServiceSpecification
public abstract class FsDriverMapModifier
extends LocatableModifier<Map<FsScheme, FsDriver>> { }
