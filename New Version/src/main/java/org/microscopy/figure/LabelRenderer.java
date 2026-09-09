package org.microscopy.figure;

import java.awt.*;
import java.awt.geom.AffineTransform;

public class LabelRenderer {
  public FontMetrics fitFont(Graphics2D g, int requestedSize, String text, int available) {
    Font font = new Font(Font.SANS_SERIF, Font.PLAIN, requestedSize);
    g.setFont(font);
    FontMetrics metrics = g.getFontMetrics();
    while (metrics.stringWidth(text) > available) {
      float size = font.getSize2D() * Math.min(0.95f, available / (float) metrics.stringWidth(text));
      font = font.deriveFont(Math.max(0.01f, size));
      g.setFont(font);
      metrics = g.getFontMetrics();
      if (font.getSize2D() <= 0.01f) break;
    }
    return metrics;
  }

  private void drawLabel(Graphics2D g, FigureConfiguration c, DisplayChannel display,
      String text, int x, int y, boolean row) {
    Color neutral = LabelColors.neutral(c.labels, row);
    if (display == null) {
      g.setColor(neutral);
      g.drawString(text, x, y);
      return;
    }
    String prefix = "";
    boolean first = true;
    for (int id : display.channels) {
      if (!first) {
        g.setColor(neutral);
        g.drawString("/", x + g.getFontMetrics().stringWidth(prefix), y);
        prefix += "/";
      }
      ChannelConfig channel = c.channel(id);
      // Grayscale labels follow the selected text color for background readability.
      Color color = LabelColors.channel(channel.lut, c.labels, row);
      g.setColor(color);
      g.drawString(channel.label, x + g.getFontMetrics().stringWidth(prefix), y);
      prefix += channel.label;
      first = false;
    }
  }

  public int rowBand(LabelConfig c) {
    return c.showRows ? c.rowFontSize + 8 + c.rowMargin : 0;
  }

  public int columnBand(LabelConfig c) {
    return c.showColumns ? c.columnFontSize + 8 + c.columnMargin : 0;
  }

  public void draw(
      Graphics2D g,
      FigureConfiguration c,
      int x0,
      int y0,
      int cellWidth,
      int cellHeight,
      int width,
      int height) {
    LabelConfig l = c.labels;
    g.setColor(l.whiteText ? Color.WHITE : Color.BLACK);
    g.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    if (l.showColumns) {
      for (int col = 0; col < c.columns(); col++) {
        String text =
            c.rowsAreChannels
                ? c.conditions.get(col).label
                : c.displayLabel(c.displayChannels.get(col));
        FontMetrics fm = fitFont(g, l.columnFontSize, text, cellWidth);
        int y =
            l.columnBottom
                ? height - columnBand(l) + l.columnMargin + fm.getAscent()
                : fm.getAscent() + 4;
        drawLabel(
            g, c, c.rowsAreChannels ? null : c.displayChannels.get(col), text,
            x0 + col * (cellWidth + c.horizontalGap) + (cellWidth - fm.stringWidth(text)) / 2,
            y, false);
      }
    }
    if (l.showRows) {
      for (int row = 0; row < c.rows(); row++) {
        String text =
            c.rowsAreChannels
                ? c.displayLabel(c.displayChannels.get(row))
                : c.conditions.get(row).label;
        FontMetrics fm = fitFont(g, l.rowFontSize, text, cellHeight);
        AffineTransform old = g.getTransform();
        double x = l.rowRight ? width - (l.rowFontSize + 8) / 2.0 : (l.rowFontSize + 8) / 2.0;
        g.translate(x, y0 + row * (cellHeight + c.verticalGap) + cellHeight / 2.0);
        g.rotate(l.rowRight ? Math.PI / 2 : -Math.PI / 2);
        drawLabel(g, c, c.rowsAreChannels ? c.displayChannels.get(row) : null,
            text, -fm.stringWidth(text) / 2, (fm.getAscent() - fm.getDescent()) / 2, true);
        g.setTransform(old);
      }
    }
  }
}
