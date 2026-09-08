package org.microscopy.figure;

import static org.junit.jupiter.api.Assertions.*;

import ij.ImagePlus;
import org.junit.jupiter.api.Test;

class AppearanceTest {
  @Test
  void calibrationAndManual() {
    InputImageManager in = new InputImageManager();
    ImagePlus image = CoreTest.image(1);
    image.getCalibration().pixelWidth = 0.25;
    image.getCalibration().setUnit("µm");
    InputImageManager.Source s = in.snapshot(image, null);
    ScaleBarConfig c = new ScaleBarConfig();
    assertEquals(80, new ScaleBarRenderer().lengthPixels(s, c));
    image.getCalibration().setUnit("nm");
    image.getCalibration().pixelWidth = 250;
    s = in.snapshot(image, null);
    assertEquals(80, new ScaleBarRenderer().lengthPixels(s, c));
    InputImageManager.Source uncal = in.snapshot(CoreTest.image(1), null);
    assertThrows(
        IllegalArgumentException.class, () -> new ScaleBarRenderer().lengthPixels(uncal, c));
    c.manualPixelSizeUm = 0.5;
    assertEquals(40, new ScaleBarRenderer().lengthPixels(uncal, c));
  }

  @Test
  void gapsAndLabelBands() {
    InputImageManager in = new InputImageManager();
    FigureConfiguration c = CoreTest.config(in, 100, 200);
    c.horizontalGap = 3;
    c.verticalGap = 4;
    assertEquals(19, new PanelLayoutEngine().render(c, in).getWidth());
    assertEquals(26, new PanelLayoutEngine().render(c, in).getHeight());
    c.labels.showRows = true;
    c.labels.showColumns = true;
    c.labels.rowFontSize = 1;
    c.labels.columnFontSize = 1;
    c.channel(1).label = "G";
    c.channel(2).label = "R";
    java.awt.image.BufferedImage out = new PanelLayoutEngine().render(c, in);
    assertEquals(38, out.getWidth());
    assertEquals(45, out.getHeight());
    c.labels.rowRight = true;
    c.labels.columnBottom = true;
    assertEquals(38, new PanelLayoutEngine().render(c, in).getWidth());
  }

  @Test
  void mergeLabelsUseSourceNamesAndLutColorsInBothOrientations() {
    FigureConfiguration c = new FigureConfiguration();
    c.channels.add(new ChannelConfig(1, "DNA", ChannelConfig.Lut.Blue));
    c.channels.add(new ChannelConfig(2, "Marker", ChannelConfig.Lut.Red));
    c.displayChannels.add(new DisplayChannel("Merge", true, 1, 2));
    c.conditions.add(new ConditionConfig());
    c.conditions.get(0).label = "Control";
    c.labels.showRows = c.labels.showColumns = true;
    assertEquals("DNA/Marker", c.displayLabel(c.displayChannels.get(0)));
    for (boolean rows : new boolean[] {true, false}) {
      c.rowsAreChannels = rows;
      java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
          400, 400, java.awt.image.BufferedImage.TYPE_INT_RGB);
      java.awt.Graphics2D g = image.createGraphics();
      new LabelRenderer().draw(g, c, 50, 50, 300, 300, 400, 400);
      g.dispose();
      java.util.Set<Integer> colors = new java.util.HashSet<>();
      for (int y = 0; y < 400; y++)
        for (int x = 0; x < 400; x++) colors.add(image.getRGB(x, y) & 0xffffff);
      assertTrue(colors.contains(0x6666ff));
      assertTrue(colors.contains(0xff0000));
      assertTrue(colors.contains(0xffffff));
    }
  }

  @Test
  void longMergeLabelFitsWithoutChangingConfiguredFontSize() {
    FigureConfiguration c = new FigureConfiguration();
    c.channels.add(new ChannelConfig(1, "Green", ChannelConfig.Lut.Green));
    c.channels.add(new ChannelConfig(2, "Red", ChannelConfig.Lut.Red));
    c.channels.add(new ChannelConfig(3, "magenta", ChannelConfig.Lut.Magenta));
    c.displayChannels.add(new DisplayChannel("Merge", true, 1, 2, 3));
    c.conditions.add(new ConditionConfig());
    c.conditions.get(0).label = "Control";
    c.labels.showRows = c.labels.showColumns = true;
    for (boolean rows : new boolean[] {true, false}) {
      c.rowsAreChannels = rows;
      java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(300, 300,
          java.awt.image.BufferedImage.TYPE_INT_RGB);
      java.awt.Graphics2D g = out.createGraphics();
      assertDoesNotThrow(() -> new LabelRenderer().draw(g, c, 42, 42, 160, 160, 202, 202));
      g.dispose();
      int colored = 0;
      for (int y = 0; y < 300; y++) for (int x = 0; x < 300; x++) {
        int rgb = out.getRGB(x, y) & 0xffffff;
        if (((rgb >> 16) & 255) != ((rgb >> 8) & 255) || ((rgb >> 8) & 255) != (rgb & 255)) {
          assertTrue(rows ? y >= 42 && y < 202 : x >= 42 && x < 202);
          colored++;
        }
      }
      assertTrue(colored > 0);
      assertEquals(24, c.labels.rowFontSize);
      assertEquals(24, c.labels.columnFontSize);
    }
  }

  @Test
  void scopesAndFit() {
    InputImageManager in = new InputImageManager();
    FigureConfiguration c = CoreTest.config(in, 1, 2);
    ScaleBarRenderer r = new ScaleBarRenderer();
    c.scaleBar.show = true;
    c.scaleBar.scope = ScaleBarConfig.Scope.ONE_PER_CONDITION;
    assertTrue(r.applies(c.scaleBar, c, 0, 2, 2, 0));
    assertFalse(r.applies(c.scaleBar, c, 0, 1, 1, 0));
    c.scaleBar.scope = ScaleBarConfig.Scope.FIGURE_ONCE;
    assertTrue(r.applies(c.scaleBar, c, 1, 2, 2, 1));
    c.scaleBar.manualPixelSizeUm = 0.25;
    assertThrows(IllegalArgumentException.class, () -> new PanelLayoutEngine().render(c, in));
  }
}
