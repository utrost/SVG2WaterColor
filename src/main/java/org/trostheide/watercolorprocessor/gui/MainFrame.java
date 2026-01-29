package org.trostheide.watercolorprocessor.gui;

import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame {

    public MainFrame() {
        setTitle("Watercolor Processor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null); // Center
        setLayout(new BorderLayout());

        // Shared Status Area for Processor
        JTextArea statusArea = new JTextArea();
        statusArea.setEditable(false);
        statusArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        // Create Panels
        SettingsPanel settingsPanel = new SettingsPanel();
        // PlotterPanel now takes SettingsPanel to read speed configs
        PlotterPanel plotterPanel = new PlotterPanel(settingsPanel);
        ProcessorPanel processorPanel = new ProcessorPanel(statusArea);

        // Tabs
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("1. Process SVG", processorPanel);
        tabbedPane.addTab("2. Plot (Driver)", plotterPanel);

        // Wrap Settings in ScrollPane for small screens
        JScrollPane settingsScroll = new JScrollPane(settingsPanel);
        settingsScroll.setBorder(null); // Clean look
        settingsScroll.getVerticalScrollBar().setUnitIncrement(16); // Fast scrolling
        tabbedPane.addTab("Settings", settingsScroll);

        add(tabbedPane, BorderLayout.CENTER);
    }

    public void setConnectionStatus(boolean connected) {
        if (connected) {
            // Green Frame
            getRootPane().setBorder(BorderFactory.createMatteBorder(5, 5, 5, 5, Color.GREEN));
        } else {
            // Red Frame or None? User said "Red if offline".
            getRootPane().setBorder(BorderFactory.createMatteBorder(5, 5, 5, 5, Color.RED));
        }
    }
}
