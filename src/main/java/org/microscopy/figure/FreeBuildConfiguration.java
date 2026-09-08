package org.microscopy.figure;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/** Independent grid: each occupied slot owns a one-image configuration and its own B&C. */
public final class FreeBuildConfiguration {
  public int rows, columns;
  public int cellWidth = 512, cellHeight = 512, gap = 8, fontSize = 32;
  public boolean whiteBackground = true;
  public List<Panel> panels = new ArrayList<>();
  public List<String> rowLabels = new ArrayList<>(), columnLabels = new ArrayList<>();

  public static final class Panel {
    public FigureConfiguration image;
    public String label = "";
    public boolean showName;
  }

  public FreeBuildConfiguration(int rows, int columns) {
    if (rows < 1 || columns < 1 || rows > 20 || columns > 20)
      throw new IllegalArgumentException("Rows and columns must be between 1 and 20.");
    this.rows = rows; this.columns = columns;
    for (int i = 0; i < rows * columns; i++) panels.add(new Panel());
    for (int i = 0; i < rows; i++) rowLabels.add("");
    for (int i = 0; i < columns; i++) columnLabels.add("");
  }

  public static FigureConfiguration image(InputImageManager.Source s, Integer[] ids, InputImageManager inputs) {
    if (ids.length == 0) throw new IllegalArgumentException("Select at least one channel.");
    FigureConfiguration c = new FigureConfiguration();
    c.conditions.add(new ConditionConfig("", s.id));
    for (int id : ids) {
      if (id < 1 || id > s.channels) throw new IllegalArgumentException("Invalid source channel.");
      ChannelConfig.Lut lut = s.channels == 1 ? ChannelConfig.Lut.Grayscale
          : new ChannelConfig.Lut[] {ChannelConfig.Lut.Green, ChannelConfig.Lut.Red, ChannelConfig.Lut.Blue}[(id-1)%3];
      ChannelConfig ch = new ChannelConfig(id, "Channel " + id, lut);
      double[] range = new ImageRenderer().range(inputs, c, id);
      ch.min = range[0]; ch.max = range[1];
      c.channels.add(ch);
    }
    c.displayChannels.add(new DisplayChannel("", ids.length > 1, ids));
    AppearanceDefaults.initialize(c, s);
    c.scaleBar.show = false;
    return c;
  }

  /** Copies display tone by source channel number; unmatched channels and empty slots stay unchanged. */
  public int applyTone(int selected, boolean row) {
    FigureConfiguration from = panels.get(selected).image;
    if (from == null) return 0;
    int count = 0;
    for (int i = 0; i < panels.size(); i++) {
      if (i == selected || (row ? i / columns != selected / columns : i % columns != selected % columns)) continue;
      FigureConfiguration to = panels.get(i).image;
      if (to == null) continue;
      boolean copied = false;
      for (ChannelConfig a : from.channels) for (ChannelConfig b : to.channels) if (a.index == b.index) {
        b.min = a.min; b.max = a.max; b.lut = a.lut; b.invert = a.invert; copied = true;
      }
      if (copied) count++;
    }
    return count;
  }

  public void validate(InputImageManager inputs) {
    if (rows < 1 || columns < 1 || rows > 20 || columns > 20 || panels == null
        || panels.size() != rows * columns || rowLabels.size() != rows || columnLabels.size() != columns
        || cellWidth < 16 || cellHeight < 16 || cellWidth > 8192 || cellHeight > 8192
        || gap < 0 || gap > 4096 || fontSize < 1 || fontSize > 4096)
      throw new IllegalArgumentException("Invalid free-build grid.");
    for (Panel p : panels) {
      if (p == null || p.label == null) throw new IllegalArgumentException("Invalid panel.");
      if (p.image != null) {
        p.image.validate(inputs);
        if (p.image.conditions.size() != 1 || p.image.displayChannels.size() != 1)
          throw new IllegalArgumentException("A free-build panel must contain one image display.");
      }
    }
    Dimension d = dimensions();
    if ((long)d.width * d.height > 100000000L) throw new IllegalArgumentException("Figure exceeds 100 million pixels.");
  }

  private boolean any(List<String> labels) { for (String s : labels) if (s != null && !s.isEmpty()) return true; return false; }
  public int leftBand() { return any(rowLabels) ? fontSize + 16 : 0; }
  public int topBand() { return any(columnLabels) ? fontSize + 16 : 0; }
  public int labelBand() { for (Panel p : panels) if (!p.label.isEmpty()) return fontSize + 12; return 0; }
  public Rectangle bounds(int index) {
    return new Rectangle(leftBand() + index % columns * (cellWidth + gap),
        topBand() + index / columns * (cellHeight + labelBand() + gap), cellWidth, cellHeight);
  }
  public Dimension dimensions() {
    return new Dimension(leftBand() + columns * cellWidth + (columns - 1) * gap,
        topBand() + rows * (cellHeight + labelBand()) + (rows - 1) * gap);
  }

  public static final class Item {
    public BufferedImage image;
    public String text;
    public Rectangle bounds;
    public int rotation;
    Item(BufferedImage image, String text, Rectangle bounds, int rotation) {
      this.image = image; this.text = text; this.bounds = bounds; this.rotation = rotation;
    }
  }

  /** Output scene has no placeholders, selection borders or plus icons. */
  public List<Item> scene(InputImageManager inputs, double scale) {
    validate(inputs);
    List<Item> items = new ArrayList<>();
    for (int n = 0; n < panels.size(); n++) {
      Panel p = panels.get(n); Rectangle b = bounds(n);
      if (p.image == null) continue;
      InputImageManager.Source source = inputs.get(p.image.conditions.get(0).sourceId);
      if (p.showName) items.add(new Item(null, source.title, b, 0));
      else {
        double fit = Math.min((double)cellWidth / source.width, (double)cellHeight / source.height);
        int w = Math.max(1, (int)Math.round(source.width * fit)), h = Math.max(1, (int)Math.round(source.height * fit));
        // Render with the original calibration before fitting into the slot; preserve aspect ratio.
        BufferedImage image = new PanelLayoutEngine().render(p.image, inputs, Math.min(1, fit * scale));
        items.add(new Item(image, null, new Rectangle(b.x + (b.width-w)/2, b.y + (b.height-h)/2, w, h), 0));
      }
      if (!p.label.isEmpty()) items.add(new Item(null, p.label, new Rectangle(b.x, b.y+b.height, b.width, labelBand()), 0));
    }
    for (int r = 0; r < rows; r++) if (!rowLabels.get(r).isEmpty()) {
      Rectangle b = bounds(r * columns);
      items.add(new Item(null, rowLabels.get(r), new Rectangle(0, b.y, leftBand(), b.height), -90));
    }
    for (int c = 0; c < columns; c++) if (!columnLabels.get(c).isEmpty()) {
      Rectangle b = bounds(c);
      items.add(new Item(null, columnLabels.get(c), new Rectangle(b.x, 0, b.width, topBand()), 0));
    }
    return items;
  }

  public BufferedImage render(InputImageManager inputs, double scale) {
    if (!(scale > 0 && scale <= 1)) throw new IllegalArgumentException("Invalid preview scale.");
    List<Item> items = scene(inputs, scale);
    Dimension d = dimensions();
    BufferedImage result = new BufferedImage(Math.max(1,(int)Math.ceil(d.width*scale)), Math.max(1,(int)Math.ceil(d.height*scale)), BufferedImage.TYPE_INT_RGB);
    Graphics2D g = result.createGraphics();
    try {
      g.scale(scale, scale); g.setColor(whiteBackground ? Color.WHITE : Color.BLACK); g.fillRect(0,0,d.width,d.height);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(whiteBackground ? Color.BLACK : Color.WHITE);
      for (Item item : items) {
        Rectangle b = item.bounds;
        if (item.image != null) g.drawImage(item.image,b.x,b.y,b.width,b.height,null);
        else {
          Graphics2D text = (Graphics2D)g.create();
          try {
            text.translate(b.getCenterX(),b.getCenterY()); text.rotate(Math.toRadians(item.rotation));
            int width = item.rotation == 0 ? b.width : b.height;
            FontMetrics fm = new LabelRenderer().fitFont(text,fontSize,item.text,width-8);
            text.drawString(item.text,-fm.stringWidth(item.text)/2,(fm.getAscent()-fm.getDescent())/2);
          } finally { text.dispose(); }
        }
      }
    } finally { g.dispose(); }
    return result;
  }
}
