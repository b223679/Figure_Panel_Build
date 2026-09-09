package org.microscopy.figure;

import java.io.*;
import java.nio.file.Files;

public final class OutputSafety {
  private OutputSafety() {}

  public static void checkDestination(File destination, InputImageManager inputs)
      throws IOException {
    File canonical = destination.getCanonicalFile();
    for (InputImageManager.Source source : inputs.all())
      if (source.path != null) {
        File original = new File(source.path).getCanonicalFile();
        if (original.equals(canonical)
            || (original.exists()
                && canonical.exists()
                && Files.isSameFile(original.toPath(), canonical.toPath())))
          throw new IllegalArgumentException("Cannot overwrite a source TIFF.");
      }
  }
}
