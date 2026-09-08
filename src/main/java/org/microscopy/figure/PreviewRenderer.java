package org.microscopy.figure;

import java.awt.Dimension;
import java.awt.image.BufferedImage;

public class PreviewRenderer {
  public BufferedImage render(
      FigureConfiguration config, InputImageManager inputs, int maxWidth, int maxHeight) {
    PanelLayoutEngine engine = new PanelLayoutEngine();
    Dimension size = engine.dimensions(config, inputs);
    double scale =
        Math.min(1, Math.min((double) maxWidth / size.width, (double) maxHeight / size.height));
    return engine.render(config, inputs, scale);
  }

  /** Exact pooled histogram; finite pixel values only. */
  public long[] histogram(
      FigureConfiguration config, InputImageManager inputs, int channel, double min, double max) {
    long[] bins = new long[256];
    for (ConditionConfig c : config.conditions) {
      InputImageManager.Source s = inputs.get(c.sourceId);
      if (channel > s.channels)
        throw new IllegalArgumentException("Channel unavailable: " + s.title);
      for (int y = 0; y < s.height; y++)
        for (int x = 0; x < s.width; x++) {
          float v = s.value(channel, x, y);
          if (Float.isFinite(v)) {
            int b = (int) Math.floor(255 * (v - min) / (max - min));
            bins[Math.max(0, Math.min(255, b))]++;
          }
        }
    }
    return bins;
  }
}
