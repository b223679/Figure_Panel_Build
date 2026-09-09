package org.microscopy.figure;

import static org.junit.jupiter.api.Assertions.*;
import ij.ImagePlus;
import ij.process.ShortProcessor;
import org.junit.jupiter.api.Test;

class AppearanceDefaultsTest {
  private InputImageManager.Source source(int width, int height, boolean calibrated) {
    ImagePlus image = new ImagePlus("Test", new ShortProcessor(width, height));
    if (calibrated) { image.getCalibration().setUnit("µm"); image.getCalibration().pixelWidth = 0.25; }
    return new InputImageManager().snapshot(image, null);
  }

  @Test
  void initializesFromLongEdgeAndCalibration() {
    FigureConfiguration c = new FigureConfiguration();
    InputImageManager.Source s = source(1024, 1024, true);
    AppearanceDefaults.initialize(c, s);
    assertEquals(102, c.labels.rowFontSize);
    assertEquals(102, c.labels.columnFontSize);
    assertEquals(102, c.scaleBar.fontSize);
    assertEquals(31, c.scaleBar.thickness);
    assertEquals(20, c.scaleBar.lengthUm);
    assertEquals(80, new ScaleBarRenderer().lengthPixels(s, c.scaleBar));
    assertDoesNotThrow(() -> new ScaleBarRenderer().draw(
        new java.awt.image.BufferedImage(1024, 1024, java.awt.image.BufferedImage.TYPE_INT_RGB), s, c.scaleBar));
    FigureConfiguration rectangular = new FigureConfiguration();
    AppearanceDefaults.initialize(rectangular, source(2048, 512, false));
    assertEquals(205, rectangular.labels.rowFontSize);
    assertEquals(20, rectangular.scaleBar.lengthUm);
  }

  @Test
  void preservesManualValuesAndLegacyColors() {
    FigureConfiguration c = new FigureConfiguration();
    c.labels.rowFontSize = 85; c.labels.columnFontSize = 90;
    c.scaleBar.fontSize = 45; c.scaleBar.lengthUm = 30; c.scaleBar.thickness = 15;
    AppearanceDefaults.initialize(c, source(1024, 1024, true));
    assertEquals(85, c.labels.rowFontSize);
    assertEquals(90, c.labels.columnFontSize);
    assertEquals(45, c.scaleBar.fontSize);
    assertEquals(30, c.scaleBar.lengthUm);
    assertEquals(15, c.scaleBar.thickness);
    c.labels.whiteText = false;
    assertFalse(c.labels.isWhiteText(true)); assertFalse(c.labels.isWhiteText(false));
    c.labels.rowWhiteText = true;
    FigureConfiguration copied = new SettingsSerializer().copy(c);
    assertTrue(copied.labels.isWhiteText(true)); assertFalse(copied.labels.isWhiteText(false));
  }
}
