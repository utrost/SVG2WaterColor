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
    
    print(f"--- REFILLING at {station_id} ({station['x']} mm / {station['y']} mm) ---")
    
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

def execute_layer(ad, layer, report_pos=False, verbose=False, model=1, invert_x=False, invert_y=False, swap_xy=False):
    print(f"\n=== Starting Layer: {layer['id']} (Station: {layer['stationId']}) ===")
    input("Press Enter to start this layer (Ensure correct paint is ready)...")
    
    # Model 1 (V3 A4): X ~300, Y ~218
    # Model 2 (V3 A3): X ~430, Y ~297
    maxX = 300.0 if model == 1 else 430.0
    maxY = 215.0 if model == 1 else 297.0
    
    # If we are swapping XY, then the "Y" axis that we might invert is actually physically X?
    # Let's clarify the logic.
    # Standard: X is Horizontal (Long), Y is Vertical (Short).
    # Invert X: Mirror Horizontal.
    # Invert Y: Mirror Vertical.
    # Swap XY: X becomes Vertical, Y becomes Horizontal.
    #
    # The transform order is crucial.
    # Usually: 1. Swap? 2. Invert?
    # The current code does: 1. Swap 2. Invert Physical X.
    #
    # If we want "Invert Y" to mean "Invert the axis that IS Y in the SVG", then we should do it BEFORE Swap?
    # Or probably "Invert Physical Y" (the axis that is Y on the machine).
    #
    # Existing 'invert_x' implementation:
    # 1. Swap (if set): px, py = ty, tx
    # 2. Invert Physical X (if set): px = maxX - px
    #
    # So 'invert_x' here means "Invert the coordinate mapped to the Machine X axis".
    # Therefore, 'invert_y' should mean "Invert the coordinate mapped to the Machine Y axis".
    
    def transform_point(tx, ty):
        # 1. Swap if requested
        if swap_xy:
            px, py = ty, tx
        else:
            px, py = tx, ty
            
        # 2. Invert Physical X if requested
        if invert_x:
            px = maxX - px
            
        # 3. Invert Physical Y if requested
        if invert_y:
            py = maxY - py
            
        return px, py

    commands = layer['commands']
    for cmd in commands:
        op = cmd['op']
        
        if op == "MOVE":
            px, py = transform_point(cmd['x'], cmd['y'])
            print(f"  [MOVE] To ({px:.2f}, {py:.2f}) [Orig: ({cmd['x']}, {cmd['y']})]")
            ad.moveto(px, py)
            if report_pos:
                print(f"POS:X:{px}:Y:{py}")
                sys.stdout.flush()
            
        elif op == "DRAW":
            print(f"  [DRAW] Polyline with {len(cmd['points'])} points")
            points = cmd['points']
            for p in points:
                px, py = transform_point(p['x'], p['y'])
                
                if verbose:
                    print(f"    -> Lineto ({px:.2f}, {py:.2f})")
                    if py > maxY or px > maxX: 
                        print(f"       [WARNING] Coordinate out of bounds for Model {model} (Max: {maxX}x{maxY})!")
                
                ad.lineto(px, py)
                if report_pos:
                    print(f"POS:X:{px}:Y:{py}")
                    sys.stdout.flush()
                
        elif op == "REFILL":
            perform_refill(ad, cmd['stationId'])
            
    print(f"=== Layer {layer['id']} Complete ===")

def load_station_config(custom_path=None):
    # Look for config.json first (New Format), then stations.json (Old Format)
    # in CWD or script dir
    files_to_check = []
    
    if custom_path:
        files_to_check.append(custom_path)
    
    # Default Paths
    files_to_check.append("config.json")
    files_to_check.append(os.path.join(os.path.dirname(__file__), "config.json"))
    
    for p in files_to_check:
        if os.path.exists(p):
            print(f"INFO: Loading Configuration from {p}")
            try:
                with open(p, 'r') as f:
                    data = json.load(f)
                    
                    # 1. Load Stations
                    stations_data = data
                    if 'stations' in data:
                         stations_data = data['stations']
                         
                    # Merge into config.STATIONS or replace
                    for k, v in stations_data.items():
                        config.STATIONS[k] = v

                    # 2. Return General Settings if present
                    if 'general' in data:
                        return data['general']
                
                return {} # Return empty dict if no general settings found but file loaded
            except Exception as e:
                print(f"ERROR: Failed to load config {p}: {e}")

    # Fallback to legacy stations.json
    paths = ["stations.json", os.path.join(os.path.dirname(__file__), "stations.json")]
    for p in paths:
        if os.path.exists(p):
            print(f"INFO: Loading Station Configuration from {p}")
            try:
                with open(p, 'r') as f:
                    data = json.load(f)
                    for k, v in data.items():
                        config.STATIONS[k] = v
                return
            except Exception as e:
                print(f"ERROR: Failed to load stations.json: {e}")

def main():
    parser = argparse.ArgumentParser(description='Watercolor Driver')
    parser.add_argument('input', nargs='?', help='Input JSON file (optional if --manual-pen used)')
    parser.add_argument('--invert-x', action='store_true', help='Invert X Axis (Mirror Plot) for standard SVGs')
    parser.add_argument('--invert-y', action='store_true', help='Invert Y Axis (Mirror Plot)')
    parser.add_argument('--swap-xy', action='store_true', help='Swap X and Y Axis')
    parser.add_argument('--mock', action='store_true', help='Force Mock Mode')
    parser.add_argument('--verbose', action='store_true', help='Enable verbose logging')
    parser.add_argument('--model', type=int, default=1, help='AxiDraw Model (1=A4, 2=A3/XL)')
    parser.add_argument('--speed-down', type=int, default=25, help='Pen Down Speed (1-100)')
    parser.add_argument('--speed-up', type=int, default=75, help='Pen Up Speed (1-100)')
    parser.add_argument('--pen-up', type=int, help='Pen Up Height (0-100)')
    parser.add_argument('--pen-down', type=int, help='Pen Down Height (0-100)')
    parser.add_argument('--manual-pen', choices=['UP', 'DOWN'], help='Manually move pen and exit')
    parser.add_argument('--move-x', type=float, help='Manual Move X (mm)')
    parser.add_argument('--move-y', type=float, help='Manual Move Y (mm)')
    parser.add_argument('--interactive-server', action='store_true', help='Run in persistent server mode for manual control')
    parser.add_argument('--report-position', action='store_true', help='Report realtime position for GUI')
    parser.add_argument('--config', help='Path to custom configuration file')
    args = parser.parse_args()

    # Load Station Config Early
    loaded_general = load_station_config(getattr(args, 'config', None))

    # Apply defaults from loaded config if CLI args are not explicit user overrides
    # (Since argparse defaults are set, we might overwrite them if we just trust loaded_general.
    # But usually we prioritize CLI > Config > Hardcoded defaults.
    # However, since argparse sets defaults (e.g. speed=25), we can't easily distinguish "user didn't set" vs "default".
    # Strategy: If loaded_general has a value, update the args namespace IF the user didn't specify it? 
    # Actually, argparse is parsed already. We can just set the values if they exist in config, 
    # BUT we should only do so if we want config to override default. 
    # For a CLI, explicit flags > config file > defaults. 
    # Since we can't tell if '25' came from user or default easily without complex argparse usage,
    # we will trust the Config file values to update our "defaults" logic below, OR explicitly update `args` here.)

    if loaded_general:
        # Map config keys to args keys
        # Config: modelIndex, mock, invertX, invertY, swapXY, visualMirror, speedDown, speedUp, penUp, penDown
        if 'mock' in loaded_general and not args.mock: args.mock = loaded_general['mock']
        if 'invertX' in loaded_general and not args.invert_x: args.invert_x = loaded_general['invertX']
        if 'invertY' in loaded_general and not args.invert_y: args.invert_y = loaded_general['invertY']
        if 'swapXY' in loaded_general and not args.swap_xy: args.swap_xy = loaded_general['swapXY']
        
        # Model (0=A4, 1=A3) -> Arg is 1 or 2
        if 'modelIndex' in loaded_general: args.model = loaded_general['modelIndex'] + 1
        
        # Speeds (If not default? Or always overwrite if in config?)
        # Let's overwrite if in config, assuming config is "persistent default"
        if 'speedDown' in loaded_general: args.speed_down = loaded_general['speedDown']
        if 'speedUp' in loaded_general: args.speed_up = loaded_general['speedUp']
        if 'penUp' in loaded_general: args.pen_up = loaded_general['penUp']
        if 'penDown' in loaded_general: args.pen_down = loaded_general['penDown']

    print(f"INFO: Active Configuration -> Model: {args.model}, Mock: {args.mock}, InvertX: {args.invert_x}, InvertY: {args.invert_y}, SwapXY: {args.swap_xy}")

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
        
    # Ensure Global Units are MM
    ad.options.units = 2
    ad.update()

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
    
    # Override from CLI if provided
    if args.pen_up is not None:
        print(f"INFO: Overriding Pen Up -> {args.pen_up}%")
        ad.options.pen_pos_up = args.pen_up
    
    if args.pen_down is not None:
        print(f"INFO: Overriding Pen Down -> {args.pen_down}%")
        ad.options.pen_pos_down = args.pen_down
    try:
        ad.options.model = args.model
    except:
        pass # Mock might not have it, or old lib

    print(f"INFO: Setting Speeds -> Draw: {args.speed_down}%, Travel: {args.speed_up}%")
    ad.options.speed_pendown = args.speed_down
    ad.options.speed_penup = args.speed_up
    
    # FORCE UNITS TO MM (2)
    # 0=Inches, 1=cm, 2=mm
    ad.options.units = 2
    
    # Init options for interactive mode
    print("INFO: Updating AxiDraw options...")
    ad.update()
    
    # Verify Units
    print(f"DEBUG: ad.options.units is now {ad.options.units}")

    # Safety: Always raise pen on connect/start
    print("INFO: Safely raising pen...")
    ad.penup()

    # Manual Mode Check
    if args.manual_pen:
        print(f"INFO: Manual Pen Mode -> {args.manual_pen}")
        if args.manual_pen == 'DOWN':
            ad.pendown()
        else:
            ad.penup()
        
        print("INFO: Manual operation complete. Exiting.")
        ad.disconnect()
        return

    # Manual Move Check
    if args.move_x is not None or args.move_y is not None:
        mx = args.move_x if args.move_x is not None else 0.0
        my = args.move_y if args.move_y is not None else 0.0
        print(f"INFO: Manual Move -> X: {mx} mm, Y: {my} mm")
        
        # Ensure units are mm
        ad.options.units = 2 
        ad.update()

        # Perform move (relative from 0,0 where 0,0 is current pos)
        ad.moveto(mx, my)
        
        print("INFO: Manual move complete. Exiting.")
        ad.disconnect()
        return

    # Interactive Server Mode
    if args.interactive_server:
        print("INFO: Starting Interactive Server Mode...")
        # Ensure units are mm
        ad.options.units = 2 
        ad.update()
        
        # Track position (Assumes starting at 0,0)
        curr_x = 0.0
        curr_y = 0.0
        
        print("SERVER_READY")
        sys.stdout.flush()
        
        try:
            while True:
                line = sys.stdin.readline()
                if not line:
                    break
                line = line.strip()
                if not line:
                    continue
                
                parts = line.split()
                cmd = parts[0].upper()
                
                if cmd == "MOVE":
                    # MOVE <dX> <dY>
                    try:
                        dx = float(parts[1])
                        dy = float(parts[2])
                        # Use relative move directly
                        # ad.move(dx, dy) raises pen and moves
                        # To ensure pen state (usually manual moves want pen up unless specified?)
                        # The user just said "move", so we assume travel/jog.
                        
                        if args.swap_xy:
                            dx, dy = dy, dx

                        print(f"INFO: Moving relative ({dx}, {dy})")
                        ad.move(dx, dy)
                        print("OK")
                    except ValueError:
                        print(f"ERR Invalid Number Format: {parts}")
                    except Exception as e:
                        print(f"ERR {e}")

                elif cmd == "PEN":
                    # PEN <UP|DOWN>
                    try:
                        sub = parts[1].upper()
                        if sub == "UP":
                            print("INFO: Pen UP")
                            ad.penup()
                            print("OK")
                        elif sub == "DOWN":
                            print("INFO: Pen DOWN")
                            ad.pendown()
                            print("OK")
                        else:
                            print(f"ERR Invalid Pen Command: {sub}")
                    except IndexError:
                        print("ERR Missing Pen Argument")
                    except Exception as e:
                        print(f"ERR {e}")

                elif cmd == "EXIT":
                    break
                else:
                    print(f"ERR Unknown command: {cmd}")
                
                sys.stdout.flush()
                
        except KeyboardInterrupt:
            pass
            
        print("INFO: Interactive Server Exiting.")
        ad.disconnect()
        return


    if args.input:
        data = load_json(args.input)
        print(f"INFO: Plot Configuration -> Invert X: {args.invert_x}, Invert Y: {args.invert_y}, Swap X/Y: {args.swap_xy}")
    elif not args.interactive_server and not args.manual_pen:
        print("ERROR: No input file specified for plot mode.")
        sys.exit(1)

    try:
        print(f"DEBUG: ad.options.units = {ad.options.units} (Should be 2 for mm)")
        for layer in data['layers']:
            execute_layer(ad, layer, report_pos=args.report_position, verbose=args.verbose, model=args.model, invert_x=args.invert_x, invert_y=args.invert_y, swap_xy=args.swap_xy)
            
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
