package org.microscopy.figure;

import java.awt.image.BufferedImage;

public class ImageRenderer {
  public int rgb(float value, ChannelConfig c) {
    double normalized =
        Double.isFinite(value) ? Math.max(0, Math.min(1, (value - c.min) / (c.max - c.min))) : 0;
    int v = (int) Math.round(255 * normalized);
    if (c.lut == ChannelConfig.Lut.Grayscale && c.invert) v = 255 - v;
    return lutRgb(c.lut, v);
  }

  public static int lutRgb(ChannelConfig.Lut lut, int v) {
    switch (lut) {
      case Red:
        return v << 16;
      case Green:
        return v << 8;
      case Blue:
        return v;
      case Cyan:
        return (v << 8) | v;
      case Magenta:
        return (v << 16) | v;
      case Yellow:
        return (v << 16) | (v << 8);
      default:
        return (v << 16) | (v << 8) | v;
    }
  }

  public BufferedImage render(
      InputImageManager.Source source, DisplayChannel display, FigureConfiguration config) {
    return render(source, display, config, source.width, source.height);
  }

  public BufferedImage render(
      InputImageManager.Source source,
      DisplayChannel display,
      FigureConfiguration config,
      int width,
      int height) {
    return render(source, display, config, 0, 0, source.width, source.height, width, height);
  }

  /** Renders the sub-rectangle [srcX, srcX+srcW) x [srcY, srcY+srcH) of the source, scaled to width x height. */
  public BufferedImage render(
      InputImageManager.Source source,
      DisplayChannel display,
      FigureConfiguration config,
      int srcX,
      int srcY,
      int srcW,
      int srcH,
      int width,
      int height) {
    BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < height; y++)
      for (int x = 0; x < width; x++) {
        int r = 0, g = 0, b = 0;
        for (int id : display.channels) {
          int color =
              rgb(
                  source.value(
                      id,
                      srcX + (int) ((long) x * srcW / width),
                      srcY + (int) ((long) y * srcH / height)),
                  config.channel(id));
          r += (color >> 16) & 255;
          g += (color >> 8) & 255;
          b += color & 255;
        }
        out.setRGB(x, y, (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b));
      }
    return out;
  }

  /** Full-range Auto over all conditions; no per-image autoscaling. */
  public double[] range(InputImageManager inputs, FigureConfiguration config, int channel) {
    double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
    for (ConditionConfig c : config.conditions) {
      InputImageManager.Source s = inputs.get(c.sourceId);
      if (channel > s.channels)
        throw new IllegalArgumentException("Channel unavailable: " + s.title);
      for (int y = 0; y < s.height; y++)
        for (int x = 0; x < s.width; x++) {
          float v = s.value(channel, x, y);
          if (Float.isFinite(v)) {
            min = Math.min(min, v);
            max = Math.max(max, v);
          }
        }
    }
    if (!Double.isFinite(min)) throw new IllegalArgumentException("No finite pixels for Auto.");
    return new double[] {min, max > min ? max : min + Math.max(1, Math.abs(min) * 0.001)};
  }
}
