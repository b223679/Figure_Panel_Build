package org.microscopy.figure;

import java.awt.*;
import javax.swing.JFrame;

/** Initial placement in AWT logical coordinates; native maximization stays OS-managed. */
final class FigureWindowPlacement {
  private FigureWindowPlacement() {}

  static void apply(JFrame frame, int width, int height, int minWidth, int minHeight) {
    GraphicsConfiguration screen = frame.getGraphicsConfiguration();
    Rectangle usable = new Rectangle(screen.getBounds());
    Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(screen);
    usable.x += insets.left;
    usable.y += insets.top;
    usable.width -= insets.left + insets.right;
    usable.height -= insets.top + insets.bottom;
    Rectangle initial = initialBounds(usable, width, height);
    frame.setMinimumSize(new Dimension(Math.min(minWidth, initial.width), Math.min(minHeight, initial.height)));
    frame.setBounds(initial);
    // Do not set maximized bounds: Windows owns taskbar and DPI-aware non-client sizing.
  }

  static Rectangle initialBounds(Rectangle usable, int width, int height) {
    int w = Math.max(1, Math.min(width, (int) (usable.width * 0.95)));
    int h = Math.max(1, Math.min(height, (int) (usable.height * 0.97)));
    return new Rectangle(usable.x + (usable.width - w) / 2,
        usable.y + (usable.height - h) / 3, w, h);
  }
}
