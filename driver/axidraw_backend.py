from backend import PlotterBackend


class AxiDrawBackend(PlotterBackend):
    def __init__(self, axidraw_module):
        self._ad = axidraw_module.AxiDraw()
        self.options = self._ad.options

    def interactive(self):
        self._ad.interactive()

    def update(self):
        self._ad.update()

    def connect(self) -> bool:
        return self._ad.connect()

    def disconnect(self):
        self._ad.disconnect()

    def moveto(self, x, y):
        self._ad.moveto(x, y)

    def lineto(self, x, y):
        self._ad.lineto(x, y)

    def move(self, dx, dy):
        self._ad.move(dx, dy)

    def penup(self):
        self._ad.penup()

    def pendown(self):
        self._ad.pendown()
