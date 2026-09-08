package org.microscopy.figure;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Locale;

public class ScaleBarRenderer {
  public double pixelSizeUm(InputImageManager.Source source, ScaleBarConfig config) {
    double multiplier;
    String unit = source.unit.toLowerCase(Locale.ROOT).replace('\u03bc', '\u00b5');
    switch (unit) {
      case "nm":
        multiplier = 0.001;
        break;
      case "um":
      case "µm":
      case "micron":
      case "microns":
      case "micrometer":
        multiplier = 1;
        break;
      case "mm":
        multiplier = 1000;
        break;
      default:
        multiplier = 0;
    }
    double size = multiplier > 0 ? source.pixelWidth * multiplier : config.manualPixelSizeUm;
    if (!Double.isFinite(size) || size <= 0)
      throw new IllegalArgumentException(
          "Pixel size must be greater than zero. Enter manual µm/pixel for uncalibrated images: "
              + source.title);
    return size;
  }

  public int lengthPixels(InputImageManager.Source source, ScaleBarConfig config) {
    if (!Double.isFinite(config.lengthUm) || config.lengthUm <= 0)
      throw new IllegalArgumentException("Scale bar length must be positive.");
    double pixels = config.lengthUm / pixelSizeUm(source, config);
    if (pixels < 0.5 || pixels > Integer.MAX_VALUE)
      throw new IllegalArgumentException("Scale bar length is outside the image pixel range.");
    return (int) Math.round(pixels);
  }

  public boolean applies(
      ScaleBarConfig s, FigureConfiguration config, int condition, int display, int row, int col) {
    if (!s.show) return false;
    switch (s.scope) {
      case EVERY_CELL:
        return true;
      case ONE_PER_CONDITION:
        return display == config.displayChannels.size() - 1;
      case SELECTED_CELL:
        return row == s.selectedRow - 1 && col == s.selectedColumn - 1;
      default:
        return row == config.rows() - 1 && col == config.columns() - 1;
    }
  }

  public void draw(BufferedImage cell, InputImageManager.Source source, ScaleBarConfig s) {
    draw(cell, source, s, 1);
  }

  public void draw(
      BufferedImage cell, InputImageManager.Source source, ScaleBarConfig s, double scale) {
    int length = lengthPixels(source, s), w = source.width, h = source.height;
    if (s.thickness < 1 || s.marginX < 0 || s.marginY < 0 || s.fontSize < 1 || s.fontSize > LabelConfig.MAX_FONT_SIZE)
      throw new IllegalArgumentException("Invalid scale bar appearance.");
    Graphics2D g = cell.createGraphics();
    try {
      g.scale(scale, scale);
      g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, s.fontSize));
      FontMetrics fm = g.getFontMetrics();
      String text =
          String.format(
              Locale.ROOT,
              "%s µm",
              java.math.BigDecimal.valueOf(s.lengthUm).stripTrailingZeros().toPlainString());
      int textHeight = s.showText ? fm.getHeight() + 3 : 0,
          boxWidth = Math.max(length, s.showText ? fm.stringWidth(text) : 0),
          boxHeight = s.thickness + textHeight;
      if (boxWidth + 2L * s.marginX > w || boxHeight + 2L * s.marginY > h)
        throw new IllegalArgumentException("Scale bar/text does not fit cell: " + source.title);
      boolean right =
          s.position == ScaleBarConfig.Position.TOP_RIGHT
              || s.position == ScaleBarConfig.Position.BOTTOM_RIGHT;
      boolean bottom =
          s.position == ScaleBarConfig.Position.BOTTOM_RIGHT
              || s.position == ScaleBarConfig.Position.BOTTOM_LEFT;
      int x = right ? w - s.marginX - boxWidth : s.marginX,
          y = bottom ? h - s.marginY - boxHeight : s.marginY;
      g.setColor(s.white ? Color.WHITE : Color.BLACK);
      g.fillRect(x + (boxWidth - length) / 2, y + textHeight, length, s.thickness);
      if (s.showText)
        g.drawString(text, x + (boxWidth - fm.stringWidth(text)) / 2, y + fm.getAscent());
    } finally {
      g.dispose();
    }
  }
}
