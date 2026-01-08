package org.trostheide.watercolorprocessor.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class VisualizationPanel extends JPanel {

    private final ObjectMapper mapper = new ObjectMapper();
    private List<List<Point2D>> allPaths = new ArrayList<>();

    private double currentX = 0;
    private double currentY = 0;

    // Bounds for scaling
    private double minX = 0, minY = 0, maxX = 300, maxY = 300; // Default A4/A3-ish range

    private boolean invertX = false;
    private boolean swapXY = false;

    public void setInvertX(boolean invertX) {
        this.invertX = invertX;
        repaint();
    }

    public void setSwapXY(boolean swapXY) {
        this.swapXY = swapXY;
        repaint();
    }

    public VisualizationPanel() {
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createTitledBorder("Live View"));
    }

    public void loadFromJson(File jsonFile) {
        allPaths.clear();
        currentX = 0;
        currentY = 0;

        try {
            JsonNode root = mapper.readTree(jsonFile);

            // Try to read bounds from metadata if available, otherwise just use paths
            if (root.has("metadata") && root.get("metadata").has("bounds")) {
                JsonNode b = root.get("metadata").get("bounds");
                minX = b.get("minX").asDouble();
                minY = b.get("minY").asDouble();
                maxX = b.get("maxX").asDouble();
                maxY = b.get("maxY").asDouble();
            }

            JsonNode layers = root.get("layers");
            if (layers != null) {
                for (JsonNode layer : layers) {
                    JsonNode commands = layer.get("commands");
                    for (JsonNode cmd : commands) {
                        if ("DRAW".equals(cmd.get("op").asText())) {
                            List<Point2D> stroke = new ArrayList<>();
                            JsonNode points = cmd.get("points");
                            for (JsonNode p : points) {
                                stroke.add(new Point2D(p.get("x").asDouble(), p.get("y").asDouble()));
                            }
                            if (!stroke.isEmpty()) {
                                allPaths.add(stroke);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace(); // Log error but don't crash
        }
        repaint();
    }

    public void updatePosition(double x, double y) {
        this.currentX = x;
        this.currentY = y;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // Calculate Scale
        double dataW = maxX - minX;
        double dataH = maxY - minY;

        if (swapXY) {
            double tmp = dataW;
            dataW = dataH;
            dataH = tmp;
        }

        if (dataW <= 0)
            dataW = 1;
        if (dataH <= 0)
            dataH = 1;

        double scaleX = (w - 40) / dataW; // 20px padding each side
        double scaleY = (h - 40) / dataH;
        double scale = Math.min(scaleX, scaleY);

        // Transform: Center the drawing
        double tx = 20 - (minX * scale) + (w - 40 - dataW * scale) / 2.0;
        double ty = 20 - (minY * scale) + (h - 40 - dataH * scale) / 2.0;

        AffineTransform old = g2.getTransform();
        g2.translate(tx, ty);
        g2.scale(scale, scale);

        if (invertX) {
            // If Inverted, we mirror effectively.
            // But we want to represent the Machine View where (0,0) is Top Right.
            // Standard: (0,0) at Top-Left.
            // If Machine (0,0) is Top-Right, then a coordinate of 10 is near the right
            // edge.
            // But we are drawing logical paths.
            // The physical moves received (currentX) are large values if inverted logic was
            // used in driver?
            // Actually, if we use "--invert-x", the driver calculates 'px = maxX - lx'.
            // So logical 0 becomes physical 430.
            // The driver reports 'px' (430).
            // If we draw a point at 430, it is at the "Bottom" (or Right) of the panel.
            // This matches Top-Left Origin logic: 0 is Left, 430 is Right.
            // BUT, user says: 0/0 is Top Right.
            // This means they interpret the machine's "Right" edge as 0.
            // The driver reports physical coordinates (e.g., 0 to 430).
            // Standard view: 0 is Left.
            // User view: 0 is Right.
            // So to match User Mental Model, we should FLIP the display.
            // Such that Physical 0 draws on the Right.
            // And Physical 430 draws on the Left.
            //
            // Transformation: x' = maxX - x (assuming maxX is the width).
            // To implement this in graphics transform:
            // Translate(dataW, 0) -> Scale(-1, 1).
            g2.translate(dataW, 0);
            if (swapXY) {
                g2.scale(-1, 1);
            }
        }

        // Draw Paper Border (optional, roughly)
        g2.setColor(new Color(240, 240, 240));
        g2.fill(new java.awt.geom.Rectangle2D.Double(minX, minY, dataW, dataH));

        // Draw Paths in Gray
        g2.setColor(Color.LIGHT_GRAY);
        g2.setStroke(new BasicStroke((float) (1.0 / scale))); // 1px visual stroke

        for (List<Point2D> path : allPaths) {
            if (path.isEmpty())
                continue;
            Path2D p2d = new Path2D.Double();
            double sx = path.get(0).x;
            double sy = path.get(0).y;
            if (swapXY)
                p2d.moveTo(sy, sx);
            else
                p2d.moveTo(sx, sy);

            for (int i = 1; i < path.size(); i++) {
                sx = path.get(i).x;
                sy = path.get(i).y;
                if (swapXY)
                    p2d.lineTo(sy, sx);
                else
                    p2d.lineTo(sx, sy);
            }
            g2.draw(p2d);
        }

        // Draw Current Position Cursor (Red Dot with Crosshair)
        g2.setColor(Color.RED);
        double r = 4.0 / scale; // constant visual size radius
        g2.fill(new Ellipse2D.Double(currentX - r, currentY - r, r * 2, r * 2));

        // Optional Crosshair
        g2.setStroke(new BasicStroke((float) (0.5 / scale)));
        double crossSize = 100.0 / scale; // large crosshair
        g2.draw(new Line2D.Double(currentX - crossSize, currentY, currentX + crossSize, currentY));
        g2.draw(new Line2D.Double(currentX, currentY - crossSize, currentX, currentY + crossSize));

        g2.setTransform(old);

        // Draw Coordinates Text HUD
        g2.setColor(Color.BLACK);
        g2.drawString(String.format("X: %.2f  Y: %.2f", currentX, currentY), 10, h - 10);
    }

    // Internal Point class to avoid AWt Point (int only) confusion
    private record Point2D(double x, double y) {
    }
}
