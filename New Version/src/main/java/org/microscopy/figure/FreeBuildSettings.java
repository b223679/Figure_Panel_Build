package org.microscopy.figure;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Separate document discriminator; normal Figure settings remain version 1 and unchanged. */
public final class FreeBuildSettings {
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static class Document {
    String mode = "free-build";
    int version = 1;
    FreeBuildConfiguration configuration;
    Map<String,String> sources = new LinkedHashMap<>();
  }
  public static final class Loaded {
    public FreeBuildConfiguration configuration;
    public InputImageManager inputs;
  }
  public static String snapshot(FreeBuildConfiguration c) { return GSON.toJson(c); }
  public static FreeBuildConfiguration restore(String s) { return GSON.fromJson(s,FreeBuildConfiguration.class); }

  public static void save(File file, FreeBuildConfiguration c, InputImageManager inputs) throws IOException {
    OutputSafety.checkDestination(file,inputs); c.validate(inputs);
    Document doc = new Document(); doc.configuration = c;
    Path dest = file.toPath().toAbsolutePath();
    for (FreeBuildConfiguration.Panel p : c.panels) if (p.image != null) {
      InputImageManager.Source source = inputs.get(p.image.conditions.get(0).sourceId);
      if (source.path == null) throw new IllegalArgumentException("Source TIFF path is required.");
      Path path = Paths.get(source.path).toAbsolutePath();
      doc.sources.put(source.id, Objects.equals(dest.getRoot(),path.getRoot()) ? dest.getParent().relativize(path).toString() : path.toString());
    }
    Path temp = Files.createTempFile(dest.getParent(),"free-settings-",".tmp");
    try {
      Files.write(temp,GSON.toJson(doc).getBytes(StandardCharsets.UTF_8));
      Files.move(temp,dest,StandardCopyOption.REPLACE_EXISTING);
    } finally { Files.deleteIfExists(temp); }
  }

  public static Loaded load(File file) throws IOException {
    Document doc = GSON.fromJson(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8),Document.class);
    if (doc == null || !"free-build".equals(doc.mode) || doc.version != 1 || doc.configuration == null || doc.sources == null)
      throw new IllegalArgumentException("Select Free build settings (normal Figure settings use the other mode).");
    Loaded loaded = new Loaded(); loaded.configuration = doc.configuration; loaded.inputs = new InputImageManager();
    Map<String,String> remap = new HashMap<>();
    for (FreeBuildConfiguration.Panel p : loaded.configuration.panels) if (p.image != null) {
      if (p.image.conditions.size() != 1) throw new IllegalArgumentException("Invalid panel settings.");
      ConditionConfig condition = p.image.conditions.get(0);
      String id = remap.get(condition.sourceId);
      if (id == null) {
        String path = doc.sources.get(condition.sourceId);
        if (path == null) throw new IllegalArgumentException("Missing TIFF path.");
        File source = new File(path);
        if (!source.isAbsolute()) source = new File(file.getAbsoluteFile().getParentFile(),path);
        id = loaded.inputs.load(source).id; remap.put(condition.sourceId,id);
      }
      condition.sourceId = id;
    }
    loaded.configuration.validate(loaded.inputs);
    return loaded;
  }
}
