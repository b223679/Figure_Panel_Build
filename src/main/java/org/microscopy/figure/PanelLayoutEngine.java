package org.microscopy.figure;

import ij.ImagePlus;
import java.awt.*;
import java.awt.image.BufferedImage;

public class PanelLayoutEngine {
  public BufferedImage render(FigureConfiguration config, InputImageManager inputs) {
    return render(config, inputs, 1);
  }

  public Dimension dimensions(FigureConfiguration config, InputImageManager inputs) {
    config.validate(inputs);
    InputImageManager.Source ref = inputs.get(config.conditions.get(0).sourceId);
    LabelRenderer labels = new LabelRenderer();
    long
        width =
            (long) config.columns() * ref.width
                + (long) (config.columns() - 1) * config.horizontalGap
                + labels.rowBand(config.labels),
        height =
            (long) config.rows() * ref.height
                + (long) (config.rows() - 1) * config.verticalGap
                + labels.columnBand(config.labels);
    if (width > Integer.MAX_VALUE || height > Integer.MAX_VALUE || width > 100000000L / height)
      throw new IllegalArgumentException("Figure exceeds 100 million pixels; reduce the grid.");
    return new Dimension((int) width, (int) height);
  }

  public BufferedImage render(FigureConfiguration config, InputImageManager inputs, double scale) {
    if (scale <= 0 || scale > 1) throw new IllegalArgumentException("Invalid preview scale.");
    Dimension size = dimensions(config, inputs);
    int width = size.width, height = size.height;
    InputImageManager.Source ref = inputs.get(config.conditions.get(0).sourceId);
    LabelRenderer labels = new LabelRenderer();
    int x0 = config.labels.rowRight ? 0 : labels.rowBand(config.labels),
        y0 = config.labels.columnBottom ? 0 : labels.columnBand(config.labels);
    BufferedImage result =
        new BufferedImage(
            Math.max(1, (int) Math.ceil(width * scale)),
            Math.max(1, (int) Math.ceil(height * scale)),
            config.labels.transparentBackground ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
    Graphics2D g = result.createGraphics();
    ImageRenderer renderer = new ImageRenderer();
    g.scale(scale, scale);
    if (!config.labels.transparentBackground) {
      g.setColor(config.labels.whiteBackground ? Color.WHITE : Color.BLACK);
      g.fillRect(0, 0, (int) width, (int) height);
    }
    try {
      for (int c = 0; c < config.conditions.size(); c++)
        for (int d = 0; d < config.displayChannels.size(); d++) {
          int row = config.rowsAreChannels ? d : c, col = config.rowsAreChannels ? c : d;
          InputImageManager.Source source = inputs.get(config.conditions.get(c).sourceId);
          BufferedImage cell =
              renderer.render(
                  source,
                  config.displayChannels.get(d),
                  config,
                  Math.max(1, (int) Math.ceil(ref.width * scale)),
                  Math.max(1, (int) Math.ceil(ref.height * scale)));
          ScaleBarRenderer bars = new ScaleBarRenderer();
          if (bars.applies(config.scaleBar, config, c, d, row, col))
            bars.draw(cell, source, config.scaleBar, scale);
          g.drawImage(
              cell,
              x0 + col * (ref.width + config.horizontalGap),
              y0 + row * (ref.height + config.verticalGap),
              ref.width,
              ref.height,
              null);
        }
      labels.draw(g, config, x0, y0, ref.width, ref.height, (int) width, (int) height);
    } finally {
      g.dispose();
    }
    return result;
  }

  public ImagePlus generate(FigureConfiguration config, InputImageManager inputs) {
    return new ImagePlus("Figure Panel", render(config, inputs));
  }
}
