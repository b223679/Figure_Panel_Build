package org.microscopy.figure;

import ij.io.FileSaver;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Generates review artifacts without interacting with the user's Fiji windows. */
public class DeliverableGenerator {
  public static void main(String[] args) throws Exception {
    Path folder = Paths.get("test-data"), artifacts = Paths.get("artifacts");
    Files.createDirectories(artifacts);
    TestImageGenerator.generate(folder);
    InputImageManager inputs = new InputImageManager();
    FigureConfiguration c = new FigureConfiguration();
    List<java.io.File> files = new ArrayList<>();
    for (String name : new String[] {"Image A", "Image B", "Image C"}) {
      java.io.File file = folder.resolve(name + ".tif").toFile();
      files.add(file);
      InputImageManager.Source s = inputs.load(file);
      c.conditions.add(new ConditionConfig(name, s.id));
    }
    c.channels.add(new ChannelConfig(1, "Green", ChannelConfig.Lut.Green));
    c.channels.add(new ChannelConfig(2, "Red", ChannelConfig.Lut.Red));
    c.channels.add(new ChannelConfig(3, "Blue", ChannelConfig.Lut.Blue));
    c.displayChannels.add(new DisplayChannel("Green", false, 1));
    c.displayChannels.add(new DisplayChannel("Red", false, 2));
    c.displayChannels.add(new DisplayChannel("Merge", true, 1, 2));
    c.labels.showRows = true;
    c.labels.showColumns = true;
    c.horizontalGap = 5;
    c.verticalGap = 5;
    c.scaleBar.show = true;
    BufferedImage figure = new PanelLayoutEngine().render(c, inputs);
    ImageIO.write(figure, "png", artifacts.resolve("example-figure.png").toFile());
    new FileSaver(new ij.ImagePlus("Example", figure))
        .saveAsTiff(artifacts.resolve("example-figure.tif").toString());
    new SettingsSerializer().save(folder.resolve("example-settings.json").toFile(), c, inputs);
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            RepaintManager.currentManager(new JPanel()).setDoubleBufferingEnabled(false);
            FigurePanelBuilderDialog dialog = new FigurePanelBuilderDialog();
            dialog.addFiles(files);
            Container content = dialog.getContentPane();
            content.addNotify();
            content.setSize(1280, 820);
            layout(content);
            BufferedImage screenshot = new BufferedImage(1280, 820, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = screenshot.createGraphics();
            content.printAll(g);
            g.dispose();
            ImageIO.write(screenshot, "png", artifacts.resolve("gui-layout.png").toFile());
            dialog.dispose();
            ContrastPanel contrast = new ContrastPanel(c, inputs, () -> {});
            contrast.addNotify();
            contrast.setSize(1100, 560);
            layout(contrast);
            screenshot = new BufferedImage(1100, 560, BufferedImage.TYPE_INT_RGB);
            g = screenshot.createGraphics();
            contrast.printAll(g);
            g.dispose();
            ImageIO.write(screenshot, "png", artifacts.resolve("contrast-layout.png").toFile());
            contrast.stop();
            contrast.removeNotify();
          } catch (Exception ex) {
            throw new RuntimeException(ex);
          }
        });
    System.out.println(
        "Created 3 synthetic C=3 Z=1 T=1 TIFFs, settings, RGB example and GUI review images.");
  }

  private static void layout(Container c) {
    c.doLayout();
    for (Component child : c.getComponents())
      if (child instanceof Container) layout((Container) child);
  }
}
