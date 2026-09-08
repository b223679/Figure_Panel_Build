package org.microscopy.figure;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;

/** One slider pair edits one shared ChannelConfig; no per-cell controls exist. */
public class ContrastPanel extends JPanel {
  private final FigureConfiguration config;
  private final InputImageManager inputs;
  private final Runnable changed;
  private final JComboBox<String> selector = new JComboBox<>();
  private final JPanel strip = new JPanel();
  private final JSlider low = new JSlider(0, 10000, 0), high = new JSlider(0, 10000, 10000);
  private final JSlider brightness = new JSlider(0, 10000, 5000),
      contrast = new JSlider(0, 10000, 5000);
  private final JComboBox<ChannelConfig.Lut> lut = new JComboBox<>(ChannelConfig.Lut.values());
  private final JCheckBox invert = new JCheckBox("Invert gray");
  private final boolean compact;
  private final JSpinner min = new JSpinner(new SpinnerNumberModel(0.0, null, null, 1.0)),
      max = new JSpinner(new SpinnerNumberModel(2000.0, null, null, 1.0));
  private final JLabel message = new JLabel("Shared B&C: all conditions use identical Min/Max.");
  private double rangeMin, rangeMax = 65535;
  private boolean updating;
  private final javax.swing.Timer timer;

  public ContrastPanel(FigureConfiguration c, InputImageManager i, Runnable changed) {
    this(c, i, changed, false);
  }

  public ContrastPanel(FigureConfiguration c, InputImageManager i, Runnable changed, boolean compact) {
    this.config = c;
    this.inputs = i;
    this.changed = changed;
    this.compact = compact;
    setLayout(new BorderLayout());
    setBorder(BorderFactory.createTitledBorder("B&C — shared across all conditions"));
    strip.setLayout(new BoxLayout(strip, BoxLayout.X_AXIS));
    JPanel top = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 3));
    selector.setPreferredSize(new Dimension(140, 26));
    lut.setPreferredSize(new Dimension(95, 26));
    top.add(new JLabel("Channel"));
    top.add(selector);
    top.add(new JLabel("LUT"));
    top.add(lut);
    invert.setName("invertGray");
    invert.setToolTipText("Invert grayscale display only; source pixels remain unchanged.");
    top.add(invert);
    add(top, BorderLayout.NORTH);
    if (!compact) add(new JScrollPane(strip), BorderLayout.CENTER);
    JPanel bottom = new JPanel();
    bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
    JPanel numbers = new JPanel();
    min.setPreferredSize(new Dimension(125, 26));
    max.setPreferredSize(new Dimension(125, 26));
    numbers.add(new JLabel("Min"));
    numbers.add(min);
    numbers.add(new JLabel("Max"));
    numbers.add(max);
    JButton auto = new JButton("Auto");
    auto.setToolTipText("Use the full intensity range across all conditions");
    numbers.add(auto);
    JButton reset = new JButton("Reset");
    numbers.add(reset);
    bottom.add(numbers);
    JPanel sliders = new JPanel(new GridLayout(4, 2, 6, 0));
    sliders.add(new JLabel("Minimum")); sliders.add(low);
    sliders.add(new JLabel("Maximum")); sliders.add(high);
    sliders.add(new JLabel("Brightness")); sliders.add(brightness);
    sliders.add(new JLabel("Contrast")); sliders.add(contrast);
    bottom.add(sliders);
    bottom.add(message);
    add(bottom, compact ? BorderLayout.CENTER : BorderLayout.SOUTH);
    timer = new javax.swing.Timer(120, e -> paintStrip());
    timer.setRepeats(false);
    for (ChannelConfig cc : c.channels) selector.addItem(cc.index + ": " + cc.label);
    selector.addActionListener(e -> select());
    low.addChangeListener(e -> slider(true));
    high.addChangeListener(e -> slider(false));
    min.addChangeListener(e -> numbers());
    max.addChangeListener(e -> numbers());
    brightness.addChangeListener(e -> adjustTone(true));
    contrast.addChangeListener(e -> adjustTone(false));
    lut.addActionListener(e -> {
      if (updating || selector.getSelectedIndex() < 0) return;
      current().lut = (ChannelConfig.Lut) lut.getSelectedItem();
      timer.restart();
      changed.run();
    });
    invert.addActionListener(e -> {
      if (updating || selector.getSelectedIndex() < 0) return;
      current().invert = invert.isSelected(); timer.restart(); changed.run();
    });
    reset.addActionListener(e -> resetRange());
    auto.addActionListener(
        e -> {
          try {
            ChannelConfig cc = current();
            double[] r = new ImageRenderer().range(inputs, config, cc.index);
            cc.min = r[0];
            cc.max = r[1];
            select();
            changed.run();
          } catch (Exception ex) {
            message.setText(ex.getMessage());
          }
        });
    if (!c.channels.isEmpty()) select();
    else {
      for (JSlider slider : new JSlider[] {low, high, brightness, contrast}) slider.setEnabled(false);
      min.setEnabled(false); max.setEnabled(false); lut.setEnabled(false); invert.setEnabled(false);
      auto.setEnabled(false); reset.setEnabled(false);
    }
  }

  public void selectChannel(int id) {
    for (int i = 0; i < config.channels.size(); i++) {
      if (config.channels.get(i).index == id) {
        if (selector.getSelectedIndex() == i) select();
        else selector.setSelectedIndex(i);
        return;
      }
    }
  }

  public int selectedChannel() { return selector.getSelectedIndex() < 0 ? -1 : current().index; }

  public void refreshFromModel() {
    int selected = selector.getSelectedIndex();
    updating = true;
    try {
      selector.removeAllItems();
      for (ChannelConfig ch : config.channels) selector.addItem(ch.index + ": " + ch.label);
      if (selected >= 0 && selected < selector.getItemCount()) selector.setSelectedIndex(selected);
    } finally { updating = false; }
    select();
  }

  private void resetRange() {
    if (selector.getSelectedIndex() < 0 || config.conditions.isEmpty()) return;
    ChannelConfig cc = current();
    InputImageManager.Source source = inputs.get(config.conditions.get(0).sourceId);
    if (source.bitDepth == 32) {
      double[] range = new ImageRenderer().range(inputs, config, cc.index);
      cc.min = range[0]; cc.max = range[1];
    } else {
      cc.min = 0; cc.max = source.bitDepth == 8 ? 255 : 65535;
    }
    select();
    changed.run();
  }

  private void adjustTone(boolean isBrightness) {
    if (updating || selector.getSelectedIndex() < 0) return;
    ChannelConfig cc = current();
    double span = rangeMax - rangeMin;
    double center = (cc.min + cc.max) / 2;
    double window = cc.max - cc.min;
    if (isBrightness) center = rangeMin + span * (1 - brightness.getValue() / 10000.0);
    else window = span * Math.pow(2, (5000 - contrast.getValue()) / 1250.0);
    cc.min = center - window / 2;
    cc.max = center + window / 2;
    sync();
    timer.restart();
    changed.run();
  }

  private ChannelConfig current() {
    return config.channels.get(selector.getSelectedIndex());
  }

  private void select() {
    if (updating || selector.getSelectedIndex() < 0) return;
    try {
      ChannelConfig cc = current();
      double[] r = new ImageRenderer().range(inputs, config, cc.index);
      rangeMin = Math.min(r[0], cc.min);
      rangeMax = Math.max(r[1], cc.max);
      sync();
      paintStrip();
    } catch (Exception ex) {
      message.setText(ex.getMessage());
    }
  }

  private void sync() {
    updating = true;
    try {
      ChannelConfig cc = current();
      min.setValue(cc.min);
      max.setValue(cc.max);
      lut.setSelectedItem(cc.lut);
      invert.setSelected(cc.invert);
      low.setValue((int) Math.round(10000 * (cc.min - rangeMin) / (rangeMax - rangeMin)));
      high.setValue((int) Math.round(10000 * (cc.max - rangeMin) / (rangeMax - rangeMin)));
      brightness.setValue((int) Math.round(10000 * (1 - ((cc.min + cc.max) / 2 - rangeMin) / (rangeMax - rangeMin))));
      contrast.setValue((int) Math.round(5000 - 1250 * Math.log((cc.max - cc.min) / (rangeMax - rangeMin)) / Math.log(2)));
    } finally {
      updating = false;
    }
  }

  private void slider(boolean isMin) {
    if (updating || selector.getSelectedIndex() < 0) return;
    ChannelConfig cc = current();
    double v =
        rangeMin + (rangeMax - rangeMin) * (isMin ? low.getValue() : high.getValue()) / 10000;
    if (isMin && v < cc.max) cc.min = v;
    else if (!isMin && v > cc.min) cc.max = v;
    sync();
    timer.restart();
    changed.run();
  }

  private void numbers() {
    if (updating || selector.getSelectedIndex() < 0) return;
    double a = ((Number) min.getValue()).doubleValue(), b = ((Number) max.getValue()).doubleValue();
    if (!Double.isFinite(a) || !Double.isFinite(b) || a >= b) {
      message.setText("Minimum intensity must be smaller than maximum intensity.");
      return;
    }
    ChannelConfig cc = current();
    cc.min = a;
    cc.max = b;
    rangeMin = Math.min(rangeMin, a);
    rangeMax = Math.max(rangeMax, b);
    sync();
    timer.restart();
    changed.run();
  }

  private void paintStrip() {
    if (selector.getSelectedIndex() < 0) return;
    strip.removeAll();
    ChannelConfig cc = current();
    try {
      cc.validate();
      if (!compact) for (ConditionConfig condition : config.conditions) {
        InputImageManager.Source s = inputs.get(condition.sourceId);
        int w = Math.min(220, s.width), h = Math.max(1, s.height * w / s.width);
        BufferedImage image =
            new ImageRenderer()
                .render(s, new DisplayChannel(cc.label, false, cc.index), config, w, h);
        JLabel label = new JLabel(condition.label, new ImageIcon(image), SwingConstants.CENTER);
        label.setHorizontalTextPosition(SwingConstants.CENTER);
        label.setVerticalTextPosition(SwingConstants.TOP);
        label.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        strip.add(label);
      }
      message.setText(
          "Shared across ALL conditions: "
              + String.format(java.util.Locale.ROOT, "%.3f – %.3f", cc.min, cc.max));
    } catch (Exception ex) {
      message.setText(ex.getMessage());
    }
    strip.revalidate();
    strip.repaint();
  }

  public void stop() {
    timer.stop();
  }

}
