package org.microscopy.figure;

/** Initial appearance in source pixels; called once for a new figure, never for loaded settings. */
public final class AppearanceDefaults {
  private AppearanceDefaults() {}

  public static void initialize(FigureConfiguration c, InputImageManager.Source source) {
    int edge = Math.max(source.width, source.height);
    int font = Math.max(1, Math.min(LabelConfig.MAX_FONT_SIZE, (int) Math.round(edge * 0.10)));
    int margin = Math.min(4096, Math.max(1, (int) Math.round(edge * 0.02)));
    LabelConfig originalLabels = new LabelConfig();
    ScaleBarConfig originalBar = new ScaleBarConfig();
    InsetConfig originalInset = new InsetConfig();
    LabelConfig l = c.labels;
    ScaleBarConfig s = c.scaleBar;
    InsetConfig i = c.inset;
    if (l.rowFontSize == originalLabels.rowFontSize) l.rowFontSize = font;
    if (l.columnFontSize == originalLabels.columnFontSize) l.columnFontSize = font;
    if (l.rowMargin == originalLabels.rowMargin) l.rowMargin = margin;
    if (l.columnMargin == originalLabels.columnMargin) l.columnMargin = margin;
    if (s.fontSize == originalBar.fontSize) s.fontSize = font;
    if (s.thickness == originalBar.thickness)
      s.thickness = Math.max(1, Math.min(4096, (int) Math.round(edge * 0.03)));
    if (s.marginX == originalBar.marginX) s.marginX = margin;
    if (s.marginY == originalBar.marginY) s.marginY = margin;
    if (i.insetWidth == originalInset.insetWidth)
      i.insetWidth = Math.max(1, (int) Math.round(edge * 0.20));
    if (i.insetHeight == originalInset.insetHeight)
      i.insetHeight = Math.max(1, (int) Math.round(edge * 0.20));
    if (i.insetStrokeWidth == originalInset.insetStrokeWidth)
      i.insetStrokeWidth = Math.max(1, (int) Math.round(edge * 0.005));
    if (i.roiStrokeWidth == originalInset.roiStrokeWidth)
      i.roiStrokeWidth = Math.max(1, (int) Math.round(edge * 0.005));
  }
}
