# Contributing to SVG2WaterColor

## Development Setup

### Java (Stage 1)
1. Ensure Java 17+ and Maven 3.6+ are installed
2. Build: `mvn clean package`
3. Run GUI: `java -jar target/watercolor-processor-1.0-SNAPSHOT.jar --gui`

### Python (Stage 2)
1. `pip install -r driver/requirements.txt`
2. Test with mock: `python driver/driver.py commands.json --mock --verbose`

## Architecture

The system is split into two independent stages connected by a JSON contract:

| Stage | Language | Responsibility |
|---|---|---|
| Preprocessor | Java 17 | SVG parsing, primitive normalization, segmentation, refill insertion |
| Driver | Python 3 | Hardware control, coordinate transforms, physical plotting |

**Key class:** `ProcessorService.java` (599 LOC) handles the core pipeline: layer identification → primitive normalization → linearization → segmentation → refill insertion.

## Code Style

- **Java:** Standard conventions, 4-space indent, Java 17 features
- **Python:** PEP 8, type hints where practical

## Commit Messages

Use conventional prefixes: `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`
