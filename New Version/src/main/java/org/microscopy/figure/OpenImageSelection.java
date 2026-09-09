package org.microscopy.figure;

import ij.ImagePlus;
import ij.WindowManager;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/** Shared ImageJ window selector; callers retain their own import/assignment semantics. */
final class OpenImageSelection {
  final List<ImagePlus> images = new ArrayList<>();
  boolean browse;

  static OpenImageSelection choose(Component parent, boolean multiple) {
    int[] ids = WindowManager.getIDList(); if (ids == null) ids = new int[0];
    DefaultListModel<String> model = new DefaultListModel<>();
    for (int id : ids) { ImagePlus image = WindowManager.getImage(id); model.addElement(image == null ? "(Image closed)" : image.getTitle()); }
    JList<String> list = new JList<>(model); list.setName("openImageList");
    list.setSelectionMode(multiple ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION : ListSelectionModel.SINGLE_SELECTION);
    list.setVisibleRowCount(Math.max(4, Math.min(12, ids.length)));
    if (!multiple && ids.length > 0) list.setSelectedIndex(0);
    JPanel selection = new JPanel(new BorderLayout(6,6));
    selection.add(new JLabel(ids.length == 0 ? "No images are open in Fiji. You can open TIFF files below."
        : multiple ? "Select images already open in Fiji (Ctrl / Shift for multiple images)" : "Select an image already open in Fiji"), BorderLayout.NORTH);
    selection.add(new JScrollPane(list));
    int choice = JOptionPane.showOptionDialog(parent, selection, "Select Images", JOptionPane.DEFAULT_OPTION,
        JOptionPane.PLAIN_MESSAGE, null, new String[]{"Add selected", "Open TIFF files...", "Cancel"}, "Add selected");
    OpenImageSelection result = new OpenImageSelection(); result.browse = choice == 1;
    if (choice == 0) for (int index : list.getSelectedIndices()) {
      ImagePlus image = WindowManager.getImage(ids[index]);
      if (image == null) throw new IllegalArgumentException("Selected ImageJ image was closed. Select it again.");
      result.images.add(image);
    }
    return result;
  }
}
