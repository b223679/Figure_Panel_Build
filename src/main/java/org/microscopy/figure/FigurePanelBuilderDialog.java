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
  private final JPanel controls = new JPanel();
  private final FigureHistory history = new FigureHistory();
  private boolean restoringHistory, refreshing;
  private final JButton undo = new JButton(new FigureIcons(FigureIcons.Kind.UNDO, 20));
  private final JButton redo = new JButton(new FigureIcons(FigureIcons.Kind.REDO, 20));
  private final TrashTarget trash = new TrashTarget();
  private final FigureWorkspace workspace = new FigureWorkspace(new FigureWorkspace.Actions() {
    public void add(boolean channel) { attempt(() -> { if (channel) chooseDisplay(); else selectImages(); }); }
    public void select(int condition, int display) { attempt(() -> selectDisplay(condition, display)); }
    public void edit(boolean channel, int index) { attempt(() -> editCanvasLabel(channel, index)); }
    public void move(boolean channel, int from, int to) { attempt(() -> reorderCanvas(channel, from, to)); }
    public void swap() { attempt(() -> swapAxes()); }
    public void remove(boolean channel, int index) { attempt(() -> removeEntry(channel, index)); }
  });
  private final JScrollPane canvasScroll = new JScrollPane(workspace);
  private final JPanel contrastDock = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 8));
  private final JLabel selection = new JLabel("Select a channel in the figure to adjust B&C");
  private final JToggleButton showContrast = new JToggleButton("B&C", true);
  private final JPanel advanced = new JPanel(new BorderLayout());
  private ContrastPanel dockContrast;
  private InsetPanel insetPanel;
  private int selectedDisplay = -1;
  private int reorderHighlightCondition = -1;
  private boolean appearanceInitialized;
  private final JLabel status = new JLabel("B&C is shared by all conditions for each channel.");
  private final ConditionTable conditionModel = new ConditionTable();
  private final ChannelTable channelModel = new ChannelTable();
  private final JTable conditions = new JTable(conditionModel), channels = new JTable(channelModel);
  private final JTabbedPane tabs = new JTabbedPane();
  private final javax.swing.Timer previewTimer = new javax.swing.Timer(180, e -> startPreview());
  private SwingWorker<java.awt.image.BufferedImage, Void> previewWorker;
  private long previewRevision;

  public FigurePanelBuilderDialog() {
    this(true);
  }

  FigurePanelBuilderDialog(boolean selectOnStartup) {
    super("Figure Panel Builder");
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    config.labels.showRows = true;
    config.labels.showColumns = true;
    controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
    controls.setBorder(BorderFactory.createEmptyBorder(6, 4, 10, 4));
    conditions.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    channels.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    conditions.setFillsViewportHeight(true); channels.setFillsViewportHeight(true);
    conditions.setName("conditionsTable"); channels.setName("channelsTable");
    JPanel conditionSection = new JPanel(new BorderLayout(0, 4));
    conditionSection.add(new JLabel("Conditions — double-click a name to edit"), BorderLayout.NORTH);
    conditionSection.add(new JScrollPane(conditions), BorderLayout.CENTER);
    JPanel ordering = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 3));
    button(ordering, "Up", () -> moveCondition(-1));
    button(ordering, "Down", () -> moveCondition(1));
    button(
        ordering,
        "Remove",
        () -> removeEntry(false, conditions.getSelectedRow()));
    button(ordering, "Change source...", this::changeSource);
    conditionSection.add(ordering, BorderLayout.SOUTH);
    controls.add(conditionSection);
    JPanel channelSection = new JPanel(new BorderLayout(0, 4));
    channelSection.add(new JLabel("Channel — double-click a name to edit"), BorderLayout.NORTH);
    channelSection.add(new JScrollPane(channels), BorderLayout.CENTER);
    channels.getColumnModel().getColumn(1).setCellEditor(
        new DefaultCellEditor(new JComboBox<>(ChannelConfig.Lut.values())));
    channels.addMouseListener(new java.awt.event.MouseAdapter() {
      public void mouseClicked(java.awt.event.MouseEvent e) {
        int row = channels.rowAtPoint(e.getPoint()), col = channels.columnAtPoint(e.getPoint());
        if (row >= 0 && col <= 1 && e.getClickCount() == 2 && config.displayChannels.get(row).merge)
          attempt(() -> editCanvasLabel(true, row));
      }
    });
    channels.getSelectionModel().addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting() && !refreshing && channels.getSelectedRow() >= 0)
        selectDisplay(-1, channels.getSelectedRow());
    });
    JPanel displayActions = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 3));
    button(displayActions, "Up", () -> moveDisplay(-1));
    button(displayActions, "Down", () -> moveDisplay(1));
    button(
        displayActions,
        "Remove",
        () -> removeEntry(true, channels.getSelectedRow()));
    channelSection.add(displayActions, BorderLayout.SOUTH);
    controls.add(channelSection);
    controls.add(Box.createVerticalGlue());
    tabs.addTab("Label name", controls);
    advanced.add(tabs, BorderLayout.CENTER);
    advanced.setPreferredSize(new Dimension(490, 600));
    advanced.setVisible(true);
    JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
    button(toolbar, "Select Images", this::selectImages);
    JToggleButton freeMode = new JToggleButton("Free mode OFF");
    toolbar.add(freeMode);
    freeMode.addActionListener(e -> {
      freeMode.setSelected(false);
      if (JOptionPane.showConfirmDialog(this,
          "Free modeをONにすると、現在のFigureをすべて消去します。続けますか？",
          "Free build", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
      int[] grid = FreeBuildDialog.chooseGrid(this);
      if (grid == null) return;
      new FreeBuildDialog(grid[0], grid[1]).setVisible(true);
      dispose();
    });
    toolbar.add(showContrast);
    showContrast.addActionListener(e -> {
      contrastDock.setVisible(showContrast.isSelected());
      revalidate(); schedulePreview();
    });
    JToggleButton settingsToggle = new JToggleButton("Design", true);
    toolbar.add(settingsToggle);
    settingsToggle.addActionListener(e -> {
      advanced.setVisible(settingsToggle.isSelected());
      revalidate();
      schedulePreview();
    });
    button(toolbar, "Load settings", () -> settings(true));
    button(toolbar, "Save settings", () -> settings(false));
    undo.setToolTipText("Undo (Ctrl+Z)"); redo.setToolTipText("Redo (Ctrl+Y)");
    undo.setName("undo"); redo.setName("redo");
    undo.getAccessibleContext().setAccessibleName("Undo"); redo.getAccessibleContext().setAccessibleName("Redo");
    undo.addActionListener(e -> attempt(() -> restoreHistory(false)));
    redo.addActionListener(e -> attempt(() -> restoreHistory(true)));
    toolbar.add(undo); toolbar.add(redo);
    installHistoryKeys();
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
    workspace.setTrashTarget(trash);
    center.add(contrastDock, BorderLayout.SOUTH);
    add(center, BorderLayout.CENTER);
    add(advanced, BorderLayout.EAST);
    JPanel footer = new JPanel(new BorderLayout());
    JPanel outputActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
    button(outputActions, "Generate TIF", () -> generate(false));
    button(outputActions, "Save RGB TIFF...", () -> generate(true));
    button(outputActions, "Save PNG...", () -> exportFigure("png"));
    button(outputActions, "Save PPTX...", () -> exportFigure("pptx"));
    footer.add(outputActions, BorderLayout.WEST);
    footer.add(status, BorderLayout.CENTER);
    add(footer, BorderLayout.SOUTH);
    tabs.addTab(
        "Style", new JScrollPane(new AppearancePanel(config, this::appearanceChanged)));
    tabs.addTab(
        "Inset", new JScrollPane(insetPanel = new InsetPanel(config, inputs, this::appearanceChanged)));
    previewTimer.setRepeats(false);
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
    if (selectOnStartup) addWindowListener(new java.awt.event.WindowAdapter() {
      public void windowOpened(java.awt.event.WindowEvent e) {
        SwingUtilities.invokeLater(() -> {
          if (isDisplayable() && config.conditions.isEmpty()) attempt(() -> selectImages());
        });
      }
    });
  }

  private void button(JPanel p, String title, Runnable action) {
    JButton b = new JButton(title);
    b.addActionListener(e -> attempt(action));
    p.add(b);
  }

  private void attempt(Runnable action) {
    try {
      history.endGroup();
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

  private void selectImages() {
    int[] ids = WindowManager.getIDList();
    if (ids == null) ids = new int[0];
    DefaultListModel<String> model = new DefaultListModel<>();
    for (int id : ids) {
      ImagePlus image = WindowManager.getImage(id);
      model.addElement(image == null ? "(Image closed)" : image.getTitle());
    }
    JList<String> list = new JList<>(model);
    list.setName("openImageList"); list.setVisibleRowCount(Math.max(4, Math.min(12, ids.length)));
    JPanel selection = new JPanel(new BorderLayout(6, 6));
    selection.add(new JLabel(ids.length == 0 ? "No images are open in Fiji. You can open TIFF files below."
        : "Select images already open in Fiji (Ctrl / Shift for multiple images)"), BorderLayout.NORTH);
    selection.add(new JScrollPane(list), BorderLayout.CENTER);
    int choice = JOptionPane.showOptionDialog(this, selection, "Select Images", JOptionPane.DEFAULT_OPTION,
        JOptionPane.PLAIN_MESSAGE, null, new String[] {"Add selected", "Open TIFF files...", "Cancel"}, "Add selected");
    if (choice == 1) { chooseFiles(); return; }
    if (choice != 0) return;
    try {
      for (int i : list.getSelectedIndices()) addSource(inputs.snapshot(WindowManager.getImage(ids[i]), null));
    } finally { refresh(); }
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
      tabs.setComponentAt(1, new JScrollPane(new AppearancePanel(config, this::appearanceChanged)));
      tabs.setComponentAt(2, new JScrollPane(insetPanel = new InsetPanel(config, inputs, this::appearanceChanged)));
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
    int i = channels.getSelectedRow(), j = i + delta;
    if (i >= 0 && j >= 0 && j < config.displayChannels.size()) {
      Collections.swap(config.displayChannels, i, j);
      onChannelGroupSwapped(i, j);
      refresh();
      channels.setRowSelectionInterval(j, j);
    }
  }

  private void readGrid() {
    config.automaticGrid = true;
    config.manualRows = Math.max(1, config.rows());
    config.manualColumns = Math.max(1, config.columns());
  }

  private void refresh() {
    readGrid();
    refreshing = true;
    try { conditionModel.fireTableDataChanged(); channelModel.fireTableDataChanged(); }
    finally { refreshing = false; }
    for (int i = 0; i < 2; i++) {
      JPanel section = (JPanel) controls.getComponent(i);
      JTable table = i == 0 ? conditions : channels;
      int height = 65 + table.getTableHeader().getPreferredSize().height
          + Math.max(5, table.getRowCount()) * table.getRowHeight();
      section.setAlignmentX(Component.LEFT_ALIGNMENT);
      section.setPreferredSize(new Dimension(470, height));
      section.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
      section.setMinimumSize(new Dimension(0, 115));
    }
    controls.revalidate();
    workspace.setAxes(config.rowsAreChannels);
    if (insetPanel != null) insetPanel.refreshBounds();
    rebuildDock();
    recordChange(null);
    schedulePreview();
  }

  private void recordChange(String group) {
    if (!restoringHistory) history.record(config, inputs, appearanceInitialized, group);
    undo.setEnabled(history.canUndo()); redo.setEnabled(history.canRedo());
  }

  private void appearanceChanged() { recordChange("style"); schedulePreview(); }

  private void installHistoryKeys() {
    JRootPane root = getRootPane();
    root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("control Z"), "figureUndo");
    root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("control Y"), "figureRedo");
    root.getActionMap().put("figureUndo", new AbstractAction() {
      public void actionPerformed(java.awt.event.ActionEvent e) { attempt(() -> restoreHistory(false)); }
    });
    root.getActionMap().put("figureRedo", new AbstractAction() {
      public void actionPerformed(java.awt.event.ActionEvent e) { attempt(() -> restoreHistory(true)); }
    });
  }

  private void restoreHistory(boolean forward) {
    FigureHistory.State state = forward ? history.redo() : history.undo();
    if (state == null) return;
    restoringHistory = true;
    try {
      config = state.configuration; inputs = state.inputs; appearanceInitialized = state.appearanceInitialized;
      selectedDisplay = -1;
      tabs.setComponentAt(1, new JScrollPane(new AppearancePanel(config, this::appearanceChanged)));
      tabs.setComponentAt(2, new JScrollPane(insetPanel = new InsetPanel(config, inputs, this::appearanceChanged)));
      refresh();
    } finally { restoringHistory = false; }
  }

  private void swapAxes() {
    config.rowsAreChannels = !config.rowsAreChannels;
    refresh();
  }

  private void removeEntry(boolean channel, int index) {
    int size = channel ? config.displayChannels.size() : config.conditions.size();
    if (index < 0 || index >= size) return;
    String name = channel ? config.displayLabel(config.displayChannels.get(index)) : config.conditions.get(index).label;
    if (JOptionPane.showConfirmDialog(this, "Remove " + name + " from this figure?\nOriginal images and TIFF files will be kept.",
        "Remove " + (channel ? "Channel / Merge" : "Condition"), JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION) return;
    if (channel) {
      config.displayChannels.remove(index);
      onChannelGroupRemoved(index);
      selectedDisplay = -1;
    } else config.conditions.remove(index);
    refresh();
  }

  /**
   * Keeps each condition's per-cell Inset overrides index-aligned with config.displayChannels
   * across structural edits to that list. Adding a group needs no action: a short override list
   * already means "no inset" for the new trailing index.
   */
  private void onChannelGroupRemoved(int index) {
    for (ConditionConfig c : config.conditions) if (index < c.insets.size()) c.insets.remove(index);
  }

  private void onChannelGroupSwapped(int i, int j) {
    for (ConditionConfig c : config.conditions) swapEntry(c.insets, i, j);
  }

  private void onChannelGroupMoved(int from, int to) {
    for (ConditionConfig c : config.conditions) moveEntry(c.insets, from, to);
  }

  private static <T> void swapEntry(List<T> list, int i, int j) {
    while (list.size() <= Math.max(i, j)) list.add(null);
    Collections.swap(list, i, j);
  }

  private static <T> void moveEntry(List<T> list, int from, int to) {
    while (list.size() <= from) list.add(null);
    T value = list.remove(from);
    list.add(Math.min(to, list.size()), value);
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
      workspace.clear(config.conditions.isEmpty() ? "Select Images or use + Condition to start; TIFF files can also be dropped here"
          : "Use + Channel / Merge to add a displayed channel");
      status.setText(config.conditions.isEmpty() ? "No conditions — use Select Images or + Condition."
          : "No displayed channels — use + Channel / Merge.");
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
    tabs.setComponentAt(1, new JScrollPane(new AppearancePanel(config, this::appearanceChanged)));
    tabs.setComponentAt(2, new JScrollPane(insetPanel = new InsetPanel(config, inputs, this::appearanceChanged)));
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
    refresh();
  }

  private void selectDisplay(int condition, int display) {
    if (display < 0 || display >= config.displayChannels.size()) return;
    if (!showContrast.isSelected()) showContrast.doClick();
    selectedDisplay = display;
    if (insetPanel != null) insetPanel.selectCell(condition, display);
    if (channels.getSelectedRow() != display) {
      refreshing = true;
      try { channels.setRowSelectionInterval(display, display); }
      finally { refreshing = false; }
    }
    if (condition >= 0) conditions.setRowSelectionInterval(condition, condition);
    workspace.select(condition, display);
    if (config.conditions.isEmpty()) {
      status.setText("Select Images or + Condition to add an image for this channel.");
      return;
    }
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
        refreshing = true;
        try { channelModel.fireTableRowsUpdated(0, Math.max(0, channelModel.getRowCount() - 1)); }
        finally { refreshing = false; }
        recordChange("bc:" + (dockContrast == null ? "" : dockContrast.selectedChannel()));
        schedulePreview();
      }, true);
      dockContrast.setPreferredSize(new Dimension(530, 190));
      contrastDock.add(dockContrast);
      if (!config.displayChannels.isEmpty()) {
        selectedDisplay = Math.max(0, Math.min(selectedDisplay, config.displayChannels.size() - 1));
        dockContrast.selectChannel(config.displayChannels.get(selectedDisplay).channels.get(0));
      }
    }
    contrastDock.add(trash);
    if (reorderHighlightCondition >= 0) {
      workspace.select(reorderHighlightCondition, -1);
      reorderHighlightCondition = -1;
    } else {
      workspace.select(-1, selectedDisplay);
    }
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
      onChannelGroupMoved(from, to);
      selectedDisplay = to;
    } else {
      config.conditions.add(to, config.conditions.remove(from));
      reorderHighlightCondition = to;
    }
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
      return col == 0 ? "Name" : "Source (C / bit / size)";
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
      recordChange(null); schedulePreview();
    }
  }

  private class ChannelTable extends AbstractTableModel {
    private final String[] names = {"Name", "LUT", "Channel No."};

    public int getRowCount() {
      return config.displayChannels.size();
    }

    public int getColumnCount() {
      return names.length;
    }

    public String getColumnName(int c) {
      return names[c];
    }

    public Class<?> getColumnClass(int c) {
      return Object.class;
    }

    public Object getValueAt(int r, int c) {
      DisplayChannel d = config.displayChannels.get(r);
      switch (c) {
        case 0:
          return config.displayLabel(d);
        case 1:
          if (!d.merge) return config.channel(d.channels.get(0)).lut;
          StringJoiner luts = new StringJoiner(" / ");
          for (int id : d.channels) luts.add(config.channel(id).lut.toString());
          return luts.toString();
        default:
          StringJoiner ids = new StringJoiner(" / ");
          for (int id : d.channels) ids.add(Integer.toString(id));
          return ids.toString();
      }
    }

    public boolean isCellEditable(int r, int c) {
      return c < 2 && !config.displayChannels.get(r).merge;
    }

    public void setValueAt(Object v, int r, int c) {
      ChannelConfig ch = config.channel(config.displayChannels.get(r).channels.get(0));
      switch (c) {
        case 0:
          ch.label = v.toString();
          break;
        case 1:
          ch.lut = (ChannelConfig.Lut) v;
          break;
      }
      fireTableRowsUpdated(0, Math.max(0, getRowCount() - 1));
      if (dockContrast != null) dockContrast.refreshFromModel();
      recordChange(null); schedulePreview();
    }
  }
}
