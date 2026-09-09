package org.microscopy.figure;

import static org.junit.jupiter.api.Assertions.*;

import ij.ImagePlus;
import ij.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IntegrationTest {
  @TempDir Path temp;

  @Test
  void tifSettingsPreviewAndOutput() throws Exception {
    TestImageGenerator.generate(temp);
    InputImageManager inputs = new InputImageManager();
    FigureConfiguration config = new FigureConfiguration();
    byte[] before =
        MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(temp.resolve("Control.tif")));
    for (String name : new String[] {"Control", "HPR", "KO"}) {
      InputImageManager.Source s = inputs.load(temp.resolve(name + ".tif").toFile());
      assertEquals(3, s.channels);
      assertEquals(16, s.bitDepth);
      assertEquals(0.25, s.pixelWidth);
      config.conditions.add(new ConditionConfig(name, s.id));
      ImagePlus reopened = new Opener().openImage(s.path);
      assertEquals(1, reopened.getNSlices());
      assertEquals(1, reopened.getNFrames());
    }
    config.channels.add(new ChannelConfig(1, "Green", ChannelConfig.Lut.Green));
    config.channels.add(new ChannelConfig(2, "Red", ChannelConfig.Lut.Red));
    config.channels.add(new ChannelConfig(3, "Blue", ChannelConfig.Lut.Blue));
    config.displayChannels.add(new DisplayChannel("Green", false, 1));
    config.displayChannels.add(new DisplayChannel("Red", false, 2));
    config.displayChannels.add(new DisplayChannel("Merge", true, 1, 2));
    config.labels.showRows = true;
    config.labels.showColumns = true;
    config.horizontalGap = 5;
    config.verticalGap = 5;
    config.scaleBar.show = true;
    java.awt.image.BufferedImage preview = new PreviewRenderer().render(config, inputs, 500, 500);
    assertTrue(preview.getWidth() <= 500);
    assertTrue(preview.getHeight() <= 500);
    long count = 0;
    for (long n : new PreviewRenderer().histogram(config, inputs, 1, 0, 4000)) count += n;
    assertEquals(3L * 256 * 192, count);
    SettingsSerializer json = new SettingsSerializer();
    Path settings = temp.resolve("settings.json");
    json.save(settings.toFile(), config, inputs);
    SettingsSerializer.Loaded loaded = json.load(settings.toFile());
    assertEquals(3, loaded.configuration.conditions.size());
    assertEquals(config.channels.get(0).max, loaded.configuration.channels.get(0).max);
    assertTrue(loaded.configuration.scaleBar.show);
    ImagePlus result = new PanelLayoutEngine().generate(loaded.configuration, loaded.inputs);
    assertEquals(24, result.getBitDepth());
    assertTrue(new FileSaver(result).saveAsTiff(temp.resolve("figure.tif").toString()));
    ImagePlus out = new Opener().openImage(temp.resolve("figure.tif").toString());
    assertEquals(24, out.getBitDepth());
    assertEquals(result.getWidth(), out.getWidth());
    assertEquals(result.getHeight(), out.getHeight());
    assertArrayEquals(
        before,
        MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(temp.resolve("Control.tif"))));
    assertThrows(
        IllegalArgumentException.class,
        () -> json.save(temp.resolve("Control.tif").toFile(), config, inputs));
  }

  @Test
  void malformedSettingsAndMissingFiles() throws Exception {
    Path p = temp.resolve("bad.json");
    Files.write(p, "{\"version\":99}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertThrows(IllegalArgumentException.class, () -> new SettingsSerializer().load(p.toFile()));
  }

  @Test
  void allLutsAndMissingChannel() {
    ImageRenderer r = new ImageRenderer();
    int[] colors = {0xffffff, 0xff0000, 0x00ff00, 0x0000ff, 0x00ffff, 0xff00ff, 0xffff00};
    int i = 0;
    for (ChannelConfig.Lut lut : ChannelConfig.Lut.values()) {
      ChannelConfig c = new ChannelConfig(1, "x", lut);
      assertEquals(colors[i++], r.rgb(2000, c));
    }
    InputImageManager in = new InputImageManager();
    FigureConfiguration c = CoreTest.config(in, 1000);
    c.displayChannels.get(0).channels.set(0, 4);
    assertThrows(IllegalArgumentException.class, () -> c.validate(in));
  }
}
