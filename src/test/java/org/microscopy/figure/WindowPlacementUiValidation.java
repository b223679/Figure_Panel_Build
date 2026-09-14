package org.microscopy.figure;

import java.awt.*;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Run in a separate JVM; only creates and disposes its own empty figure windows. */
public final class WindowPlacementUiValidation {
  public static void main(String[] args) throws Exception {
    for (boolean free : new boolean[] {false, true}) {
      final JFrame[] holder = new JFrame[1];
      try {
        SwingUtilities.invokeAndWait(() -> {
          JFrame frame = free ? new FreeBuildDialog(2, 2) : new FigurePanelBuilderDialog(false);
          holder[0] = frame;
          frame.setAlwaysOnTop(true);
          frame.setVisible(true);
        });
        Thread.sleep(1000);
        JFrame frame = holder[0];
        final Rectangle[] normal = new Rectangle[1];
        SwingUtilities.invokeAndWait(() -> {
          GraphicsConfiguration gc = frame.getGraphicsConfiguration();
          Rectangle usable = new Rectangle(gc.getBounds());
          Insets i = Toolkit.getDefaultToolkit().getScreenInsets(gc);
          usable.x += i.left; usable.y += i.top;
          usable.width -= i.left + i.right; usable.height -= i.top + i.bottom;
          normal[0] = frame.getBounds();
          if (frame.getExtendedState() != JFrame.NORMAL || !usable.contains(normal[0]))
            throw new AssertionError("Startup outside usable desktop: " + normal[0] + " / " + usable);
          if (Math.abs(normal[0].getCenterX() - usable.getCenterX()) > 1
              || normal[0].getCenterY() > usable.getCenterY())
            throw new AssertionError("Startup must be horizontally centered and slightly above center");
          if (frame.getMaximizedBounds() != null || frame.isUndecorated()
              || frame.getRootPane().getWindowDecorationStyle() != JRootPane.NONE)
            throw new AssertionError("Expected only native window decorations and OS maximization");
          System.out.println("Startup " + free + ": " + normal[0] + "; usable=" + usable);
        });
        capture(frame, free, "normal");
        SwingUtilities.invokeAndWait(() -> frame.setExtendedState(JFrame.MAXIMIZED_BOTH));
        Thread.sleep(1200);
        capture(frame, free, "maximized");
        SwingUtilities.invokeAndWait(() -> {
          if ((frame.getExtendedState() & JFrame.MAXIMIZED_BOTH) != JFrame.MAXIMIZED_BOTH)
            throw new AssertionError("Manual maximize failed");
          frame.setExtendedState(JFrame.NORMAL);
        });
        Thread.sleep(800);
        SwingUtilities.invokeAndWait(() -> {
          if (!normal[0].equals(frame.getBounds())) throw new AssertionError("Restore changed startup bounds");
        });
      } finally {
        SwingUtilities.invokeAndWait(() -> { if (holder[0] != null) holder[0].dispose(); });
      }
    }
    System.out.println("PASS: normal and free startup, native decorations, maximize and restore");
  }

  private static void capture(JFrame frame, boolean free, String state) throws Exception {
    File directory = new File("artifacts/window-placement");
    directory.mkdirs();
    ImageIO.write(new Robot().createScreenCapture(frame.getGraphicsConfiguration().getBounds()),
        "png", new File(directory, (free ? "free-" : "normal-") + state + ".png"));
  }
}
