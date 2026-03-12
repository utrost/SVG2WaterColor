# Validation Script 04: Live Visualization (Digital Twin)

**Objective:** Verify that the Live View panel accurately represents the physical machine state in real-time, acting as a Digital Twin per Requirements §3.

**Prerequisites:**
- GUI built and launchable (`./run_gui.sh`)
- Valid JSON command file

---

## Test Cases

### 4.1 Path Preview (Static)
| Field | Value |
|---|---|
| **Steps** | In the Plotter tab, select a JSON file. Observe the Live View panel before starting a plot. |
| **Expected** | All drawing paths rendered in light gray. Paper boundary visible. Content scaled to fit the panel. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 4.2 Real-Time Cursor Tracking
| Field | Value |
|---|---|
| **Steps** | Start a mock plot. Observe the Live View panel during execution. |
| **Expected** | Red dot/crosshair moves along the gray paths in sync with console `[MOVE]`/`[DRAW]` logs. Coordinate HUD updates. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 4.3 Portrait Orientation
| Field | Value |
|---|---|
| **Steps** | Observe the Live View panel shape. |
| **Expected** | The on-screen "paper" rectangle appears as Portrait (taller than wide), matching the A3 physical machine. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 4.4 Origin Sync (Top-Right)
| Field | Value |
|---|---|
| **Steps** | Start a mock plot. Observe where the cursor begins (position 0,0). |
| **Expected** | The `(0,0)` marker / initial cursor position is at the **Top Right** of the paper rectangle. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 4.5 Movement Direction Sync
| Field | Value |
|---|---|
| **Steps** | Watch the cursor as the driver reports increasing X and increasing Y values. |
| **Expected** | X increasing → cursor moves **Left**. Y increasing → cursor moves **Down**. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 4.6 Data Rotation Preview
| Field | Value |
|---|---|
| **Steps** | Set Data Rotation to 90° in Settings. Load a landscape SVG. Observe the Live View. |
| **Expected** | The bounding box of the drawing content is visually rotated to fit within the portrait paper. The content preview matches the expected physical output. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 4.7 Station Markers
| Field | Value |
|---|---|
| **Steps** | Configure stations with known X/Y positions. Observe the Live View. |
| **Expected** | Station positions rendered as labeled markers on the canvas at the correct coordinates. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |
