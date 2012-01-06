/*
 * Copyright 2004-2012 Schlichtherle IT Services
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.schlichtherle.truezip.fs.sl;

import de.schlichtherle.truezip.fs.FsDefaultManager;
import de.schlichtherle.truezip.fs.FsFailSafeManager;
import de.schlichtherle.truezip.fs.FsManager;
import de.schlichtherle.truezip.fs.FsManagerProvider;
import de.schlichtherle.truezip.fs.spi.FsManagerService;
import de.schlichtherle.truezip.util.ServiceLocator;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Iterator;
import static java.util.logging.Level.CONFIG;
import java.util.logging.Logger;
import net.jcip.annotations.Immutable;

/**
 * Locates a file system manager service of a class with a name which is
 * resolved by querying a system property or searching the class path
 * or using a default implementation, whatever yields a result first.
 * <p>
 * First, the value of the {@link System#getProperty system property}
 * with the class name {@code "de.schlichtherle.truezip.fs.spi.FsManagerService"}
 * as the key is queried.
 * If this yields a value, the class with that name is then loaded and
 * instantiated by calling its public no-argument constructor.
 * <p>
 * Otherwise, the class path is searched for any resource file with the name
 * {@code "META-INF/services/de.schlichtherle.truezip.fs.spi.FsManagerService"}.
 * If this yields a result, the class with the name in this file is then loaded
 * and instantiated by calling its public no-argument constructor.
 * <p>
 * Otherwise, the expression
 * {@code new FsFailSafeManager(new FsDefaultManager())} is used to create the
 * file system manager in this container.
 *
 * @see     FsFailSafeManager
 * @see     FsDefaultManager
 * @see     FsManagerService
 * @author  Christian Schlichtherle
 * @version $Id$
 */
@Immutable
@DefaultAnnotation(NonNull.class)
public final class FsManagerLocator implements FsManagerProvider {

    /** The singleton instance of this class. */
    public static final FsManagerLocator SINGLETON = new FsManagerLocator();

    /** You cannot instantiate this class. */
    private FsManagerLocator() {
    }

    @Override
    public FsManager get() {
        return Boot.MANAGER;
    }

    /** A static data utility class used for lazy initialization. */
    private static final class Boot {
        static final FsManager MANAGER;
        static {
            final Logger logger = Logger.getLogger(
                    FsManagerLocator.class.getName(),
                    FsManagerLocator.class.getName());
            final ServiceLocator locator = new ServiceLocator(
                    FsManagerLocator.class.getClassLoader());
            FsManagerService
                    service = locator.getService(FsManagerService.class, null);
            if (null == service) {
                FsManagerService oldService = null;
                for (   final Iterator<FsManagerService>
                            i = locator.getServices(FsManagerService.class);
                        i.hasNext();
                        oldService = service) {
                    service = i.next();
                    logger.log(CONFIG, "located", service);
                    if (null != oldService
                            && oldService.getPriority() > service.getPriority())
                        service = oldService;
                }
            }
            FsManager manager;
            if (null != service) {
                manager = service.get();
                logger.log(CONFIG, "provided", manager);
            } else {
                manager = new FsFailSafeManager(new FsDefaultManager());
                logger.log(CONFIG, "default", manager);
            }
            MANAGER = manager;
        }
    } // Boot
}
