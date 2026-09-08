package org.microscopy.figure;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class SettingsSerializer {
  private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

  private static class Document {
    int version = 1;
    FigureConfiguration configuration;
    Map<String, String> sources = new LinkedHashMap<>();
  }

  public static class Loaded {
    public final FigureConfiguration configuration;
    public final InputImageManager inputs;

    Loaded(FigureConfiguration c, InputImageManager i) {
      configuration = c;
      inputs = i;
    }
  }

  public FigureConfiguration copy(FigureConfiguration c) {
    return gson.fromJson(gson.toJson(c), FigureConfiguration.class);
  }

  public void save(File file, FigureConfiguration config, InputImageManager inputs)
      throws IOException {
    OutputSafety.checkDestination(file, inputs);
    config.validate(inputs);
    Document doc = new Document();
    doc.configuration = config;
    for (ConditionConfig c : config.conditions) {
      InputImageManager.Source s = inputs.get(c.sourceId);
      if (s.path == null)
        throw new IllegalArgumentException(
            "Settings require source TIFF paths. Save the open image as a TIFF and add that file"
                + " first: "
                + s.title);
      if (file.getCanonicalFile().equals(new File(s.path).getCanonicalFile()))
        throw new IllegalArgumentException("Cannot overwrite source TIFF with settings.");
      doc.sources.put(s.id, s.path);
    }
    Path dest = file.toPath().toAbsolutePath();
    for (Map.Entry<String, String> entry : doc.sources.entrySet()) {
      Path source = Paths.get(entry.getValue()).toAbsolutePath();
      if (Objects.equals(dest.getRoot(), source.getRoot()))
        entry.setValue(dest.getParent().relativize(source).toString());
    }
    Path temp = Files.createTempFile(dest.getParent(), "figure-settings-", ".tmp");
    try {
      try (Writer w = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
        gson.toJson(doc, w);
      }
      Files.move(temp, dest, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  public Loaded load(File file) throws IOException {
    Document doc;
    JsonObject raw;
    try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
      raw = gson.fromJson(reader, JsonObject.class);
      doc = gson.fromJson(raw, Document.class);
    }
    if (doc == null || doc.version != 1 || doc.configuration == null || doc.sources == null)
      throw new IllegalArgumentException("Invalid/unsupported settings document.");
    FigureConfiguration cfg = doc.configuration;
    // Claude's original inset documents used explicit width/height, before the ROI-size mode existed.
    JsonObject oldConfig = raw.getAsJsonObject("configuration");
    if (oldConfig.has("inset") && oldConfig.get("inset").isJsonObject()
        && !oldConfig.getAsJsonObject("inset").has("sameAsRoi")) cfg.inset.sameAsRoi = false;
    InputImageManager inputs = new InputImageManager();
    Map<String, String> remap = new HashMap<>();
    try {
      for (ConditionConfig c : cfg.conditions) {
        String id = remap.get(c.sourceId);
        if (id == null) {
          String path = doc.sources.get(c.sourceId);
          if (path == null) throw new IllegalArgumentException("Missing source path: " + c.label);
          File source = new File(path);
          if (!source.isAbsolute()) source = new File(file.getAbsoluteFile().getParentFile(), path);
          id = inputs.load(source).id;
          remap.put(c.sourceId, id);
        }
        c.sourceId = id;
      }
      cfg.validate(inputs);
    } catch (NullPointerException ex) {
      throw new IllegalArgumentException("Settings contain missing fields.", ex);
    }
    return new Loaded(cfg, inputs);
  }
}
