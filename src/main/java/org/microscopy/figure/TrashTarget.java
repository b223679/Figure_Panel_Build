package org.microscopy.figure;

import java.awt.*;
import javax.swing.*;

/** Drop target for layout entries only; no filesystem operation exists here. */
final class TrashTarget extends JPanel {
  private boolean active, hover;
  private final JLabel title = new JLabel("Remove", SwingConstants.CENTER);
  TrashTarget() {
    setName("layoutTrash"); setLayout(new BorderLayout(0, 5));
    setPreferredSize(new Dimension(105, 130));
    setBorder(BorderFactory.createEmptyBorder(14, 12, 12, 12));
    JLabel icon = new JLabel(new FigureIcons(FigureIcons.Kind.TRASH, 62));
    icon.setHorizontalAlignment(SwingConstants.CENTER); icon.setForeground(Color.WHITE);
    title.setForeground(Color.LIGHT_GRAY); add(icon, BorderLayout.CENTER); add(title, BorderLayout.SOUTH);
    setToolTipText("Drag a row or column here to remove it from this figure. Original files are kept.");
    setState(false, false);
  }
  boolean containsDrop(Component origin, Point point) {
    return isShowing() && contains(SwingUtilities.convertPoint(origin, point, this));
  }
  void setState(boolean dragging, boolean inside) {
    active = dragging; hover = inside;
    setBackground(hover ? new Color(145, 50, 45) : active ? new Color(65, 65, 65) : Color.BLACK);
    title.setText(hover ? "Drop here" : "Remove"); repaint();
  }
}
