package org.microscopy.figure;

import ij.ImagePlus;
import ij.WindowManager;
import ij.io.Opener;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Isolated desktop acceptance test. Never attaches to a user's existing Fiji process. */
public class DirectManipulationValidation {
  private static FigurePanelBuilderDialog dialog;
  private static Path output;
  private static final List<ImagePlus> opened = new ArrayList<>();
  private static final AtomicReference<Throwable> asynchronousFailure = new AtomicReference<>();
  public static void main(String[] args) throws Exception {
    output = Files.createTempDirectory(Paths.get("artifacts"), "direct-ui-");
    Map<Path, byte[]> hashes = new LinkedHashMap<>();
    for (String name : new String[] {"Image A", "Image B", "Image C"}) {
      Path file = Paths.get("test-data", name + ".tif"); hashes.put(file, hash(file));
    }
    try {
      edt(() -> {
        for (Path file : hashes.keySet()) {
          ImagePlus image = new Opener().openImage(file.toAbsolutePath().toString()); opened.add(image); image.show();
        }
        respond("Select Images", w -> selectOpen(w, 0, 1));
        dialog = new FigurePanelBuilderDialog(); dialog.setTitle("Direct manipulation QA — temporary test window");
        dialog.setVisible(true);
      });
      await(() -> config().conditions.size() == 2, "startup image selection"); waitPreview();
      edt(() -> {
        check(button(dialog, "Add TIF...") == null && button(dialog, "Preview") == null, "Removed toolbar actions");
        check(button(dialog, "Generate TIF") != null, "Generate TIF button");
        JTabbedPane tabs = (JTabbedPane) field(dialog, "tabs");
        check(tabs.getTitleAt(0).equals("Label name") && tabs.getTitleAt(1).equals("Style"), "Tab names");
        respond("Select Images", w -> selectOpen(w, 2));
        ((JButton) workspace().getComponent(1)).doClick();
        check(config().conditions.size() == 3, "+ Condition uses image selector");
        respond("Select Images", w -> selectOpen(w, 0));
        button(dialog, "Select Images").doClick();
        check(config().conditions.size() == 4, "Select Images toolbar adds images");
        shortcut("control Z"); check(config().conditions.size() == 3, "Undo condition addition");
        shortcut("control Y"); check(config().conditions.size() == 4, "Redo condition addition");
        shortcut("control Z");
        respond("Select Images", w -> pane(w).setValue("Cancel"));
        button(dialog, "Select Images").doClick();
        check(config().conditions.size() == 3, "Image selector cancellation");
        addDisplay(1);
        shortcut("control Z"); check(config().displayChannels.size() == 1, "Undo channel addition");
        shortcut("control Y"); check(config().displayChannels.size() == 2, "Redo channel addition");
        addDisplay(0, 1, 2);
        check(config().displayChannels.size() == 3 && config().displayChannels.get(2).merge, "Channel and merge addition");
        JTable table = (JTable) field(dialog, "channels");
        check(table.getColumnCount() == 3 && table.getRowCount() == 3, "Compact displayed-channel table");
        check(table.getColumnName(0).equals("Name") && table.getColumnName(2).equals("Channel No."), "Channel columns");
      });
      waitPreview(); capture("direct-layout.png");
      edt(() -> {
        Point a = cellPoint(0, 0), b = cellPoint(0, 2);
        drag(a, b, false); captureUnchecked("direct-drag-column.png"); release(b);
        check(config().conditions.get(2).label.equals("Image A"), "Condition column reorder");
      });
      waitPreview();
      edt(() -> {
        Point a = labelPoint(true, 0), b = labelPoint(true, 2);
        drag(a, b, false); captureUnchecked("direct-drag-row.png"); release(b);
        check(config().displayChannels.get(2).channels.equals(Arrays.asList(1)), "Channel row reorder");
        button(dialog, "Swap").doClick(); check(!config().rowsAreChannels, "Swap");
      });
      waitPreview(); capture("direct-swapped.png");
      edt(() -> {
        int count = config().conditions.size();
        respond("Remove Condition", w -> pane(w).setValue(JOptionPane.CANCEL_OPTION));
        dropTrash(false, 0); check(config().conditions.size() == count, "Cancel removal");
      });
      waitPreview();
      edt(() -> {
        respond("Remove Condition", w -> pane(w).setValue(JOptionPane.OK_OPTION));
        dropTrash(false, 0); check(config().conditions.size() == 2, "Trash removes transposed condition row");
        shortcut("control Z"); check(config().conditions.size() == 3, "Undo condition removal");
        shortcut("control Y"); check(config().conditions.size() == 2, "Redo condition removal");
        shortcut("control Z");
      });
      waitPreview();
      edt(() -> {
        respond("Remove Channel / Merge", w -> pane(w).setValue(JOptionPane.OK_OPTION));
        dropTrash(true, 1); check(config().displayChannels.size() == 2, "Trash removes transposed merge column");
        shortcut("control Z"); check(config().displayChannels.size() == 3, "Undo merge removal");
        check(config().displayChannels.get(1).merge, "Merge source channels restored");
        button(dialog, "Swap").doClick();
      });
      waitPreview();
      edt(() -> {
        respond("Remove Condition", w -> pane(w).setValue(JOptionPane.OK_OPTION));
        dropTrash(false, 0); check(config().conditions.size() == 2, "Trash removes condition column"); shortcut("control Z");
      });
      waitPreview();
      edt(() -> {
        respond("Remove Channel / Merge", w -> pane(w).setValue(JOptionPane.OK_OPTION));
        dropTrash(true, 0); check(config().displayChannels.size() == 2, "Trash removes channel row"); shortcut("control Z");
        JTable conditions = (JTable) field(dialog, "conditions");
        conditions.setValueAt("Edited condition", 0, 0);
        check(config().conditions.get(0).label.equals("Edited condition"), "Condition name editing");
        shortcut("control Z"); check(!config().conditions.get(0).label.equals("Edited condition"), "Undo name");
        shortcut("control Y");
        JTable channels = (JTable) field(dialog, "channels");
        channels.setValueAt("Edited channel", 0, 0); channels.setValueAt(ChannelConfig.Lut.Grayscale, 0, 1);
        check(config().displayLabel(config().displayChannels.get(1)).contains("Edited channel"), "Merge label follows shared source name");
        ContrastPanel bc = (ContrastPanel) field(dialog, "dockContrast");
        bc.selectChannel(2);
        ((JSpinner) field(bc, "min")).setValue(20.0);
        ((JCheckBox) field(bc, "invert")).doClick();
        check(config().channel(2).min == 20 && config().channel(2).invert, "Shared B&C and invert");
        shortcut("control Z"); check(config().channel(2).min == 0 && !config().channel(2).invert, "Undo continuous B&C");
        shortcut("control Y"); check(config().channel(2).min == 20 && config().channel(2).invert, "Redo B&C");
        JTabbedPane tabs = (JTabbedPane) field(dialog, "tabs"); tabs.setSelectedIndex(1);
        named(dialog, JCheckBox.class, "showScaleBar").doClick();
        named(dialog, JSpinner.class, "scaleLength").setValue(20.0);
        named(dialog, JSpinner.class, "horizontalGap").setValue(8);
        named(dialog, JSpinner.class, "verticalGap").setValue(6);
        check(config().scaleBar.show && config().horizontalGap == 8, "Style controls");
        tabs.setSelectedIndex(0);
      });
      waitPreview(); capture("direct-final-layout.png");
      edt(() -> {
        button(dialog, "Generate TIF").doClick();
        ImagePlus figure = WindowManager.getImage("Figure Panel");
        check(figure != null && figure.getBitDepth() == 24, "Generate TIF RGB ImagePlus");
        figure.changes = false; figure.close();
      });
      for (String format : new String[] {"tif", "png", "pptx"}) {
        File file = output.resolve("figure." + format).toFile();
        edt(() -> {
          chooseFile(file, () -> button(dialog, format.equals("tif") ? "Save RGB TIFF..." : "Save " + format.toUpperCase(Locale.ROOT) + "...").doClick());
        });
        await(() -> file.isFile() && file.length() > 0, "save " + format);
      }
      edt(() -> {
        File file = output.resolve("figure-settings.json").toFile();
        chooseFile(file, () -> button(dialog, "Save settings").doClick());
        int rows = config().rows(), columns = config().columns();
        chooseFile(file, () -> button(dialog, "Load settings").doClick());
        check(config().rows() == rows && config().columns() == columns && config().scaleBar.show
            && config().scaleBar.lengthUm == 20 && config().channel(2).invert, "Settings roundtrip");
      });
      waitPreview();
      edt(() -> {
        dialog.setExtendedState(JFrame.NORMAL); dialog.setSize(1280, 800);
        dialog.validate();
      });
      waitPreview(); capture("direct-normal-window.png");
      edt(() -> {
        JPanel controls = (JPanel) field(dialog, "controls");
        for (int i = 0; i < 2; i++) {
          Component section = controls.getComponent(i);
          check(section.getY() + section.getHeight() <= controls.getHeight(), "Both name tables fit without outer scrolling");
        }
        ContrastPanel bc = (ContrastPanel) field(dialog, "dockContrast");
        JPanel dock = (JPanel) field(dialog, "contrastDock");
        check(bc.getX() >= 0 && bc.getX() + bc.getWidth() <= dock.getWidth(), "B&C fits normal window");
        Component trash = (Component) field(dialog, "trash");
        check(trash.getY() + trash.getHeight() <= dock.getHeight(), "Trash fits normal window");
        while (!config().conditions.isEmpty()) {
          respond("Remove Condition", w -> pane(w).setValue(JOptionPane.OK_OPTION));
          try {
            Method remove = dialog.getClass().getDeclaredMethod("removeEntry", boolean.class, int.class);
            remove.setAccessible(true); remove.invoke(dialog, false, 0);
          } catch (Exception ex) { throw new RuntimeException(ex); }
        }
        JTable channels = (JTable) field(dialog, "channels");
        check(channels.getRowCount() == 0, "Removing the last condition also clears stale channel rows");
        check(field(dialog, "dockContrast") == null, "Empty layout remains editable without B&C errors");
        shortcut("control Z"); check(config().conditions.size() == 1, "Undo final condition removal");
      });
      edt(() -> {
        // Removing every condition must reset stale channel/display state: otherwise a
        // differently-sized/channeled image added afterward fails to render until restart.
        while (!config().conditions.isEmpty()) {
          respond("Remove Condition", w -> pane(w).setValue(JOptionPane.OK_OPTION));
          try {
            Method remove = dialog.getClass().getDeclaredMethod("removeEntry", boolean.class, int.class);
            remove.setAccessible(true); remove.invoke(dialog, false, 0);
          } catch (Exception ex) { throw new RuntimeException(ex); }
        }
        check(config().channels.isEmpty() && config().displayChannels.isEmpty(),
            "Stale channel/display definitions cleared with the last condition");
        File differentlySized;
        try { differentlySized = syntheticTiff("resized.tif", 96, 64, 2); }
        catch (Exception ex) { throw new RuntimeException(ex); }
        dialog.addFiles(Collections.singletonList(differentlySized));
        check(config().conditions.size() == 1, "Differently-sized image opens after full removal");
        check(config().channels.size() == 2, "New image's own channel count applies, not the old one");
        // Neither is what this check is about: the replacement image is uncalibrated, and label
        // bands would otherwise inflate the rendered size beyond the raw image dimensions.
        config().scaleBar.show = false;
        config().labels.showRows = false; config().labels.showColumns = false;
        BufferedImage rendered = new PanelLayoutEngine().render(config(), inputs());
        check(rendered.getWidth() == 96 && rendered.getHeight() == 64, "Renders at the new image's own size");
      });
      for (Map.Entry<Path, byte[]> entry : hashes.entrySet())
        check(Files.exists(entry.getKey()) && Arrays.equals(entry.getValue(), hash(entry.getKey())), "Original TIFF preserved");
      System.out.println("PASS: startup/Select Images/+Condition; add Channel/Merge; reorder both axes; swap; drag highlights; trash confirm/cancel on both axes; Undo/Redo key bindings; names/LUT/shared B&C/invert; Style; Generate TIF; TIFF/PNG/PPTX save buttons; settings roundtrip; original TIFF hashes unchanged. Outputs: " + output);
    } finally {
      SwingUtilities.invokeAndWait(() -> {
        if (dialog != null) dialog.dispose();
        for (ImagePlus image : opened) { image.changes = false; image.close(); }
        for (Window window : Window.getWindows()) if (window.isDisplayable()) window.dispose();
      });
    }
  }
  private static void addDisplay(int... indices) {
    respond("Add channel / merge", w -> {
      List<JCheckBox> checks = findAll(w, JCheckBox.class);
      for (int i = 0; i < checks.size(); i++) { boolean selected = false; for (int index : indices) selected |= i == index; checks.get(i).setSelected(selected); }
      pane(w).setValue(JOptionPane.OK_OPTION);
    });
    ((JButton) workspace().getComponent(config().rowsAreChannels ? 2 : 1)).doClick();
  }
  private static void selectOpen(Container w, int... indices) {
    JList<?> list = named(w, JList.class, "openImageList"); check(list != null, "Shared image selection list");
    list.setSelectedIndices(indices); pane(w).setValue("Add selected");
  }
  private static void respond(String title, Consumer<Container> action) {
    final int[] attempts = {0};
    javax.swing.Timer timer = new javax.swing.Timer(60, null);
    timer.addActionListener(e -> {
      for (Window w : Window.getWindows()) if (w instanceof JDialog && w.isVisible() && ((JDialog) w).getTitle().equals(title)) {
        timer.stop(); try { action.accept((Container) w); }
        catch (Throwable failure) { asynchronousFailure.set(failure); w.dispose(); }
        return;
      }
      if (++attempts[0] > 100) { timer.stop(); asynchronousFailure.set(new AssertionError("Dialog did not open: " + title)); }
    }); timer.start();
  }
  private static void chooseFile(File file, Runnable trigger) {
    javax.swing.Timer timer = new javax.swing.Timer(60, null);
    final int[] attempts = {0};
    timer.addActionListener(e -> {
      for (Window w : Window.getWindows()) if (w instanceof JDialog && w.isVisible()) {
        List<JFileChooser> choosers = findAll((Container) w, JFileChooser.class);
        if (!choosers.isEmpty()) { timer.stop(); choosers.get(0).setSelectedFile(file.getAbsoluteFile()); choosers.get(0).approveSelection(); return; }
      }
      if (++attempts[0] > 100) { timer.stop(); asynchronousFailure.set(new AssertionError("File chooser did not open")); }
    }); timer.start(); trigger.run();
  }
  private static void shortcut(String key) {
    Object action = dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(key));
    check(action != null, "Shortcut registered: " + key);
    dialog.getRootPane().getActionMap().get(action).actionPerformed(new ActionEvent(dialog, 0, key));
  }
  private static Point cellPoint(int row, int col) {
    FigureConfiguration c = config(); InputImageManager.Source source = inputs().get(c.conditions.get(0).sourceId);
    LabelRenderer labels = new LabelRenderer();
    return canvasPoint((c.labels.rowRight ? 0 : labels.rowBand(c.labels)) + col * (source.width + c.horizontalGap) + source.width / 2.0,
        (c.labels.columnBottom ? 0 : labels.columnBand(c.labels)) + row * (source.height + c.verticalGap) + source.height / 2.0);
  }
  private static Point labelPoint(boolean channel, int index) {
    FigureConfiguration c = config(); InputImageManager.Source source = inputs().get(c.conditions.get(0).sourceId);
    Dimension size = new PanelLayoutEngine().dimensions(c, inputs());
    Rectangle box = FigureWorkspace.labelBounds(c, source.width, source.height, size.width, size.height, channel, index);
    return canvasPoint(box.getCenterX(), box.getCenterY());
  }
  private static Point canvasPoint(double x, double y) {
    BufferedImage image = (BufferedImage) field(workspace(), "image");
    Dimension size = new PanelLayoutEngine().dimensions(config(), inputs()); Component canvas = canvas();
    return new Point((canvas.getWidth() - image.getWidth()) / 2 + (int)Math.round(x * image.getWidth() / size.width),
        (canvas.getHeight() - image.getHeight()) / 2 + (int)Math.round(y * image.getHeight() / size.height));
  }
  private static void dropTrash(boolean channel, int index) {
    TrashTarget trash = (TrashTarget) field(dialog, "trash");
    Point drop = SwingUtilities.convertPoint(trash, trash.getWidth() / 2, trash.getHeight() / 2, canvas());
    drag(labelPoint(channel, index), drop, false);
    check((Boolean) field(trash, "hover"), "Trash hover feedback");
    captureUnchecked("direct-trash-hover.png"); release(drop);
  }
  private static void drag(Point from, Point to, boolean release) {
    mouse(MouseEvent.MOUSE_PRESSED, from); mouse(MouseEvent.MOUSE_DRAGGED, to);
    check((Boolean) field(workspace(), "dragging"), "Drag visual state");
    if (release) release(to);
  }
  private static void release(Point point) { mouse(MouseEvent.MOUSE_RELEASED, point); }
  private static void mouse(int event, Point p) {
    canvas().dispatchEvent(new MouseEvent(canvas(), event, System.currentTimeMillis(), InputEvent.BUTTON1_DOWN_MASK,
        p.x, p.y, 1, false, MouseEvent.BUTTON1));
  }
  private static Component canvas() { return workspace().getComponent(0); }
  private static FigureWorkspace workspace() { return (FigureWorkspace) field(dialog, "workspace"); }
  private static FigureConfiguration config() { return (FigureConfiguration) field(dialog, "config"); }
  private static InputImageManager inputs() { return (InputImageManager) field(dialog, "inputs"); }
  private static Object field(Object o, String name) {
    try { Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o); }
    catch (Exception ex) { throw new RuntimeException(ex); }
  }
  private static <T extends Component> List<T> findAll(Container c, Class<T> type) {
    List<T> found = new ArrayList<>(); if (type.isInstance(c)) found.add(type.cast(c));
    for (Component child : c.getComponents()) {
      if (child instanceof Container) found.addAll(findAll((Container) child, type));
      else if (type.isInstance(child)) found.add(type.cast(child));
    } return found;
  }
  private static <T extends Component> T named(Container c, Class<T> type, String name) {
    for (T component : findAll(c, type)) if (name.equals(component.getName())) return component; return null;
  }
  private static AbstractButton button(Container c, String text) {
    for (AbstractButton b : findAll(c, AbstractButton.class)) if (text.equals(b.getText())) return b; return null;
  }
  private static JOptionPane pane(Container c) { return findAll(c, JOptionPane.class).get(0); }
  private interface Check { boolean ready(); }
  private static void await(Check check, String name) throws Exception {
    for (int i = 0; i < 200; i++) {
      AtomicReference<Boolean> ready = new AtomicReference<>(false); edt(() -> ready.set(check.ready()));
      if (ready.get()) return; Thread.sleep(50);
    } throw new AssertionError("Timed out: " + name);
  }
  private static void waitPreview() throws Exception { await(() -> (Boolean) field(workspace(), "ready"), "preview"); }
  private static void edt(Runnable run) throws Exception {
    if (asynchronousFailure.get() != null) throw new AssertionError(asynchronousFailure.get());
    SwingUtilities.invokeAndWait(run);
    if (asynchronousFailure.get() != null) throw new AssertionError(asynchronousFailure.get());
  }
  private static void capture(String name) throws Exception { edt(() -> captureUnchecked(name)); }
  private static void captureUnchecked(String name) {
    try {
      Container c = dialog.getContentPane(); BufferedImage image = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
      Graphics2D g = image.createGraphics(); c.printAll(g); g.dispose(); ImageIO.write(image, "png", output.resolve(name).toFile());
    } catch (Exception ex) { throw new RuntimeException(ex); }
  }
  private static byte[] hash(Path p) throws Exception { return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)); }
  private static File syntheticTiff(String name, int width, int height, int channels) throws Exception {
    ij.ImageStack stack = new ij.ImageStack(width, height);
    for (int ch = 0; ch < channels; ch++) {
      short[] pixels = new short[width * height];
      Arrays.fill(pixels, (short) (500 + ch * 100));
      stack.addSlice("Channel " + (ch + 1), new ij.process.ShortProcessor(width, height, pixels, null));
    }
    ImagePlus image = new ImagePlus(name, stack);
    image.setDimensions(channels, 1, 1);
    image.setOpenAsHyperStack(true);
    File file = output.resolve(name).toFile();
    if (!new ij.io.FileSaver(image).saveAsTiff(file.getAbsolutePath()))
      throw new java.io.IOException("Could not write synthetic TIFF: " + file);
    return file;
  }
  private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
