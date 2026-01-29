package org.trostheide.watercolorprocessor.gui;

import javax.swing.*;
import com.formdev.flatlaf.FlatLightLaf;

public class WatercolorGUI {
    public static void main(String[] args) {
        // Must setup FlatLaf before any Swing code
        FlatLightLaf.setup();

        SwingUtilities.invokeLater(() -> {
            new MainFrame().setVisible(true);
        });
    }
}
