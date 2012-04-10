/*
 * Copyright (C) 2005-2012 Schlichtherle IT Services.
 * All rights reserved. Use is subject to license terms.
 */
package de.truezip.key.sl;

import static de.truezip.kernel.util.Maps.initialCapacity;
import de.truezip.kernel.util.ServiceLocator;
import de.truezip.key.AbstractKeyManagerProvider;
import de.truezip.key.KeyManager;
import de.truezip.key.spi.KeyManagerService;
import java.util.*;
import static java.util.logging.Level.CONFIG;
import static java.util.logging.Level.WARNING;
import java.util.logging.Logger;
import javax.annotation.concurrent.Immutable;

/**
 * Locates all key managers on the class path.
 * The map of key managers is populated by instantiating all classes
 * which are named in the resource files with the name
 * {@code "META-INF/services/de.truezip.key.spi.KeyManagerService"}
 * on the class path by calling their public no-argument constructor.
 *
 * @see    KeyManagerService
 * @author Christian Schlichtherle
 */
@Immutable
public final class KeyManagerLocator extends AbstractKeyManagerProvider {

    /** The singleton instance of this class. */
    public static final KeyManagerLocator SINGLETON = new KeyManagerLocator();

    /** Can't touch this - hammer time! */
    private KeyManagerLocator() { }

    @Override
    public Map<Class<?>, KeyManager<?>> getKeyManagers() {
        return Boot.MANAGERS;
    }

    /** A static data utility class used for lazy initialization. */
    private static final class Boot {
        static final Map<Class<?>, KeyManager<?>> MANAGERS;
        static {
            final Logger logger = Logger.getLogger(
                    KeyManagerLocator.class.getName(),
                    KeyManagerLocator.class.getName());
            final Iterator<KeyManagerService>
                    i = new ServiceLocator(KeyManagerLocator.class.getClassLoader())
                        .getServices(KeyManagerService.class);
            final Map<Class<?>, KeyManager<?>>
                    sorted = new TreeMap<Class<?>, KeyManager<?>>(
                        ClassComparator.INSTANCE);
            if (!i.hasNext())
                logger.log(WARNING, "null", KeyManagerService.class);
            while (i.hasNext()) {
                KeyManagerService service = i.next();
                logger.log(CONFIG, "located", service);
                for (final Map.Entry<Class<?>, KeyManager<?>> entry
                        : service.getKeyManagers().entrySet()) {
                    final Class<?> type = entry.getKey();
                    final KeyManager<?> newManager = entry.getValue();
                    if (null != type && null != newManager) {
                        final KeyManager<?> oldManager = sorted.put(type, newManager);
                        if (null != oldManager) {
                            final int op = oldManager.getPriority();
                            final int np = newManager.getPriority();
                            if (np < op)
                                sorted.put(type, oldManager);
                            else if (op == np)
                                logger.log(WARNING, "collision",
                                        new Object[] { op, type, oldManager, newManager });
                        }
                    }
                }
            }
            final Map<Class<?>, KeyManager<?>>
                    fast = new LinkedHashMap<Class<?>, KeyManager<?>>(
                        initialCapacity(sorted.size()));
            for (final Map.Entry<Class<?>, KeyManager<?>> entry : sorted.entrySet()) {
                final Class<?> type = entry.getKey();
                final KeyManager<?> manager = entry.getValue();
                logger.log(CONFIG, "mapping",
                        new Object[] { type, manager });
                fast.put(type, manager);
            }
            MANAGERS = Collections.unmodifiableMap(fast);
        }
    } // class Boot

    private static final class ClassComparator implements Comparator<Class<?>> {
        static final ClassComparator INSTANCE = new ClassComparator();

        @Override
        public int compare(Class<?> o1, Class<?> o2) {
            return o1.getName().compareTo(o2.getName());
        }
    } // ClassComparator
}