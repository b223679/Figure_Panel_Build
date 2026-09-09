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

  public int index;
  public String label;
  public double min = 0, max = 2000;
  public Lut lut = Lut.Grayscale;
  public boolean invert = false;

  public ChannelConfig() {}

  public ChannelConfig(int index, String label, Lut lut) {
    this.index = index;
    this.label = label;
    this.lut = lut;
  }

  public void validate() {
    if (!Double.isFinite(min) || !Double.isFinite(max) || min >= max)
      throw new IllegalArgumentException(
          "Minimum intensity must be smaller than maximum intensity: " + label);
    if (index < 1 || lut == null || label == null)
      throw new IllegalArgumentException("Invalid channel configuration.");
  }
}
