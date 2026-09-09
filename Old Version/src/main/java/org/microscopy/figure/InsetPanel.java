package org.microscopy.figure;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.function.*;
import javax.swing.*;

/**
 * Inset tab: global ROI/box appearance (shared) plus a live, draggable preview of the currently
 * selected panel's own on/off + ROI position (per panel).
 */
public class InsetPanel extends JPanel implements Scrollable {
  private static final int PREVIEW_MAX = 380;
  private final FigureConfiguration config;
  private final InputImageManager inputs;
  private final Runnable changed;
  private final Preview preview = new Preview();
  private final JCheckBox enable = new JCheckBox("Show inset");
  private final JCheckBox share = new JCheckBox("Share ROI within this condition");
  private final JLabel placeholder = new JLabel("Select a panel in the figure to edit its inset.");
  private final JPanel body = new JPanel();
  private final JPanel details = new JPanel();
  private int selectedCondition = -1, selectedDisplay = -1;
  private boolean updating;

  public InsetPanel(FigureConfiguration config, InputImageManager inputs, Runnable changed) {
    this.config = config;
    this.inputs = inputs;
    this.changed = changed;
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    InsetConfig i = config.inset;

    JPanel columns = new JPanel();
    columns.setLayout(new GridLayout(1, 2, 6, 0));
    columns.setAlignmentX(LEFT_ALIGNMENT);
    details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
    details.setAlignmentX(LEFT_ALIGNMENT);
    details.add(columns);

    JPanel roi = section(columns, "ROI");
    JPanel shape = new JPanel(new GridLayout(1, 2));
    ButtonGroup shapeGroup = new ButtonGroup();
    JToggleButton rect = new JToggleButton("□", i.shape == InsetConfig.Shape.RECTANGLE);
    JToggleButton circle = new JToggleButton("○", i.shape == InsetConfig.Shape.CIRCLE);
    rect.setName("insetShapeRect"); circle.setName("insetShapeCircle");
    shapeGroup.add(rect); shapeGroup.add(circle); shape.add(rect); shape.add(circle);
    rect.addActionListener(e -> { i.shape = InsetConfig.Shape.RECTANGLE; fire(); });
    circle.addActionListener(e -> { i.shape = InsetConfig.Shape.CIRCLE; fire(); });
    line(roi, "Shape", shape);
    number(roi, "Vertical px", i.roiHeight, 1, 100000, v -> i.roiHeight = v, "insetRoiHeight");
    number(roi, "Horizontal px", i.roiWidth, 1, 100000, v -> i.roiWidth = v, "insetRoiWidth");
    number(roi, "Width", i.roiStrokeWidth, 0, 4096, v -> i.roiStrokeWidth = v, "insetRoiStroke");
    choice(
        roi,
        "Color",
        InsetConfig.RoiColor.values(),
        new String[] {"Yellow", "Cyan", "Magenta", "White", "Black"},
        i.color,
        v -> i.color = v,
        "insetRoiColor");

    JPanel box = section(columns, "Inset");
    JPanel customSize = new JPanel();
    customSize.setLayout(new BoxLayout(customSize, BoxLayout.Y_AXIS));
    customSize.setAlignmentX(LEFT_ALIGNMENT);
    JPanel zoom = group();
    zoom.setBorder(BorderFactory.createEmptyBorder());
    JSpinner magnification = new JSpinner(new SpinnerNumberModel(i.magnification, 1.0, 100.0, 0.25));
    magnification.setName("insetMagnification");
    line(zoom, "Zoom ×", magnification);
    magnification.addChangeListener(e -> { i.magnification = ((Number)magnification.getValue()).doubleValue(); fire(); });
    choice(box, "Size mode", new Boolean[] {true, false},
        new String[] {"Same as ROI", "Custom size"}, i.sameAsRoi,
        v -> { i.sameAsRoi = v; customSize.setVisible(!v); zoom.setVisible(v); revalidate(); }, "insetSizeMode");
    box.add(zoom); box.add(customSize);
    zoom.setVisible(i.sameAsRoi); customSize.setVisible(!i.sameAsRoi);
    number(customSize, "Vertical px", i.insetHeight, 1, 100000, v -> i.insetHeight = v, "insetBoxHeight");
    number(customSize, "Horizontal px", i.insetWidth, 1, 100000, v -> i.insetWidth = v, "insetBoxWidth");
    number(box, "Width", i.insetStrokeWidth, 0, 4096, v -> i.insetStrokeWidth = v, "insetBoxStroke");
    JPanel location = section(box, "Location");
    choice(
        location,
        "Position",
        InsetConfig.Position.values(),
        new String[] {"Top left", "Top right", "Bottom left", "Bottom right"},
        i.position,
        v -> i.position = v,
        "insetPosition");
    number(location, "Margin X px", i.marginX, 0, 100000, v -> i.marginX = v, "insetMarginX");
    number(location, "Margin Y px", i.marginY, 0, 100000, v -> i.marginY = v, "insetMarginY");

    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    body.setAlignmentX(LEFT_ALIGNMENT);
    enable.setName("insetEnabled");
    enable.setAlignmentX(LEFT_ALIGNMENT);
    enable.addActionListener(
        e -> {
          if (updating) return;
          cell().enabled = enable.isSelected();
          details.setVisible(enable.isSelected());
          revalidate();
          fire();
        });
    body.add(enable);
    share.setName("shareInsetRoi"); share.setAlignmentX(LEFT_ALIGNMENT);
    share.setToolTipText("Share ROI position across this condition. Show inset remains per panel.");
    share.addActionListener(e -> {
      if (updating || selectedCondition < 0) return;
      InsetCell current = cell();
      ConditionConfig condition = config.conditions.get(selectedCondition);
      if (share.isSelected()) {
        condition.sharedRoiX = current.x; condition.sharedRoiY = current.y;
      } else {
        // Keep the visible ROI position in every display when unlinking.
        while (condition.insets.size() < config.displayChannels.size()) condition.insets.add(null);
        for (int n = 0; n < condition.insets.size(); n++) {
          if (condition.insets.get(n) == null) condition.insets.set(n, new InsetCell());
          condition.insets.get(n).x = condition.sharedRoiX; condition.insets.get(n).y = condition.sharedRoiY;
        }
      }
      condition.shareInsetRoi = share.isSelected(); fire();
    });
    body.add(share);
    preview.setAlignmentX(LEFT_ALIGNMENT);
    preview.setPreferredSize(new Dimension(PREVIEW_MAX, PREVIEW_MAX));
    preview.setMaximumSize(new Dimension(Integer.MAX_VALUE, PREVIEW_MAX));
    details.add(preview);
    details.add(dpad());
    body.add(details);
    placeholder.setAlignmentX(LEFT_ALIGNMENT);
    placeholder.setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 4));
    add(placeholder);
    add(body);
    selectCell(-1, -1);
  }

  /** Re-checks the current selection against the (possibly changed) configuration; call after structural edits. */
  public void refreshBounds() {
    selectCell(selectedCondition, selectedDisplay);
  }

  public void selectCell(int condition, int display) {
    selectedCondition = condition;
    selectedDisplay = display;
    boolean valid =
        condition >= 0
            && display >= 0
            && condition < config.conditions.size()
            && display < config.displayChannels.size();
    placeholder.setVisible(!valid);
    enable.setEnabled(valid);
    share.setEnabled(valid); share.setVisible(config.displayChannels.size() > 1);
    if (valid) sync();
    else { enable.setSelected(false); details.setVisible(false); }
    revalidate();
    repaint();
  }

  private void sync() {
    updating = true;
    try {
      enable.setSelected(cell().enabled);
      share.setSelected(config.conditions.get(selectedCondition).shareInsetRoi);
      details.setVisible(cell().enabled);
    } finally {
      updating = false;
    }
    preview.repaint();
  }

  private InsetCell cell() {
    java.util.List<InsetCell> cells = config.conditions.get(selectedCondition).insets;
    while (cells.size() <= selectedDisplay) cells.add(null);
    InsetCell c = cells.get(selectedDisplay);
    if (c == null) {
      c = new InsetCell();
      cells.set(selectedDisplay, c);
    }
    ConditionConfig condition = config.conditions.get(selectedCondition);
    if (condition.shareInsetRoi) { c.x = condition.sharedRoiX; c.y = condition.sharedRoiY; }
    return c;
  }

  private void fire() {
    if (selectedCondition >= 0 && selectedDisplay >= 0) {
      ConditionConfig condition = config.conditions.get(selectedCondition);
      if (condition.shareInsetRoi && selectedDisplay < condition.insets.size()) {
        InsetCell edited = condition.insets.get(selectedDisplay);
        if (edited != null) { condition.sharedRoiX = edited.x; condition.sharedRoiY = edited.y; }
      }
    }
    preview.repaint();
    changed.run();
  }

  private void nudge(int dx, int dy) {
    InsetCell c = cell();
    InputImageManager.Source source = source();
    int x = clamp(c.roiX(source, config.inset) + dx, 0, Math.max(0, source.width - config.inset.roiWidth));
    int y = clamp(c.roiY(source, config.inset) + dy, 0, Math.max(0, source.height - config.inset.roiHeight));
    c.x = x;
    c.y = y;
    fire();
  }

  private static int clamp(int v, int lo, int hi) {
    return Math.max(lo, Math.min(hi, v));
  }

  private InputImageManager.Source source() {
    return inputs.get(config.conditions.get(selectedCondition).sourceId);
  }

  private DisplayChannel display() {
    return config.displayChannels.get(selectedDisplay);
  }

  private JPanel dpad() {
    JPanel p = new JPanel(new GridLayout(2, 3, 2, 2));
    p.setAlignmentX(LEFT_ALIGNMENT);
    p.setMaximumSize(new Dimension(220, 56));
    p.add(new JLabel());
    p.add(arrow("Up", 0, -1));
    p.add(new JLabel());
    p.add(arrow("Left", -1, 0));
    p.add(arrow("Down", 0, 1));
    p.add(arrow("Right", 1, 0));
    return p;
  }

  private JButton arrow(String text, int dx, int dy) {
    JButton b = new JButton(text);
    b.addActionListener(e -> nudge(dx, dy));
    return b;
  }

  private JPanel group() {
    JPanel p = new JPanel();
    p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
    p.setAlignmentX(LEFT_ALIGNMENT);
    p.setAlignmentY(TOP_ALIGNMENT);
    p.setBorder(BorderFactory.createEmptyBorder(2, 12, 4, 0));
    return p;
  }

  private JPanel section(JPanel parent, String title) {
    JPanel p = group();
    p.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(title), BorderFactory.createEmptyBorder(2, 6, 6, 6)));
    parent.add(p);
    return p;
  }

  private void line(JPanel parent, String text, JComponent control) {
    JPanel p = new JPanel(new BorderLayout(8, 0));
    p.setAlignmentX(LEFT_ALIGNMENT);
    JLabel label = new JLabel(text);
    label.setHorizontalAlignment(SwingConstants.LEFT);
    control.setMinimumSize(new Dimension(0, 24));
    label.setLabelFor(control);
    label.setPreferredSize(new Dimension(88, 28));
    p.add(label, BorderLayout.WEST);
    p.add(control, BorderLayout.CENTER);
    p.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
    p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
    parent.add(p);
  }

  private void number(JPanel p, String text, int value, int min, int max, IntConsumer set, String name) {
    JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
    spinner.setName(name);
    spinner.setPreferredSize(new Dimension(72, 24));
    line(p, text, spinner);
    spinner.addChangeListener(
        e -> {
          set.accept(((Number) spinner.getValue()).intValue());
          fire();
        });
  }

  private <T> void choice(
      JPanel p, String text, T[] values, String[] captions, T value, Consumer<T> set, String name) {
    JComboBox<String> combo = new JComboBox<>(captions);
    for (int idx = 0; idx < values.length; idx++) if (values[idx].equals(value)) combo.setSelectedIndex(idx);
    combo.setName(name);
    combo.setPreferredSize(new Dimension(110, 24));
    line(p, text, combo);
    combo.addActionListener(
        e -> {
          set.accept(values[combo.getSelectedIndex()]);
          fire();
        });
  }

  public Dimension getPreferredScrollableViewportSize() {
    return new Dimension(450, 650);
  }

  public boolean getScrollableTracksViewportWidth() {
    return true;
  }

  public boolean getScrollableTracksViewportHeight() {
    return false;
  }

  public int getScrollableUnitIncrement(Rectangle r, int axis, int direction) {
    return 28;
  }

  public int getScrollableBlockIncrement(Rectangle r, int axis, int direction) {
    return Math.max(28, r.height - 28);
  }

  private class Preview extends JPanel {
    double fit = 1;
    int dragOffsetX, dragOffsetY;
    boolean dragging;

    Preview() {
      setBackground(Color.BLACK);
      MouseAdapter mouse =
          new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
              if (selectedCondition < 0 || fit <= 0) return;
              InsetCell c = cell();
              InputImageManager.Source s = source();
              double sx = e.getX() / fit, sy = e.getY() / fit;
              int roiX = c.roiX(s, config.inset), roiY = c.roiY(s, config.inset);
              if (sx >= roiX && sx <= roiX + config.inset.roiWidth
                  && sy >= roiY && sy <= roiY + config.inset.roiHeight) {
                dragging = true;
                dragOffsetX = (int) Math.round(sx) - roiX;
                dragOffsetY = (int) Math.round(sy) - roiY;
              }
            }

            public void mouseDragged(MouseEvent e) {
              if (!dragging || selectedCondition < 0 || fit <= 0) return;
              InsetCell c = cell();
              InputImageManager.Source s = source();
              int sx = (int) Math.round(e.getX() / fit) - dragOffsetX;
              int sy = (int) Math.round(e.getY() / fit) - dragOffsetY;
              c.x = clamp(sx, 0, Math.max(0, s.width - config.inset.roiWidth));
              c.y = clamp(sy, 0, Math.max(0, s.height - config.inset.roiHeight));
              fire();
            }

            public void mouseReleased(MouseEvent e) {
              dragging = false;
            }
          };
      addMouseListener(mouse);
      addMouseMotionListener(mouse);
    }

    protected void paintComponent(Graphics graphics) {
      super.paintComponent(graphics);
      if (selectedCondition < 0) return;
      InputImageManager.Source s;
      try {
        s = source();
      } catch (Exception ex) {
        return;
      }
      fit = Math.min((double) (getWidth() - 2) / s.width, (double) (getHeight() - 2) / s.height);
      if (fit <= 0 || !Double.isFinite(fit)) return;
      int pw = Math.max(1, (int) Math.round(s.width * fit)), ph = Math.max(1, (int) Math.round(s.height * fit));
      BufferedImage rendered = new ImageRenderer().render(s, display(), config, pw, ph);
      Graphics2D g = (Graphics2D) graphics.create();
      try {
        g.drawImage(rendered, 0, 0, null);
        InsetCell c = cell();
        InsetConfig ic = config.inset;
        if (new InsetRenderer().applies(c, s, ic)) {
          new InsetRenderer().draw(rendered, s, display(), config, ic, c, fit);
          g.drawImage(rendered, 0, 0, null);
        } else if (c.enabled) {
          g.setColor(Color.YELLOW);
          g.drawString("ROI / inset does not fit this image", 8, 20);
        }
      } finally {
        g.dispose();
      }
    }
  }
}
