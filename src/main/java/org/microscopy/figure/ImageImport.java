package org.microscopy.figure;

import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;
import ij.io.Opener;
import ij.process.ImageProcessor;
import java.awt.Component;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import javax.swing.*;

/** Converts private image instances only, keeping source files and open Fiji images untouched. */
final class ImageImport {
  private ImageImport() {}

  static boolean confirm(Component owner, String title) {
    return confirm(owner, title, false);
  }

  private static boolean confirm(Component owner, String title, boolean lif) {
    final boolean[] accepted = {false};
    Runnable prompt = () -> accepted[0] = JOptionPane.showConfirmDialog(owner,
        lif ? "Save and open MAX Z-projections of all images in " + title + "?\n"
            + "TIFF files will be saved in a new folder next to the LIF file.\n"
            + "Single-plane images will be saved without projection."
            : "Create, save, and open a MAX Z-projection of " + title + "?",
        "MAX Projection", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION;
    try {
      if (SwingUtilities.isEventDispatchThread()) prompt.run();
      else SwingUtilities.invokeAndWait(prompt);
    } catch (Exception ex) { throw new IllegalArgumentException(ex); }
    return accepted[0];
  }

  static List<InputImageManager.Source> load(InputImageManager inputs, File file, Component owner) {
    boolean lif = file.getName().toLowerCase(Locale.ROOT).endsWith(".lif");
    if (!lif && !file.getName().toLowerCase(Locale.ROOT).matches(".*\\.tiff?"))
      throw new IllegalArgumentException("Select a TIF/TIFF or LIF file.");
    if (lif && !confirm(owner, file.getName(), true)) return Collections.emptyList();
    ImagePlus[] images = lif ? readLif(file) : new ImagePlus[]{new Opener().openImage(file.getAbsolutePath())};
    try {
      boolean projection = false;
      for (ImagePlus image : images) {
        validate(image);
        projection |= image.getNSlices() > 1;
      }
      if (!lif && projection && !confirm(owner, file.getName())) return Collections.emptyList();
      File directory = lif ? Files.createTempDirectory(file.getAbsoluteFile().getParentFile().toPath(),
          stem(file) + "_TIFF_").toFile() : file.getAbsoluteFile().getParentFile();
      List<InputImageManager.Source> result = new ArrayList<>();
      for (int i = 0; i < images.length; i++) {
        ImagePlus image = images[i];
        if (lif || image.getNSlices() > 1) {
          String name = lif ? String.format(Locale.ROOT, "%03d_%s", i + 1,
              image.getTitle().replaceAll("[^\\p{L}\\p{N}._-]", "_")) : stem(file);
          result.add(save(inputs, image, directory, name));
        } else result.add(inputs.snapshot(image, file.getAbsolutePath()));
      }
      return result;
    } catch (java.io.IOException ex) { throw new IllegalArgumentException("Cannot save imported TIFF: " + ex.getMessage(), ex); }
    finally { for (ImagePlus image : images) if (image != null) image.flush(); }
  }

  static InputImageManager.Source open(InputImageManager inputs, ImagePlus image, Component owner) {
    validate(image);
    if (image.getNSlices() == 1) return inputs.snapshot(image, null);
    ij.io.FileInfo info = image.getOriginalFileInfo();
    if (info == null || info.directory == null || info.fileName == null)
      throw new IllegalArgumentException("Save this Z stack to a file before importing it.");
    if (!confirm(owner, image.getTitle())) return null;
    File file = new File(info.directory, info.fileName);
    return save(inputs, image, file.getAbsoluteFile().getParentFile(), stem(file));
  }

  static void validate(ImagePlus image) {
    if (image == null) throw new IllegalArgumentException("Cannot read input image.");
    if (image.getNFrames() != 1) throw new IllegalArgumentException("T > 1 is not supported: " + image.getTitle());
    if (image.getBitDepth() == 24) throw new IllegalArgumentException("Use grayscale channel planes: " + image.getTitle());
  }

  static ImagePlus maximum(ImagePlus image) {
    validate(image);
    ImageStack stack = new ImageStack(image.getWidth(), image.getHeight());
    for (int c = 1; c <= image.getNChannels(); c++) {
      int index = image.getStackIndex(c, 1, 1);
      ImageProcessor plane = image.getStack().getProcessor(index).duplicate();
      for (int z = 2; z <= image.getNSlices(); z++) {
        ImageProcessor next = image.getStack().getProcessor(image.getStackIndex(c, z, 1));
        for (int p = 0; p < plane.getPixelCount(); p++) plane.setf(p, Math.max(plane.getf(p), next.getf(p)));
      }
      stack.addSlice(image.getStack().getSliceLabel(index), plane);
    }
    ImagePlus result = new ImagePlus(image.getTitle(), stack);
    result.setDimensions(image.getNChannels(), 1, 1);
    result.setCalibration(image.getCalibration().copy());
    if (image.getNChannels() > 1) {
      ij.CompositeImage composite = new ij.CompositeImage(result, ij.CompositeImage.COMPOSITE);
      if (image instanceof ij.CompositeImage) composite.setLuts(((ij.CompositeImage) image).getLuts());
      result = composite;
    }
    return result;
  }

  static InputImageManager.Source save(InputImageManager inputs, ImagePlus image, File directory, String name) {
    ImagePlus converted = maximum(image);
    try {
      File dest = Files.createTempFile(directory.toPath(), name + (image.getNSlices() > 1 ? "_MAX_" : "_"), ".tif").toFile();
      converted.setTitle(dest.getName());
      FileSaver saver = new FileSaver(converted);
      boolean ok = converted.getStackSize() > 1 ? saver.saveAsTiffStack(dest.getAbsolutePath()) : saver.saveAsTiff(dest.getAbsolutePath());
      if (!ok) throw new IllegalArgumentException("TIFF save failed: " + dest);
      return inputs.load(dest);
    } catch (java.io.IOException ex) { throw new IllegalArgumentException("Cannot save TIFF: " + ex.getMessage(), ex); }
    finally { converted.flush(); }
  }

  private static String stem(File file) { return file.getName().replaceFirst("\\.[^.]+$", ""); }

  private static ImagePlus[] readLif(File file) {
    try {
      Class<?> optionsClass = Class.forName("loci.plugins.in.ImporterOptions");
      Object options = optionsClass.getDeclaredConstructor().newInstance();
      optionsClass.getMethod("setId", String.class).invoke(options, file.getAbsolutePath());
      // Do not inherit a user's last interactive Bio-Formats split/crop/range preferences.
      for (String option : new String[]{"setSplitChannels", "setSplitTimepoints", "setSplitFocalPlanes",
          "setConcatenate", "setGroupFiles", "setCrop", "setSpecifyRanges", "setAutoscale"})
        optionsClass.getMethod(option, boolean.class).invoke(options, false);
      optionsClass.getMethod("setStackFormat", String.class).invoke(options, "Hyperstack");
      optionsClass.getMethod("setStackOrder", String.class).invoke(options, "XYCZT");
      optionsClass.getMethod("setOpenAllSeries", boolean.class).invoke(options, true);
      optionsClass.getMethod("setQuiet", boolean.class).invoke(options, true);
      optionsClass.getMethod("setVirtual", boolean.class).invoke(options, false);
      return (ImagePlus[]) Class.forName("loci.plugins.BF").getMethod("openImagePlus", optionsClass).invoke(null, options);
    } catch (ClassNotFoundException ex) {
      throw new IllegalArgumentException("LIF import requires the Fiji Bio-Formats plugin.", ex);
    } catch (Exception ex) {
      Throwable cause = ex.getCause() == null ? ex : ex.getCause();
      throw new IllegalArgumentException("Cannot read LIF: " + cause.getMessage(), cause);
    }
  }
}
