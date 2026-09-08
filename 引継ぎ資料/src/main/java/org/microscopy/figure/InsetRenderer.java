package org.microscopy.figure;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/** Draws the ROI outline on a cell and the zoomed crop box on top of it, in source-pixel units. */
public class InsetRenderer {
  /** Whether an inset should be drawn: enabled and the configured ROI/box actually fit this source. */
  public boolean applies(InsetCell cell, InputImageManager.Source source, InsetConfig config) {
    if (cell == null || !cell.enabled) return false;
    int roiX = cell.roiX(source, config), roiY = cell.roiY(source, config);
    if (roiX < 0 || roiY < 0 || roiX + config.roiWidth > source.width || roiY + config.roiHeight > source.height)
      return false;
    return config.insetWidth + 2L * config.marginX <= source.width
        && config.insetHeight + 2L * config.marginY <= source.height;
  }

  /** Top-left corner of the zoomed inset box within a width x height cell, per the configured corner/margins. */
  public static Point boxOrigin(InsetConfig config, int width, int height) {
    boolean right =
        config.position == InsetConfig.Position.TOP_RIGHT
            || config.position == InsetConfig.Position.BOTTOM_RIGHT;
    boolean bottom =
        config.position == InsetConfig.Position.BOTTOM_LEFT
            || config.position == InsetConfig.Position.BOTTOM_RIGHT;
    int x = right ? width - config.marginX - config.insetWidth : config.marginX;
    int y = bottom ? height - config.marginY - config.insetHeight : config.marginY;
    return new Point(x, y);
  }

  public void draw(
      BufferedImage cell,
      InputImageManager.Source source,
      DisplayChannel display,
      FigureConfiguration config,
      InsetConfig insetConfig,
      InsetCell insetCell,
      double scale) {
    int roiX = insetCell.roiX(source, insetConfig), roiY = insetCell.roiY(source, insetConfig);
    int roiW = insetConfig.roiWidth, roiH = insetConfig.roiHeight;
    if (roiX < 0 || roiY < 0 || roiX + roiW > source.width || roiY + roiH > source.height)
      throw new IllegalArgumentException("Inset ROI does not fit: " + source.title);
    if (insetConfig.insetWidth + 2L * insetConfig.marginX > source.width
        || insetConfig.insetHeight + 2L * insetConfig.marginY > source.height)
      throw new IllegalArgumentException("Inset box does not fit cell: " + source.title);
    BufferedImage crop =
        new ImageRenderer()
            .render(source, display, config, roiX, roiY, roiW, roiH, insetConfig.insetWidth, insetConfig.insetHeight);
    Point origin = boxOrigin(insetConfig, source.width, source.height);
    int x = origin.x, y = origin.y;
    Graphics2D g = cell.createGraphics();
    try {
      g.scale(scale, scale);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(insetConfig.color.awt);
      // Draw the inset box first: if it happens to overlap the ROI, the ROI outline (drawn last) must stay visible.
      g.drawImage(crop, x, y, insetConfig.insetWidth, insetConfig.insetHeight, null);
      g.setStroke(new BasicStroke(Math.max(1f, insetConfig.insetStrokeWidth)));
      g.drawRect(x, y, insetConfig.insetWidth, insetConfig.insetHeight);
      g.setStroke(new BasicStroke(Math.max(1f, insetConfig.roiStrokeWidth)));
      g.draw(
          insetConfig.shape == InsetConfig.Shape.CIRCLE
              ? new Ellipse2D.Double(roiX, roiY, roiW, roiH)
              : new Rectangle2D.Double(roiX, roiY, roiW, roiH));
    } finally {
      g.dispose();
    }
  }
}
