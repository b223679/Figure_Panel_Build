package org.microscopy.figure;

import java.awt.*;
import java.util.function.*;
import javax.swing.*;

/** Grouped appearance controls. Hidden options retain their configured values. */
public class AppearancePanel extends JPanel implements Scrollable {
  private final Runnable changed;
  public AppearancePanel(FigureConfiguration c, Runnable changed) {
    this.changed = changed;
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    LabelConfig l = c.labels;
    ScaleBarConfig s = c.scaleBar;
    JPanel background = section(this, "Background");
    JPanel backgrounds = new JPanel(new GridLayout(1, 3));
    ButtonGroup backgroundGroup = new ButtonGroup();
    String[] backgroundNames = {"White", "Black", "Transparent"};
    for (int i = 0; i < backgroundNames.length; i++) {
      final int option = i;
      JToggleButton b = new JToggleButton(backgroundNames[i], l.transparentBackground ? i == 2 : i == (l.whiteBackground ? 0 : 1));
      b.setMargin(new Insets(2, 3, 2, 3));
      b.setName(new String[] {"backgroundFirst", "backgroundSecond", "backgroundThird"}[i]);
      backgroundGroup.add(b); backgrounds.add(b);
      b.addActionListener(e -> { l.transparentBackground = option == 2; l.whiteBackground = option == 0; changed.run(); });
    }
    line(background, "Color", backgrounds);
    JPanel gap = section(this, "Gap");
    number(gap, "Horizontal px", c.horizontalGap, 0, 4096, v -> c.horizontalGap = v, "horizontalGap");
    number(gap, "Vertical px", c.verticalGap, 0, 4096, v -> c.verticalGap = v, "verticalGap");
    JPanel labels = section(this, "Labels");
    JPanel row = group();
    check(labels, "Show row label", l.showRows, v -> { l.showRows = v; row.setVisible(v); revalidate(); }, "showRows");
    labels.add(row);
    number(row, "Size px", l.rowFontSize, 1, LabelConfig.MAX_FONT_SIZE, v -> l.rowFontSize = v, "rowFontSize");
    toggle(row, "Color", "Black", "White", l.isWhiteText(true), v -> l.rowWhiteText = v, "rowColor");
    toggle(row, "Position", "Left", "Right", l.rowRight, v -> l.rowRight = v, "rowPosition");
    number(row, "Margin px", l.rowMargin, 0, 4096, v -> l.rowMargin = v, "rowMargin");
    row.setVisible(l.showRows);
    JPanel column = group();
    check(labels, "Show column label", l.showColumns, v -> { l.showColumns = v; column.setVisible(v); revalidate(); }, "showColumns");
    labels.add(column);
    number(column, "Size px", l.columnFontSize, 1, LabelConfig.MAX_FONT_SIZE, v -> l.columnFontSize = v, "columnFontSize");
    toggle(column, "Color", "Black", "White", l.isWhiteText(false), v -> l.columnWhiteText = v, "columnColor");
    toggle(column, "Position", "Top", "Bottom", l.columnBottom, v -> l.columnBottom = v, "columnPosition");
    number(column, "Margin px", l.columnMargin, 0, 4096, v -> l.columnMargin = v, "columnMargin");
    column.setVisible(l.showColumns);
    JLabel colorHint = new JLabel("Channel label colors adapt to the background.");
    colorHint.setToolTipText("Image LUTs stay unchanged. Black / white applies to condition names, grayscale names and / separators.");
    labels.add(colorHint);
    JPanel scale = section(this, "Scale bar");
    JPanel details = group();
    check(scale, "Show scale bar", s.show, v -> { s.show = v; details.setVisible(v); revalidate(); }, "showScaleBar");
    scale.add(details);
    decimal(details, "Length µm", s.lengthUm, 0.000001, Math.max(1000000, s.lengthUm), v -> s.lengthUm = v, "scaleLength");
    number(details, "Width px", s.thickness, 1, Math.max(4096, s.thickness), v -> s.thickness = v, "scaleWidth")
        .setToolTipText("Thickness of the scale bar in source-image pixels.");
    toggle(details, "Color", "White", "Black", !s.white, v -> s.white = !v, "scaleColor");
    JPanel textOptions = group();
    check(details, "Text on / off", s.showText, v -> { s.showText = v; textOptions.setVisible(v); revalidate(); }, "showScaleText");
    details.add(textOptions);
    number(textOptions, "Size px", s.fontSize, 1, LabelConfig.MAX_FONT_SIZE, v -> s.fontSize = v, "scaleFontSize");
    textOptions.setVisible(s.showText);
    JPanel location = section(details, "Location");
    choice(location, "Position", ScaleBarConfig.Position.values(), new String[] {"Top left", "Top right", "Bottom left", "Bottom right"}, s.position, v -> s.position = v, "scalePosition");
    number(location, "Margin X px", s.marginX, 0, Math.max(4096, s.marginX), v -> s.marginX = v, "scaleMarginX");
    number(location, "Margin Y px", s.marginY, 0, Math.max(4096, s.marginY), v -> s.marginY = v, "scaleMarginY");
    JPanel selectedCell = group();
    choice(location, "Apply to", ScaleBarConfig.Scope.values(), new String[] {"Every cell", "One per condition", "Selected cell", "Figure once"}, s.scope,
        v -> { s.scope = v; selectedCell.setVisible(v == ScaleBarConfig.Scope.SELECTED_CELL); revalidate(); }, "scaleScope");
    location.add(selectedCell);
    number(selectedCell, "Row (1-based)", s.selectedRow, 1, Math.max(100, s.selectedRow), v -> s.selectedRow = v, "scaleRow");
    number(selectedCell, "Column (1-based)", s.selectedColumn, 1, Math.max(100, s.selectedColumn), v -> s.selectedColumn = v, "scaleColumn");
    selectedCell.setVisible(s.scope == ScaleBarConfig.Scope.SELECTED_CELL);
    JPanel calibration = section(details, "Calibration");
    decimal(calibration, "Manual µm/px", s.manualPixelSizeUm, 0, Math.max(1000000, s.manualPixelSizeUm), v -> s.manualPixelSizeUm = v, "manualPixelSize")
        .setToolTipText("Only used when the source image has no physical pixel calibration.");
    calibration.add(new JLabel("For uncalibrated images only."));
    details.setVisible(s.show);
  }
  private JPanel group() {
    JPanel p = new JPanel(); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); p.setAlignmentX(LEFT_ALIGNMENT);
    p.setBorder(BorderFactory.createEmptyBorder(2, 12, 4, 0)); return p;
  }
  private JPanel section(JPanel parent, String title) {
    JPanel p = group();
    p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder(title), BorderFactory.createEmptyBorder(2, 6, 6, 6)));
    parent.add(p); return p;
  }
  private void check(JPanel parent, String text, boolean value, Consumer<Boolean> set, String name) {
    JCheckBox b = new JCheckBox(text, value); b.setName(name); b.setAlignmentX(LEFT_ALIGNMENT); parent.add(b);
    b.addActionListener(e -> { set.accept(b.isSelected()); changed.run(); });
  }
  private void line(JPanel parent, String text, JComponent control) {
    JPanel p = new JPanel(new BorderLayout(8, 0)); p.setAlignmentX(LEFT_ALIGNMENT);
    JLabel label = new JLabel(text); label.setLabelFor(control); label.setPreferredSize(new Dimension(116, 28));
    p.add(label, BorderLayout.WEST); p.add(control, BorderLayout.CENTER);
    p.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0)); p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34)); parent.add(p);
  }
  private JSpinner number(JPanel p, String text, int value, int min, int max, IntConsumer set, String name) {
    JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, 1)); spinner.setName(name); line(p, text, spinner);
    spinner.addChangeListener(e -> { set.accept(((Number) spinner.getValue()).intValue()); changed.run(); }); return spinner;
  }
  private JSpinner decimal(JPanel p, String text, double value, double min, double max, DoubleConsumer set, String name) {
    JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, 0.01));
    spinner.setEditor(new JSpinner.NumberEditor(spinner, "0.######")); spinner.setName(name); line(p, text, spinner);
    spinner.addChangeListener(e -> { set.accept(((Number) spinner.getValue()).doubleValue()); changed.run(); }); return spinner;
  }
  private void toggle(JPanel p, String text, String first, String second, boolean secondSelected, Consumer<Boolean> set, String name) {
    JPanel pair = new JPanel(new GridLayout(1, 2, 0, 0));
    JToggleButton a = new JToggleButton(first, !secondSelected), b = new JToggleButton(second, secondSelected);
    a.setName(name + "First"); b.setName(name + "Second");
    ButtonGroup group = new ButtonGroup(); group.add(a); group.add(b); pair.add(a); pair.add(b); line(p, text, pair);
    a.addActionListener(e -> { set.accept(false); changed.run(); }); b.addActionListener(e -> { set.accept(true); changed.run(); });
  }
  private <T> void choice(JPanel p, String text, T[] values, String[] captions, T value, Consumer<T> set, String name) {
    JComboBox<String> combo = new JComboBox<>(captions);
    for (int i = 0; i < values.length; i++) if (values[i].equals(value)) combo.setSelectedIndex(i);
    combo.setName(name); line(p, text, combo);
    combo.addActionListener(e -> { set.accept(values[combo.getSelectedIndex()]); changed.run(); });
  }
  public Dimension getPreferredScrollableViewportSize() { return new Dimension(450, 650); }
  public boolean getScrollableTracksViewportWidth() { return true; }
  public boolean getScrollableTracksViewportHeight() { return false; }
  public int getScrollableUnitIncrement(Rectangle r, int axis, int direction) { return 28; }
  public int getScrollableBlockIncrement(Rectangle r, int axis, int direction) { return Math.max(28, r.height - 28); }
}
