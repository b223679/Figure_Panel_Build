package org.microscopy.figure;

import ij.*;
import ij.io.FileSaver;
import ij.process.ShortProcessor;
import java.nio.file.*;

/** Deterministic synthetic fixtures, never experimental observations. */
public class TestImageGenerator {
  public static ImagePlus create(int condition) {
    int w = 256, h = 192;
    ImageStack stack = new ImageStack(w, h);
    java.util.Random random = new java.util.Random(20260907L + condition);
    for (int channel = 1; channel <= 3; channel++) {
      short[] pixels = new short[w * h];
      for (int y = 0; y < h; y++)
        for (int x = 0; x < w; x++) {
          double dx = x - (65 + channel * 30 + condition * 4), dy = y - (65 + channel * 15);
          double spot =
              Math.exp(-(dx * dx + dy * dy) / (2.0 * (14 + channel * 3) * (14 + channel * 3)));
          double line =
              Math.exp(-Math.pow(y - (40 + channel * 28 + 12 * Math.sin(x / 25.0)), 2) / 12.0);
          double value =
              80
                  + random.nextInt(60)
                  + (1200 + condition * 500) * spot
                  + (450 + channel * 150) * line;
          if (x < 16 && y < 16) value = 1000;
          pixels[y * w + x] = (short) Math.round(value);
        }
      stack.addSlice(
          new String[] {"Green", "Red", "Blue"}[channel - 1],
          new ShortProcessor(w, h, pixels, null));
    }
    ImagePlus image =
        new ImagePlus(new String[] {"Image A", "Image B", "Image C"}[condition] + ".tif", stack);
    image.setDimensions(3, 1, 1);
    image.setOpenAsHyperStack(true);
    image.getCalibration().pixelWidth = 0.25;
    image.getCalibration().pixelHeight = 0.25;
    image.getCalibration().setUnit("µm");
    image.setProperty(
        "Info",
        "SYNTHETIC TEST DATA; fixed 1000 intensity patch at x,y < 16; not experimental data.");
    return image;
  }

  public static void generate(Path folder) throws Exception {
    Files.createDirectories(folder);
    for (int condition = 0; condition < 3; condition++) {
      ImagePlus image = create(condition);
      if (!new FileSaver(image).saveAsTiffStack(folder.resolve(image.getTitle()).toString()))
        throw new java.io.IOException("Could not save synthetic TIFF.");
    }
  }

  public static void main(String[] args) throws Exception {
    generate(Paths.get(args.length == 0 ? "test-data" : args[0]));
  }
}
