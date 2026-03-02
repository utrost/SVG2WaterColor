# Validation Script 03: GUI Settings & Configuration

**Objective:** Verify the Settings tab correctly manages plotter configuration, station definitions, and persists/loads state.

**Prerequisites:**
- GUI built and launchable (`./run_gui.sh`)

---

## Test Cases

### 3.1 Settings Tab Layout
| Field | Value |
|---|---|
| **Steps** | Launch GUI. Navigate to the Settings tab. |
| **Expected** | Tab displays: Plotter Model selector, Orientation controls (Invert X/Y, Swap XY, Visual Mirror), Pen Up/Down spinners, Speed spinners, Jog controls, Station table. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 3.2 Station CRUD (Add / Edit / Remove)
| Field | Value |
|---|---|
| **Steps** | 1. Add a new station: ID=`TestStation`, X=50, Y=100, Z=30, Behavior=`dip_swirl`. 2. Verify it appears in the table. 3. Select and edit it (change X to 60). 4. Remove it. |
| **Expected** | Station appears in table on Add. Edits update the row. Remove deletes the row. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 3.3 Save Configuration
| Field | Value |
|---|---|
| **Steps** | Modify settings (change speed, add a station). Click "Save Config". |
| **Expected** | Config file saved. Active config path displayed in UI. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 3.4 Load Configuration on Startup
| Field | Value |
|---|---|
| **Steps** | Save a config with distinct values. Close and relaunch the GUI. |
| **Expected** | All saved values (speeds, pen heights, flags, stations) are restored on startup. No manual refresh needed. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 3.5 Manual Jog Controls
| Field | Value |
|---|---|
| **Steps** | In Settings tab, connect to plotter (or mock). Use directional jog buttons. |
| **Expected** | Each button sends a movement command. Console/log confirms direction and distance. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |

### 3.6 Pen Test Buttons
| Field | Value |
|---|---|
| **Steps** | Click "Test UP" and "Test DOWN" buttons. |
| **Expected** | Pen physically raises / lowers (or mock logs it). Button feedback is immediate. |
| **Result** | - [ ] PASSED  /  - [ ] FAILED |
| **Deviation** | |
| **Comment** | |
