package org.microscopy.figure;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;
import java.util.zip.*;
import javax.imageio.ImageIO;

/** Offline OOXML export: full-resolution pictures and editable text/scale shapes. */
public final class PptxExporter {
  private double factor;
  private int id;
  private final StringBuilder shapes = new StringBuilder();
  private final StringBuilder relationships = new StringBuilder();
  private long emu(double px) { return Math.round(px * factor); }
  private static String xml(String s) {
    return s.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "")
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
  private static String hex(Color c) { return String.format(Locale.ROOT, "%06X", c.getRGB() & 0xffffff); }
  private String transform(double x, double y, double w, double h, int rotation) {
    return "<a:xfrm rot=\"" + rotation + "\"><a:off x=\"" + emu(x) + "\" y=\"" + emu(y)
        + "\"/><a:ext cx=\"" + emu(w) + "\" cy=\"" + emu(h) + "\"/></a:xfrm>";
  }
  private String run(String text, Color color, float size) {
    int points = Math.max(100, (int)Math.round(size * factor / 127.0));
    return "<a:r><a:rPr lang=\"ja-JP\" sz=\"" + points + "\"><a:solidFill><a:srgbClr val=\""
        + hex(color) + "\"/></a:solidFill><a:latin typeface=\"Arial\"/><a:ea typeface=\"Yu Gothic\"/></a:rPr><a:t xml:space=\"preserve\">"
        + xml(text) + "</a:t></a:r>";
  }
  private void text(String name, String runs, double x, double y, double w, double h, int rot) {
    shapes.append("<p:sp><p:nvSpPr><p:cNvPr id=\"").append(++id).append("\" name=\"").append(xml(name))
        .append("\"/><p:cNvSpPr txBox=\"1\"/><p:nvPr/></p:nvSpPr><p:spPr>")
        .append(transform(x,y,w,h,rot)).append("<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom><a:noFill/><a:ln><a:noFill/></a:ln></p:spPr>")
        .append("<p:txBody><a:bodyPr wrap=\"none\" lIns=\"0\" rIns=\"0\" tIns=\"0\" bIns=\"0\" anchor=\"ctr\"><a:noAutofit/></a:bodyPr><a:lstStyle/><a:p><a:pPr algn=\"ctr\"/>")
        .append(runs).append("</a:p></p:txBody></p:sp>");
  }
  private void label(FigureConfiguration c, DisplayChannel d, String value, boolean row,
      Graphics2D g, double x, double y, int w, int h, int rotation) {
    new LabelRenderer().fitFont(g, row ? c.labels.rowFontSize : c.labels.columnFontSize, value, w);
    StringBuilder runs = new StringBuilder();
    if (d == null) runs.append(run(value, LabelColors.neutral(c.labels,row), g.getFont().getSize2D()));
    else for (int i=0;i<d.channels.size();i++) {
      if(i>0) runs.append(run("/", LabelColors.neutral(c.labels,row),g.getFont().getSize2D()));
      ChannelConfig ch=c.channel(d.channels.get(i));
      runs.append(run(ch.label,LabelColors.channel(ch.lut,c.labels,row),g.getFont().getSize2D()));
    }
    text((row?"Row label: ":"Column label: ")+value,runs.toString(),x,y,w,h,rotation);
  }
  private void scale(FigureConfiguration c, InputImageManager.Source source, Graphics2D g, int ox, int oy) {
    ScaleBarConfig s=c.scaleBar;
    int length=new ScaleBarRenderer().lengthPixels(source,s);
    g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,s.fontSize));
    FontMetrics fm=g.getFontMetrics();
    String value=java.math.BigDecimal.valueOf(s.lengthUm).stripTrailingZeros().toPlainString()+" µm";
    int th=s.showText?fm.getHeight()+3:0, bw=Math.max(length,s.showText?fm.stringWidth(value):0), bh=s.thickness+th;
    if (bw+2L*s.marginX>source.width || bh+2L*s.marginY>source.height)
      throw new IllegalArgumentException("Scale bar/text does not fit cell: "+source.title);
    boolean right=s.position==ScaleBarConfig.Position.TOP_RIGHT || s.position==ScaleBarConfig.Position.BOTTOM_RIGHT;
    boolean bottom=s.position==ScaleBarConfig.Position.BOTTOM_LEFT || s.position==ScaleBarConfig.Position.BOTTOM_RIGHT;
    int x=ox+(right?source.width-s.marginX-bw:s.marginX), y=oy+(bottom?source.height-s.marginY-bh:s.marginY);
    Color color=s.white?Color.WHITE:Color.BLACK;
    shapes.append("<p:sp><p:nvSpPr><p:cNvPr id=\"").append(++id).append("\" name=\"Scale bar\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr>")
        .append(transform(x+(bw-length)/2,y+th,length,s.thickness,0))
        .append("<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom><a:solidFill><a:srgbClr val=\"").append(hex(color))
        .append("\"/></a:solidFill><a:ln><a:noFill/></a:ln></p:spPr></p:sp>");
    if(s.showText) text("Scale text",run(value,color,s.fontSize),x,y,bw,fm.getHeight(),0);
  }
  private static void entry(ZipOutputStream zip,String name,byte[] bytes) throws IOException {
    zip.putNextEntry(new ZipEntry(name)); zip.write(bytes); zip.closeEntry();
  }
  public void save(File destination, FigureConfiguration c, InputImageManager inputs) throws IOException {
    OutputSafety.checkDestination(destination,inputs);
    Dimension dim=new PanelLayoutEngine().dimensions(c,inputs);
    factor=12192000.0/Math.max(dim.width,dim.height); id=1; shapes.setLength(0); relationships.setLength(0);
    Path temp=Files.createTempFile(destination.toPath().toAbsolutePath().getParent(),"figure-",".pptx.tmp");
    Graphics2D g=new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB).createGraphics();
    try {
      try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(temp))) {
        InputImageManager.Source ref=inputs.get(c.conditions.get(0).sourceId);
        LabelRenderer labels=new LabelRenderer(); LabelConfig l=c.labels;
        int x0=l.rowRight?0:labels.rowBand(l), y0=l.columnBottom?0:labels.columnBand(l);
        int picture=0;
        for(int ci=0;ci<c.conditions.size();ci++) for(int di=0;di<c.displayChannels.size();di++) {
          int row=c.rowsAreChannels?di:ci, col=c.rowsAreChannels?ci:di;
          int x=x0+col*(ref.width+c.horizontalGap), y=y0+row*(ref.height+c.verticalGap);
          InputImageManager.Source source=inputs.get(c.conditions.get(ci).sourceId);
          String rel="image"+(++picture), media="figure"+picture+".png";
          ByteArrayOutputStream png=new ByteArrayOutputStream();
          ImageIO.write(new ImageRenderer().render(source,c.displayChannels.get(di),c),"png",png);
          entry(out,"ppt/media/"+media,png.toByteArray());
          relationships.append("<Relationship Id=\"").append(rel).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"../media/").append(media).append("\"/>");
          shapes.append("<p:pic><p:nvPicPr><p:cNvPr id=\"").append(++id).append("\" name=\"")
              .append(xml(c.conditions.get(ci).label+" / "+c.displayLabel(c.displayChannels.get(di))))
              .append("\"/><p:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></p:cNvPicPr><p:nvPr/></p:nvPicPr><p:blipFill><a:blip r:embed=\"")
              .append(rel).append("\"/><a:stretch><a:fillRect/></a:stretch></p:blipFill><p:spPr>")
              .append(transform(x,y,ref.width,ref.height,0)).append("<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr></p:pic>");
          if(new ScaleBarRenderer().applies(c.scaleBar,c,ci,di,row,col)) scale(c,source,g,x,y);
        }
        if(l.showColumns) for(int col=0;col<c.columns();col++) {
          DisplayChannel d=c.rowsAreChannels?null:c.displayChannels.get(col);
          String value=d==null?c.conditions.get(col).label:c.displayLabel(d);
          FontMetrics fm=labels.fitFont(g,l.columnFontSize,value,ref.width);
          int y=l.columnBottom?dim.height-labels.columnBand(l)+l.columnMargin:4;
          label(c,d,value,false,g,x0+col*(ref.width+c.horizontalGap),y,ref.width,fm.getHeight(),0);
        }
        if(l.showRows) for(int row=0;row<c.rows();row++) {
          DisplayChannel d=c.rowsAreChannels?c.displayChannels.get(row):null;
          String value=d==null?c.conditions.get(row).label:c.displayLabel(d);
          FontMetrics fm=labels.fitFont(g,l.rowFontSize,value,ref.height);
          double cx=l.rowRight?dim.width-(l.rowFontSize+8)/2.0:(l.rowFontSize+8)/2.0;
          double cy=y0+row*(ref.height+c.verticalGap)+ref.height/2.0;
          label(c,d,value,true,g,cx-ref.height/2.0,cy-fm.getHeight()/2.0,ref.height,fm.getHeight(),l.rowRight?5400000:16200000);
        }
        String slide="<?xml version=\"1.0\" encoding=\"UTF-8\"?><p:sld xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><p:cSld>"
            +(l.transparentBackground?"":"<p:bg><p:bgPr><a:solidFill><a:srgbClr val=\""+(l.whiteBackground?"FFFFFF":"000000")+"\"/></a:solidFill><a:effectLst/></p:bgPr></p:bg>")
            +"<p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>"+shapes+"</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>";
        try(InputStream resource=PptxExporter.class.getResourceAsStream("blank.pptx")) {
          if(resource==null) throw new IOException("Missing PPTX template.");
          try(ZipInputStream template=new ZipInputStream(resource)) {
            ZipEntry e; byte[] buffer=new byte[8192];
            while((e=template.getNextEntry())!=null) {
              ByteArrayOutputStream bytes=new ByteArrayOutputStream(); int n;
              while((n=template.read(buffer))!=-1) bytes.write(buffer,0,n);
              byte[] data=bytes.toByteArray(); String name=e.getName();
              if(name.equals("ppt/slides/slide1.xml")) data=slide.getBytes(StandardCharsets.UTF_8);
              else if(name.equals("ppt/slides/_rels/slide1.xml.rels")) data=new String(data,StandardCharsets.UTF_8).replace("</Relationships>",relationships+"</Relationships>").getBytes(StandardCharsets.UTF_8);
              else if(name.equals("ppt/presentation.xml")) data=new String(data,StandardCharsets.UTF_8).replaceAll("<p:sldSz[^>]*/>","<p:sldSz cx=\""+emu(dim.width)+"\" cy=\""+emu(dim.height)+"\"/>").getBytes(StandardCharsets.UTF_8);
              else if(name.equals("[Content_Types].xml")) data=new String(data,StandardCharsets.UTF_8).replace("</Types>","<Default Extension=\"png\" ContentType=\"image/png\"/></Types>").getBytes(StandardCharsets.UTF_8);
              entry(out,name,data);
            }
          }
        }
      }
      Files.move(temp,destination.toPath(),StandardCopyOption.REPLACE_EXISTING);
    } finally { g.dispose(); Files.deleteIfExists(temp); }
  }
}
