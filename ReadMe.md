# **Watercolor Plotting Workflow: Architecture & Requirements**

## **1\. High-Level Architecture**

The system transforms a multi-layered vector design into physical watercolor strokes using a pen plotter. The workflow is decoupled into three stages to ensure scalability, modularity, and precise control over the "analog" variables (paint flow, brush refilling).

### **Data Flow**

1. **Design (Input):** input.svg (Multi-layer SVG).
    * *Convention:* Inkscape Layer Names correspond to **Station IDs** (e.g., red\_wash, blue\_detail).
2. **Stage 1 \- Preparation (Java):** Parses the SVG, identifies layers, converts shapes to paths, segments lines based on paint capacity, and inserts refill commands.
    * *Input:* input.svg \+ Configuration.
    * *Output:* commands.json (Multi-layer, sequential instructions).
3. **Stage 2 \- Execution (Python):** Reads the command structure and drives the hardware via pyaxidraw (or G-code compiler).
    * *Input:* commands.json.
    * *Output:* Physical Plot (Iterating through layers with pauses for brush changes).

## **2\. Stage 1: Java Pre-Processor (Preparation)**

**Goal:** Convert vector geometry into a linear sequence of drawing and logistical (refill) commands, grouped by color layer.

### **2.0 Project Specifications**

* **Project Name:** Watercolor Processor
* **Package:** org.trostheide.watercolorprocessor
* **Runtime:** Java 17 (LTS)
* **Build Tool:** Maven
* **Core Libraries:** Apache Batik (SVG), Jackson (JSON), Apache Commons CLI.

### **2.1 Inputs**

The Java application requires a configuration object (passed via CLI):

| Parameter | Type | Description |
| :---- | :---- | :---- |
| inputFile (-i) | String | Path to the input SVG file. |
| outputFile (-o) | String | Path where the resulting JSON will be saved. |
| maxDrawDistance (-d) | Double | Maximum distance (mm) the brush can travel before requiring a refill. |
| defaultStationId (-s) | String | Fallback Station ID used if SVG layers are unnamed or missing. |
| curveApproximation (-c) | Double | Step size (mm) for linearizing curves. Default: 0.5. |

### **2.2 Functional Logic**

1. **Layer Identification:**
    * Scans the SVG DOM for \<g\> elements with inkscape:groupmode="layer".
    * Extracts the inkscape:label to use as the **Station ID** (e.g., "red\_wash").
    * If no layers are found, processes the root document using the defaultStationId.
2. **Primitive Normalization:**
    * Recursively collects drawable elements.
    * Automatically converts primitives (\<rect\>, \<circle\>, \<ellipse\>, \<line\>, \<polyline\>, \<polygon\>) into standard SVG Path definitions (d="...").
3. **Linearization:**
    * Converts all paths into a simplified list of PolyLines (sequences of X,Y points) using PathIterator.
4. **Segmentation & Refill Insertion:**
    * Iterates through the geometry of *each layer*.
    * **Initial Refill:** Inserts a REFILL command at the start of every layer.
    * **Refill Logic:** Tracks cumulative distance. If a segment exceeds remainingCapacity:
        * Calculates the split point.
        * Ends current stroke.
        * Inserts REFILL \-\> MOVE (return to split point).
        * Resumes drawing.
5. **Serialization:** Writes the ProcessorOutput (Metadata \+ List of Layers) to JSON.

## **3\. Interface: JSON Command Structure**

This file serves as the strict contract between the Java Pre-processor and the Python Driver.

### **3.1 Root Object**

{  
"metadata": {  
"source": "input\_design.svg",  
"generatedAt": 1763989354.734,  
"stationId": "MULTI\_LAYER",  
"units": "mm",  
"totalCommands": 450,  
"bounds": {  
"minX": 0.0, "minY": 0.0,  
"maxX": 210.0, "maxY": 297.0  
}  
},  
"layers": \[  
{  
"id": "red\_wash",  
"stationId": "red\_wash",  
"commands": \[ /\* Array of Command Objects \*/ \]  
},  
{  
"id": "blue\_detail",  
"stationId": "blue\_detail",  
"commands": \[ /\* Array of Command Objects \*/ \]  
}  
\]  
}

### **3.2 Command Objects**

Every command includes a unique, sequential id for traceability.  
Type 1: MOVE (Travel)  
Moves the head to a location with the brush raised.  
{  
"op": "MOVE",  
"id": 101,  
"x": 10.5,  
"y": 20.0  
}

Type 2: DRAW (PolyLine)  
Moves the head through a series of points with the brush lowered.  
{  
"op": "DRAW",  
"id": 102,  
"points": \[  
{ "x": 10.5, "y": 20.0 },  
{ "x": 15.0, "y": 25.5 }  
\]  
}

Type 3: REFILL (Logistics)  
Instructs the driver to perform a refill sequence at the designated station.  
{  
"op": "REFILL",  
"id": 100,  
"stationId": "red\_wash"  
}

## **4\. Stage 2: Python Driver (Execution)**

**Goal:** Translate abstract commands into hardware stepper motor movements using pyaxidraw or G-code.

### **4.1 Physical Configuration (config.py)**

Decouples physical calibration from logical commands.  
STATIONS \= {  
"red\_wash":  { "x": 5.0, "y": 100.0, "behavior": "dip\_swirl" },  
"blue\_detail": { "x": 5.0, "y": 120.0, "behavior": "simple\_dip" }  
}

### **4.2 Functional Logic**

1. **Load JSON:** Parses the multi-layer JSON structure.
2. **Safety Check:** Validates metadata.bounds against physical limits immediately (O(1)).
3. **Layer Loop:**
    * Iterates through layers.
    * **Prompt:** Pauses and asks user to setup for station layer.stationId (Change Brush/Paint).
    * **Command Loop:** Executes MOVE, DRAW, REFILL for that layer.
4. **Refill Execution:**
    * Lookup stationId in config.py.
    * Perform physical dip sequence (Move \-\> Pen Down \-\> Wait/Swirl \-\> Pen Up).
    * *Note:* The logic is stateless; the return move to the drawing surface is handled by the *next* command in the JSON.