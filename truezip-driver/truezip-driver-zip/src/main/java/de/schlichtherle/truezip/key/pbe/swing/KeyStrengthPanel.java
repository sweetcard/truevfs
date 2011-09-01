/*
 * Copyright (C) 2006-2011 Schlichtherle IT Services
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.schlichtherle.truezip.key.pbe.swing;

import de.schlichtherle.truezip.crypto.param.KeyStrength;
import de.schlichtherle.truezip.swing.EnhancedPanel;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ResourceBundle;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import net.jcip.annotations.NotThreadSafe;

/**
 * A panel which allows the user to select the key strength for a cipher.
 *
 * @author  Christian Schlichtherle
 * @version $Id$
 */
@NotThreadSafe
@DefaultAnnotation(NonNull.class)
@SuppressWarnings("UseOfObsoleteCollectionType")
public class KeyStrengthPanel<S extends KeyStrength> extends EnhancedPanel {
    private static final long serialVersionUID = 5629581723148235643L;

    private static final ResourceBundle resources
            = ResourceBundle.getBundle(KeyStrengthPanel.class.getName());

    private final S[] availableKeyStrengths;

    /**
     * Constructs a new panel using a protective copy of the given array
     * of available key strengths.
     */
    public KeyStrengthPanel(final S[] availableKeyStrenghts) {
        this.availableKeyStrengths = availableKeyStrenghts.clone();
        initComponents();
    }

    private ComboBoxModel<S> newModel() {
        return new DefaultComboBoxModel<S>(availableKeyStrengths);
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        javax.swing.JLabel keyStrengthLong = new javax.swing.JLabel();
        final javax.swing.JLabel keyStrengthShort = new javax.swing.JLabel();

        setLayout(new java.awt.GridBagLayout());

        keyStrength.setModel(newModel());
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
     * Returns the value of the property {@code keyStrength}.
     *
     * @return The value of the property {@code keyStrength}.
     */
    @SuppressWarnings("unchecked")
    public S getKeyStrength() {
        return (S) keyStrength.getSelectedItem();
    }

    /**
     * Sets the value of the property {@code keyStrength}.
     *
     * @param keyStrength the new value of the property {@code keyStrength}.
     */
    public void setKeyStrength(final S keyStrength) {
        if (null == keyStrength)
            throw new NullPointerException();
        this.keyStrength.setSelectedItem(keyStrength);
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private final javax.swing.JComboBox keyStrength = new javax.swing.JComboBox();
    // End of variables declaration//GEN-END:variables
}
