/*
 * Copyright (C) 2005-2015 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package net.java.truevfs.kernel.spec.spi;

import net.java.truecommons.annotations.ServiceSpecification;
import net.java.truecommons.services.LocatableDecorator;
import net.java.truevfs.kernel.spec.FsManager;
import net.java.truevfs.kernel.spec.sl.FsManagerLocator;

/**
 * An abstract service for decorating file system managers.
 * Decorator services are subject to service location by the
 * {@link FsManagerLocator#SINGLETON}.
 * <p>
 * If multiple decorator services are locatable on the class path at run time,
 * they are applied in ascending order of their
 * {@linkplain #getPriority() priority} so that the product of the decorator
 * service with the greatest number becomes the head of the resulting product
 * chain.
 *
 * @author Christian Schlichtherle
 */
@ServiceSpecification
public abstract class FsManagerDecorator
extends LocatableDecorator<FsManager> { }
