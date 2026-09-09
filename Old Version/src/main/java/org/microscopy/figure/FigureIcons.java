package org.microscopy.figure;

import java.awt.*;
import java.awt.geom.Path2D;
import javax.swing.Icon;

/** Small vector UI icons; never included in figure exports. */
final class FigureIcons implements Icon {
  enum Kind { SWAP, TRASH, UNDO, REDO }
  private final Kind kind;
  private final int size;
  FigureIcons(Kind kind, int size) { this.kind = kind; this.size = size; }
  public int getIconWidth() { return size; }
  public int getIconHeight() { return size; }
  public void paintIcon(Component c, Graphics graphics, int x, int y) {
    Graphics2D g = (Graphics2D) graphics.create();
    try {
      g.translate(x, y); g.scale(size / 64.0, size / 64.0);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(c.isEnabled() ? c.getForeground() : Color.GRAY);
      g.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      if (kind == Kind.TRASH) {
        g.drawLine(11, 15, 53, 15); g.drawLine(25, 7, 39, 7);
        g.drawLine(25, 7, 22, 15); g.drawLine(39, 7, 42, 15);
        Path2D p = new Path2D.Double(); p.moveTo(16, 21); p.lineTo(20, 57); p.lineTo(44, 57); p.lineTo(48, 21); g.draw(p);
        g.drawLine(27, 26, 28, 49); g.drawLine(37, 26, 36, 49);
      } else if (kind == Kind.SWAP) {
        Path2D p = new Path2D.Double(); p.moveTo(10, 49); p.lineTo(39, 49); p.quadTo(51, 49, 51, 36); p.lineTo(51, 9); g.draw(p);
        g.drawLine(10, 49, 21, 38); g.drawLine(10, 49, 21, 60);
        g.drawLine(51, 9, 40, 20); g.drawLine(51, 9, 62, 20);
        g.drawLine(11, 28, 11, 11); g.drawLine(11, 11, 28, 11);
      } else {
        if (kind == Kind.REDO) { g.translate(64, 0); g.scale(-1, 1); }
        Path2D p = new Path2D.Double(); p.moveTo(12, 24); p.curveTo(47, 7, 60, 35, 46, 51); g.draw(p);
        g.drawLine(12, 24, 12, 9); g.drawLine(12, 24, 28, 29);
      }
    } finally { g.dispose(); }
  }
}
