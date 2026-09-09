package org.microscopy.figure;

import static org.junit.jupiter.api.Assertions.*;

import ij.*;
import ij.io.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SafetyTest {
  @TempDir Path temp;

  @Test
  void openImageRetainsSourcePathForProtection() throws Exception {
    TestImageGenerator.generate(temp);
    ImagePlus image = new Opener().openImage(temp.resolve("Image A.tif").toString());
    InputImageManager in = new InputImageManager();
    InputImageManager.Source source = in.snapshot(image, null);
    assertNotNull(source.path);
    assertThrows(
        IllegalArgumentException.class,
        () -> OutputSafety.checkDestination(temp.resolve("Image A.tif").toFile(), in));
    OutputSafety.checkDestination(temp.resolve("output.tif").toFile(), in);
  }

  @Test
  void mismatchAndNonfiniteValues() {
    InputImageManager in = new InputImageManager();
    FigureConfiguration c = CoreTest.config(in, 1);
    InputImageManager.Source different =
        in.snapshot(new ImagePlus("wrong", new ij.process.ShortProcessor(7, 5)), null);
    c.conditions.add(new ConditionConfig("wrong", different.id));
    assertThrows(IllegalArgumentException.class, () -> c.validate(in));
    ChannelConfig channel = new ChannelConfig(1, "test", ChannelConfig.Lut.Red);
    channel.max = Double.NaN;
    assertThrows(IllegalArgumentException.class, channel::validate);
  }

  @Test
  void sourcePixelsAllChannelsUnchangedByDecoratedPreview() {
    ImagePlus image = TestImageGenerator.create(0);
    short[][] before = new short[3][];
    for (int i = 0; i < 3; i++) before[i] = ((short[]) image.getStack().getPixels(i + 1)).clone();
    InputImageManager in = new InputImageManager();
    InputImageManager.Source source = in.snapshot(image, null);
    FigureConfiguration c = CoreTest.config(in, 1);
    c.conditions.get(0).sourceId = source.id;
    c.labels.showRows = true;
    c.labels.showColumns = true;
    c.scaleBar.show = true;
    new PreviewRenderer().render(c, in, 400, 300);
    new PanelLayoutEngine().render(c, in);
    for (int i = 0; i < 3; i++)
      assertArrayEquals(before[i], (short[]) image.getStack().getPixels(i + 1));
    assertEquals(0.25, image.getCalibration().pixelWidth);
    assertNull(image.getOverlay());
  }
}
