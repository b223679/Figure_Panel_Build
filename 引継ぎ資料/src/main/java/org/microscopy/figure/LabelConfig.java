package org.microscopy.figure;

public class LabelConfig {
  public static final int MAX_FONT_SIZE = 4096;
  // Null preserves the shared whiteText setting in older JSON files.
  public Boolean rowWhiteText, columnWhiteText;
  public boolean transparentBackground = false;
  public boolean showRows = false,
      showColumns = false,
      rowRight = false,
      columnBottom = false,
      whiteText = true,
      whiteBackground = false;
  public int rowFontSize = 24, columnFontSize = 24, rowMargin = 10, columnMargin = 10;

  public boolean isWhiteText(boolean row) {
    Boolean value = row ? rowWhiteText : columnWhiteText;
    return value == null ? whiteText : value;
  }

  public void validate() {
    if (rowFontSize < 1
        || columnFontSize < 1
        || rowFontSize > MAX_FONT_SIZE
        || columnFontSize > MAX_FONT_SIZE
        || rowMargin < 0
        || columnMargin < 0
        || rowMargin > 4096
        || columnMargin > 4096)
      throw new IllegalArgumentException("Invalid label font size/margin.");
  }
}
