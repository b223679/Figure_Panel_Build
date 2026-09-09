package org.microscopy.figure;

import ij.ImagePlus;
import ij.io.FileSaver;
import ij.process.ShortProcessor;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Isolated real-window verification; does not operate on existing Fiji windows. */
public class AppearanceUiValidation {
  private static FigurePanelBuilderDialog dialog;
  public static void main(String[] args) throws Exception {
    File sourceFile = new File("artifacts/Large 1024.tif");
    ShortProcessor pixels = new ShortProcessor(1024, 1024);
    for (int y = 0; y < 1024; y++) for (int x = 0; x < 1024; x++) {
      double line = Math.exp(-Math.pow((y - 512 - 160 * Math.sin(x / 130.0)) / 30, 2));
      pixels.set(x, y, (int) (100 + 1300 * line + 350 * Math.exp(-Math.pow((x - 512) / 200.0, 2) - Math.pow((y - 512) / 200.0, 2))));
    }
    ImagePlus image = new ImagePlus("Large 1024", pixels);
    image.getCalibration().setUnit("µm"); image.getCalibration().pixelWidth = 0.25;
    if (!new FileSaver(image).saveAsTiff(sourceFile.getAbsolutePath())) throw new AssertionError("Could not write test image");
    try {
      SwingUtilities.invokeAndWait(() -> {
        dialog = new FigurePanelBuilderDialog(false); dialog.setTitle("Appearance QA — temporary test window");
        if ((dialog.getExtendedState() & JFrame.MAXIMIZED_BOTH) != JFrame.MAXIMIZED_BOTH)
          throw new AssertionError("Window must start maximized");
        dialog.setVisible(true);
        dialog.addFiles(Collections.singletonList(sourceFile));
        try {
          ((JTabbedPane) field(dialog, "tabs")).setSelectedIndex(1);
          named(dialog, JCheckBox.class, "showScaleBar").doClick();
          FigureConfiguration c = (FigureConfiguration) field(dialog, "config");
          if (c.labels.rowFontSize != 102 || c.scaleBar.fontSize != 102 || c.scaleBar.lengthUm != 20 || c.scaleBar.thickness != 31)
            throw new AssertionError("Imported image did not receive proportional defaults");
        } catch (Exception ex) { throw new RuntimeException(ex); }
      });
      waitPreview();
      SwingUtilities.invokeAndWait(() -> {
        try {
          FigureWorkspace w = (FigureWorkspace) field(dialog, "workspace"); w.selectLabel(true, 0);
          capture(dialog.getContentPane(), "appearance-workspace-1024.png");
          FigureConfiguration c = (FigureConfiguration) field(dialog, "config");
          InputImageManager inputs = (InputImageManager) field(dialog, "inputs");
          new PptxExporter().save(new File("artifacts/editable-figure.pptx"), c, inputs);
          c.labels.transparentBackground = true;
          ImageIO.write(new PanelLayoutEngine().render(c, inputs), "png", new File("artifacts/transparent-figure.png"));
          new PptxExporter().save(new File("artifacts/transparent-figure.pptx"), c, inputs);
          c.labels.transparentBackground = false;
          AppearancePanel panel = new AppearancePanel(c, () -> {});
          panel.setSize(470, panel.getPreferredSize().height); layout(panel);
          capture(panel, "appearance-controls.png");
          named(dialog, JSpinner.class, "rowFontSize").setValue(77);
          dialog.addFiles(Collections.singletonList(sourceFile));
          if (c.labels.rowFontSize != 77) throw new AssertionError("Second import replaced manual font size");
          File settings = new File("artifacts/appearance-roundtrip.json");
          new SettingsSerializer().save(settings, c, (InputImageManager) field(dialog, "inputs"));
          dialog.loadSettings(settings);
          FigureConfiguration loaded = (FigureConfiguration) field(dialog, "config");
          if (loaded.labels.rowFontSize != 77) throw new AssertionError("Loading replaced manual font size");
        } catch (Exception ex) { throw new RuntimeException(ex); }
      });
      waitPreview();
      System.out.println("PASS: maximized startup; 1024px import -> 102px fonts, 80px bar / 20 um, thickness 31px; editable PPTX and transparent PNG/PPTX; manual size preserved.");
    } finally { if (dialog != null) SwingUtilities.invokeAndWait(() -> dialog.dispose()); }
  }
  private static Object field(Object object, String name) throws Exception {
    Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
  }
  private static AbstractButton button(Container root, String text) {
    for (Component c : root.getComponents()) {
      if (c instanceof AbstractButton && text.equals(((AbstractButton) c).getText())) return (AbstractButton) c;
      if (c instanceof Container) { AbstractButton b = button((Container) c, text); if (b != null) return b; }
    }
    return null;
  }
  private static <T extends Component> T named(Container root, Class<T> type, String name) {
    for (Component c : root.getComponents()) {
      if (type.isInstance(c) && name.equals(c.getName())) return type.cast(c);
      if (c instanceof Container) { T found = named((Container) c, type, name); if (found != null) return found; }
    }
    return null;
  }
  private static void waitPreview() throws Exception {
    for (int i = 0; i < 120; i++) {
      Thread.sleep(100); AtomicBoolean ready = new AtomicBoolean();
      SwingUtilities.invokeAndWait(() -> {
        try { ready.set((Boolean) field(field(dialog, "workspace"), "ready")); }
        catch (Exception ex) { throw new RuntimeException(ex); }
      });
      if (ready.get()) { Thread.sleep(300); return; }
    }
    throw new AssertionError("Preview did not complete");
  }
  private static void capture(Container c, String name) throws Exception {
    BufferedImage out = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D g = out.createGraphics(); c.printAll(g); g.dispose();
    ImageIO.write(out, "png", new File("artifacts", name));
  }
  private static void layout(Container c) {
    c.doLayout(); for (Component child : c.getComponents()) if (child instanceof Container) layout((Container) child);
  }
}
