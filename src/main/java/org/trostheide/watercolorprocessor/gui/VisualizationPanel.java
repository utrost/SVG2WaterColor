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
import java.util.List;

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
    private boolean flipY = false;

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

    public void setFlipY(boolean flip) {
        this.flipY = flip;
        recalculateTransform();
        repaint();
    }

    private boolean isOriginRight() { return machineOrigin.contains("Right"); }
    private boolean isOriginBottom() { return machineOrigin.contains("Bottom"); }
    private boolean needsAxisSwap() { return isPortrait() && machineWidth > machineHeight; }

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

    // Portrait axis swap only when machine dimensions are landscape-oriented (width > height)
    // and user selects portrait. For machines with portrait dimensions (e.g. GRBL 297x420), no swap needed.
    private boolean effectiveSwap() { return needsAxisSwap() ^ swapXY; }
    private boolean effectiveInvertX() { return needsAxisSwap() ? invertY : invertX; }
    private boolean effectiveInvertY() { return needsAxisSwap() ? invertX : invertY; }

    private double[] contentBoundsArray() {
        return new double[] { rawMinX, rawMaxX, rawMinY, rawMaxY };
    }

    /**
     * Recalculate alignment offset using the shared CoordinateTransform utility.
     * MUST produce the same result as driver/transforms.py calculate_alignment_offset.
     */
    private void recalculateTransform() {
        if (allPaths.isEmpty()) {
            rawMinX = rawMaxX = rawMinY = rawMaxY = 0;
            alignOffsetX = alignOffsetY = 0;
            return;
        }

        // Calculate raw content bounds
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

        // When axes are swapped for portrait, translate the alignment label
        String effectiveAlign = canvasAlignment;
        if (needsAxisSwap()) {
            effectiveAlign = translateAlignmentForPortrait(canvasAlignment);
        }

        double[] offset = CoordinateTransform.calculateAlignmentOffset(
                effectiveAlign, contentBoundsArray(),
                machineWidth, machineHeight,
                effectiveSwap(), effectiveInvertX(), effectiveInvertY(),
                dataRotation, isOriginRight(),
                paddingX, paddingY);
        alignOffsetX = offset[0];
        alignOffsetY = offset[1];
    }

    /**
     * In portrait mode, the alignment corners sharing exactly one component with the
     * origin corner swap with each other (the origin corner and its diagonal are fixed).
     */
    private String translateAlignmentForPortrait(String label) {
        boolean xor = isOriginRight() ^ isOriginBottom();
        if (xor) {
            if ("Top Left".equals(label)) return "Bottom Right";
            if ("Bottom Right".equals(label)) return "Top Left";
        } else {
            if ("Top Right".equals(label)) return "Bottom Left";
            if ("Bottom Left".equals(label)) return "Top Right";
        }
        return label;
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
        return isPortrait() ? Math.min(machineWidth, machineHeight) : Math.max(machineWidth, machineHeight);
    }

    private double displayHeight() {
        return isPortrait() ? Math.max(machineWidth, machineHeight) : Math.min(machineWidth, machineHeight);
    }

    private double[] physicalToScreen(double motorX, double motorY) {
        if (needsAxisSwap()) {
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
     * Uses shared CoordinateTransform (same math as driver/transforms.py).
     */
    private double[] transformPoint(Point2D rawPoint) {
        double[] motor = CoordinateTransform.transformPoint(
                rawPoint.x(), rawPoint.y(),
                effectiveSwap(), effectiveInvertX(), effectiveInvertY(),
                machineWidth, machineHeight,
                dataRotation, contentBoundsArray());
        motor[0] += alignOffsetX;
        motor[1] += alignOffsetY;
        return physicalToScreen(motor[0], motor[1]);
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
                "PhysPos: %.1f, %.1f | Align: %s | Rot: %d | Origin: %s | %s | FlipY: %s",
                currentX, currentY, canvasAlignment, dataRotation,
                machineOrigin, orientation, flipY ? "Y" : "N"), 10, h - 10);
        g2.drawString(String.format(
                "Bed: %.0fx%.0f | Offset: %.1f, %.1f | EffSwap: %s EffInvX: %s EffInvY: %s | AxisSwap: %s",
                machineWidth, machineHeight, alignOffsetX, alignOffsetY,
                effectiveSwap() ? "Y" : "N",
                effectiveInvertX() ? "Y" : "N",
                effectiveInvertY() ? "Y" : "N",
                needsAxisSwap() ? "Y" : "N"), 10, h - 24);
    }

    // ----- Internal Types -----
    private record Point2D(double x, double y) {
    }
}
