package org.microscopy.figure;

/** Per-panel inset state: on/off and ROI top-left position in source pixels. Null x/y = default centered ROI. */
public class InsetCell {
  public boolean enabled = false;
  public Integer x, y;

  public InsetCell() {}

  public int roiX(InputImageManager.Source source, InsetConfig config) {
    return x == null ? Math.max(0, (source.width - config.roiWidth) / 2) : x;
  }

  public int roiY(InputImageManager.Source source, InsetConfig config) {
    return y == null ? Math.max(0, (source.height - config.roiHeight) / 2) : y;
  }
}
