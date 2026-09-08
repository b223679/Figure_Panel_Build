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
  private final JCheckBox enable = new JCheckBox("Show inset for this panel");
  private final JLabel placeholder = new JLabel("Select a panel in the figure to edit its inset.");
  private final JPanel body = new JPanel();
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
    columns.setLayout(new BoxLayout(columns, BoxLayout.X_AXIS));
    columns.setAlignmentX(LEFT_ALIGNMENT);
    add(columns);

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
    number(box, "Vertical px", i.insetHeight, 1, 100000, v -> i.insetHeight = v, "insetBoxHeight");
    number(box, "Horizontal px", i.insetWidth, 1, 100000, v -> i.insetWidth = v, "insetBoxWidth");
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
          preview.repaint();
          fire();
        });
    body.add(enable);
    preview.setAlignmentX(LEFT_ALIGNMENT);
    preview.setPreferredSize(new Dimension(PREVIEW_MAX, PREVIEW_MAX));
    preview.setMaximumSize(new Dimension(Integer.MAX_VALUE, PREVIEW_MAX));
    body.add(preview);
    body.add(dpad());
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
    body.setVisible(valid);
    if (valid) sync();
    revalidate();
    repaint();
  }

  private void sync() {
    updating = true;
    try {
      enable.setSelected(cell().enabled);
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
    return c;
  }

  private void fire() {
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
        int roiX = c.roiX(s, ic), roiY = c.roiY(s, ic);
        g.setColor(ic.color.awt);
        if (c.enabled) {
          try {
            Point origin = InsetRenderer.boxOrigin(ic, s.width, s.height);
            BufferedImage crop =
                new ImageRenderer()
                    .render(
                        s,
                        display(),
                        config,
                        roiX,
                        roiY,
                        ic.roiWidth,
                        ic.roiHeight,
                        Math.max(1, (int) Math.round(ic.insetWidth * fit)),
                        Math.max(1, (int) Math.round(ic.insetHeight * fit)));
            int bx = (int) Math.round(origin.x * fit), by = (int) Math.round(origin.y * fit);
            // Draw the inset box first: if it happens to overlap the ROI, the ROI outline (drawn last) must stay visible.
            g.drawImage(crop, bx, by, null);
            g.setStroke(new BasicStroke(Math.max(1f, (float) (ic.insetStrokeWidth * fit))));
            g.drawRect(bx, by, crop.getWidth(), crop.getHeight());
          } catch (Exception ignored) {
            // Out-of-range box while the user is still dragging; base image + ROI already shown.
          }
        }
        g.setStroke(new BasicStroke(Math.max(1f, (float) (ic.roiStrokeWidth * fit))));
        Shape roiShape =
            ic.shape == InsetConfig.Shape.CIRCLE
                ? new Ellipse2D.Double(roiX * fit, roiY * fit, ic.roiWidth * fit, ic.roiHeight * fit)
                : new Rectangle2D.Double(roiX * fit, roiY * fit, ic.roiWidth * fit, ic.roiHeight * fit);
        g.draw(roiShape);
      } finally {
        g.dispose();
      }
    }
  }
}
