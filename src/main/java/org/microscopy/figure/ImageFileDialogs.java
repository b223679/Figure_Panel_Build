package org.microscopy.figure;

import ij.ImagePlus;
import ij.WindowManager;
import ij.io.FileInfo;
import java.io.File;
import java.util.List;

/** Resolves dialog locations without changing image or settings state. */
final class ImageFileDialogs {
  private ImageFileDialogs() {}

  static File directory(InputImageManager inputs, List<String> sourceIds) {
    for (String id : sourceIds) {
      File directory = parent(inputs.get(id).path);
      if (directory != null) return directory;
    }
    ImagePlus current = WindowManager.getCurrentImage();
    if (current != null) {
      FileInfo info = current.getOriginalFileInfo();
      if (info != null && info.directory != null && info.fileName != null)
        return parent(new File(info.directory, info.fileName).getPath());
    }
    return null;
  }

  static File parent(String path) {
    if (path == null || path.trim().isEmpty()) return null;
    File directory = new File(path).getAbsoluteFile().getParentFile();
    return directory != null && directory.isDirectory() ? directory : null;
  }
}
