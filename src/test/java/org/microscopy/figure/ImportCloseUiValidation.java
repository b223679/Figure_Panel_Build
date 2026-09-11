package org.microscopy.figure;

import java.awt.*;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.*;
import java.util.Collections;
import javax.swing.*;
import ij.ImagePlus;
import ij.io.FileSaver;

/** Run in a separate JVM with headless=false; operates on synthetic inputs only. */
public class ImportCloseUiValidation {
  static FigurePanelBuilderDialog dialog;
  static Path directory;
  static Timer responder;
  static int stage;

  public static void main(String[] args) throws Exception {
    directory = Files.createTempDirectory(Paths.get("target"), "import-close-ui-");
    ImagePlus image = ImageImportTest.stack(3);
    File source = directory.resolve("source.tif").toFile();
    new FileSaver(image).saveAsTiffStack(source.getAbsolutePath()); image.flush();
    byte[] original = Files.readAllBytes(source.toPath());
    try {
      SwingUtilities.invokeAndWait(() -> {
        dialog = new FigurePanelBuilderDialog(false); dialog.setVisible(true);
        respond(JOptionPane.CANCEL_OPTION, false, false);
        dialog.addFiles(Collections.singletonList(source)); stop();
        try (java.util.stream.Stream<Path> files = Files.list(directory)) {
          if (files.count() != 1) throw new AssertionError("Cancelled import wrote a file");
        } catch (java.io.IOException ex) { throw new RuntimeException(ex); }
        respond(JOptionPane.OK_OPTION, false, false);
        dialog.addFiles(Collections.singletonList(source)); stop();
        respond(JOptionPane.CANCEL_OPTION, false, false); close(); stop();
        if (!dialog.isDisplayable()) throw new AssertionError("Cancel closed window");
        respond(JOptionPane.YES_OPTION, true, false); close(); stop();
        if (!dialog.isDisplayable()) throw new AssertionError("Cancelled save closed window");
        respond(JOptionPane.YES_OPTION, false, true); close(); stop();
        if (dialog.isDisplayable()) throw new AssertionError("Successful save did not close window");
        if (!Files.exists(directory.resolve("saved.json"))) throw new AssertionError("Settings not saved");
        dialog = new FigurePanelBuilderDialog(false); dialog.setVisible(true);
        respond(JOptionPane.NO_OPTION, false, false); close(); stop();
        if (dialog.isDisplayable()) throw new AssertionError("No did not close window");
      });
      if (!java.util.Arrays.equals(original, Files.readAllBytes(source.toPath())))
        throw new AssertionError("Input TIFF changed");
      System.out.println("PASS: projection accept/cancel; close cancel/no/save/cancelled-save; source unchanged");
    } finally {
      SwingUtilities.invokeAndWait(() -> { stop(); if (dialog != null) dialog.dispose(); });
    }
    System.exit(0);
  }

  static void close() { dialog.dispatchEvent(new WindowEvent(dialog, WindowEvent.WINDOW_CLOSING)); }
  static void stop() { if (responder != null) responder.stop(); }
  static <T> T find(Container root, Class<T> type) {
    if (type.isInstance(root)) return type.cast(root);
    for (Component c : root.getComponents()) if (c instanceof Container) {
      T found = find((Container)c, type); if (found != null) return found;
    }
    return null;
  }
  static void respond(int answer, boolean cancelSave, boolean save) {
    stage = 0;
    responder = new Timer(150, e -> {
      for (Window w : Window.getWindows()) if (w instanceof JDialog && w.isVisible()) {
        JFileChooser chooser = find((Container)w, JFileChooser.class);
        if (chooser != null) {
          if (cancelSave) chooser.cancelSelection();
          else if (save) { chooser.setSelectedFile(directory.resolve("saved.json").toFile()); chooser.approveSelection(); }
          return;
        }
        JOptionPane pane = find((Container)w, JOptionPane.class);
        if (pane != null) { pane.setValue(answer); stage++; return; }
      }
    });
    responder.start();
  }
}
