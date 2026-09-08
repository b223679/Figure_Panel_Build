package org.microscopy.figure;

import ij.*;
import ij.io.FileSaver;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;

public class FigurePanelBuilderDialog extends JFrame {
  private InputImageManager inputs = new InputImageManager();
  private FigureConfiguration config = new FigureConfiguration();
  private final JPanel controls = new SettingsBody();
  private final FigureWorkspace workspace = new FigureWorkspace(new FigureWorkspace.Actions() {
    public void add(boolean channel) { attempt(() -> { if (channel) chooseDisplay(); else chooseFiles(); }); }
    public void select(int condition, int display) { attempt(() -> selectDisplay(condition, display)); }
    public void edit(boolean channel, int index) { attempt(() -> editCanvasLabel(channel, index)); }
    public void move(boolean channel, int from, int to) { attempt(() -> reorderCanvas(channel, from, to)); }
  });
  private final JScrollPane canvasScroll = new JScrollPane(workspace);
  private final JPanel contrastDock = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 8));
  private final JLabel selection = new JLabel("Select a channel in the figure to adjust B&C");
  private final JButton transpose = new JButton("⇄  Swap rows / columns");
  private final JToggleButton showContrast = new JToggleButton("B&C", true);
  private final JPanel advanced = new JPanel(new BorderLayout());
  private ContrastPanel dockContrast;
  private int selectedDisplay = -1;
  private boolean appearanceInitialized;
  private final JLabel status = new JLabel("B&C is shared by all conditions for each channel.");
  private final ConditionTable conditionModel = new ConditionTable();
  private final ChannelTable channelModel = new ChannelTable();
  private final JTable conditions = new JTable(conditionModel), channels = new JTable(channelModel);
  private final DefaultListModel<String> displayModel = new DefaultListModel<>();
  private final JList<String> displays = new JList<>(displayModel);
  private final JCheckBox automatic = new JCheckBox("Automatic grid", true),
      rowChannels = new JCheckBox("Rows = Channels", true);
  private final JSpinner rows = spinner(3, 1, 100), columns = spinner(3, 1, 100);
  private final JTabbedPane tabs = new JTabbedPane();
  private final javax.swing.Timer previewTimer = new javax.swing.Timer(180, e -> startPreview());
  private SwingWorker<java.awt.image.BufferedImage, Void> previewWorker;
  private long previewRevision;

  public FigurePanelBuilderDialog() {
    super("Figure Panel Builder");
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    config.labels.showRows = true;
    config.labels.showColumns = true;
    controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
    controls.add(new JLabel("Conditions — double-click a name to edit"));
    conditions.setPreferredScrollableViewportSize(new Dimension(430, 100));
    controls.add(new JScrollPane(conditions));
    JPanel ordering = new JPanel();
    button(ordering, "Up", () -> moveCondition(-1));
    button(ordering, "Down", () -> moveCondition(1));
    button(
        ordering,
        "Remove",
        () -> {
          int i = conditions.getSelectedRow();
          if (i >= 0) config.conditions.remove(i);
          refresh();
        });
    button(ordering, "Change source...", this::changeSource);
    controls.add(ordering);
    JPanel grid = new JPanel();
    grid.add(automatic);
    grid.add(rowChannels);
    controls.add(grid);
    grid = new JPanel();
    grid.add(new JLabel("Rows"));
    grid.add(rows);
    grid.add(new JLabel("Columns"));
    grid.add(columns);
    controls.add(grid);
    automatic.addActionListener(e -> refresh());
    rowChannels.addActionListener(e -> refresh());
    controls.add(new JLabel("Shared Channel B&C and LUT (all conditions)"));
    channels.setPreferredScrollableViewportSize(new Dimension(430, 110));
    controls.add(new JScrollPane(channels));
    channels
        .getColumnModel()
        .getColumn(4)
        .setCellEditor(new DefaultCellEditor(new JComboBox<>(ChannelConfig.Lut.values())));
    JPanel channelActions = new JPanel();
    button(channelActions, "Auto (all conditions)", this::autoChannel);
    controls.add(channelActions);
    controls.add(new JLabel("Displayed rows/columns"));
    displays.setVisibleRowCount(4);
    controls.add(new JScrollPane(displays));
    JPanel displayActions = new JPanel();
    button(displayActions, "Add channel / Merge", this::chooseDisplay);
    button(displayActions, "Edit label / LUT", () -> {
      int i = displays.getSelectedIndex();
      if (i >= 0) editCanvasLabel(true, i);
    });
    controls.add(displayActions);
    displayActions = new JPanel();
    button(displayActions, "Up", () -> moveDisplay(-1));
    button(displayActions, "Down", () -> moveDisplay(1));
    button(
        displayActions,
        "Remove",
        () -> {
          int i = displays.getSelectedIndex();
          if (i >= 0) config.displayChannels.remove(i);
          refresh();
        });
    controls.add(displayActions);
    for (Component child : controls.getComponents()) {
      if (child instanceof JComponent) ((JComponent) child).setAlignmentX(Component.LEFT_ALIGNMENT);
      if (child instanceof JPanel) child.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
      if (child instanceof JScrollPane) child.setMaximumSize(new Dimension(Integer.MAX_VALUE, 125));
    }
    tabs.addTab("Images / Channels", new JScrollPane(controls));
    advanced.add(tabs, BorderLayout.CENTER);
    advanced.setPreferredSize(new Dimension(490, 600));
    advanced.setVisible(false);
    JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
    button(toolbar, "Add TIF...", this::chooseFiles);
    button(toolbar, "Open images...", this::openImages);
    toolbar.add(transpose);
    toolbar.add(showContrast);
    showContrast.addActionListener(e -> {
      contrastDock.setVisible(showContrast.isSelected());
      revalidate(); schedulePreview();
    });
    transpose.addActionListener(e -> attempt(() -> {
      rowChannels.setSelected(!rowChannels.isSelected());
      automatic.setSelected(true);
      refresh();
    }));
    JToggleButton settingsToggle = new JToggleButton("Settings / Sources");
    toolbar.add(settingsToggle);
    settingsToggle.addActionListener(e -> {
      advanced.setVisible(settingsToggle.isSelected());
      revalidate();
      schedulePreview();
    });
    button(toolbar, "Load settings", () -> settings(true));
    button(toolbar, "Save settings", () -> settings(false));
    add(toolbar, BorderLayout.NORTH);
    JPanel center = new JPanel(new BorderLayout());
    JLabel hint = new JLabel("Click image: B&C    •    Double-click label: name / LUT    •    Drag image: reorder", SwingConstants.CENTER);
    hint.setOpaque(true);
    hint.setBackground(Color.BLACK);
    hint.setForeground(Color.LIGHT_GRAY);
    hint.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    center.add(hint, BorderLayout.NORTH);
    canvasScroll.getViewport().setBackground(Color.BLACK);
    canvasScroll.setBorder(BorderFactory.createEmptyBorder());
    center.add(canvasScroll, BorderLayout.CENTER);
    contrastDock.setBackground(Color.BLACK);
    selection.setForeground(Color.LIGHT_GRAY);
    contrastDock.add(selection);
    center.add(contrastDock, BorderLayout.SOUTH);
    add(center, BorderLayout.CENTER);
    add(advanced, BorderLayout.EAST);
    JPanel footer = new JPanel(new BorderLayout());
    JPanel outputActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
    button(outputActions, "Preview", this::renderPreview);
    button(outputActions, "Generate Figure", () -> generate(false));
    button(outputActions, "Save RGB TIFF...", () -> generate(true));
    button(outputActions, "Save PNG...", () -> exportFigure("png"));
    button(outputActions, "Save PPTX...", () -> exportFigure("pptx"));
    footer.add(outputActions, BorderLayout.WEST);
    footer.add(status, BorderLayout.CENTER);
    add(footer, BorderLayout.SOUTH);
    tabs.addTab(
        "Labels / Scale", new JScrollPane(new AppearancePanel(config, this::schedulePreview)));
    previewTimer.setRepeats(false);
    rows.addChangeListener(e -> schedulePreview());
    columns.addChangeListener(e -> schedulePreview());
    conditionModel.addTableModelListener(e -> schedulePreview());
    channelModel.addTableModelListener(e -> schedulePreview());
    canvasScroll.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
      public void componentResized(java.awt.event.ComponentEvent e) { schedulePreview(); }
    });
    setTransferHandler(
        new TransferHandler() {
          public boolean canImport(TransferSupport s) {
            return s.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
          }

          public boolean importData(TransferSupport s) {
            if (!canImport(s)) return false;
            try {
              List<?> objects =
                  (List<?>)
                      s.getTransferable()
                          .getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
              List<File> files = new ArrayList<>();
              for (Object o : objects) files.add((File) o);
              addFiles(files);
              return true;
            } catch (Exception ex) {
              JOptionPane.showMessageDialog(FigurePanelBuilderDialog.this, ex.getMessage());
              return false;
            }
          }
        });
    setSize(1380, 940);
    setMinimumSize(new Dimension(960, 720));
    GraphicsConfiguration screen = getGraphicsConfiguration();
    Rectangle usable = new Rectangle(screen.getBounds());
    Insets taskbar = Toolkit.getDefaultToolkit().getScreenInsets(screen);
    usable.x += taskbar.left; usable.y += taskbar.top;
    usable.width -= taskbar.left + taskbar.right; usable.height -= taskbar.top + taskbar.bottom;
    setMaximizedBounds(usable);
    setExtendedState(getExtendedState() | JFrame.MAXIMIZED_BOTH);
    setLocationByPlatform(true);
    refresh();
  }

  private static JSpinner spinner(int v, int min, int max) {
    return new JSpinner(new SpinnerNumberModel(v, min, max, 1));
  }

  private static class SettingsBody extends JPanel implements Scrollable {
    public Dimension getPreferredScrollableViewportSize() { return new Dimension(470, 640); }
    public boolean getScrollableTracksViewportWidth() { return true; }
    public boolean getScrollableTracksViewportHeight() { return false; }
    public int getScrollableUnitIncrement(Rectangle r, int axis, int direction) { return 24; }
    public int getScrollableBlockIncrement(Rectangle r, int axis, int direction) { return 200; }
  }

  private void button(JPanel p, String title, Runnable action) {
    JButton b = new JButton(title);
    b.addActionListener(e -> attempt(action));
    p.add(b);
  }

  private void attempt(Runnable action) {
    try {
      stopEditing();
      action.run();
    } catch (Exception ex) {
      JOptionPane.showMessageDialog(
          this, ex.getMessage(), "Figure Panel Builder", JOptionPane.ERROR_MESSAGE);
    }
  }

  private void stopEditing() {
    for (JTable t : Arrays.asList(conditions, channels))
      if (t.isEditing() && !t.getCellEditor().stopCellEditing())
        throw new IllegalArgumentException("Finish editing the table first.");
  }

  private void chooseFiles() {
    JFileChooser chooser = new JFileChooser();
    chooser.setMultiSelectionEnabled(true);
    chooser.setFileFilter(new FileNameExtensionFilter("TIFF", "tif", "tiff"));
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
      addFiles(Arrays.asList(chooser.getSelectedFiles()));
  }

  void addFiles(List<File> files) {
    try {
      for (File file : files) addSource(inputs.load(file));
    } finally {
      refresh();
    }
  }

  private void openImages() {
    int[] ids = WindowManager.getIDList();
    if (ids == null) throw new IllegalArgumentException("No open ImageJ images.");
    DefaultListModel<String> model = new DefaultListModel<>();
    for (int id : ids) model.addElement(WindowManager.getImage(id).getTitle());
    JList<String> list = new JList<>(model);
    if (JOptionPane.showConfirmDialog(
            this, new JScrollPane(list), "Select open images", JOptionPane.OK_CANCEL_OPTION)
        == JOptionPane.OK_OPTION)
      for (int i : list.getSelectedIndices())
        addSource(inputs.snapshot(WindowManager.getImage(ids[i]), null));
    refresh();
  }

  private void addSource(InputImageManager.Source source) {
    if (!config.conditions.isEmpty()) {
      InputImageManager.Source first = inputs.get(config.conditions.get(0).sourceId);
      if (first.width != source.width || first.height != source.height)
        throw new IllegalArgumentException(
            "Image sizes are different: "
                + first.title
                + " "
                + first.width
                + "x"
                + first.height
                + "; "
                + source.title
                + " "
                + source.width
                + "x"
                + source.height
                + ". Import cancelled.");
      if (first.channels != source.channels)
        throw new IllegalArgumentException(
            "Channel count differs between input images: "
                + first.title
                + "="
                + first.channels
                + ", "
                + source.title
                + "="
                + source.channels);
    }
    if (!appearanceInitialized) {
      AppearanceDefaults.initialize(config, source);
      appearanceInitialized = true;
      tabs.setComponentAt(1, new JScrollPane(new AppearancePanel(config, this::schedulePreview)));
    }
    config.conditions.add(
        new ConditionConfig(source.title.replaceFirst("(?i)\\.tiff?$", ""), source.id));
    if (config.channels.isEmpty()) {
      ChannelConfig.Lut[] luts = {
        ChannelConfig.Lut.Green, ChannelConfig.Lut.Red, ChannelConfig.Lut.Blue
      };
      for (int c = 1; c <= source.channels; c++) {
        ChannelConfig cc = new ChannelConfig(c, source.channelNames.get(c - 1), luts[(c - 1) % 3]);
        cc.max = source.bitDepth == 8 ? 255 : 2000;
        config.channels.add(cc);
        if (c == 1) config.displayChannels.add(new DisplayChannel(cc.label, false, c));
      }
    }
  }

  private void changeSource() {
    int i = conditions.getSelectedRow();
    if (i < 0) return;
    List<InputImageManager.Source> all = new ArrayList<>(inputs.all());
    String[] names =
        all.stream().map(s -> s.title + " [" + s.id.substring(0, 8) + "]").toArray(String[]::new);
    String choice =
        (String)
            JOptionPane.showInputDialog(
                this,
                "Source for selected condition",
                "Mapping",
                JOptionPane.PLAIN_MESSAGE,
                null,
                names,
                names[0]);
    if (choice != null) {
      config.conditions.get(i).sourceId = all.get(Arrays.asList(names).indexOf(choice)).id;
      refresh();
    }
  }

  private void moveCondition(int delta) {
    int i = conditions.getSelectedRow(), j = i + delta;
    if (i >= 0 && j >= 0 && j < config.conditions.size()) {
      Collections.swap(config.conditions, i, j);
      refresh();
      conditions.setRowSelectionInterval(j, j);
    }
  }

  private void moveDisplay(int delta) {
    int i = displays.getSelectedIndex(), j = i + delta;
    if (i >= 0 && j >= 0 && j < config.displayChannels.size()) {
      Collections.swap(config.displayChannels, i, j);
      refresh();
      displays.setSelectedIndex(j);
    }
  }

  private void autoChannel() {
    int i = channels.getSelectedRow();
    if (i < 0) throw new IllegalArgumentException("Select a channel row.");
    ChannelConfig c = config.channels.get(i);
    double[] range = new ImageRenderer().range(inputs, config, c.index);
    c.min = range[0];
    c.max = range[1];
    channelModel.fireTableDataChanged();
    if (dockContrast != null) dockContrast.refreshFromModel();
    renderPreview();
  }

  private void readGrid() {
    config.automaticGrid = automatic.isSelected();
    config.rowsAreChannels = rowChannels.isSelected();
    config.manualRows = (int) rows.getValue();
    config.manualColumns = (int) columns.getValue();
  }

  private void refresh() {
    readGrid();
    if (config.automaticGrid) {
      rows.setValue(Math.max(1, config.rows()));
      columns.setValue(Math.max(1, config.columns()));
    }
    rows.setEnabled(!config.automaticGrid);
    columns.setEnabled(!config.automaticGrid);
    conditionModel.fireTableDataChanged();
    channelModel.fireTableDataChanged();
    displayModel.clear();
    for (DisplayChannel d : config.displayChannels)
      displayModel.addElement(config.displayLabel(d) + " " + d.channels);
    workspace.setAxes(config.rowsAreChannels);
    rebuildDock();
    schedulePreview();
  }

  private void schedulePreview() {
    previewRevision++;
    workspace.pending();
    previewTimer.restart();
  }

  private void renderPreview() {
    schedulePreview();
  }

  private void startPreview() {
    if (config.conditions.isEmpty() || config.displayChannels.isEmpty()) {
      workspace.clear(config.conditions.isEmpty() ? "Add TIFF images using + Condition or drop files here"
          : "Use + Channel / Merge to add a displayed channel");
      return;
    }
    if (previewWorker != null && !previewWorker.isDone()) {
      previewTimer.restart();
      return;
    }
    readGrid();
    final FigureConfiguration snapshot = new SettingsSerializer().copy(config);
    final InputImageManager sources = inputs;
    final long revision = previewRevision;
    final int maxWidth = Math.max(320, canvasScroll.getViewport().getWidth() - 166);
    final int maxHeight = Math.max(160, canvasScroll.getViewport().getHeight() - 104);
    previewWorker =
        new SwingWorker<java.awt.image.BufferedImage, Void>() {
          protected java.awt.image.BufferedImage doInBackground() {
            return new PreviewRenderer().render(snapshot, sources, maxWidth, maxHeight);
          }

          protected void done() {
            if (revision != previewRevision) return;
            try {
              workspace.showFigure(get(), snapshot, sources);
              Dimension size = new PanelLayoutEngine().dimensions(snapshot, sources);
              String warning = snapshot.calibrationWarning(sources);
              status.setText(
                  warning.isEmpty()
                      ? "Final: "
                          + size.width
                          + " x "
                          + size.height
                          + " px (~"
                          + ((long) size.width * size.height * 4 / 1048576)
                          + " MiB RGB buffer)"
                      : warning);
            } catch (Exception ex) {
              workspace.clear("Preview unavailable — see status below");
              Throwable cause = ex.getCause() == null ? ex : ex.getCause();
              status.setText(cause.getMessage());
            }
          }
        };
    previewWorker.execute();
  }

  private void settings(boolean load) {
    JFileChooser chooser = new JFileChooser();
    chooser.setFileFilter(new FileNameExtensionFilter("Settings JSON", "json"));
    chooser.setSelectedFile(new File("figure-settings.json"));
    if ((load ? chooser.showOpenDialog(this) : chooser.showSaveDialog(this))
        != JFileChooser.APPROVE_OPTION) return;
    try {
      SettingsSerializer serializer = new SettingsSerializer();
      if (load) {
        loadSettings(chooser.getSelectedFile());
      } else {
        readGrid();
        File dest = chooser.getSelectedFile();
        if (!dest.getName().toLowerCase(Locale.ROOT).endsWith(".json"))
          dest = new File(dest.getPath() + ".json");
        if (dest.exists()
            && JOptionPane.showConfirmDialog(
                    this, "Replace " + dest.getName() + "?", "Settings", JOptionPane.YES_NO_OPTION)
                != JOptionPane.YES_OPTION) return;
        serializer.save(dest, config, inputs);
      }
    } catch (Exception ex) {
      throw new IllegalArgumentException(ex.getMessage(), ex);
    }
  }

  void loadSettings(File file) throws java.io.IOException {
    SettingsSerializer.Loaded result = new SettingsSerializer().load(file);
    inputs = result.inputs;
    config = result.configuration;
    appearanceInitialized = true;
    selectedDisplay = -1;
    automatic.setSelected(config.automaticGrid);
    rowChannels.setSelected(config.rowsAreChannels);
    rows.setValue(config.manualRows);
    columns.setValue(config.manualColumns);
    tabs.setComponentAt(1, new JScrollPane(new AppearancePanel(config, this::schedulePreview)));
    tabs.setSelectedIndex(0);
    refresh();
  }

  private void chooseDisplay() {
    if (config.channels.isEmpty()) {
      JOptionPane.showMessageDialog(this, "Add a TIFF image first.");
      return;
    }
    JPanel form = new JPanel(new BorderLayout(8, 8));
    JPanel choices = new JPanel();
    choices.setLayout(new BoxLayout(choices, BoxLayout.Y_AXIS));
    List<JCheckBox> checks = new ArrayList<>();
    boolean preselected = false;
    for (ChannelConfig ch : config.channels) {
      boolean alreadyShown = config.displayChannels.stream()
          .anyMatch(d -> !d.merge && d.channels.contains(ch.index));
      JCheckBox check = new JCheckBox(ch.index + ": " + ch.label, !preselected && !alreadyShown);
      if (check.isSelected()) preselected = true;
      checks.add(check); choices.add(check);
    }
    form.add(new JLabel("Check one channel, or multiple channels for a merge"), BorderLayout.NORTH);
    form.add(new JScrollPane(choices), BorderLayout.CENTER);
    if (JOptionPane.showConfirmDialog(this, form, "Add channel / merge",
        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
    List<Integer> selected = new ArrayList<>();
    for (int i = 0; i < checks.size(); i++) if (checks.get(i).isSelected()) selected.add(i);
    if (selected.isEmpty()) throw new IllegalArgumentException("Select at least one channel.");
    DisplayChannel d = new DisplayChannel();
    d.merge = selected.size() > 1;
    d.label = d.merge ? "Merge" : "Channel";
    for (int i : selected) d.channels.add(config.channels.get(i).index);
    config.displayChannels.add(d);
    selectedDisplay = config.displayChannels.size() - 1;
    automatic.setSelected(true);
    refresh();
  }

  private void selectDisplay(int condition, int display) {
    if (display < 0 || display >= config.displayChannels.size()) return;
    if (!showContrast.isSelected()) showContrast.doClick();
    selectedDisplay = display;
    displays.setSelectedIndex(display);
    if (condition >= 0) conditions.setRowSelectionInterval(condition, condition);
    workspace.select(condition, display);
    if (dockContrast == null) rebuildDock();
    dockContrast.selectChannel(config.displayChannels.get(display).channels.get(0));
    if (condition >= 0) {
      ConditionConfig cc = config.conditions.get(condition);
      status.setText(cc.label + " • " + inputs.get(cc.sourceId).title
          + " • " + config.displayLabel(config.displayChannels.get(display)));
    }
  }

  private void rebuildDock() {
    if (dockContrast != null) dockContrast.stop();
    dockContrast = null;
    contrastDock.removeAll();
    if (config.channels.isEmpty() || config.conditions.isEmpty()) {
      contrastDock.add(selection);
    } else {
      dockContrast = new ContrastPanel(config, inputs, () -> {
        channelModel.fireTableDataChanged();
        displayModel.clear();
        for (DisplayChannel d : config.displayChannels) displayModel.addElement(config.displayLabel(d) + " " + d.channels);
        schedulePreview();
      }, true);
      dockContrast.setPreferredSize(new Dimension(530, 190));
      contrastDock.add(dockContrast);
      if (!config.displayChannels.isEmpty()) {
        selectedDisplay = Math.max(0, Math.min(selectedDisplay, config.displayChannels.size() - 1));
        dockContrast.selectChannel(config.displayChannels.get(selectedDisplay).channels.get(0));
      }
    }
    workspace.select(-1, selectedDisplay);
    contrastDock.revalidate();
    contrastDock.repaint();
  }

  private void editCanvasLabel(boolean channel, int index) {
    if (!channel) {
      String value = JOptionPane.showInputDialog(this, "Condition label", config.conditions.get(index).label);
      if (value != null) config.conditions.get(index).label = value;
    } else {
      DisplayChannel d = config.displayChannels.get(index);
      JPanel form = new JPanel(new GridLayout(0, 3, 8, 8));
      List<JTextField> names = new ArrayList<>();
      List<JComboBox<ChannelConfig.Lut>> luts = new ArrayList<>();
      for (int id : d.channels) {
        ChannelConfig ch = config.channel(id);
        JTextField name = new JTextField(ch.label, 16);
        JComboBox<ChannelConfig.Lut> lut = new JComboBox<>(ChannelConfig.Lut.values());
        lut.setSelectedItem(ch.lut);
        form.add(new JLabel("Channel " + id)); form.add(name); form.add(lut);
        names.add(name); luts.add(lut);
      }
      if (JOptionPane.showConfirmDialog(this, form, "Channel names / LUTs — shared across the figure",
          JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
      for (int i = 0; i < d.channels.size(); i++) {
        ChannelConfig ch = config.channel(d.channels.get(i));
        ch.label = names.get(i).getText();
        ch.lut = (ChannelConfig.Lut) luts.get(i).getSelectedItem();
      }
    }
    refresh();
  }

  private void reorderCanvas(boolean channel, int from, int to) {
    if (channel) {
      config.displayChannels.add(to, config.displayChannels.remove(from));
      selectedDisplay = to;
    } else config.conditions.add(to, config.conditions.remove(from));
    refresh();
  }

  @Override
  public void dispose() {
    previewRevision++;
    previewTimer.stop();
    if (dockContrast != null) dockContrast.stop();
    if (previewWorker != null) previewWorker.cancel(true);
    super.dispose();
  }

  private void generate(boolean save) {
    readGrid();
    config.validate(inputs);
    if (config.labels.transparentBackground) {
      throw new IllegalArgumentException("Use Save PNG or Save PPTX for a transparent background. ImageJ/RGB TIFF does not preserve transparency.");
    }
    String warning = config.calibrationWarning(inputs);
    if (!warning.isEmpty())
      JOptionPane.showMessageDialog(this, warning, "Calibration", JOptionPane.WARNING_MESSAGE);
    File destination = null;
    if (save) {
      JFileChooser chooser = new JFileChooser();
      chooser.setSelectedFile(new File("figure.tif"));
      if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
      destination = chooser.getSelectedFile();
      if (!destination.getName().toLowerCase(Locale.ROOT).matches(".*\\.tiff?"))
        destination = new File(destination.getPath() + ".tif");
      try {
        OutputSafety.checkDestination(destination, inputs);
      } catch (java.io.IOException ex) {
        throw new IllegalArgumentException(ex);
      }
      if (destination.exists()
          && JOptionPane.showConfirmDialog(
                  this,
                  "Replace " + destination.getName() + "?",
                  "Save TIFF",
                  JOptionPane.YES_NO_OPTION)
              != JOptionPane.YES_OPTION) return;
    }
    ImagePlus result = new PanelLayoutEngine().generate(config, inputs);
    if (save) {
      if (!new FileSaver(result).saveAsTiff(destination.getAbsolutePath()))
        throw new IllegalArgumentException("TIFF save failed.");
      status.setText("Saved: " + destination.getName());
    } else result.show();
  }

  private void exportFigure(String format) {
    readGrid();
    config.validate(inputs);
    String warning = config.calibrationWarning(inputs);
    if (!warning.isEmpty())
      JOptionPane.showMessageDialog(this, warning, "Calibration", JOptionPane.WARNING_MESSAGE);
    JFileChooser chooser = new JFileChooser();
    chooser.setSelectedFile(new File("figure." + format));
    if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
    File chosen = chooser.getSelectedFile();
    if (!chosen.getName().toLowerCase(Locale.ROOT).endsWith("." + format))
      chosen = new File(chosen.getPath() + "." + format);
    final File destination = chosen;
    try { OutputSafety.checkDestination(destination, inputs); }
    catch (java.io.IOException ex) { throw new IllegalArgumentException(ex); }
    if (destination.exists() && JOptionPane.showConfirmDialog(this,
        "Replace " + destination.getName() + "?", "Save " + format.toUpperCase(Locale.ROOT),
        JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
    final FigureConfiguration snapshot = new SettingsSerializer().copy(config);
    final InputImageManager exportInputs = inputs.copy();
    status.setText("Saving " + format.toUpperCase(Locale.ROOT) + "...");
    new SwingWorker<Void, Void>() {
      protected Void doInBackground() throws Exception {
        if (format.equals("pptx")) new PptxExporter().save(destination, snapshot, exportInputs);
        else {
          java.nio.file.Path temp = java.nio.file.Files.createTempFile(
              destination.toPath().toAbsolutePath().getParent(), "figure-", ".png.tmp");
          try {
            if (!javax.imageio.ImageIO.write(new PanelLayoutEngine().render(snapshot, exportInputs), "png", temp.toFile()))
              throw new java.io.IOException("PNG save failed.");
            java.nio.file.Files.move(temp, destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
          } finally { java.nio.file.Files.deleteIfExists(temp); }
        }
        return null;
      }
      protected void done() {
        try { get(); status.setText("Saved: " + destination.getName()); }
        catch (Exception ex) {
          Throwable cause = ex.getCause() == null ? ex : ex.getCause();
          status.setText("Save failed");
          JOptionPane.showMessageDialog(FigurePanelBuilderDialog.this, cause.getMessage(), "Export", JOptionPane.ERROR_MESSAGE);
        }
      }
    }.execute();
  }

  private class ConditionTable extends AbstractTableModel {
    public int getRowCount() {
      return config.conditions.size();
    }

    public int getColumnCount() {
      return 2;
    }

    public String getColumnName(int col) {
      return col == 0 ? "Condition" : "Source (C / bit / size)";
    }

    public Object getValueAt(int r, int c) {
      ConditionConfig cc = config.conditions.get(r);
      InputImageManager.Source s = inputs.get(cc.sourceId);
      return c == 0
          ? cc.label
          : s.title
              + " ("
              + s.channels
              + "C / "
              + s.bitDepth
              + " / "
              + s.width
              + "x"
              + s.height
              + ")";
    }

    public boolean isCellEditable(int r, int c) {
      return c == 0;
    }

    public void setValueAt(Object v, int r, int c) {
      config.conditions.get(r).label = v.toString();
      fireTableCellUpdated(r, c);
    }
  }

  private class ChannelTable extends AbstractTableModel {
    private final String[] names = {"ID", "Label", "Min", "Max", "LUT", "Invert gray"};

    public int getRowCount() {
      return config.channels.size();
    }

    public int getColumnCount() {
      return names.length;
    }

    public String getColumnName(int c) {
      return names[c];
    }

    public Class<?> getColumnClass(int c) {
      return c == 5 ? Boolean.class : c == 2 || c == 3 ? Double.class : Object.class;
    }

    public Object getValueAt(int r, int c) {
      ChannelConfig ch = config.channels.get(r);
      switch (c) {
        case 0:
          return ch.index;
        case 1:
          return ch.label;
        case 2:
          return ch.min;
        case 3:
          return ch.max;
        case 4:
          return ch.lut;
        default:
          return ch.invert;
      }
    }

    public boolean isCellEditable(int r, int c) {
      return c > 0;
    }

    public void setValueAt(Object v, int r, int c) {
      ChannelConfig ch = config.channels.get(r);
      switch (c) {
        case 1:
          ch.label = v.toString();
          break;
        case 2:
          ch.min = ((Number) v).doubleValue();
          break;
        case 3:
          ch.max = ((Number) v).doubleValue();
          break;
        case 4:
          ch.lut = (ChannelConfig.Lut) v;
          break;
        case 5:
          ch.invert = (boolean) v;
          break;
      }
      fireTableCellUpdated(r, c);
      if (dockContrast != null) dockContrast.refreshFromModel();
    }
  }
}
