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
        JScrollPane statusScroll = new JScrollPane(statusArea);
        statusScroll.setBorder(BorderFactory.createTitledBorder("Processor Log"));
        statusScroll.setPreferredSize(new Dimension(800, 100));

        // Create Panels
        // Create Panels
        SettingsPanel settingsPanel = new SettingsPanel();
        // PlotterPanel now takes SettingsPanel to read speed configs
        PlotterPanel plotterPanel = new PlotterPanel(settingsPanel);
        ProcessorPanel processorPanel = new ProcessorPanel(statusArea);

        // Tabs
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("1. Process SVG", processorPanel);
        tabbedPane.addTab("2. Plot (Driver)", plotterPanel);
        tabbedPane.addTab("Settings", settingsPanel);

        // Layout
        // The Processor panel needs the shared status area below it,
        // but the Plotter panel has its own integrated console.
        // So we might want to put the status area INSIDE the processor tab, or keep it
        // global at the bottom.
        // Let's attach the status area to the global SOUTH, but it will mostly be used
        // by Tab 1.

        add(tabbedPane, BorderLayout.CENTER);
        add(statusScroll, BorderLayout.SOUTH);
    }
}
