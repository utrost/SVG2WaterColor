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
    ad.pendown()
    time.sleep(0.5) # Wait for ink
    ad.penup()
    
    # 3. Wiggle/Swirl if needed
    if station.get('behavior') == 'dip_swirl':
        print("  > Swirling brush...")
        # Simulate swirl movement
        ad.pendown()
        ad.lineto(station['x'] + 2, station['y'])
        ad.lineto(station['x'] - 2, station['y'])
        ad.moveto(station['x'], station['y'])
        ad.penup()
        
    print("--- Refill Complete ---")

def execute_layer(ad, layer, report_pos=False):
    print(f"\n=== Starting Layer: {layer['id']} (Station: {layer['stationId']}) ===")
    input("Press Enter to start this layer (Ensure correct paint is ready)...")
    
    commands = layer['commands']
    for cmd in commands:
        op = cmd['op']
        
        if op == "MOVE":
            print(f"  [MOVE] To ({cmd['x']}, {cmd['y']})")
            ad.moveto(cmd['x'], cmd['y'])
            if report_pos:
                print(f"POS:X:{cmd['x']}:Y:{cmd['y']}")
                sys.stdout.flush()
            
        elif op == "DRAW":
            # DRAW command has a list of points
            print(f"  [DRAW] Polyline with {len(cmd['points'])} points")
            points = cmd['points']
            for p in points:
                # print(f"    -> Lineto ({p['x']}, {p['y']})") # Too verbose?
                ad.lineto(p['x'], p['y'])
                if report_pos:
                    print(f"POS:X:{p['x']}:Y:{p['y']}")
                    sys.stdout.flush()
                
        elif op == "REFILL":
            perform_refill(ad, cmd['stationId'])
            
    print(f"=== Layer {layer['id']} Complete ===")

def load_station_config():
    # Look for stations.json in CWD or script dir
    paths = ["stations.json", os.path.join(os.path.dirname(__file__), "stations.json")]
    for p in paths:
        if os.path.exists(p):
            print(f"INFO: Loading Station Configuration from {p}")
            try:
                with open(p, 'r') as f:
                    data = json.load(f)
                    # Merge into config.STATIONS or replace
                    # We will replace entries
                    for k, v in data.items():
                        config.STATIONS[k] = v
                return
            except Exception as e:
                print(f"ERROR: Failed to load stations.json: {e}")

def main():
    parser = argparse.ArgumentParser(description='Watercolor Driver')
    parser.add_argument('input', help='Input JSON file')
    parser.add_argument('--mock', action='store_true', help='Force Mock Mode')
    parser.add_argument('--speed-down', type=int, default=25, help='Pen Down Speed (1-100)')
    parser.add_argument('--speed-up', type=int, default=75, help='Pen Up Speed (1-100)')
    parser.add_argument('--report-position', action='store_true', help='Report realtime position for GUI')
    args = parser.parse_args()

    # Load Station Config Early
    load_station_config()

    # Initialize Driver
    if args.mock:
        print("INFO: Force Mock Mode selected.")
        ad = MockAxiDraw()
    elif axidraw is None:
        print("WARNING: pyaxidraw not found. Falling back to MOCK Axidraw.")
        ad = MockAxiDraw()
    else:
        print("INFO: Initializing REAL AxiDraw...")
        ad = axidraw.AxiDraw() 

    # Enter Interactive Context
    print("INFO: Entering Interactive Mode...")
    ad.interactive()
    
    # Attempt Connection
    print("INFO: Connecting to AxiDraw...")
    connected = ad.connect()
    
    if not connected:
        print("ERROR: Could not connect to AxiDraw! Check USB connection and power.")
        if not args.mock:
            print("       Exiting...")
            sys.exit(1)
        else:
            print("       Continuing because we are in Mock mode (or fell back to it).")

    print("INFO: Connection Successful.")
    
    # Setup Options
    print(f"INFO: Setting Pen Heights -> UP: {config.PEN_HEIGHTS['UP']}%, DOWN: {config.PEN_HEIGHTS['DOWN']}%")
    ad.options.pen_pos_up = config.PEN_HEIGHTS["UP"]
    ad.options.pen_pos_down = config.PEN_HEIGHTS["DOWN"]

    print(f"INFO: Setting Speeds -> Draw: {args.speed_down}%, Travel: {args.speed_up}%")
    ad.options.speed_pendown = args.speed_down
    ad.options.speed_penup = args.speed_up
    
    # Init options for interactive mode
    print("INFO: Updating AxiDraw options...")
    ad.update()

    # Load Data
    print(f"INFO: Loading input file: {args.input}")
    data = load_json(args.input)
    print(f"INFO: Loaded {data['metadata']['source']}. Total Layers: {len(data['layers'])}")

    # Set Units based on metadata
    if data['metadata'].get('units') == 'mm':
        print("INFO: Configuring AxiDraw for Millimeters (units=2)...")
        ad.options.units = 2
        ad.update()


    try:
        for layer in data['layers']:
            execute_layer(ad, layer, report_pos=args.report_position)
            
        # Return to home
        print("\nPlot Complete. Returning Home.")
        ad.moveto(0, 0)
        ad.disconnect()
        
    except KeyboardInterrupt:
        print("\nAborted by User.")
        ad.penup()
        ad.disconnect()

if __name__ == "__main__":
    main()
