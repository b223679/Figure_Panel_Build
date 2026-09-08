package org.microscopy.figure;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import javax.swing.*;

/** Figure canvas with UI-only add tiles and hit testing against the rendered snapshot. */
public class FigureWorkspace extends JPanel {
  public interface Actions {
    void add(boolean channel);
    void select(int condition, int display);
    void edit(boolean channel, int index);
    void move(boolean channel, int from, int to);
  }

  static class Hit {
    final int row, column;
    final boolean rowLabel, columnLabel;
    Hit(int row, int column, boolean rowLabel, boolean columnLabel) {
      this.row = row;
      this.column = column;
      this.rowLabel = rowLabel;
      this.columnLabel = columnLabel;
    }
  }

  private final Actions actions;
  private final Canvas canvas = new Canvas();
  private final JButton right = addButton(), bottom = addButton();
  private boolean rowsAreChannels = true, ready;
  private FigureConfiguration snapshot;
  private int cellWidth, cellHeight, fullWidth, fullHeight;
  private BufferedImage image;
  private int selectedCondition = -1, selectedDisplay = -1;
  private int selectedLabelIndex = -1;
  private boolean selectedLabelChannel;
  private Hit pressed;
  private Point pressPoint;

  public FigureWorkspace(Actions actions) {
    this.actions = actions;
    setLayout(new GridBagLayout());
    setBackground(Color.BLACK);
    GridBagConstraints k = new GridBagConstraints();
    k.insets = new Insets(6, 6, 6, 6);
    k.gridx = 0; k.gridy = 0; k.fill = GridBagConstraints.BOTH;
    add(canvas, k);
    k.gridx = 1;
    add(right, k);
    k.gridx = 0; k.gridy = 1;
    add(bottom, k);
    right.addActionListener(e -> actions.add(!rowsAreChannels));
    bottom.addActionListener(e -> actions.add(rowsAreChannels));
    setAxes(true);
    canvas.setToolTipText("Click: B&C • Double-click label: edit • Drag: reorder row or column");
    MouseAdapter mouse = new MouseAdapter() {
      public void mousePressed(MouseEvent e) {
        pressed = hit(e.getX(), e.getY());
        pressPoint = e.getPoint();
      }
      public void mouseReleased(MouseEvent e) {
        Hit target = hit(e.getX(), e.getY());
        if (pressed == null || target == null || pressPoint.distance(e.getPoint()) < 8) return;
        boolean row = Math.abs(e.getY() - pressPoint.y) > Math.abs(e.getX() - pressPoint.x);
        int from = row ? pressed.row : pressed.column, to = row ? target.row : target.column;
        if (from >= 0 && to >= 0 && from != to) actions.move(row == rowsAreChannels, from, to);
        pressed = null;
      }
      public void mouseClicked(MouseEvent e) {
        Hit h = hit(e.getX(), e.getY());
        if (h == null) return;
        if (h.rowLabel || h.columnLabel) {
          boolean channel = h.rowLabel == rowsAreChannels;
          int index = h.rowLabel ? h.row : h.column;
          if (e.getClickCount() == 2) {
            selectLabel(channel, index);
            actions.edit(channel, index);
          } else if (channel) actions.select(-1, index);
          selectLabel(channel, index);
        } else {
          actions.select(rowsAreChannels ? h.column : h.row, rowsAreChannels ? h.row : h.column);
        }
      }
    };
    canvas.addMouseListener(mouse);
  }

  private static JButton addButton() {
    JButton b = new JButton();
    b.setBackground(new Color(190, 190, 190));
    b.setForeground(Color.BLACK);
    b.setFocusPainted(false);
    b.setPreferredSize(new Dimension(130, 72));
    return b;
  }

  public void setAxes(boolean rows) {
    rowsAreChannels = rows;
    right.setText("<html><center><font size='+3'>＋</font><br>" + (rows ? "Condition" : "Channel / Merge") + "</center></html>");
    bottom.setText("<html><center><font size='+3'>＋</font><br>" + (rows ? "Channel / Merge" : "Condition") + "</center></html>");
    right.setToolTipText(rows ? "Add condition images" : "Add a channel or merge");
    bottom.setToolTipText(rows ? "Add a channel or merge" : "Add condition images");
  }

  public void pending() { ready = false; }

  public void clear(String message) {
    ready = false;
    image = null;
    canvas.message = message;
    canvas.setPreferredSize(new Dimension(700, 200));
    canvas.revalidate();
    canvas.repaint();
  }

  public void showFigure(BufferedImage rendered, FigureConfiguration c, InputImageManager inputs) {
    image = rendered;
    snapshot = c;
    InputImageManager.Source source = inputs.get(c.conditions.get(0).sourceId);
    cellWidth = source.width; cellHeight = source.height;
    Dimension d = new PanelLayoutEngine().dimensions(c, inputs);
    fullWidth = d.width; fullHeight = d.height;
    ready = true;
    canvas.setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));
    canvas.revalidate();
    canvas.repaint();
  }

  public void select(int condition, int display) {
    selectedLabelIndex = -1;
    selectedCondition = condition; selectedDisplay = display;
    canvas.repaint();
  }

  public void selectLabel(boolean channel, int index) {
    selectedLabelChannel = channel;
    selectedLabelIndex = index;
    canvas.repaint();
  }

  static Rectangle labelBounds(FigureConfiguration c, int cw, int ch, int width, int height,
      boolean channel, int index) {
    boolean row = channel == c.rowsAreChannels;
    if (index < 0 || index >= (row ? c.rows() : c.columns())) return null;
    LabelRenderer labels = new LabelRenderer();
    int rb = labels.rowBand(c.labels), cb = labels.columnBand(c.labels);
    if (row) {
      if (rb == 0) return null;
      return new Rectangle(c.labels.rowRight ? width - rb : 0,
          (c.labels.columnBottom ? 0 : cb) + index * (ch + c.verticalGap), rb, ch);
    }
    if (cb == 0) return null;
    return new Rectangle((c.labels.rowRight ? 0 : rb) + index * (cw + c.horizontalGap),
        c.labels.columnBottom ? height - cb : 0, cw, cb);
  }

  private Hit hit(int mx, int my) {
    if (!ready || image == null) return null;
    int dx = (canvas.getWidth() - image.getWidth()) / 2;
    int dy = (canvas.getHeight() - image.getHeight()) / 2;
    if (mx < dx || my < dy || mx >= dx + image.getWidth() || my >= dy + image.getHeight()) return null;
    return hitFigure(snapshot, cellWidth, cellHeight, fullWidth, fullHeight,
        (mx - dx) * fullWidth / (double) image.getWidth(),
        (my - dy) * fullHeight / (double) image.getHeight());
  }

  static Hit hitFigure(FigureConfiguration c, int cw, int ch, int width, int height, double x, double y) {
    if (x < 0 || y < 0 || x >= width || y >= height) return null;
    LabelRenderer labels = new LabelRenderer();
    int rb = labels.rowBand(c.labels), cb = labels.columnBand(c.labels);
    int x0 = c.labels.rowRight ? 0 : rb, y0 = c.labels.columnBottom ? 0 : cb;
    double ix = x - x0, iy = y - y0;
    int col = ix < 0 ? -1 : (int) (ix / (cw + c.horizontalGap));
    int row = iy < 0 ? -1 : (int) (iy / (ch + c.verticalGap));
    boolean validCol = col >= 0 && col < c.columns() && ix % (cw + c.horizontalGap) < cw;
    boolean validRow = row >= 0 && row < c.rows() && iy % (ch + c.verticalGap) < ch;
    boolean rowLabel = rb > 0 && (c.labels.rowRight ? x >= width - rb : x < rb);
    boolean colLabel = cb > 0 && (c.labels.columnBottom ? y >= height - cb : y < cb);
    if (rowLabel && validRow) return new Hit(row, -1, true, false);
    if (colLabel && validCol) return new Hit(-1, col, false, true);
    return validRow && validCol ? new Hit(row, col, false, false) : null;
  }

  private class Canvas extends JPanel {
    String message = "Add TIFF images using + Condition or drop files here";
    Canvas() { setBackground(Color.BLACK); setPreferredSize(new Dimension(700, 200)); }
    protected void paintComponent(Graphics graphics) {
      super.paintComponent(graphics);
      Graphics2D g = (Graphics2D) graphics.create();
      try {
        if (image == null) {
          g.setColor(Color.LIGHT_GRAY);
          g.drawString(message, 18, getHeight() / 2);
          return;
        }
        int dx = (getWidth() - image.getWidth()) / 2, dy = (getHeight() - image.getHeight()) / 2;
        if (snapshot.labels.transparentBackground) {
          for (int y = 0; y < image.getHeight(); y += 12) for (int x = 0; x < image.getWidth(); x += 12) {
            g.setColor(((x / 12 + y / 12) % 2 == 0) ? new Color(190, 190, 190) : new Color(230, 230, 230));
            g.fillRect(dx + x, dy + y, Math.min(12, image.getWidth() - x), Math.min(12, image.getHeight() - y));
          }
        }
        g.drawImage(image, dx, dy, null);
        if (!ready) return;
        LabelRenderer labels = new LabelRenderer();
        int x0 = snapshot.labels.rowRight ? 0 : labels.rowBand(snapshot.labels);
        int y0 = snapshot.labels.columnBottom ? 0 : labels.columnBand(snapshot.labels);
        g.translate(dx, dy);
        g.scale(image.getWidth() / (double) fullWidth, image.getHeight() / (double) fullHeight);
        g.setColor(new Color(30, 170, 215));
        g.setStroke(new BasicStroke((float) (2.0 * fullWidth / image.getWidth())));
        if (selectedLabelIndex >= 0) {
          Rectangle bounds = labelBounds(snapshot, cellWidth, cellHeight, fullWidth, fullHeight,
              selectedLabelChannel, selectedLabelIndex);
          if (bounds != null) g.drawRect(bounds.x + 1, bounds.y + 1, bounds.width - 3, bounds.height - 3);
          return;
        }
        if (selectedDisplay < 0 || selectedDisplay >= snapshot.displayChannels.size()) return;
        for (int i = 0; i < snapshot.conditions.size(); i++) {
          if (selectedCondition >= 0 && i != selectedCondition) continue;
          int row = snapshot.rowsAreChannels ? selectedDisplay : i;
          int col = snapshot.rowsAreChannels ? i : selectedDisplay;
          g.drawRect(x0 + col * (cellWidth + snapshot.horizontalGap) + 1,
              y0 + row * (cellHeight + snapshot.verticalGap) + 1, cellWidth - 3, cellHeight - 3);
        }
      } finally { g.dispose(); }
    }
  }
}
