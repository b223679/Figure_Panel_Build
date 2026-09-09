package org.microscopy.figure;

import static org.junit.jupiter.api.Assertions.*;

import ij.*;
import ij.gui.*;
import ij.process.*;
import org.junit.jupiter.api.Test;

class CoreTest {
  static ImagePlus image(int value) {
    ImageStack stack = new ImageStack(8, 6);
    for (int c = 0; c < 3; c++) {
      short[] p = new short[48];
      java.util.Arrays.fill(p, (short) value);
      stack.addSlice("Channel " + (c + 1), new ShortProcessor(8, 6, p, null));
    }
    ImagePlus im = new ImagePlus("condition.tif", stack);
    im.setDimensions(3, 1, 1);
    im.setOpenAsHyperStack(true);
    return im;
  }

  static FigureConfiguration config(InputImageManager inputs, int... values) {
    FigureConfiguration cfg = new FigureConfiguration();
    for (int value : values) {
      InputImageManager.Source s = inputs.snapshot(image(value), null);
      cfg.conditions.add(new ConditionConfig("C" + value, s.id));
    }
    cfg.channels.add(new ChannelConfig(1, "Green", ChannelConfig.Lut.Green));
    cfg.channels.add(new ChannelConfig(2, "Red", ChannelConfig.Lut.Red));
    cfg.displayChannels.add(new DisplayChannel("Green", false, 1));
    cfg.displayChannels.add(new DisplayChannel("Red", false, 2));
    cfg.displayChannels.add(new DisplayChannel("Merge", true, 1, 2));
    return cfg;
  }

  @Test
  void commonBcAndMerge() {
    InputImageManager in = new InputImageManager();
    FigureConfiguration c = config(in, 1000, 1000, 2000);
    java.awt.image.BufferedImage out = new PanelLayoutEngine().render(c, in);
    assertEquals(24, out.getWidth());
    assertEquals(18, out.getHeight());
    assertEquals(0x008000, out.getRGB(0, 0) & 0xffffff);
    assertEquals(out.getRGB(0, 0), out.getRGB(8, 0));
    assertEquals(0x808000, out.getRGB(0, 12) & 0xffffff);
    assertEquals(0xffff00, out.getRGB(16, 12) & 0xffffff);
  }

  @Test
  void axesAndManualValidation() {
    InputImageManager in = new InputImageManager();
    FigureConfiguration c = config(in, 1, 2, 3, 4, 5, 6, 7);
    assertEquals(3, c.rows());
    assertEquals(7, c.columns());
    c.rowsAreChannels = false;
    assertEquals(7, c.rows());
    assertEquals(3, c.columns());
    c.automaticGrid = false;
    c.manualRows = 1;
    assertThrows(IllegalArgumentException.class, () -> c.validate(in));
  }

  @Test
  void sourceIsUntouched() {
    ImagePlus original = image(1000);
    original.setPosition(2, 1, 1);
    original.setRoi(new Roi(1, 1, 3, 2));
    original.setOverlay(new Overlay(new Roi(0, 0, 2, 2)));
    original.getProcessor().setMinAndMax(100, 1234);
    Object pixels = original.getStack().getPixels(1);
    short[] before = ((short[]) pixels).clone();
    java.awt.image.ColorModel lut = original.getProcessor().getColorModel();
    InputImageManager in = new InputImageManager();
    InputImageManager.Source s = in.snapshot(original, null);
    FigureConfiguration c = config(in, 1000);
    c.conditions.get(0).sourceId = s.id;
    new PanelLayoutEngine().render(c, in);
    assertArrayEquals(before, (short[]) original.getStack().getPixels(1));
    assertSame(pixels, original.getStack().getPixels(1));
    assertSame(lut, original.getProcessor().getColorModel());
    assertEquals(2, original.getC());
    assertEquals(100, original.getDisplayRangeMin());
    assertEquals(1234, original.getDisplayRangeMax());
    assertEquals(1, original.getRoi().getBounds().x);
    assertEquals(1, original.getOverlay().size());
    original.getStack().getProcessor(1).set(0, 0, 0);
    assertEquals(1000, s.value(1, 0, 0));
  }

  @Test
  void validationAndAuto() {
    InputImageManager in = new InputImageManager();
    FigureConfiguration c = config(in, 100, 500);
    assertArrayEquals(new double[] {100, 500}, new ImageRenderer().range(in, c, 1));
    c.channels.get(0).min = 2000;
    assertThrows(IllegalArgumentException.class, () -> c.validate(in));
    ImagePlus z = image(1);
    z.setDimensions(1, 3, 1);
    assertThrows(IllegalArgumentException.class, () -> in.snapshot(z, null));
  }

  @Test
  void invertAndClipping() {
    ChannelConfig c = new ChannelConfig(1, "gray", ChannelConfig.Lut.Grayscale);
    ImageRenderer r = new ImageRenderer();
    assertEquals(0, r.rgb(-1, c));
    assertEquals(0xffffff, r.rgb(3000, c));
    c.invert = true;
    assertEquals(0xffffff, r.rgb(-1, c));
    assertEquals(0, r.rgb(3000, c));
  }

  @Test
  void defaultLutOrderCoversFiveChannelsThenFallsBackToGrayscale() {
    assertEquals(ChannelConfig.Lut.Blue, ChannelConfig.defaultLut(1));
    assertEquals(ChannelConfig.Lut.Green, ChannelConfig.defaultLut(2));
    assertEquals(ChannelConfig.Lut.Red, ChannelConfig.defaultLut(3));
    assertEquals(ChannelConfig.Lut.Cyan, ChannelConfig.defaultLut(4));
    assertEquals(ChannelConfig.Lut.Magenta, ChannelConfig.defaultLut(5));
    assertEquals(ChannelConfig.Lut.Grayscale, ChannelConfig.defaultLut(6));
  }

  @Test
  void grayscaleOverrideAppliesOnlyToSingleChannelDisplayNotMerge() {
    ChannelConfig c = new ChannelConfig(1, "Green", ChannelConfig.Lut.Green);
    c.grayscale = true;
    ImageRenderer r = new ImageRenderer();
    assertEquals(0x00ff00, r.rgb(2000, c, true) & 0xffffff, "Merge keeps the real LUT");
    assertEquals(0xffffff, r.rgb(2000, c, false) & 0xffffff, "Single-channel display honors the Grayscale override");
    c.invert = true;
    assertEquals(0x00ff00, r.rgb(2000, c, true) & 0xffffff, "Merge is unaffected by Invert too");
    assertEquals(0, r.rgb(2000, c, false) & 0xffffff, "Invert flips the grayscale-overridden display");
  }

  @Test
  void grayscaleOverrideKeepsTrueLutInsideMergeEndToEnd() {
    InputImageManager in = new InputImageManager();
    FigureConfiguration c = config(in, 1000);
    c.channels.get(0).grayscale = true; // Channel 1 (Green) viewed as grayscale, standalone only.
    java.awt.image.BufferedImage out = new PanelLayoutEngine().render(c, in);
    assertEquals(0x808080, out.getRGB(0, 0) & 0xffffff, "Standalone Green cell renders grayscale");
    assertEquals(0x808000, out.getRGB(0, 12) & 0xffffff, "Merge cell still combines the real Green/Red LUTs");
  }
}
