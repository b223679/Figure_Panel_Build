package org.microscopy.figure;

import java.awt.Color;

/** Shared inset appearance; identical for every panel. Per-panel on/off and ROI position live in InsetCell. */
public class InsetConfig {
  public enum Shape { RECTANGLE, CIRCLE }

  public enum Position { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

  public enum RoiColor {
    YELLOW(Color.YELLOW),
    CYAN(Color.CYAN),
    MAGENTA(Color.MAGENTA),
    WHITE(Color.WHITE),
    BLACK(Color.BLACK);

    public final Color awt;

    RoiColor(Color awt) {
      this.awt = awt;
    }
  }

  public Shape shape = Shape.RECTANGLE;
  public int roiWidth = 40, roiHeight = 40, roiStrokeWidth = 2;
  public RoiColor color = RoiColor.YELLOW;
  public int insetWidth = 150, insetHeight = 150, insetStrokeWidth = 2;
  /** Keep ROI aspect and outline, or choose independent rectangular inset dimensions. */
  public boolean sameAsRoi = true;
  public double magnification = 3.0;
  public int boxWidth() { return sameAsRoi ? (int) Math.round(roiWidth * magnification) : insetWidth; }
  public int boxHeight() { return sameAsRoi ? (int) Math.round(roiHeight * magnification) : insetHeight; }
  public Position position = Position.TOP_RIGHT;
  public int marginX = 8, marginY = 8;

  public void validate() {
    if (!Double.isFinite(magnification) || magnification < 1 || magnification > 100
        || boxWidth() < 1 || boxHeight() < 1)
      throw new IllegalArgumentException("Inset magnification must be between 1 and 100.");
    if (roiWidth < 1 || roiHeight < 1 || insetWidth < 1 || insetHeight < 1)
      throw new IllegalArgumentException("Inset ROI/size must be at least 1 px.");
    if (roiStrokeWidth < 0 || insetStrokeWidth < 0 || marginX < 0 || marginY < 0)
      throw new IllegalArgumentException("Invalid inset appearance.");
  }
}
