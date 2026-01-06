import argparse
import json
import os
import sys
import time

# Import Config
import config

# Try to import real pyaxidraw, fallback to mock if missing or flag set
try:
    from pyaxidraw import axidraw
except ImportError:
    axidraw = None

from mock_axidraw import AxiDraw as MockAxiDraw

def load_json(path):
    with open(path, 'r') as f:
        return json.load(f)

def perform_refill(ad, station_id):
    station = config.STATIONS.get(station_id)
    if not station:
        print(f"Warning: Unknown station '{station_id}'. Using default.")
        station = config.STATIONS.get("default_station")
    
    print(f"--- REFILLING at {station_id} ---")
    
    # 1. Move to station
    ad.moveto(station['x'], station['y'])
    
    # 2. Dip (Custom behavior could go here, for now just down/up)
    # Note: Real AD requires modifying options to change "pen_pos_down" dynamically if we want deep dips
    # For now we simulate a simple dip
    print(f"  > Dipping into {station_id}...")
    ad.pen_down()
    time.sleep(0.5) # Wait for ink
    ad.pen_up()
    
    # 3. Wiggle/Swirl if needed
    if station.get('behavior') == 'dip_swirl':
        print("  > Swirling brush...")
        # Simulate swirl movement
        ad.pen_down()
        ad.lineto(station['x'] + 2, station['y'])
        ad.lineto(station['x'] - 2, station['y'])
        ad.moveto(station['x'], station['y'])
        ad.pen_up()
        
    print("--- Refill Complete ---")

def execute_layer(ad, layer):
    print(f"\n=== Starting Layer: {layer['id']} (Station: {layer['stationId']}) ===")
    input("Press Enter to start this layer (Ensure correct paint is ready)...")
    
    commands = layer['commands']
    for cmd in commands:
        op = cmd['op']
        
        if op == "MOVE":
            ad.moveto(cmd['x'], cmd['y'])
            
        elif op == "DRAW":
            # DRAW command has a list of points
            # We assume we are already at the start point (handled by previous MOVE or DRAW end)
            # But the JAVA processor sends DRAW as a PolyLine. 
            # The first point of DRAW is usually where we are, but let's be safe.
            points = cmd['points']
            for p in points:
                ad.lineto(p['x'], p['y'])
                
        elif op == "REFILL":
            perform_refill(ad, cmd['stationId'])
            
    print(f"=== Layer {layer['id']} Complete ===")

def main():
    parser = argparse.ArgumentParser(description='Watercolor Driver')
    parser.add_argument('input', help='Input JSON file')
    parser.add_argument('--mock', action='store_true', help='Force Mock Mode')
    args = parser.parse_args()

    # Initialize Driver
    if args.mock or axidraw is None:
        print("Initializing MOCK AxiDraw...")
        ad = MockAxiDraw()
    else:
        print("Initializing REAL AxiDraw...")
        ad = axidraw.AxiDraw() 

    ad.connect()
    
    # Setup Options
    ad.options.pen_pos_up = config.PEN_HEIGHTS["UP"]
    ad.options.pen_pos_down = config.PEN_HEIGHTS["DOWN"]
    ad.plot_setup()

    # Load Data
    data = load_json(args.input)
    print(f"Loaded {data['metadata']['source']}. Total Layers: {len(data['layers'])}")

    try:
        for layer in data['layers']:
            execute_layer(ad, layer)
            
        # Return to home
        print("\nPlot Complete. Returning Home.")
        ad.moveto(0, 0)
        ad.disconnect()
        
    except KeyboardInterrupt:
        print("\nAborted by User.")
        ad.pen_up()
        ad.disconnect()

if __name__ == "__main__":
    main()
