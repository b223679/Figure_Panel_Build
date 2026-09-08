package org.microscopy.figure;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Real Swing layout and asynchronous-preview validation, run with headless=false. */
public class WorkspaceUiValidation {
  private static FigurePanelBuilderDialog dialog;

  public static void main(String[] args) throws Exception {
    try {
      SwingUtilities.invokeAndWait(() -> {
        RepaintManager.currentManager(new JPanel()).setDoubleBufferingEnabled(false);
        dialog = new FigurePanelBuilderDialog();
        dialog.setSize(1380, 940);
        dialog.setVisible(true);
        dialog.addFiles(Arrays.asList(new File("test-data/Control.tif"), new File("test-data/HPR.tif"), new File("test-data/KO.tif")));
        layout(dialog.getContentPane());
      });
      waitPreview();
      capture("workspace-single.png");
      SwingUtilities.invokeAndWait(() -> {
        Timer accept = new Timer(250, e -> {
          for (Window window : Window.getWindows()) {
            if (window instanceof JDialog && window.getOwner() == dialog && window.isVisible()) {
              chooseAll((Container) window);
              findPane((Container) window).setValue(JOptionPane.OK_OPTION);
              return;
            }
          }
          throw new AssertionError("Add channel dialog did not open");
        });
        accept.setRepeats(false);
        accept.start();
        try {
          FigureWorkspace w = (FigureWorkspace) field(dialog, "workspace");
          ((JButton) w.getComponent(2)).doClick();
          FigureConfiguration c = (FigureConfiguration) field(dialog, "config");
          if (c.displayChannels.size() != 2 || !c.displayChannels.get(1).channels.equals(Arrays.asList(1, 2, 3)))
            throw new AssertionError("Add tile did not create the selected three-channel merge");
        } catch (Exception e) { throw new RuntimeException(e); }
      });
      waitPreview();
      capture("workspace-three-channel-merge.png");
      SwingUtilities.invokeAndWait(() -> {
        try { dialog.loadSettings(new File("test-data/example-settings.json")); }
        catch (Exception e) { throw new RuntimeException(e); }
        layout(dialog.getContentPane());
      });
      waitPreview();
      capture("workspace-merge.png");
      SwingUtilities.invokeAndWait(() -> button(dialog, "B&C").doClick());
      waitPreview();
      capture("workspace-figure.png");
      SwingUtilities.invokeAndWait(() -> button(dialog, "B&C").doClick());
      SwingUtilities.invokeAndWait(() -> button(dialog, "⇄  Swap rows / columns").doClick());
      waitPreview();
      capture("workspace-transposed.png");
      SwingUtilities.invokeAndWait(() -> {
        AbstractButton toggle = button(dialog, "Settings / Sources");
        toggle.doClick();
        layout(dialog.getContentPane());
      });
      waitPreview();
      capture("workspace-settings.png");
      SwingUtilities.invokeAndWait(() -> {
        try {
          button(dialog, "Settings / Sources").doClick();
          SettingsSerializer serializer = new SettingsSerializer();
          SettingsSerializer.Loaded loaded = serializer.load(new File("test-data/example-settings.json"));
          FigureConfiguration c = loaded.configuration;
          for (int i = 3; i < 7; i++) c.conditions.add(new ConditionConfig("Condition " + (i + 1), c.conditions.get(i % 3).sourceId));
          c.displayChannels.get(2).channels.add(3);
          File file = new File("artifacts/workspace-seven-settings.json");
          serializer.save(file, c, loaded.inputs);
          dialog.loadSettings(file);
        } catch (Exception e) { throw new RuntimeException(e); }
      });
      waitPreview();
      capture("workspace-seven-conditions.png");
      System.out.println("PASS: + tile and checkbox merge dialog; single/three-channel merge, seven conditions, axis swap, B&C toggle, settings panel; seven UI screenshots.");
    } finally {
      if (dialog != null && args.length == 0) SwingUtilities.invokeAndWait(() -> dialog.dispose());
    }
  }

  private static void chooseAll(Container root) {
    for (Component c : root.getComponents()) {
      if (c instanceof JCheckBox) ((JCheckBox) c).setSelected(true);
      if (c instanceof Container) chooseAll((Container) c);
    }
  }

  private static JOptionPane findPane(Container root) {
    if (root instanceof JOptionPane) return (JOptionPane) root;
    for (Component c : root.getComponents()) if (c instanceof Container) {
      JOptionPane result = findPane((Container) c);
      if (result != null) return result;
    }
    return null;
  }

  private static AbstractButton button(Container root, String text) {
    for (Component c : root.getComponents()) {
      if (c instanceof AbstractButton && text.equals(((AbstractButton) c).getText())) return (AbstractButton) c;
      if (c instanceof Container) {
        AbstractButton found = button((Container) c, text);
        if (found != null) return found;
      }
    }
    return null;
  }

  private static Object field(Object object, String name) throws Exception {
    Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
  }

  private static void waitPreview() throws Exception {
    for (int i = 0; i < 100; i++) {
      Thread.sleep(100);
      AtomicBoolean ready = new AtomicBoolean();
      SwingUtilities.invokeAndWait(() -> {
        try {
          layout(dialog.getContentPane());
          FigureWorkspace workspace = (FigureWorkspace) field(dialog, "workspace");
          ready.set((Boolean) field(workspace, "ready"));
        } catch (Exception e) { throw new RuntimeException(e); }
      });
      if (ready.get()) return;
    }
    throw new AssertionError("Preview did not complete: " + ((JLabel) field(dialog, "status")).getText());
  }

  private static void capture(String name) throws Exception {
    Thread.sleep(250);
    SwingUtilities.invokeAndWait(() -> {
      try {
        Container content = dialog.getContentPane();
        dialog.validate();
        BufferedImage image = new BufferedImage(content.getWidth(), content.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics(); content.printAll(g); g.dispose();
        ImageIO.write(image, "png", new File("artifacts", name));
      } catch (Exception e) { throw new RuntimeException(e); }
    });
  }

  private static void layout(Container root) {
    root.doLayout();
    for (Component c : root.getComponents()) if (c instanceof Container) layout((Container) c);
  }
}
