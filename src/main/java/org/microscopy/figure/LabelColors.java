package org.microscopy.figure;

import java.awt.Color;

/** Label-only contrast palette. Source-channel LUTs and image pixels are never changed. */
public final class LabelColors {
  private LabelColors() {}
  public static Color neutral(LabelConfig labels, boolean row) {
    return labels.isWhiteText(row) ? Color.WHITE : Color.BLACK;
  }
  public static Color channel(ChannelConfig.Lut lut, LabelConfig labels, boolean row) {
    if (lut == ChannelConfig.Lut.Grayscale) return neutral(labels, row);
    if (!labels.transparentBackground) {
      if (labels.whiteBackground) {
        if (lut == ChannelConfig.Lut.Yellow) return new Color(0x808000);
        if (lut == ChannelConfig.Lut.Cyan) return new Color(0x008080);
        if (lut == ChannelConfig.Lut.Green) return new Color(0x008000);
      } else if (lut == ChannelConfig.Lut.Blue) return new Color(0x6666ff);
    }
    return new Color(ImageRenderer.lutRgb(lut, 255));
  }
}
