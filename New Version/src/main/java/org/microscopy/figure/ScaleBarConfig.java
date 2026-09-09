package org.microscopy.figure;

public class ScaleBarConfig {
  public enum Position {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
  }

  public enum Scope {
    EVERY_CELL,
    ONE_PER_CONDITION,
    SELECTED_CELL,
    FIGURE_ONCE
  }

  public boolean show = false, showText = true, white = true;
  public double lengthUm = 20, manualPixelSizeUm = 0;
  public int thickness = 4,
      marginX = 10,
      marginY = 10,
      fontSize = 14,
      selectedRow = 1,
      selectedColumn = 1;
  public Position position = Position.BOTTOM_RIGHT;
  public Scope scope = Scope.EVERY_CELL;
}
