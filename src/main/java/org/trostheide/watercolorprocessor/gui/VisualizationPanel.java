package org.trostheide.watercolorprocessor.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Visualization Panel - Digital Twin of the Physical Plotter
 * 
 * Based on Requirements.md:
 * - Machine: A3 Portrait, Origin Top-Right, X grows Left, Y grows Down
 * - Input: Standard Canvas (Origin Top-Left, X grows Right, Y grows Down)
 * - Screen: Java2D (Origin Top-Left, X grows Right, Y grows Down)
 * 
 * Transformation Pipeline:
 * 1. Load raw data points
 * 2. Rotate data around content center (user selection: 0, 90, 180, 270)
 * 3. Compute rotated bounds
 * 4. Calculate alignment offset (snap corner to machine corner)
 * 5. Convert to Physical coords (flip X direction + offset)
 * 6. Simulate Driver transforms (SwapXY, InvertX, InvertY)
 * 7. Map Physical to Screen (physicalToScreen)
 */
public class VisualizationPanel extends JPanel {

    private final ObjectMapper mapper = new ObjectMapper();
    private List<List<Point2D>> allPaths = new ArrayList<>();

    // Current head position (Physical coords, reported by driver)
    private double currentX = 0;
    private double currentY = 0;

    // Machine Bounds (Fixed by Settings - A3 Portrait default)
    private double machineWidth = 297; // A3 Portrait width (short edge)
    private double machineHeight = 420; // A3 Portrait height (long edge)

    // Raw Content Bounds (from JSON, before rotation)
    private double rawMinX = 0, rawMaxX = 0, rawMinY = 0, rawMaxY = 0;

    // Rotated Content Bounds (after applying dataRotation)
    private double rotMinX = 0, rotMaxX = 0, rotMinY = 0, rotMaxY = 0;

    // Alignment Offset (calculated based on alignment choice)
    private double alignOffsetX = 0;
    private double alignOffsetY = 0;

    // User Settings
    private String canvasAlignment = "Top Right"; // Default: align to physical origin
    private int dataRotation = 0; // 0, 90, 180, 270 degrees
    private String orientation = "Portrait";

    // Driver Simulation Flags
    private boolean swapXY = false;
    private boolean invertX = false;
    private boolean invertY = false;
    private double paddingX = 0;
    private double paddingY = 0;

    // Machine origin corner (determines how motor coords map to screen)
    private String machineOrigin = "Top-Right";

    // Refill Stations (loaded from config)
    public record Station(String name, double x, double y) {
    }

    private List<Station> stations = new ArrayList<>();

    public void setStations(List<Station> newStations) {
        this.stations.clear();
        this.stations.addAll(newStations);
        repaint();
    }

    // ----- Setters -----

    public void setOrientation(String orientation) {
        this.orientation = orientation;
        repaint();
    }

    public void setDataRotation(int degrees) {
        this.dataRotation = degrees;
        recalculateTransform();
        repaint();
    }

    // Alias for compatibility with existing code
    public void setViewRotation(int degrees) {
        setDataRotation(degrees);
    }

    public void setSwapXY(boolean swap) {
        this.swapXY = swap;
        recalculateTransform();
        repaint();
    }

    public void setDataInvertX(boolean invert) {
        this.invertX = invert;
        recalculateTransform();
        repaint();
    }

    public void setDataInvertY(boolean invert) {
        this.invertY = invert;
        recalculateTransform();
        repaint();
    }

    public void setMachineSize(double width, double height) {
        this.machineWidth = width;
        this.machineHeight = height;
        recalculateTransform();
        repaint();
    }

    public void setCanvasAlignment(String alignment) {
        this.canvasAlignment = alignment;
        recalculateTransform();
        repaint();
    }

    public void setPadding(double x, double y) {
        this.paddingX = x;
        this.paddingY = y;
        recalculateTransform();
        repaint();
    }

    public void setMachineOrigin(String origin) {
        this.machineOrigin = origin;
        recalculateTransform();
        repaint();
    }

    private boolean isOriginRight() { return machineOrigin.contains("Right"); }
    private boolean isOriginBottom() { return machineOrigin.contains("Bottom"); }

    public VisualizationPanel() {
        setBackground(new Color(35, 35, 40));
        TitledBorder border = BorderFactory.createTitledBorder("Live View");
        border.setTitleFont(border.getTitleFont().deriveFont(Font.BOLD, 12f));
        setBorder(border);
    }

    // ----- Data Loading -----

    public void loadFromJson(File jsonFile) {
        allPaths.clear();
        currentX = 0;
        currentY = 0;

        try {
            JsonNode root = mapper.readTree(jsonFile);
            JsonNode layers = root.get("layers");
            if (layers != null) {
                for (JsonNode layer : layers) {
                    JsonNode commands = layer.get("commands");
                    for (JsonNode cmd : commands) {
                        if ("DRAW".equals(cmd.get("op").asText())) {
                            List<Point2D> stroke = new ArrayList<>();
                            JsonNode points = cmd.get("points");
                            for (JsonNode p : points) {
                                double x = p.get("x").asDouble();
                                double y = p.get("y").asDouble();
                                stroke.add(new Point2D(x, y));
                            }
                            if (!stroke.isEmpty()) {
                                allPaths.add(stroke);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        recalculateTransform();
        repaint();
    }

    public void updatePosition(double x, double y) {
        this.currentX = x;
        this.currentY = y;
        repaint();
    }

    /**
     * Load refill station positions from a config file.
     * Stations are displayed as markers on the canvas.
     */
    public void loadStationsFromConfig(File configFile) {
        stations.clear();
        if (configFile == null || !configFile.exists()) {
            return;
        }
        try {
            JsonNode root = mapper.readTree(configFile);
            JsonNode stationsNode = root.get("stations");
            if (stationsNode != null && stationsNode.isObject()) {
                var fields = stationsNode.fields();
                while (fields.hasNext()) {
                    var entry = fields.next();
                    String name = entry.getKey();
                    JsonNode station = entry.getValue();
                    double x = station.has("x") ? station.get("x").asDouble() : 0;
                    double y = station.has("y") ? station.get("y").asDouble() : 0;
                    stations.add(new Station(name, x, y));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        repaint();
    }

    // ----- Transformation Helpers -----

    /**
     * Step 2: Rotate a point around the raw content center.
     */
    private Point2D rotateAroundCenter(Point2D p) {
        double cx = (rawMinX + rawMaxX) / 2.0;
        double cy = (rawMinY + rawMaxY) / 2.0;

        double dx = p.x() - cx;
        double dy = p.y() - cy;

        double rx, ry;
        switch (dataRotation) {
            case 90:
                rx = -dy;
                ry = dx;
                break;
            case 180:
                rx = -dx;
                ry = -dy;
                break;
            case 270:
                rx = dy;
                ry = -dx;
                break;
            default: // 0
                rx = dx;
                ry = dy;
        }

        // Translate back (now centered at origin after rotation, we'll add center back)
        // Actually, we want the rotated content centered at the same center as before.
        // But for simplicity, let's just return the rotated coords relative to (0,0).
        // The alignment offset will place it correctly.
        return new Point2D(rx + cx, ry + cy);
    }

    /**
     * Step 3 & 4: Recalculate transformed bounds and alignment offset.
     * MUST match driver.py's alignment logic exactly!
     * Driver calculates bounds AFTER applying swap/invert to corners.
     */
    private void recalculateTransform() {
        if (allPaths.isEmpty()) {
            rotMinX = rotMaxX = rotMinY = rotMaxY = 0;
            alignOffsetX = alignOffsetY = 0;
            return;
        }

        // Step 1: Calculate RAW bounds (before any transform) - same as driver
        // Use instance fields directly so rotateAroundCenter sees correct values
        this.rawMinX = Double.MAX_VALUE;
        this.rawMaxX = -Double.MAX_VALUE;
        this.rawMinY = Double.MAX_VALUE;
        this.rawMaxY = -Double.MAX_VALUE;

        for (List<Point2D> path : allPaths) {
            for (Point2D p : path) {
                this.rawMinX = Math.min(this.rawMinX, p.x());
                this.rawMaxX = Math.max(this.rawMaxX, p.x());
                this.rawMinY = Math.min(this.rawMinY, p.y());
                this.rawMaxY = Math.max(this.rawMaxY, p.y());
            }
        }

        // Step 2: Transform corners through rotation + swap/invert
        // Must match driver's get_transformed_point (which now includes rotation)
        double[][] corners = {
                { rawMinX, rawMinY }, { rawMaxX, rawMinY },
                { rawMinX, rawMaxY }, { rawMaxX, rawMaxY }
        };

        double tMinX = Double.MAX_VALUE, tMaxX = -Double.MAX_VALUE;
        double tMinY = Double.MAX_VALUE, tMaxY = -Double.MAX_VALUE;

        double cx = (rawMinX + rawMaxX) / 2.0;
        double cy = (rawMinY + rawMaxY) / 2.0;

        for (double[] c : corners) {
            double x = c[0], y = c[1];

            // Apply rotation around content center (matching driver)
            if (dataRotation != 0) {
                double dx = x - cx;
                double dy = y - cy;
                switch (dataRotation) {
                    case 90:  { double t = dx; dx = -dy; dy = t; break; }
                    case 180: { dx = -dx; dy = -dy; break; }
                    case 270: { double t = dx; dx = dy; dy = -t; break; }
                }
                x = dx + cx;
                y = dy + cy;
            }

            // Apply swap (portrait-effective)
            if (effectiveSwap()) {
                double temp = x;
                x = y;
                y = temp;
            }

            // Apply invert (portrait-effective)
            if (effectiveInvertX()) {
                x = machineWidth - x;
            }
            if (effectiveInvertY()) {
                y = machineHeight - y;
            }

            tMinX = Math.min(tMinX, x);
            tMaxX = Math.max(tMaxX, x);
            tMinY = Math.min(tMinY, y);
            tMaxY = Math.max(tMaxY, y);
        }

        // Store rotated bounds (used in rotateAroundCenter for center calculation)
        rotMinX = rawMinX;
        rotMaxX = rawMaxX;
        rotMinY = rawMinY;
        rotMaxY = rawMaxY;

        // Determine left/right edge semantics based on origin (mirrors driver's calculate_alignment_offset)
        double contentLeftEdge, contentRightEdge, targetLeft, targetRight;
        if (isOriginRight()) {
            contentRightEdge = tMinX;
            contentLeftEdge = tMaxX;
            targetLeft = machineWidth - paddingX;
            targetRight = paddingX;
        } else {
            contentLeftEdge = tMinX;
            contentRightEdge = tMaxX;
            targetLeft = paddingX;
            targetRight = machineWidth - paddingX;
        }
        double targetTop = paddingY;
        double targetBottom = machineHeight - paddingY;

        // In portrait mode, translate the alignment label to account for swapped axes.
        // The two corners sharing exactly one component with the origin swap with each other.
        String effectiveAlign = canvasAlignment;
        if (isPortrait()) {
            effectiveAlign = translateAlignmentForPortrait(canvasAlignment);
        }

        switch (effectiveAlign) {
            case "Top Left":
                alignOffsetX = targetLeft - contentLeftEdge;
                alignOffsetY = targetTop - tMinY;
                break;
            case "Top Right":
                alignOffsetX = targetRight - contentRightEdge;
                alignOffsetY = targetTop - tMinY;
                break;
            case "Bottom Left":
                alignOffsetX = targetLeft - contentLeftEdge;
                alignOffsetY = targetBottom - tMaxY;
                break;
            case "Bottom Right":
                alignOffsetX = targetRight - contentRightEdge;
                alignOffsetY = targetBottom - tMaxY;
                break;
            case "Center":
                double tWidth = tMaxX - tMinX;
                double tHeight = tMaxY - tMinY;
                alignOffsetX = (machineWidth - tWidth) / 2 - tMinX;
                alignOffsetY = (machineHeight - tHeight) / 2 - tMinY;
                break;
            default:
                alignOffsetX = 0;
                alignOffsetY = 0;
        }
    }

    /**
     * Step 5: Convert rotated input point to Physical coordinates.
     * Simply applies alignment offset. NO X-flip here (driver doesn't flip X by
     * default).
     */
    private Point2D inputToPhysical(Point2D rotatedPoint) {
        // Just apply offset, no flip!
        double physX = rotatedPoint.x() + alignOffsetX;
        double physY = rotatedPoint.y() + alignOffsetY;
        return new Point2D(physX, physY);
    }

    /**
     * In portrait mode, the alignment corners sharing exactly one component with the
     * origin corner swap with each other (the origin corner and its diagonal are fixed).
     */
    private String translateAlignmentForPortrait(String label) {
        boolean xor = isOriginRight() ^ isOriginBottom();
        if (xor) {
            // Origin is Top-Right or Bottom-Left: swap Top-Left <-> Bottom-Right
            if ("Top Left".equals(label)) return "Bottom Right";
            if ("Bottom Right".equals(label)) return "Top Left";
        } else {
            // Origin is Top-Left or Bottom-Right: swap Top-Right <-> Bottom-Left
            if ("Top Right".equals(label)) return "Bottom Left";
            if ("Bottom Left".equals(label)) return "Top Right";
        }
        return label;
    }

    // Portrait-effective values: portrait swaps which axis gets inverted and auto-toggles swap
    private boolean effectiveSwap() { return isPortrait() ^ swapXY; }
    private boolean effectiveInvertX() { return isPortrait() ? invertY : invertX; }
    private boolean effectiveInvertY() { return isPortrait() ? invertX : invertY; }

    /**
     * Step 6: Simulate Driver transforms (SwapXY, InvertX, InvertY).
     * Uses portrait-effective values so the visualization matches the driver.
     */
    private Point2D simulateDriver(Point2D phys) {
        double x = phys.x();
        double y = phys.y();

        if (effectiveSwap()) {
            double temp = x;
            x = y;
            y = temp;
        }

        if (effectiveInvertX()) {
            x = machineWidth - x;
        }

        if (effectiveInvertY()) {
            y = machineHeight - y;
        }

        return new Point2D(x, y);
    }

    /**
     * Step 7: Map motor coordinates to screen coordinates.
     *
     * Motor (0,0) sits at the machineOrigin corner.
     * Screen (0,0) is always top-left, X right, Y down.
     */
    private boolean isPortrait() {
        return "Portrait".equals(orientation);
    }

    private double displayWidth() {
        return isPortrait() ? machineHeight : machineWidth;
    }

    private double displayHeight() {
        return isPortrait() ? machineWidth : machineHeight;
    }

    private double[] physicalToScreen(double motorX, double motorY) {
        if (isPortrait()) {
            // Portrait: motor X → screen Y, motor Y → screen X
            double screenX = isOriginRight() ? (machineHeight - motorY) : motorY;
            double screenY = isOriginBottom() ? (machineWidth - motorX) : motorX;
            return new double[] { screenX, screenY };
        }
        double screenX = isOriginRight() ? (machineWidth - motorX) : motorX;
        double screenY = isOriginBottom() ? (machineHeight - motorY) : motorY;
        return new double[] { screenX, screenY };
    }

    /**
     * Full pipeline: Raw Point -> Screen Point
     * Order matches driver.py: rotate -> swap/invert -> offset
     */
    private double[] transformPoint(Point2D rawPoint) {
        // 1. Rotate around content center
        Point2D rotated = rotateAroundCenter(rawPoint);
        // 2. Apply swap/invert (driver's transform_point does this first)
        Point2D driverSim = simulateDriver(rotated);
        // 3. Apply alignment offset (driver adds offset AFTER transform_point)
        Point2D offsetApplied = new Point2D(driverSim.x() + alignOffsetX, driverSim.y() + alignOffsetY);
        // 4. Convert to screen coords for display
        return physicalToScreen(offsetApplied.x(), offsetApplied.y());
    }

    // ----- Painting -----

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        double dw = displayWidth();
        double dh = displayHeight();

        // Calculate scale to fit displayed bed in panel
        double scaleX = (w - 40) / dw;
        double scaleY = (h - 40) / dh;
        double scale = Math.min(scaleX, scaleY);
        if (scale <= 0)
            scale = 1.0;

        // Center the displayed bed in the panel
        double tx = 20 + (w - 40 - dw * scale) / 2.0;
        double ty = 20 + (h - 40 - dh * scale) / 2.0;

        AffineTransform old = g2.getTransform();
        g2.translate(tx, ty);
        g2.scale(scale, scale);

        // --- Draw Machine Bed ---
        g2.setColor(new Color(50, 52, 58));
        g2.fill(new java.awt.geom.Rectangle2D.Double(0, 0, dw, dh));
        g2.setColor(new Color(80, 82, 90));
        g2.setStroke(new BasicStroke((float) (1.5 / scale)));
        g2.draw(new java.awt.geom.Rectangle2D.Double(0, 0, dw, dh));

        // --- Draw Origin Marker (Physical 0,0 = Screen Top-Right) ---
        double[] originScreen = physicalToScreen(0, 0);
        g2.setColor(Color.ORANGE);
        double markerR = 5 / scale;
        g2.fill(new java.awt.geom.Ellipse2D.Double(originScreen[0] - markerR, originScreen[1] - markerR,
                markerR * 2, markerR * 2));
        g2.setColor(new Color(200, 200, 200));
        g2.setFont(g2.getFont().deriveFont((float) (12 / scale)));
        g2.drawString("0,0 (Origin)", (float) (originScreen[0] - 50 / scale), (float) (originScreen[1] + 15 / scale));

        // --- Draw Axes (from Physical Origin) ---
        g2.setStroke(new BasicStroke((float) (2.0 / scale)));
        double axisLen = Math.min(machineWidth, machineHeight) * 0.15;

        // X-Axis (Physical +X = Left, so Screen -X = Left from origin)
        double[] xAxisEnd = physicalToScreen(axisLen, 0);
        g2.setColor(Color.RED);
        g2.draw(new java.awt.geom.Line2D.Double(originScreen[0], originScreen[1], xAxisEnd[0], xAxisEnd[1]));
        g2.drawString("X", (float) (xAxisEnd[0] - 10 / scale), (float) (xAxisEnd[1] + 15 / scale));

        // Y-Axis (Physical +Y = Down, so Screen +Y = Down from origin)
        double[] yAxisEnd = physicalToScreen(0, axisLen);
        g2.setColor(Color.GREEN);
        g2.draw(new java.awt.geom.Line2D.Double(originScreen[0], originScreen[1], yAxisEnd[0], yAxisEnd[1]));
        g2.drawString("Y", (float) (yAxisEnd[0] + 5 / scale), (float) (yAxisEnd[1] + 5 / scale));

        // --- Draw Refill Stations ---
        for (Station station : stations) {
            // Station coords are in raw input space, transform to screen
            double[] sScreen = physicalToScreen(station.x(), station.y());
            g2.setColor(new Color(80, 180, 255)); // Blue marker
            double stationR = 4 / scale;
            g2.fill(new java.awt.geom.Ellipse2D.Double(sScreen[0] - stationR, sScreen[1] - stationR,
                    stationR * 2, stationR * 2));
            // Draw station label
            g2.setColor(new Color(190, 190, 190));
            g2.setFont(g2.getFont().deriveFont((float) (9 / scale)));
            g2.drawString(station.name(), (float) (sScreen[0] + stationR + 2 / scale),
                    (float) (sScreen[1] + 4 / scale));
        }

        // --- Draw Paths ---
        g2.setColor(new Color(130, 160, 255));
        g2.setStroke(new BasicStroke((float) (1.0 / scale)));

        for (List<Point2D> path : allPaths) {
            if (path.isEmpty())
                continue;
            Path2D p2d = new Path2D.Double();

            double[] p0 = transformPoint(path.get(0));
            p2d.moveTo(p0[0], p0[1]);

            for (int i = 1; i < path.size(); i++) {
                double[] pi = transformPoint(path.get(i));
                p2d.lineTo(pi[0], pi[1]);
            }
            g2.draw(p2d);
        }

        // --- Draw Cursor (Head Position) ---
        // currentX, currentY are Physical coordinates from driver
        double[] headScreen = physicalToScreen(currentX, currentY);
        g2.setColor(Color.RED);
        double r = 4.0 / scale;
        g2.fill(new java.awt.geom.Ellipse2D.Double(headScreen[0] - r, headScreen[1] - r, r * 2, r * 2));

        // Crosshair
        g2.setStroke(new BasicStroke((float) (0.5 / scale)));
        double crossSize = Math.max(machineWidth, machineHeight);
        g2.draw(new java.awt.geom.Line2D.Double(headScreen[0] - crossSize, headScreen[1],
                headScreen[0] + crossSize, headScreen[1]));
        g2.draw(new java.awt.geom.Line2D.Double(headScreen[0], headScreen[1] - crossSize,
                headScreen[0], headScreen[1] + crossSize));

        g2.setTransform(old);

        // --- HUD ---
        g2.setColor(new Color(180, 180, 180));
        g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        g2.drawString(String.format(
                "PhysPos: %.1f, %.1f | Align: %s | Rot: %d | Origin: %s | %s",
                currentX, currentY, canvasAlignment, dataRotation,
                machineOrigin, orientation), 10, h - 10);
        g2.drawString(String.format(
                "Bed: %.0fx%.0f | Offset: %.1f, %.1f | EffSwap: %s EffInvX: %s EffInvY: %s",
                machineWidth, machineHeight, alignOffsetX, alignOffsetY,
                effectiveSwap() ? "Y" : "N",
                effectiveInvertX() ? "Y" : "N",
                effectiveInvertY() ? "Y" : "N"), 10, h - 24);
    }

    // ----- Internal Types -----
    private record Point2D(double x, double y) {
    }
}
