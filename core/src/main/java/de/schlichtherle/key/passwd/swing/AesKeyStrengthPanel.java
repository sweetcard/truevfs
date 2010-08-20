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

package de.schlichtherle.key.passwd.swing;

import de.schlichtherle.key.PromptingAesKeyProvider;
import de.schlichtherle.swing.EnhancedPanel;
import java.util.ResourceBundle;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;

/**
 * A panel which allows the user to select the key strength for the AES cipher.
 *
 * @author Christian Schlichtherle
 * @since TrueZIP 6.0 (refactored from package de.schlichtherle.crypto.io.swing)
 * @version $Id$
 */
public class AesKeyStrengthPanel extends EnhancedPanel {
    private static final long serialVersionUID = 5629581723148235643L;

    private static final String CLASS_NAME
            = "de.schlichtherle.key.passwd.swing.AesKeyStrengthPanel";
    private static final ResourceBundle resources
            = ResourceBundle.getBundle(CLASS_NAME);

    static {
        // This check avoids a mapping function.
        assert 0 == PromptingAesKeyProvider.KEY_STRENGTH_128;
        assert 1 == PromptingAesKeyProvider.KEY_STRENGTH_192;
        assert 2 == PromptingAesKeyProvider.KEY_STRENGTH_256;
    }

    /**
     * Creates new form AesKeyStrengthPanel
     */
    public AesKeyStrengthPanel() {
        initComponents();
        keyStrength.setSelectedIndex(PromptingAesKeyProvider.KEY_STRENGTH_256);
    }

    private ComboBoxModel createModel() {
        return new DefaultComboBoxModel(
            new String[] {
                resources.getString("medium"),
                resources.getString("high"),
                resources.getString("ultra"),
            });
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;
        javax.swing.JLabel keyStrengthLong;

        keyStrengthLong = new javax.swing.JLabel();
        final javax.swing.JLabel keyStrengthShort = new javax.swing.JLabel();

        setLayout(new java.awt.GridBagLayout());

        keyStrength.setModel(createModel());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        add(keyStrength, gridBagConstraints);

        keyStrengthLong.setLabelFor(keyStrength);
        keyStrengthLong.setText(resources.getString("prompt")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        add(keyStrengthLong, gridBagConstraints);

        keyStrengthShort.setLabelFor(keyStrength);
        keyStrengthShort.setText(resources.getString("keyStrength")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 5);
        add(keyStrengthShort, gridBagConstraints);

    }// </editor-fold>//GEN-END:initComponents
    
    /**
     * Getter for the metaData property .
     *
     * @return Value of property keyStrength.
     */
    public int getKeyStrength() {
        final int ks = keyStrength.getSelectedIndex();
        assert ks == PromptingAesKeyProvider.KEY_STRENGTH_128
                || ks == PromptingAesKeyProvider.KEY_STRENGTH_192
                || ks == PromptingAesKeyProvider.KEY_STRENGTH_256;
        return ks;
    }
    
    /**
     * Setter for property keyStrength.
     *
     * @param keyStrength One of
     *        {@code PromptingAesKeyProvider.KEY_STRENGTH_128},
     *        {@code PromptingAesKeyProvider.KEY_STRENGTH_192} or
     *        {@code PromptingAesKeyProvider.KEY_STRENGTH_256}.
     *
     * @throws IllegalArgumentException If the preconditions for the parameter
     *         do not hold.
     */
    public void setKeyStrength(int keyStrength) {
        if (keyStrength != PromptingAesKeyProvider.KEY_STRENGTH_128
                && keyStrength != PromptingAesKeyProvider.KEY_STRENGTH_192
                && keyStrength != PromptingAesKeyProvider.KEY_STRENGTH_256)
            throw new IllegalArgumentException();
        this.keyStrength.setSelectedIndex(keyStrength);
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private final javax.swing.JComboBox keyStrength = new javax.swing.JComboBox();
    // End of variables declaration//GEN-END:variables

}
