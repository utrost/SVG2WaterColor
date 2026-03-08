# Changelog

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- README.md (project overview, build instructions, architecture)
- CONTRIBUTING.md
- This CHANGELOG
- GitHub Actions CI (Java 17/21 + Python lint)

### Removed
- `.idea/` directory
- `__pycache__/` files
- `git_status.txt`

## [1.0-SNAPSHOT] — 2025

### Added
- Two-stage pipeline: Java SVG preprocessor → Python hardware driver
- Paint capacity management with automatic refill insertion
- Multi-layer/multi-color support (Inkscape layers → paint stations)
- Primitive normalization (rect, circle, ellipse, etc. → path)
- Curve linearization with configurable step size
- Auto-scaling (fit-to-page A5/A4/A3/XL)
- Swing GUI with 6 panels: Processor, Plotter, Settings, Visualization, Station Config
- Live visualization (digital twin) with real-time position tracking
- Python driver with pyaxidraw support, mock mode, coordinate transforms
- Canvas alignment (any corner or center), axis inversion, XY swap
- Manual jog controls and pen testing
- Configuration persistence (JSON)
- 5 validation test scripts
