# SVG2WaterColor

A two-stage pipeline that transforms multi-layered SVG vector designs into physical watercolor paintings via pen plotter. The system handles the unique challenges of painting with real brushes and water-based paint: automatic refill scheduling, multi-station paint management, and precise coordinate mapping between digital canvas and physical machine.

## How It Works

```
SVG (Inkscape layers) → Java Processor → commands.json → Python Driver → Physical Plot
```

**Stage 1 — Java Preprocessor:** Parses the SVG, identifies color layers (Inkscape layer names = paint station IDs), converts all primitives to paths, linearizes curves, segments strokes by paint capacity (`maxDrawDistance`), and inserts automatic refill commands.

**Stage 2 — Python Driver:** Reads the command JSON and drives the physical plotter via pyaxidraw or G-Code. Handles pen up/down, refill dip sequences at configured station coordinates, inter-layer brush changes (with user prompts), and real-time position reporting.

## Key Features

- **Paint Capacity Management** — Tracks brush ink distance, auto-inserts REFILL commands with calculated split points when strokes exceed capacity
- **Multi-Layer / Multi-Color** — SVG layers map to physical paint stations, each with configurable XY position and dip behavior
- **Primitive Normalization** — Automatically converts `<rect>`, `<circle>`, `<ellipse>`, `<line>`, `<polyline>`, `<polygon>` to paths
- **Curve Linearization** — PathIterator-based, configurable step size (default 0.5mm)
- **Auto-Scaling** — Fit-to-page for A5/A4/A3/XL with padding
- **Swing GUI** — SVG processing, live visualization (digital twin), station management, plotter settings, manual jog controls
- **Mock Mode** — Full simulation without hardware for testing and preview
- **Coordinate Mapping** — Handles axis inversion, XY swap, data rotation, canvas alignment (any corner or center)

## Prerequisites

### Java (Stage 1)
- Java 17+
- Maven 3.6+

### Python (Stage 2)
- Python 3.8+
- Dependencies: `pip install -r driver/requirements.txt`
- AxiDraw software (for pyaxidraw API) or G-Code compatible plotter

## Build & Run

### Build Java Processor
```bash
mvn clean package
```

### Run GUI
```bash
# Linux/macOS
java -jar target/watercolor-processor-1.0-SNAPSHOT.jar --gui

# Windows
run_gui.bat
```

### Run CLI (Java Processor)
```bash
java -jar target/watercolor-processor-1.0-SNAPSHOT.jar \
  -i input.svg -o commands.json -f A4 -p 10 -d 150
```

| Flag | Description | Default |
|---|---|---|
| `-i` | Input SVG file | — |
| `-o` | Output JSON file | — |
| `-d` | Max draw distance before refill (mm) | — |
| `-s` | Default station ID | — |
| `-c` | Curve approximation step (mm) | 0.5 |
| `-f` | Fit to format (A5/A4/A3/XL) | none |
| `-p` | Padding (mm) | 10.0 |

### Run Python Driver
```bash
python driver/driver.py commands.json [OPTIONS]
```

Key options: `--mock`, `--verbose`, `--config settings.json`, `--canvas-align center`, `--speed-down 25`

See [Architecture Documentation](docs/architecture.md) for the complete system specification and CLI reference.

## Project Structure

```
├── src/main/java/.../watercolorprocessor/
│   ├── WatercolorProcessor.java    # CLI entry point
│   ├── ProcessorService.java       # SVG parsing, segmentation, refill logic
│   ├── dto/                        # Data structures (Command, Layer, Point, etc.)
│   └── gui/                        # Swing UI (6 panels)
│       ├── MainFrame.java
│       ├── ProcessorPanel.java     # SVG processing controls
│       ├── PlotterPanel.java       # Driver control & live plotting
│       ├── SettingsPanel.java      # Hardware & station config
│       └── VisualizationPanel.java # Digital twin / live view
├── driver/
│   ├── driver.py                   # Hardware driver (pyaxidraw/G-Code)
│   ├── transforms.py               # Coordinate transformations
│   ├── config.py                   # Station & hardware config loader
│   └── mock_axidraw.py             # Simulation backend
├── validation/                     # Manual test procedures (5 scripts)
├── docs/
│   └── architecture.md             # Full architecture & system spec
├── stations.json                   # Default station configuration
└── Requirements.md                 # Hardware constraints
```

## Command JSON Format

The contract between Java processor and Python driver:

```json
{
  "metadata": { "source": "input.svg", "units": "mm", "bounds": {...} },
  "layers": [
    {
      "id": "red_wash",
      "stationId": "red_wash",
      "commands": [
        { "op": "REFILL", "id": 1, "stationId": "red_wash" },
        { "op": "MOVE", "id": 2, "x": 10.5, "y": 20.0 },
        { "op": "DRAW", "id": 3, "points": [{"x": 10.5, "y": 20.0}, ...] }
      ]
    }
  ]
}
```

## Tech Stack

- **Java 17** — SVG processing (Apache Batik 1.17), JSON (Jackson), CLI (Commons CLI), GUI (Swing)
- **Python 3** — Hardware driver (pyaxidraw), coordinate transforms, mock simulation
- **3,030 LOC Java** + **728 LOC Python**

## License

Private repository. All rights reserved.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).
