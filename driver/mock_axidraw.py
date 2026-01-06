class AxiDraw:
    def __init__(self):
        self.connected = False
        self.options = MockOptions()

    def connect(self):
        print("[Mock] AxiDraw Connected.")
        self.connected = True

    def disconnect(self):
        print("[Mock] AxiDraw Disconnected.")
        self.connected = False

    def plot_setup(self):
        print("[Mock] Setup complete.")

    def moveto(self, x, y):
        print(f"[Mock] Moving to ({x:.2f}, {y:.2f}) [Pen UP]")

    def lineto(self, x, y):
        print(f"[Mock] Drawing line to ({x:.2f}, {y:.2f}) [Pen DOWN]")

    def pen_up(self):
        print("[Mock] Pen UP")

    def pen_down(self):
        print("[Mock] Pen DOWN")

class MockOptions:
    def __init__(self):
        self.speed_penup = 75
        self.speed_pendown = 25
        self.accel = 50
        self.pen_pos_up = 60
        self.pen_pos_down = 30
