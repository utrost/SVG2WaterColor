package org.trostheide.watercolorprocessor.dto.command;

/**
 * Represents a non-drawing travel move (Pen Up).
 * @param id Sequential command ID.
 * @param x Target X coordinate (mm).
 * @param y Target Y coordinate (mm).
 */
public final class MoveCommand extends Command {
    public final int id;
    public final double x;
    public final double y;

    public MoveCommand(int id, double x, double y) {
        this.id = id;
        this.x = x;
        this.y = y;
    }

    @Override
    public int getId() {
        return id;
    }
}