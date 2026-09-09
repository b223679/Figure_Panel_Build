package org.microscopy.figure;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class UiModelTest {
  private static <T> List<T> find(Container root, Class<T> type) {
    List<T> out = new ArrayList<>();
    for (Component c : root.getComponents()) {
      if (type.isInstance(c)) out.add(type.cast(c));
      if (c instanceof Container) out.addAll(find((Container) c, type));
    }
    return out;
  }

  @Test
  void sliderChangesSharedChannelOnly() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          InputImageManager inputs = new InputImageManager();
          FigureConfiguration c = CoreTest.config(inputs, 1000, 1000, 2000);
          ContrastPanel panel = new ContrastPanel(c, inputs, () -> {});
          List<JSlider> sliders = find(panel, JSlider.class);
          assertEquals(4, sliders.size());
          sliders.get(0).setValue(1000);
          assertTrue(c.channels.get(0).min > 0);
          assertEquals(0, c.channels.get(1).min);
          java.awt.image.BufferedImage rendered = new PanelLayoutEngine().render(c, inputs);
          assertEquals(rendered.getRGB(0, 0), rendered.getRGB(8, 0));
          panel.stop();
        });
  }

  @Test
  void brightnessContrastResetAndLutShareTheSelectedSourceChannel() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      InputImageManager inputs = new InputImageManager();
      FigureConfiguration c = CoreTest.config(inputs, 1000, 2000);
      ContrastPanel panel = new ContrastPanel(c, inputs, () -> {}, true);
      panel.selectChannel(2);
      List<JSlider> sliders = find(panel, JSlider.class);
      double width = c.channel(2).max - c.channel(2).min;
      sliders.get(2).setValue(7500);
      assertEquals(width, c.channel(2).max - c.channel(2).min, 1e-6);
      assertTrue(c.channel(2).min < 0);
      double center = (c.channel(2).min + c.channel(2).max) / 2;
      sliders.get(3).setValue(7500);
      assertEquals(center, (c.channel(2).min + c.channel(2).max) / 2, 1e-6);
      assertTrue(c.channel(2).max - c.channel(2).min < width);
      assertEquals(0, c.channel(1).min);
      JComboBox<?> lut = find(panel, JComboBox.class).get(1);
      lut.setSelectedItem(ChannelConfig.Lut.Magenta);
      assertEquals(ChannelConfig.Lut.Magenta, c.channel(2).lut);
      find(panel, JButton.class).stream().filter(b -> "Reset".equals(b.getText())).findFirst().get().doClick();
      assertEquals(0, c.channel(2).min);
      assertEquals(65535, c.channel(2).max);
      panel.stop();
    });
  }

  @Test
  void groupedAppearanceControlsUpdateIndependentSettings() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          FigureConfiguration c = new FigureConfiguration();
          AppearancePanel panel = new AppearancePanel(c, () -> {});
          named(panel, JSpinner.class, "horizontalGap").setValue(11);
          assertEquals(11, c.horizontalGap);
          assertEquals(0, c.verticalGap);
          named(panel, JSpinner.class, "verticalGap").setValue(7);
          assertEquals(7, c.verticalGap);
          named(panel, JToggleButton.class, "backgroundFirst").doClick();
          assertTrue(c.labels.whiteBackground);
          named(panel, JCheckBox.class, "showRows").doClick();
          named(panel, JToggleButton.class, "rowColorFirst").doClick();
          assertFalse(c.labels.isWhiteText(true));
          assertTrue(c.labels.isWhiteText(false));
          named(panel, JToggleButton.class, "rowPositionSecond").doClick();
          assertTrue(c.labels.rowRight);
          named(panel, JToggleButton.class, "columnPositionSecond").doClick();
          assertTrue(c.labels.columnBottom);
          named(panel, JCheckBox.class, "showScaleBar").doClick();
          assertTrue(c.scaleBar.show);
          named(panel, JSpinner.class, "scaleWidth").setValue(12);
          assertEquals(12, c.scaleBar.thickness);
          named(panel, JComboBox.class, "scalePosition").setSelectedIndex(0);
          assertEquals(ScaleBarConfig.Position.TOP_LEFT, c.scaleBar.position);
          named(panel, JComboBox.class, "scaleScope").setSelectedIndex(2);
          assertEquals(ScaleBarConfig.Scope.SELECTED_CELL, c.scaleBar.scope);
          named(panel, JCheckBox.class, "showScaleText").doClick();
          assertFalse(c.scaleBar.showText);
          assertFalse(named(panel, JSpinner.class, "scaleFontSize").getParent().getParent().isVisible());
        });
  }

  private static <T extends Component> T named(Container root, Class<T> type, String name) {
    return find(root, type).stream().filter(c -> name.equals(c.getName())).findFirst().get();
  }
}
