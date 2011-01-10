/*
 * Copyright (C) 2006-2011 Schlichtherle IT Services
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
package de.schlichtherle.truezip.key.passwd.swing;

import de.schlichtherle.truezip.key.AesKeyProvider;
import java.net.URI;
import org.netbeans.jemmy.operators.JComboBoxOperator;
import org.netbeans.jemmy.operators.JDialogOperator;

/**
 * @author Christian Schlichtherle
 * @version $Id$
 */
public class AesRemoteControl extends RemoteControl {
    public AesRemoteControl(final URI id) {
        super(id);
    }

    @Override
    protected void createResourceHook(final JDialogOperator dialog) {
        final JComboBoxOperator keyStrength = new JComboBoxOperator(dialog);
        assertEquals(   AesKeyProvider.KEY_STRENGTH_256,
                        keyStrength.getSelectedIndex());
        keyStrength.selectItem(AesKeyProvider.KEY_STRENGTH_128);
    }

    @Override
    protected void overwriteResourceHook(final JDialogOperator dialog) {
        final JComboBoxOperator keyStrength = new JComboBoxOperator(dialog);
        assertEquals(   AesKeyProvider.KEY_STRENGTH_128,
                        keyStrength.getSelectedIndex());
        keyStrength.selectItem(AesKeyProvider.KEY_STRENGTH_192);
    }
}