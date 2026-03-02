# Watercolor Processor - System Requirements

This document outlines the core requirements and immutable physical constraints of the Watercolor Processor system. It serves as the single source of truth for system behavior.

## 1. Hardware Constraints (Immutable)
These properties describe the physical machine and cannot be changed by software.

*   **Machine Size**: A3 Standard (~297mm width x 420mm height).
*   **Physical Orientation**: **Portrait** (Vertical). The machine is taller than it is wide.
*   **Physical Origin (Home)**: **Top Right Corner**. The machine reports `(0, 0)` when the head is at the top-right limit.
*   **Axis Directions**:
    *   **X-Axis**: Horizontal. Positive movement is **LEFT** (away from Origin).
    *   **Y-Axis**: Vertical. Positive movement is **DOWN** (away from Origin).
*   **Park Position**: Top Right `(0, 0)`.

## 2. Input Data Goals
*   **Formats**: JSON (derived from SVG).
*   **Input Coordinates**: Standard Canvas (Origin Top-Left, X Right, Y Down).
*   **Variability**: Input files may be any size (A4, A3, etc.) and any orientation (Portrait, Landscape).

## 3. Visualization Requirements
The GUI "Live View" must act as a **Digital Twin** of the physical machine.

*   **Orientation Accuracy**: The on-screen "Paper" must appear as Portrait (Vertical).
*   **Origin Sync**: The visual `(0,0)` marker must appear at the **Top Right** of the screen rectangle, matching the physical machine.
*   **Movement Sync**:
    *   When the Driver reports `X` increasing, the visual marker must move **Left**.
    *   When the Driver reports `Y` increasing, the visual marker must move **Down**.
*   **WYSIWYG**: The drawing shown on screen must strictly match what will be drawn on paper. Using "Mirror View" or tricks that desynchronize the screen from the paper is discouraged unless strictly for debugging.

## 4. Transformation Logic (Processing Pipeline)
To map *Input Data* (Step 2) to *Hardware* (Step 1), the system must support:

*   **Data Rotation**: The ability to visually rotate the *Drawing Content's Bounding Box* (e.g., by 90°) to accurately preview how a Landscape image maps onto the Portrait machine bed without altering the orientation of the raw SVG paths themselves.
*   **Corner Alignment**: The ability to snap the drawing content (after rotation) to any of the four corners of the physical bed (e.g., "Align Top Right" = Snap Drawing Corner to Machine Origin).
*   **Hardware Mapping**:
    *   **Swap XY**: Option to treat Logic X as Physical Y (and vice-versa) for rotated mounting.
    *   **Inversion**: Option to invert axis logic (0..Max vs Max..0) to match motor wiring.

## 5. Operational Requirements
*   **Persistence**: All alignment, rotation, and flag settings must be saved to config and restored automatically on startup.
*   **Startup State**: The application must launch in a "Ready" state with the visualization correctly representing the saved configuration immediately (no manual refresh needed).
