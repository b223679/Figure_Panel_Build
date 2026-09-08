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
    default void swap() {}
    default void remove(boolean channel, int index) {}
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
  private final JButton swap = new JButton("Swap", new FigureIcons(FigureIcons.Kind.SWAP, 38));
  private TrashTarget trash;
  private boolean dragging, dragRow;
  private int dropIndex = -1;
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
    k.gridx = 1;
    swap.setName("swapAxes"); swap.setForeground(Color.WHITE); swap.setBackground(Color.BLACK);
    swap.setBorderPainted(false); swap.setFocusPainted(false);
    swap.setToolTipText("Swap rows and columns");
    swap.addActionListener(e -> actions.swap()); add(swap, k);
    right.addActionListener(e -> actions.add(!rowsAreChannels));
    bottom.addActionListener(e -> actions.add(rowsAreChannels));
    setAxes(true);
    canvas.setToolTipText("Click: B&C • Double-click label: edit • Drag: reorder row or column");
    MouseAdapter mouse = new MouseAdapter() {
      public void mousePressed(MouseEvent e) {
        resetDrag();
        if (!SwingUtilities.isLeftMouseButton(e)) return;
        pressed = hit(e.getX(), e.getY());
        pressPoint = e.getPoint();
      }
      public void mouseDragged(MouseEvent e) { updateDrag(e.getPoint()); }
      public void mouseReleased(MouseEvent e) {
        // Re-evaluate on release as well, including synthetic drag events and a final outside point.
        updateDrag(e.getPoint());
        if (!dragging || pressed == null) { resetDrag(); return; }
        boolean channel = dragRow == rowsAreChannels;
        int from = dragRow ? pressed.row : pressed.column, to = dropIndex;
        boolean remove = trash != null && trash.containsDrop(canvas, e.getPoint());
        resetDrag();
        if (from < 0) return;
        if (remove) actions.remove(channel, from);
        else if (to >= 0 && from != to) actions.move(channel, from, to);
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
    canvas.addMouseMotionListener(mouse);
    canvas.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "cancelDrag");
    canvas.getActionMap().put("cancelDrag", new AbstractAction() {
      public void actionPerformed(ActionEvent e) { resetDrag(); }
    });
  }

  void setTrashTarget(TrashTarget target) { trash = target; }

  private void resetDrag() {
    dragging = false; pressed = null; pressPoint = null; dropIndex = -1;
    if (trash != null) trash.setState(false, false);
    canvas.setCursor(Cursor.getDefaultCursor()); canvas.repaint();
  }

  private void updateDrag(Point point) {
    if (!ready || pressed == null || pressPoint == null) return;
    if (!dragging) {
      if (pressPoint.distance(point) < 8) return;
      dragRow = pressed.rowLabel || (!pressed.columnLabel
          && Math.abs(point.y - pressPoint.y) > Math.abs(point.x - pressPoint.x));
      dragging = true; canvas.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
    }
    boolean overTrash = trash != null && trash.containsDrop(canvas, point);
    if (trash != null) trash.setState(true, overTrash);
    dropIndex = overTrash ? -1 : destination(point, dragRow);
    canvas.repaint();
  }

  private int destination(Point point, boolean row) {
    if (image == null) return -1;
    int dx = (canvas.getWidth() - image.getWidth()) / 2, dy = (canvas.getHeight() - image.getHeight()) / 2;
    // A small outer margin allows dropping at the beginning/end; far outside cancels the move.
    if (point.x < dx - 20 || point.y < dy - 20 || point.x > dx + image.getWidth() + 20
        || point.y > dy + image.getHeight() + 20) return -1;
    LabelRenderer labels = new LabelRenderer();
    double coordinate = row ? (point.y - dy) * fullHeight / (double) image.getHeight()
        : (point.x - dx) * fullWidth / (double) image.getWidth();
    int origin = row ? (snapshot.labels.columnBottom ? 0 : labels.columnBand(snapshot.labels))
        : (snapshot.labels.rowRight ? 0 : labels.rowBand(snapshot.labels));
    int span = row ? cellHeight + snapshot.verticalGap : cellWidth + snapshot.horizontalGap;
    return Math.max(0, Math.min((row ? snapshot.rows() : snapshot.columns()) - 1,
        (int) Math.floor((coordinate - origin) / span)));
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

  public void pending() { ready = false; resetDrag(); }

  public void clear(String message) {
    resetDrag();
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
    String message = "Select Images or use + Condition to start; TIFF files can also be dropped here";
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
        if (dragging && pressed != null) {
          int from = dragRow ? pressed.row : pressed.column;
          Rectangle moving = dragRow
              ? new Rectangle(0, y0 + from * (cellHeight + snapshot.verticalGap), fullWidth, cellHeight)
              : new Rectangle(x0 + from * (cellWidth + snapshot.horizontalGap), 0, cellWidth, fullHeight);
          g.setColor(new Color(255, 255, 255, 90)); g.fill(moving);
          g.setStroke(new BasicStroke((float) (3.0 * fullWidth / image.getWidth())));
          g.setColor(new Color(255, 200, 65)); g.draw(moving);
          if (dropIndex >= 0 && dropIndex != from) {
            int edge = dragRow ? y0 + dropIndex * (cellHeight + snapshot.verticalGap)
                : x0 + dropIndex * (cellWidth + snapshot.horizontalGap);
            if (dropIndex > from) edge += dragRow ? cellHeight : cellWidth;
            g.setColor(new Color(65, 220, 255));
            if (dragRow) g.drawLine(0, edge, fullWidth, edge);
            else g.drawLine(edge, 0, edge, fullHeight);
          }
          return;
        }
        g.setColor(new Color(30, 170, 215));
        g.setStroke(new BasicStroke((float) (2.0 * fullWidth / image.getWidth())));
        if (selectedLabelIndex >= 0) {
          Rectangle bounds = labelBounds(snapshot, cellWidth, cellHeight, fullWidth, fullHeight,
              selectedLabelChannel, selectedLabelIndex);
          if (bounds != null) g.drawRect(bounds.x + 1, bounds.y + 1, bounds.width - 3, bounds.height - 3);
          return;
        }
        if (selectedDisplay < 0 && selectedCondition < 0) return;
        for (int i = 0; i < snapshot.conditions.size(); i++) {
          if (selectedCondition >= 0 && i != selectedCondition) continue;
          for (int j = 0; j < snapshot.displayChannels.size(); j++) {
            if (selectedDisplay >= 0 && j != selectedDisplay) continue;
            int row = snapshot.rowsAreChannels ? j : i;
            int col = snapshot.rowsAreChannels ? i : j;
            g.drawRect(x0 + col * (cellWidth + snapshot.horizontalGap) + 1,
                y0 + row * (cellHeight + snapshot.verticalGap) + 1, cellWidth - 3, cellHeight - 3);
          }
        }
      } finally { g.dispose(); }
    }
  }
}
