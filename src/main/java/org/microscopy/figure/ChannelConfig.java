package org.microscopy.figure;

public class ChannelConfig {
  public enum Lut {
    Grayscale,
    Red,
    Green,
    Blue,
    Cyan,
    Magenta,
    Yellow
  }

  private static final Lut[] DEFAULT_LUTS = {Lut.Blue, Lut.Green, Lut.Red, Lut.Cyan, Lut.Magenta};

  public int index;
  public String label;
  public double min = 0, max = 2000;
  public Lut lut = Lut.Grayscale;
  public boolean invert = false;
  /**
   * View-only override: render this channel in grayscale when it is the sole channel of a
   * single-channel display, so its true lut/color is unaffected and still used inside any Merge.
   */
  public boolean grayscale = false;

  public ChannelConfig() {}

  public ChannelConfig(int index, String label, Lut lut) {
    this.index = index;
    this.label = label;
    this.lut = lut;
  }

  /** Default channel order: Blue/Green/Red, then Cyan/Magenta for a 4th/5th channel, then Grayscale. */
  public static Lut defaultLut(int channelIndex1Based) {
    int i = channelIndex1Based - 1;
    return i >= 0 && i < DEFAULT_LUTS.length ? DEFAULT_LUTS[i] : Lut.Grayscale;
  }

  public void validate() {
    if (!Double.isFinite(min) || !Double.isFinite(max) || min >= max)
      throw new IllegalArgumentException(
          "Minimum intensity must be smaller than maximum intensity: " + label);
    if (index < 1 || lut == null || label == null)
      throw new IllegalArgumentException("Invalid channel configuration.");
  }
}
