package org.microscopy.figure;

import java.util.ArrayList;
import java.util.List;

public class ConditionConfig {
  public String label, sourceId;
  /** Per display-channel-group override, index-aligned with FigureConfiguration.displayChannels. */
  public List<InsetCell> insets = new ArrayList<>();
  public boolean shareInsetRoi;
  public Integer sharedRoiX, sharedRoiY;

  public ConditionConfig() {}

  public ConditionConfig(String label, String sourceId) {
    this.label = label;
    this.sourceId = sourceId;
  }
}
