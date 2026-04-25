from abc import ABC, abstractmethod


class BackendOptions:
    def __init__(self):
        self.units = 2
        self.model = 1
        self.pen_pos_up = 60
        self.pen_pos_down = 30
        self.speed_penup = 75
        self.speed_pendown = 25


class PlotterBackend(ABC):
    def __init__(self):
        self.options = BackendOptions()

    @abstractmethod
    def interactive(self):
        ...

    @abstractmethod
    def update(self):
        ...

    @abstractmethod
    def connect(self) -> bool:
        ...

    @abstractmethod
    def disconnect(self):
        ...

    @abstractmethod
    def moveto(self, x: float, y: float):
        ...

    @abstractmethod
    def lineto(self, x: float, y: float):
        ...

    @abstractmethod
    def move(self, dx: float, dy: float):
        ...

    @abstractmethod
    def penup(self):
        ...

    @abstractmethod
    def pendown(self):
        ...
