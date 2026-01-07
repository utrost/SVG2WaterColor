package org.trostheide.watercolorprocessor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.parser.AWTPathProducer;
import org.apache.batik.parser.PathParser;
import org.apache.batik.util.XMLResourceDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.trostheide.watercolorprocessor.dto.*;
import org.trostheide.watercolorprocessor.dto.command.*;
import org.w3c.dom.*;

import java.awt.Shape;
import java.awt.geom.PathIterator;
import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Core logic service for the Watercolor Processor.
 * Handles Multi-Layer SVG processing for watercolor plotting.
 */
public class ProcessorService {

    private static final Logger logger = LoggerFactory.getLogger(ProcessorService.class);
    private final ObjectMapper objectMapper;

    // Global state for the processing run
    private int commandCounter = 1;

    public ProcessorService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public void process(File inputFile, File outputFile, double maxDrawDistance, String defaultStationId,
            double curveApproximation) {
        logger.info("Starting processing for: {}", inputFile.getName());

        try {
            // 1. Load SVG
            String parser = XMLResourceDescriptor.getXMLParserClassName();
            SAXSVGDocumentFactory f = new SAXSVGDocumentFactory(parser);
            Document doc = f.createDocument(inputFile.toURI().toString());

            // 2. Identify Layers
            List<LayerProcessingContext> layersToProcess = identifyLayers(doc, defaultStationId);

            // 3. Process Each Layer
            List<Layer> resultLayers = new ArrayList<>();
            BoundsBuilder globalBounds = new BoundsBuilder();
            PathParser pathParser = new PathParser();
            AWTPathProducer pathProducer = new AWTPathProducer();
            pathParser.setPathHandler(pathProducer);

            int totalCommands = 0;

            for (LayerProcessingContext ctx : layersToProcess) {
                logger.info("Processing Layer: '{}' mapped to Station: '{}'", ctx.layerName, ctx.stationId);

                List<Command> layerCommands = generateCommandsForLayer(
                        ctx.rootNode,
                        ctx.stationId,
                        maxDrawDistance,
                        curveApproximation,
                        pathParser,
                        pathProducer,
                        globalBounds);

                if (!layerCommands.isEmpty()) {
                    resultLayers.add(new Layer(ctx.layerName, ctx.stationId, layerCommands));
                    totalCommands += layerCommands.size();
                } else {
                    logger.warn("Layer '{}' contained no drawable paths.", ctx.layerName);
                }
            }

            // 4. Build Output
            Metadata metadata = new Metadata(
                    inputFile.getName(),
                    Instant.now(),
                    "MULTI_LAYER", // Station ID is now per-layer
                    "mm",
                    totalCommands,
                    globalBounds.build());

            ProcessorOutput output = new ProcessorOutput(metadata, resultLayers);

            // 5. Write JSON
            objectMapper.writeValue(outputFile, output);
            logger.info("Successfully wrote {} layers with {} total commands to {}",
                    resultLayers.size(), totalCommands, outputFile.getAbsolutePath());

        } catch (Exception e) {
            logger.error("Fatal Error during SVG processing", e);
            throw new RuntimeException(e);
        }
    }

    // --- Layer Identification ---

    private record LayerProcessingContext(String layerName, String stationId, Element rootNode) {
    }

    private List<LayerProcessingContext> identifyLayers(Document doc, String defaultStationId) {
        List<LayerProcessingContext> contexts = new ArrayList<>();
        Element root = doc.getDocumentElement();

        // Scan direct children for Inkscape Layers
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals("g")) {
                Element g = (Element) n;
                // Check for inkscape:groupmode="layer"
                if ("layer".equals(g.getAttribute("inkscape:groupmode")) ||
                        "layer".equals(g.getAttributeNS("http://www.inkscape.org/namespaces/inkscape", "groupmode"))) {

                    String label = g.getAttribute("inkscape:label");
                    if (label.isEmpty()) {
                        label = g.getAttributeNS("http://www.inkscape.org/namespaces/inkscape", "label");
                    }

                    // Renaming Strategy: User wants generic Layer1, Layer2... regardless of
                    // Inkscape label.
                    // The label is still useful for logging/debugging.
                    String genericId = "Layer" + (contexts.size() + 1);
                    String stationId = genericId;
                    String layerName = (label != null && !label.isEmpty()) ? label + " (" + genericId + ")" : genericId;

                    contexts.add(new LayerProcessingContext(layerName, stationId, g));
                }
            }
        }

        // Fallback: If no explicit layers found, treat the whole document root as one
        // layer
        if (contexts.isEmpty()) {
            logger.info("No explicit Inkscape layers found. Processing entire document as default layer.");
            contexts.add(new LayerProcessingContext("Default", defaultStationId, root));
        }

        return contexts;
    }

    // --- Core Generation Logic ---

    private List<Command> generateCommandsForLayer(Element layerRoot, String stationId, double maxDist,
            double curveStep,
            PathParser parser, AWTPathProducer producer, BoundsBuilder bounds) {
        List<Command> cmds = new ArrayList<>();
        List<Node> drawables = new ArrayList<>();
        collectDrawableElements(layerRoot, drawables);

        if (drawables.isEmpty())
            return cmds;

        // Initial Refill for this layer
        cmds.add(new RefillCommand(commandCounter++, stationId));
        double currentPaintDist = 0.0;

        for (Node node : drawables) {
            String d = getRawPathData(node);
            if (d == null)
                continue;

            parser.parse(d);
            Shape shape = producer.getShape();
            PathIterator pi = shape.getPathIterator(null, curveStep);

            double[] coords = new double[6];
            Point currentPos = null;
            Point startPoint = null;
            List<Point> currentStroke = new ArrayList<>();

            while (!pi.isDone()) {
                int type = pi.currentSegment(coords);
                switch (type) {
                    case PathIterator.SEG_MOVETO:
                        if (!currentStroke.isEmpty()) {
                            finishStroke(cmds, currentStroke, bounds);
                        }
                        double mx = coords[0];
                        double my = coords[1];
                        MoveCommand mc = new MoveCommand(commandCounter++, mx, my);
                        cmds.add(mc);
                        bounds.add(mx, my);
                        currentPos = new Point(mx, my);
                        startPoint = currentPos;
                        currentStroke.add(currentPos);
                        break;

                    case PathIterator.SEG_LINETO:
                        Point target = new Point(coords[0], coords[1]);
                        double dist = distance(currentPos, target);

                        while (currentPaintDist + dist > maxDist) {
                            double rem = maxDist - currentPaintDist;
                            Point split = interpolate(currentPos, target, rem, dist);

                            currentStroke.add(split);
                            finishStroke(cmds, currentStroke, bounds);

                            // Refill
                            cmds.add(new RefillCommand(commandCounter++, stationId));
                            MoveCommand ret = new MoveCommand(commandCounter++, split.x(), split.y());
                            cmds.add(ret);
                            bounds.add(split.x(), split.y());

                            currentPaintDist = 0.0;
                            currentPos = split;
                            currentStroke.add(currentPos);
                            dist = distance(currentPos, target);
                        }
                        currentStroke.add(target);
                        currentPaintDist += dist;
                        currentPos = target;
                        break;

                    case PathIterator.SEG_CLOSE:
                        if (startPoint != null && currentPos != null && !startPoint.equals(currentPos)) {
                            double cDist = distance(currentPos, startPoint);
                            while (currentPaintDist + cDist > maxDist) {
                                double rem = maxDist - currentPaintDist;
                                Point split = interpolate(currentPos, startPoint, rem, cDist);
                                currentStroke.add(split);
                                finishStroke(cmds, currentStroke, bounds);

                                cmds.add(new RefillCommand(commandCounter++, stationId));
                                MoveCommand ret = new MoveCommand(commandCounter++, split.x(), split.y());
                                cmds.add(ret);
                                bounds.add(split.x(), split.y());

                                currentPaintDist = 0.0;
                                currentPos = split;
                                currentStroke.add(currentPos);
                                cDist = distance(currentPos, startPoint);
                            }
                            currentStroke.add(startPoint);
                            currentPaintDist += cDist;
                            currentPos = startPoint;
                        }
                        break;
                }
                pi.next();
            }
            if (!currentStroke.isEmpty()) {
                finishStroke(cmds, currentStroke, bounds);
            }
        }
        return cmds;
    }

    private void finishStroke(List<Command> cmds, List<Point> stroke, BoundsBuilder bounds) {
        DrawCommand dc = new DrawCommand(commandCounter++, new ArrayList<>(stroke));
        cmds.add(dc);
        for (Point p : stroke)
            bounds.add(p.x(), p.y());
        stroke.clear();
    }

    // --- Helpers ---

    private void collectDrawableElements(Node node, List<Node> result) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            String tagName = ((Element) node).getTagName();
            // Added basic primitives: circle, ellipse, line, polyline, polygon
            if (tagName.equals("path") || tagName.equals("rect") ||
                    tagName.equals("circle") || tagName.equals("ellipse") ||
                    tagName.equals("line") || tagName.equals("polyline") || tagName.equals("polygon")) {
                result.add(node);
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++)
            collectDrawableElements(children.item(i), result);
    }

    private String getRawPathData(Node node) {
        if (node.getNodeType() != Node.ELEMENT_NODE)
            return null;
        Element el = (Element) node;
        String tagName = el.getTagName();

        if (el.hasAttribute("transform")) {
            // Warn once per element but skip to avoid complex matrix math implementation in
            // this iteration
            logger.warn("Skipping transformed element '{}'. Convert to path in Inkscape first.", tagName);
            return null;
        }

        try {
            if ("path".equals(tagName))
                return el.getAttribute("d");

            if ("rect".equals(tagName)) {
                double x = d(el, "x");
                double y = d(el, "y");
                double w = d(el, "width");
                double h = d(el, "height");
                return String.format(java.util.Locale.US, "M%f %f L%f %f L%f %f L%f %f Z", x, y, x + w, y, x + w, y + h,
                        x, y + h);
            }

            if ("circle".equals(tagName)) {
                double cx = d(el, "cx");
                double cy = d(el, "cy");
                double r = d(el, "r");
                // Two arcs to make a circle
                return String.format(java.util.Locale.US, "M %f %f A %f %f 0 1 0 %f %f A %f %f 0 1 0 %f %f Z",
                        cx - r, cy, r, r, cx + r, cy, r, r, cx - r, cy);
            }

            if ("ellipse".equals(tagName)) {
                double cx = d(el, "cx");
                double cy = d(el, "cy");
                double rx = d(el, "rx");
                double ry = d(el, "ry");
                return String.format(java.util.Locale.US, "M %f %f A %f %f 0 1 0 %f %f A %f %f 0 1 0 %f %f Z",
                        cx - rx, cy, rx, ry, cx + rx, cy, rx, ry, cx - rx, cy);
            }

            if ("line".equals(tagName)) {
                return String.format(java.util.Locale.US, "M %f %f L %f %f", d(el, "x1"), d(el, "y1"), d(el, "x2"),
                        d(el, "y2"));
            }

            if ("polyline".equals(tagName) || "polygon".equals(tagName)) {
                String points = el.getAttribute("points").trim();
                if (points.isEmpty())
                    return null;
                // Simple conversion: points="x1,y1 x2,y2" -> M x1 y1 L x2 y2 ...
                // Note: This is a naive regex replacement for demonstration.
                // Ideally parse numbers, but Batik PathParser expects standard path commands.
                // We'll assume space/comma separation.
                String[] pts = points.split("[\\s,]+");
                if (pts.length < 2)
                    return null;

                StringBuilder sb = new StringBuilder("M ").append(pts[0]).append(" ").append(pts[1]);
                for (int i = 2; i < pts.length - 1; i += 2) {
                    sb.append(" L ").append(pts[i]).append(" ").append(pts[i + 1]);
                }
                if ("polygon".equals(tagName))
                    sb.append(" Z");
                return sb.toString();
            }

        } catch (Exception e) {
            logger.warn("Failed to convert primitive '{}' to path: {}", tagName, e.getMessage());
            return null;
        }
        return null;
    }

    private double d(Element e, String attr) {
        String v = e.getAttribute(attr);
        return v.isEmpty() ? 0.0 : Double.parseDouble(v);
    }

    private double distance(Point p1, Point p2) {
        return Math.sqrt(Math.pow(p2.x() - p1.x(), 2) + Math.pow(p2.y() - p1.y(), 2));
    }

    private Point interpolate(Point start, Point end, double distToTravel, double totalDist) {
        if (totalDist == 0)
            return start;
        double ratio = distToTravel / totalDist;
        return new Point(start.x() + (end.x() - start.x()) * ratio, start.y() + (end.y() - start.y()) * ratio);
    }

    private static class BoundsBuilder {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;

        void add(double x, double y) {
            if (x < minX)
                minX = x;
            if (y < minY)
                minY = y;
            if (x > maxX)
                maxX = x;
            if (y > maxY)
                maxY = y;
        }

        Bounds build() {
            if (minX == Double.MAX_VALUE)
                return Bounds.empty();
            return new Bounds(minX, minY, maxX, maxY);
        }
    }
}