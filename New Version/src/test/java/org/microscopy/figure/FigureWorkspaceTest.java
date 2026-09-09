package org.microscopy.figure;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.*;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class FigureWorkspaceTest {
  @Test
  void hitTestingHonorsLabelSidesGapsAndTransposition() {
    InputImageManager inputs = new InputImageManager();
    FigureConfiguration c = CoreTest.config(inputs, 100, 200);
    c.labels.showRows = c.labels.showColumns = true;
    c.horizontalGap = 7; c.verticalGap = 9;
    for (boolean rows : new boolean[] {true, false})
      for (boolean farSide : new boolean[] {true, false}) {
        c.rowsAreChannels = rows;
        c.labels.rowRight = c.labels.columnBottom = farSide;
        int rb = new LabelRenderer().rowBand(c.labels), cb = new LabelRenderer().columnBand(c.labels);
        int w = c.columns() * 200 + (c.columns() - 1) * 7 + rb;
        int h = c.rows() * 160 + (c.rows() - 1) * 9 + cb;
        int x0 = farSide ? 0 : rb, y0 = farSide ? 0 : cb;
        FigureWorkspace.Hit cell = FigureWorkspace.hitFigure(c, 200, 160, w, h, x0 + 220, y0 + 180);
        assertEquals(1, cell.row); assertEquals(1, cell.column);
        assertNull(FigureWorkspace.hitFigure(c, 200, 160, w, h, x0 + 203, y0 + 10));
        assertNull(FigureWorkspace.hitFigure(c, 200, 160, w, h, x0 + 10, y0 + 164));
        assertNull(FigureWorkspace.hitFigure(c, 200, 160, w, h, -0.1, y0 + 10));
        FigureWorkspace.Hit label = FigureWorkspace.hitFigure(c, 200, 160, w, h, farSide ? w - 1 : 1, y0 + 180);
        assertTrue(label.rowLabel); assertEquals(1, label.row);
        label = FigureWorkspace.hitFigure(c, 200, 160, w, h, x0 + 220, farSide ? h - 1 : 1);
        assertTrue(label.columnLabel); assertEquals(1, label.column);
      }
  }

  @Test
  void canvasDispatchesSelectionAndDragButIgnoresStalePreview() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      InputImageManager inputs = new InputImageManager();
      FigureConfiguration c = CoreTest.config(inputs, 100, 200, 300);
      java.util.List<String> events = new ArrayList<>();
      FigureWorkspace w = new FigureWorkspace(new FigureWorkspace.Actions() {
        public void add(boolean channel) {}
        public void select(int condition, int display) { events.add("select:" + condition + ":" + display); }
        public void edit(boolean channel, int index) {}
        public void move(boolean channel, int from, int to) { events.add("move:" + channel + ":" + from + ":" + to); }
      });
      for (boolean rows : new boolean[] {true, false}) {
        c.rowsAreChannels = rows; w.setAxes(rows);
        java.awt.image.BufferedImage image = new PanelLayoutEngine().render(c, inputs);
        w.showFigure(image, c, inputs);
        Component canvas = w.getComponent(0);
        canvas.setSize(image.getWidth(), image.getHeight());
        events.clear();
        mouse(canvas, java.awt.event.MouseEvent.MOUSE_CLICKED, 2, 2);
        assertEquals("select:0:0", events.get(0));
        mouse(canvas, java.awt.event.MouseEvent.MOUSE_PRESSED, 2, 2);
        mouse(canvas, java.awt.event.MouseEvent.MOUSE_RELEASED, 18, 2);
        assertEquals("move:" + !rows + ":0:2", events.get(1));
        w.pending();
        mouse(canvas, java.awt.event.MouseEvent.MOUSE_CLICKED, 2, 2);
        assertEquals(2, events.size());
      }
    });
  }

  private static void mouse(Component canvas, int event, int x, int y) {
    canvas.dispatchEvent(new java.awt.event.MouseEvent(canvas, event, System.currentTimeMillis(), 0,
        x, y, 1, false, java.awt.event.MouseEvent.BUTTON1));
  }

  @Test
  void labelSelectionFramesFollowTheLabelAndNeverChangeExportPixels() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      InputImageManager inputs = new InputImageManager();
      ij.ImagePlus source = new ij.ImagePlus("source", new ij.process.ShortProcessor(200, 160));
      InputImageManager.Source s = inputs.snapshot(source, null);
      FigureConfiguration c = new FigureConfiguration();
      c.conditions.add(new ConditionConfig("Condition", s.id));
      c.channels.add(new ChannelConfig(1, "Channel", ChannelConfig.Lut.Green));
      c.displayChannels.add(new DisplayChannel("Channel", false, 1));
      c.labels.showRows = c.labels.showColumns = true;
      FigureWorkspace w = new FigureWorkspace(new FigureWorkspace.Actions() {
        public void add(boolean channel) {}
        public void select(int condition, int display) { wSelect[0].select(condition, display); }
        public void edit(boolean channel, int index) {}
        public void move(boolean channel, int from, int to) {}
        private final FigureWorkspace[] wSelect = holder;
      });
      holder[0] = w;
      for (boolean rows : new boolean[] {true, false}) for (boolean far : new boolean[] {true, false}) {
        c.rowsAreChannels = rows; c.labels.rowRight = c.labels.columnBottom = far; w.setAxes(rows);
        java.awt.image.BufferedImage original = new PanelLayoutEngine().render(c, inputs);
        w.showFigure(original, c, inputs);
        Component canvas = w.getComponent(0); canvas.setSize(original.getWidth(), original.getHeight());
        for (boolean channel : new boolean[] {true, false}) {
          Rectangle box = FigureWorkspace.labelBounds(c, 200, 160, original.getWidth(), original.getHeight(), channel, 0);
          mouse(canvas, java.awt.event.MouseEvent.MOUSE_CLICKED, box.x + box.width / 2, box.y + box.height / 2);
          java.awt.image.BufferedImage ui = new java.awt.image.BufferedImage(original.getWidth(), original.getHeight(), original.getType());
          Graphics2D g = ui.createGraphics(); canvas.paint(g); g.dispose();
          int border = new Color(30, 170, 215).getRGB();
          assertEquals(border, ui.getRGB(box.x + 1, box.y + box.height / 2));
          assertNotEquals(border, original.getRGB(box.x + 1, box.y + box.height / 2));
        }
      }
    });
  }
  private final FigureWorkspace[] holder = new FigureWorkspace[1];

  @Test
  void addTilesFollowTheCurrentAxes() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      java.util.List<Boolean> actions = new ArrayList<>();
      FigureWorkspace w = new FigureWorkspace(new FigureWorkspace.Actions() {
        public void add(boolean channel) { actions.add(channel); }
        public void select(int condition, int display) {}
        public void edit(boolean channel, int index) {}
        public void move(boolean channel, int from, int to) {}
      });
      for (boolean rows : new boolean[] {true, false}) {
        w.setAxes(rows);
        actions.clear();
        for (Component component : w.getComponents())
          if (component instanceof JButton) ((JButton) component).doClick();
        assertEquals(Arrays.asList(!rows, rows), actions);
      }
    });
  }
}
