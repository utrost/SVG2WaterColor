package org.trostheide.watercolorprocessor.gui;

import javax.swing.*;

public class WatercolorGUI {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                // Manual Font Scaling for High DPI
                scaleUI(2.0f);
            } catch (Exception e) {
                e.printStackTrace();
            }
            new MainFrame().setVisible(true);
        });
    }

    private static void scaleUI(float scale) {
        try {
            java.util.Set<Object> keys = UIManager.getLookAndFeelDefaults().keySet();
            for (Object key : keys) {
                Object value = UIManager.get(key);
                if (value instanceof javax.swing.plaf.FontUIResource) {
                    javax.swing.plaf.FontUIResource font = (javax.swing.plaf.FontUIResource) value;
                    UIManager.put(key, new javax.swing.plaf.FontUIResource(font.deriveFont(font.getSize2D() * scale)));
                }
            }
            // Increase global icon scaling if possible?
            // Swing doesn't scale icons automatically easily, but font size handles 90% of
            // the UI.
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
