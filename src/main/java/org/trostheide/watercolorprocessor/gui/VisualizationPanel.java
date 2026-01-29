package org.trostheide.watercolorprocessor.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.*;
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
        repaint();
    }

    public void setDataInvertX(boolean invert) {
        this.invertX = invert;
        repaint();
    }

    public void setDataInvertY(boolean invert) {
        this.invertY = invert;
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

    // DEPRECATED: viewMirror is removed. This is a no-op for compatibility.
    public void setViewMirror(boolean mirror) {
        // No-op. Mirror view is no longer supported.
    }

    public VisualizationPanel() {
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createTitledBorder("Live View"));
    }

    // ----- Data Loading -----

    public void loadFromJson(File jsonFile) {
        allPaths.clear();
        currentX = 0;
        currentY = 0;

        rawMinX = Double.MAX_VALUE;
        rawMinY = Double.MAX_VALUE;
        rawMaxX = -Double.MAX_VALUE;
        rawMaxY = -Double.MAX_VALUE;

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

                                rawMinX = Math.min(rawMinX, x);
                                rawMaxX = Math.max(rawMaxX, x);
                                rawMinY = Math.min(rawMinY, y);
                                rawMaxY = Math.max(rawMaxY, y);
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
        double rawMinX = Double.MAX_VALUE, rawMaxX = -Double.MAX_VALUE;
        double rawMinY = Double.MAX_VALUE, rawMaxY = -Double.MAX_VALUE;

        for (List<Point2D> path : allPaths) {
            for (Point2D p : path) {
                rawMinX = Math.min(rawMinX, p.x());
                rawMaxX = Math.max(rawMaxX, p.x());
                rawMinY = Math.min(rawMinY, p.y());
                rawMaxY = Math.max(rawMaxY, p.y());
            }
        }

        // Step 2: Transform corners through rotation + swap/invert (like driver's
        // get_transformed_point)
        // The driver does NOT include rotation in get_transformed_point, but DOES in
        // transform_point.
        // This is inconsistent in the driver! But we'll match it.
        // Actually, looking at driver code more carefully:
        // - get_transformed_point does swap/invert only (no rotation)
        // - alignment is based on get_transformed_point output
        // - actual drawing uses transform_point which INCLUDES rotation
        //
        // So the driver's alignment doesn't account for rotation! This is likely a bug
        // in driver.
        // For now, let's match the driver's behavior (no rotation in bounds for
        // alignment).

        // Corners in raw coords
        double[][] corners = {
                { rawMinX, rawMinY }, { rawMaxX, rawMinY },
                { rawMinX, rawMaxY }, { rawMaxX, rawMaxY }
        };

        // Transform corners with swap/invert only (matching driver's
        // get_transformed_point)
        double tMinX = Double.MAX_VALUE, tMaxX = -Double.MAX_VALUE;
        double tMinY = Double.MAX_VALUE, tMaxY = -Double.MAX_VALUE;

        for (double[] c : corners) {
            double x = c[0], y = c[1];

            // Apply swap
            if (swapXY) {
                double temp = x;
                x = y;
                y = temp;
            }

            // Apply invert
            if (invertX) {
                x = machineWidth - x;
            }
            if (invertY) {
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

        // Calculate alignment offset based on TRANSFORMED bounds (matching driver)
        switch (canvasAlignment) {
            case "Top Right":
                // Content's physical "right" edge (tMinX after transforms) -> Machine origin
                // (0)
                // Content's physical "top" edge (tMinY) -> 0
                alignOffsetX = -tMinX;
                alignOffsetY = -tMinY;
                break;

            case "Top Left":
                alignOffsetX = machineWidth - tMaxX;
                alignOffsetY = -tMinY;
                break;

            case "Bottom Right":
                alignOffsetX = -tMinX;
                alignOffsetY = machineHeight - tMaxY;
                break;

            case "Bottom Left":
                alignOffsetX = machineWidth - tMaxX;
                alignOffsetY = machineHeight - tMaxY;
                break;

            case "Center":
                double contentW = tMaxX - tMinX;
                double contentH = tMaxY - tMinY;
                alignOffsetX = (machineWidth - contentW) / 2.0 - tMinX;
                alignOffsetY = (machineHeight - contentH) / 2.0 - tMinY;
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
     * Step 6: Simulate Driver transforms (SwapXY, InvertX, InvertY).
     */
    private Point2D simulateDriver(Point2D phys) {
        double x = phys.x();
        double y = phys.y();

        if (swapXY) {
            double temp = x;
            x = y;
            y = temp;
        }

        if (invertX) {
            x = machineWidth - x;
        }

        if (invertY) {
            y = machineHeight - y;
        }

        return new Point2D(x, y);
    }

    /**
     * Step 7: Map Physical coordinates to Screen coordinates for drawing.
     * 
     * Physical Machine (A3 model 2, NO swap):
     * - Motor X: 0-430 (long axis, horizontal in Landscape)
     * - Motor Y: 0-297 (short axis, vertical in Landscape)
     * - Origin: Motor (0,0) at top-right
     * 
     * Display (Portrait mode):
     * - Screen Width: 297 (short axis)
     * - Screen Height: 420 (long axis)
     * - Screen (0,0): top-left
     * 
     * When SwapXY is ENABLED:
     * - Motor X now uses the INPUT Y (which may be large)
     * - Motor Y now uses the INPUT X
     * - So Motor X values can exceed 297 (up to 420+)
     * - We need to swap the mapping
     */
    private double[] physicalToScreen(double physX, double physY) {
        if (swapXY) {
            // With SwapXY, motor coords are swapped relative to display:
            // - physX (was input Y) maps to screen Y (long axis, height)
            // - physY (was input X) maps to screen X (short axis, width)
            // Origin is still top-right, so Y grows down (physX to screenY directly)
            // and X grows left (physY to screenX with flip)
            return new double[] { machineWidth - physY, physX };
        } else {
            // Standard mapping: origin at top-right, X grows left
            return new double[] { machineWidth - physX, physY };
        }
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

        // Calculate scale to fit machine bed in panel
        double scaleX = (w - 40) / machineWidth;
        double scaleY = (h - 40) / machineHeight;
        double scale = Math.min(scaleX, scaleY);
        if (scale <= 0)
            scale = 1.0;

        // Center the machine bed in the panel
        double tx = 20 + (w - 40 - machineWidth * scale) / 2.0;
        double ty = 20 + (h - 40 - machineHeight * scale) / 2.0;

        AffineTransform old = g2.getTransform();
        g2.translate(tx, ty);
        g2.scale(scale, scale);

        // --- Draw Machine Bed ---
        g2.setColor(new Color(245, 245, 255));
        g2.fill(new java.awt.geom.Rectangle2D.Double(0, 0, machineWidth, machineHeight));
        g2.setColor(Color.LIGHT_GRAY);
        g2.setStroke(new BasicStroke((float) (1.0 / scale)));
        g2.draw(new java.awt.geom.Rectangle2D.Double(0, 0, machineWidth, machineHeight));

        // --- Draw Origin Marker (Physical 0,0 = Screen Top-Right) ---
        double[] originScreen = physicalToScreen(0, 0);
        g2.setColor(Color.ORANGE);
        double markerR = 5 / scale;
        g2.fill(new java.awt.geom.Ellipse2D.Double(originScreen[0] - markerR, originScreen[1] - markerR,
                markerR * 2, markerR * 2));
        g2.setColor(Color.DARK_GRAY);
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

        // --- Draw Paths ---
        g2.setColor(new Color(50, 50, 150));
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
        g2.setColor(Color.BLACK);
        g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        g2.drawString(String.format(
                "PhysPos: %.1f, %.1f | Align: %s | Rot: %d | Swap: %s | InvX: %s | InvY: %s",
                currentX, currentY, canvasAlignment, dataRotation,
                swapXY ? "Y" : "N", invertX ? "Y" : "N", invertY ? "Y" : "N"), 10, h - 10);
    }

    // ----- Internal Types -----
    private record Point2D(double x, double y) {
    }
}
