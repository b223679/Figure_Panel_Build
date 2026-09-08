package org.microscopy.figure;

import java.util.*;

/** A single source channel or an additive merge referencing shared ChannelConfig IDs. */
public class DisplayChannel {
  public String label;
  public boolean merge;
  public List<Integer> channels = new ArrayList<>();

  public DisplayChannel() {}

  public DisplayChannel(String label, boolean merge, Integer... indices) {
    this.label = label;
    this.merge = merge;
    channels.addAll(Arrays.asList(indices));
  }
}
