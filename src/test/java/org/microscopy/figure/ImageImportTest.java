package org.microscopy.figure;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ShortProcessor;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ImageImportTest {
  @TempDir Path directory;

  static ImagePlus stack(int slices) {
    ImageStack stack = new ImageStack(2, 1);
    for (int z = 0; z < slices; z++) for (int c = 0; c < 2; c++)
      stack.addSlice("Channel " + c, new ShortProcessor(2, 1,
          new short[]{(short)(c * 100 + z), (short)(c * 100 + slices - z)}, null));
    ImagePlus image = new ImagePlus("synthetic", stack);
    image.setDimensions(2, slices, 1);
    image.getCalibration().pixelWidth = 0.25;
    image.getCalibration().pixelHeight = 0.5;
    image.getCalibration().setUnit("um");
    return image;
  }

  @Test void maximumKeepsChannelsCalibrationAndOriginalPixels() {
    for (int z : new int[]{1, 2, 4}) {
      ImagePlus original = stack(z), projected = ImageImport.maximum(original);
      assertEquals(2, projected.getNChannels());
      assertEquals(1, projected.getNSlices());
      assertEquals(16, projected.getBitDepth());
      assertEquals(0.25, projected.getCalibration().pixelWidth);
      assertEquals(0.5, projected.getCalibration().pixelHeight);
      assertEquals(z - 1, projected.getStack().getProcessor(1).get(0));
      assertEquals(100 + z, projected.getStack().getProcessor(2).get(1));
      assertEquals(0, original.getStack().getProcessor(1).get(0));
      assertEquals(z, original.getNSlices());
      original.flush(); projected.flush();
    }
  }

  @Test void savedProjectionReopensAndDoesNotOverwrite() throws Exception {
    ImagePlus original = stack(4);
    Path existing = directory.resolve("sample_MAX.tif");
    Files.write(existing, new byte[]{1,2,3});
    InputImageManager inputs = new InputImageManager();
    InputImageManager.Source first = ImageImport.save(inputs, original, directory.toFile(), "sample");
    InputImageManager.Source second = ImageImport.save(inputs, original, directory.toFile(), "sample");
    assertNotEquals(first.path, second.path);
    assertEquals(directory, Paths.get(first.path).getParent());
    assertEquals(2, first.channels);
    assertEquals(103, first.value(2, 0, 0));
    assertEquals(0.25, first.pixelWidth);
    assertArrayEquals(new byte[]{1,2,3}, Files.readAllBytes(existing));
    FigureConfiguration config = new FigureConfiguration();
    config.conditions.add(new ConditionConfig("test", first.id));
    config.channels.add(new ChannelConfig(1, "one", ChannelConfig.Lut.Blue));
    config.channels.add(new ChannelConfig(2, "two", ChannelConfig.Lut.Green));
    config.displayChannels.add(new DisplayChannel("one", false, 1));
    Path settings = directory.resolve("settings.json");
    new SettingsSerializer().save(settings.toFile(), config, inputs);
    SettingsSerializer.Loaded loaded = new SettingsSerializer().load(settings.toFile());
    assertEquals(103, loaded.inputs.get(loaded.configuration.conditions.get(0).sourceId).value(2,0,0));
    original.flush();
  }

  @Test void rejectsTimeSeriesWithoutChangingIt() {
    ImagePlus original = stack(4);
    original.setDimensions(2, 2, 2);
    assertThrows(IllegalArgumentException.class, () -> ImageImport.maximum(original));
    assertEquals(2, original.getNFrames());
    original.flush();
  }
}
