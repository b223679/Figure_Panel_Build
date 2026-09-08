package org.microscopy.figure;

import ij.ImagePlus;
import ij.io.Opener;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import java.io.File;
import java.util.*;

/** Owns snapshots only; never retains a caller's mutable ImagePlus. */
public class InputImageManager {
  public static final class Source {
    public final String id, title, path, unit;
    public final int width, height, channels, bitDepth;
    public final double pixelWidth, pixelHeight;
    public final List<String> channelNames;
    private final ImageProcessor[] planes;

    Source(String id, String path, ImagePlus image) {
      this.id = id;
      this.path = path;
      title = image.getTitle();
      width = image.getWidth();
      height = image.getHeight();
      channels = image.getNChannels();
      bitDepth = image.getBitDepth();
      Calibration cal = image.getCalibration();
      pixelWidth = cal.pixelWidth;
      pixelHeight = cal.pixelHeight;
      unit = cal.getUnit();
      planes = new ImageProcessor[channels];
      List<String> names = new ArrayList<>();
      for (int c = 0; c < channels; c++) {
        int index = image.getStackIndex(c + 1, 1, 1);
        planes[c] = image.getStack().getProcessor(index).duplicate();
        String name = image.getStack().getSliceLabel(index);
        names.add(
            name == null || name.trim().isEmpty() ? "Channel " + (c + 1) : name.split("\n")[0]);
      }
      channelNames = Collections.unmodifiableList(names);
    }

    public float value(int channel, int x, int y) {
      return planes[channel - 1].getf(x, y);
    }
  }

  private final Map<String, Source> sources = new LinkedHashMap<>();

  /** Copies the index; immutable source pixel snapshots can safely be shared by export workers. */
  public InputImageManager copy() {
    InputImageManager copy = new InputImageManager();
    copy.sources.putAll(sources);
    return copy;
  }

  public Source load(File file) {
    if (!file.getName().toLowerCase(Locale.ROOT).matches(".*\\.tiff?"))
      throw new IllegalArgumentException("Select a TIF/TIFF file.");
    ImagePlus image = new Opener().openImage(file.getAbsolutePath());
    if (image == null) throw new IllegalArgumentException("Cannot read TIFF: " + file);
    try {
      return snapshot(image, file.getAbsolutePath());
    } finally {
      image.flush();
    }
  }

  public Source snapshot(ImagePlus image, String path) {
    if (image == null) throw new IllegalArgumentException("No image selected.");
    if (image.getNSlices() != 1 || image.getNFrames() != 1)
      throw new IllegalArgumentException(
          "This image contains Z/T dimensions. Please create a 2D image before using Figure Panel"
              + " Builder: "
              + image.getTitle());
    if (image.getBitDepth() == 24)
      throw new IllegalArgumentException(
          "RGB input is not supported; use grayscale channel planes: " + image.getTitle());
    if (path == null && image.getOriginalFileInfo() != null) {
      ij.io.FileInfo info = image.getOriginalFileInfo();
      if (info.directory != null && info.fileName != null)
        path = new File(info.directory, info.fileName).getAbsolutePath();
    }
    Source s = new Source(UUID.randomUUID().toString(), path, image);
    sources.put(s.id, s);
    return s;
  }

  public Source get(String id) {
    Source s = sources.get(id);
    if (s == null) throw new IllegalArgumentException("Missing source: " + id);
    return s;
  }

  public Collection<Source> all() {
    return Collections.unmodifiableCollection(sources.values());
  }

  public void clear() {
    sources.clear();
  }
}
