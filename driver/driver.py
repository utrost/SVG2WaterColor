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

def execute_layer(ad, layer, report_pos=False, verbose=False, model=1, invert_x=False, swap_xy=False):
    print(f"\n=== Starting Layer: {layer['id']} (Station: {layer['stationId']}) ===")
    input("Press Enter to start this layer (Ensure correct paint is ready)...")
    
    # Model 1 (V3 A4): X ~300, Y ~218
    # Model 2 (V3 A3): X ~430, Y ~297
    maxX = 300.0 if model == 1 else 430.0
    maxY = 215.0 if model == 1 else 297.0

    def transform_point(tx, ty):
        # 1. Swap if requested
        if swap_xy:
            px, py = ty, tx
        else:
            px, py = tx, ty
            
        # 2. Invert Physical X if requested
        if invert_x:
            px = maxX - px
            
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
    parser.add_argument('input', nargs='?', help='Input JSON file (optional if --manual-pen used)')
    parser.add_argument('--invert-x', action='store_true', help='Invert X Axis (Mirror Plot) for standard SVGs')
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
        print(f"INFO: Plot Configuration -> Invert X: {args.invert_x}, Swap X/Y: {args.swap_xy}")
    elif not args.interactive_server and not args.manual_pen:
        print("ERROR: No input file specified for plot mode.")
        sys.exit(1)

    try:
        print(f"DEBUG: ad.options.units = {ad.options.units} (Should be 2 for mm)")
        for layer in data['layers']:
            execute_layer(ad, layer, report_pos=args.report_position, verbose=args.verbose, model=args.model, invert_x=args.invert_x, swap_xy=args.swap_xy)
            
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
