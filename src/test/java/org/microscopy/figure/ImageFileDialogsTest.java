package org.microscopy.figure;

import static org.junit.jupiter.api.Assertions.*;
import ij.ImagePlus;
import ij.process.ByteProcessor;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageFileDialogsTest {
  @TempDir Path temp;

  @Test void selectedImageWinsAndMissingDirectoryFallsBack() throws Exception {
    Path first = Files.createDirectory(temp.resolve("first"));
    Path second = Files.createDirectory(temp.resolve("second"));
    InputImageManager inputs = new InputImageManager();
    ImagePlus image = new ImagePlus("sample", new ByteProcessor(2, 2));
    String a = inputs.snapshot(image, first.resolve("a.tif").toString()).id;
    String b = inputs.snapshot(image, second.resolve("b.tif").toString()).id;
    String missing = inputs.snapshot(image, temp.resolve("missing/c.tif").toString()).id;
    assertEquals(second.toFile(), ImageFileDialogs.directory(inputs, Arrays.asList(b, a)));
    assertEquals(first.toFile(), ImageFileDialogs.directory(inputs, Arrays.asList(missing, a)));
    assertNull(ImageFileDialogs.directory(inputs, Collections.singletonList(missing)));
  }

  @Test void unsavedImageUsesStandardChooserLocation() {
    InputImageManager inputs = new InputImageManager();
    String id = inputs.snapshot(new ImagePlus("unsaved", new ByteProcessor(2, 2)), null).id;
    assertNull(ImageFileDialogs.directory(inputs, Collections.singletonList(id)));
    assertNull(ImageFileDialogs.parent(""));
  }
}
