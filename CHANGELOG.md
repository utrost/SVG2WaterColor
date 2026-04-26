# Changelog

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- **G-code/GRBL backend** -- Full support for GRBL-compatible plotters over serial (pyserial). Three pen control modes: Servo (M280), Z-Axis, and M3/M5 (spindle/solenoid). Configurable serial port, baud rate, feed rates, servo angles, Z positions, and machine dimensions.
- **Backend abstraction** -- `PlotterBackend` ABC in `backend.py` with `AxiDrawBackend`, `GcodeBackend`, and `MockBackend` implementations. Backend factory in `driver.py`.
- **Machine Origin setting** -- Single dropdown to configure plotter home corner (Top-Left, Top-Right, Bottom-Left, Bottom-Right). Replaces confusing manual invertX/invertY checkboxes. Automatically derives correct axis inversions and origin-right flag.
- **`--machine-origin` CLI arg** -- Python driver accepts `--machine-origin {top-left,top-right,bottom-left,bottom-right}` to set origin from command line.
- **G-code settings UI** -- CardLayout panel in Settings tab swaps between AxiDraw and G-code configuration when backend is changed.
- **`GcodeSettings.java`** -- POJO for G-code backend configuration (serial port, baud rate, pen mode, feed rates, servo/Z positions, machine dimensions).
- Comprehensive project documentation (README.md, architecture.md, Requirements.md)

### Fixed
- **Visualization `physicalToScreen()` hardcoded top-right origin** -- Now correctly maps motor coordinates to screen pixels for any origin corner, not just top-right.
- **`--origin-right` always sent to driver** -- Now conditional on actual machine origin, fixing alignment offsets for left-origin plotters.
- **Visualization alignment calculation** -- Origin-aware left/right edge semantics now mirror the Python driver's `calculate_alignment_offset()` for all origin corners.
- **Jog button directions** -- Manual control buttons and arrow keys now adapt to the selected machine origin instead of hardcoding AxiDraw conventions.

### Changed
- GUI modernized with FlatDarkLaf dark theme
- Settings panel reorganized into Hardware, Coordinate Mapping, Paint Stations, and Manual Control sections
- Config format extended with `machineOrigin`, `backend`, and `gcode` fields
- Old config files without `machineOrigin` are automatically migrated from legacy `invertX`/`invertY` flags

### Removed
- `invertXCheckBox`, `invertYCheckBox`, `visualMirrorCheckBox` from Settings UI (replaced by Machine Origin dropdown)

## [1.0-SNAPSHOT] -- 2025

### Added
- Two-stage pipeline: Java SVG preprocessor -> Python hardware driver
- Paint capacity management with automatic refill insertion
- Multi-layer/multi-color support (Inkscape layers -> paint stations)
- Primitive normalization (rect, circle, ellipse, line, polyline, polygon -> path)
- Curve linearization with configurable step size
- Auto-scaling (fit-to-page A5/A4/A3/XL)
- Swing GUI with Process SVG, Plot, and Settings tabs
- Live visualization (digital twin) with real-time position tracking
- Python driver with pyaxidraw support, mock mode, coordinate transforms
- Canvas alignment (any corner or center), axis inversion, XY swap
- Manual jog controls and pen testing
- Configuration persistence (JSON)
- Validation test scripts
