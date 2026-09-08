package org.microscopy.figure;

import java.util.*;

public class FigureConfiguration {
  public LabelConfig labels = new LabelConfig();
  public ScaleBarConfig scaleBar = new ScaleBarConfig();
  public InsetConfig inset = new InsetConfig();
  public int horizontalGap = 0, verticalGap = 0;
  public List<ConditionConfig> conditions = new ArrayList<>();
  public List<ChannelConfig> channels = new ArrayList<>();
  public List<DisplayChannel> displayChannels = new ArrayList<>();
  public boolean automaticGrid = true, rowsAreChannels = true;
  public int manualRows = 3, manualColumns = 3;

  public int rows() {
    return automaticGrid
        ? (rowsAreChannels ? displayChannels.size() : conditions.size())
        : manualRows;
  }

  public int columns() {
    return automaticGrid
        ? (rowsAreChannels ? conditions.size() : displayChannels.size())
        : manualColumns;
  }

  public ChannelConfig channel(int id) {
    for (ChannelConfig c : channels) if (c.index == id) return c;
    throw new IllegalArgumentException("Missing channel: " + id);
  }

  public String displayLabel(DisplayChannel d) {
    StringJoiner label = new StringJoiner("/");
    for (int id : d.channels) label.add(channel(id).label);
    return label.toString();
  }

  public InsetCell insetCell(int conditionIndex, int displayIndex) {
    List<InsetCell> cells = conditions.get(conditionIndex).insets;
    InsetCell cell = displayIndex < cells.size() ? cells.get(displayIndex) : null;
    return cell == null ? new InsetCell() : cell;
  }

  public void validate(InputImageManager inputs) {
    labels.validate();
    if (horizontalGap < 0 || verticalGap < 0 || horizontalGap > 4096 || verticalGap > 4096)
      throw new IllegalArgumentException("Gap must be between 0 and 4096 px.");
    if (conditions.isEmpty()) throw new IllegalArgumentException("No input images selected.");
    if (displayChannels.isEmpty())
      throw new IllegalArgumentException("Select at least one display channel.");
    if (rows() != (rowsAreChannels ? displayChannels.size() : conditions.size())
        || columns() != (rowsAreChannels ? conditions.size() : displayChannels.size()))
      throw new IllegalArgumentException(
          "Grid dimensions do not match the selected channels and conditions.");
    InputImageManager.Source ref = inputs.get(conditions.get(0).sourceId);
    Set<Integer> ids = new HashSet<>();
    for (ChannelConfig c : channels) {
      c.validate();
      if (!ids.add(c.index)) throw new IllegalArgumentException("Duplicate channel ID.");
    }
    for (ConditionConfig c : conditions) {
      InputImageManager.Source s = inputs.get(c.sourceId);
      if (s.width != ref.width || s.height != ref.height)
        throw new IllegalArgumentException(
            "Image sizes are different: "
                + ref.title
                + " "
                + ref.width
                + " x "
                + ref.height
                + "; "
                + s.title
                + " "
                + s.width
                + " x "
                + s.height
                + ". No automatic resizing.");
      if (s.channels != ref.channels)
        throw new IllegalArgumentException(
            "Channel count differs between input images: "
                + ref.title
                + "="
                + ref.channels
                + ", "
                + s.title
                + "="
                + s.channels);
    }
    for (DisplayChannel d : displayChannels) {
      if (d.channels.isEmpty()
          || (!d.merge && d.channels.size() != 1)
          || new HashSet<>(d.channels).size() != d.channels.size())
        throw new IllegalArgumentException("Invalid display channel.");
      for (int id : d.channels) {
        channel(id);
        if (id > ref.channels)
          throw new IllegalArgumentException("Source channel unavailable: " + id);
      }
    }
    if (scaleBar.show) {
      if (scaleBar.scope == ScaleBarConfig.Scope.SELECTED_CELL
          && (scaleBar.selectedRow < 1
              || scaleBar.selectedRow > rows()
              || scaleBar.selectedColumn < 1
              || scaleBar.selectedColumn > columns()))
        throw new IllegalArgumentException("Selected scale bar cell is outside the grid.");
      for (ConditionConfig c : conditions)
        new ScaleBarRenderer().lengthPixels(inputs.get(c.sourceId), scaleBar);
    }
    inset.validate();
  }

  public String calibrationWarning(InputImageManager inputs) {
    if (conditions.size() < 2) return "";
    InputImageManager.Source first = inputs.get(conditions.get(0).sourceId);
    for (ConditionConfig c : conditions) {
      InputImageManager.Source s = inputs.get(c.sourceId);
      if (!s.unit.equals(first.unit)
          || Math.abs(s.pixelWidth - first.pixelWidth) > 1e-9
          || Math.abs(s.pixelHeight - first.pixelHeight) > 1e-9)
        return "WARNING: Pixel calibration differs between images. Scale bars use each source"
                   + " calibration.";
    }
    return "";
  }
}
