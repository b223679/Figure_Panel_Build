package org.microscopy.figure;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.*;

class ExportTest {
  @TempDir Path temp;
  @Test void transparentPixelsAndLabelOnlyContrast() throws Exception {
    InputImageManager inputs=new InputImageManager();
    FigureConfiguration c=CoreTest.config(inputs,1000,2000);
    c.horizontalGap=5; c.labels.transparentBackground=true;
    BufferedImage image=new PanelLayoutEngine().render(c,inputs);
    assertEquals(0,image.getRGB(9,0)>>>24);
    assertEquals(255,image.getRGB(0,0)>>>24);
    File png=temp.resolve("alpha.png").toFile(); ImageIO.write(image,"png",png);
    assertEquals(0,ImageIO.read(png).getRGB(9,0)>>>24);
    int original=image.getRGB(0,0);
    c.labels.transparentBackground=false; c.labels.whiteBackground=true;
    assertEquals(original,new PanelLayoutEngine().render(c,inputs).getRGB(0,0));
    assertEquals(0x008000,LabelColors.channel(ChannelConfig.Lut.Green,c.labels,true).getRGB()&0xffffff);
    assertEquals(0x008080,LabelColors.channel(ChannelConfig.Lut.Cyan,c.labels,true).getRGB()&0xffffff);
    assertEquals(0x808000,LabelColors.channel(ChannelConfig.Lut.Yellow,c.labels,true).getRGB()&0xffffff);
    c.labels.whiteBackground=false;
    assertEquals(0x6666ff,LabelColors.channel(ChannelConfig.Lut.Blue,c.labels,true).getRGB()&0xffffff);
    assertEquals(0x0000ff,ImageRenderer.lutRgb(ChannelConfig.Lut.Blue,255));
    c.labels.transparentBackground=true;
    assertTrue(new SettingsSerializer().copy(c).labels.transparentBackground);
  }
  private Document parse(ZipFile zip,String part) throws Exception {
    DocumentBuilderFactory f=DocumentBuilderFactory.newInstance(); f.setNamespaceAware(true);
    f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
    try(InputStream in=zip.getInputStream(zip.getEntry(part))) { return f.newDocumentBuilder().parse(in); }
  }
  @Test void pptxHasSeparateFullResolutionPicturesTextAndScaleShapes() throws Exception {
    InputImageManager inputs=new InputImageManager();
    ij.ImageStack stack=new ij.ImageStack(512,512);
    stack.addSlice(new ij.process.ShortProcessor(512,512));
    stack.addSlice(new ij.process.ShortProcessor(512,512));
    ij.ImagePlus source=new ij.ImagePlus("Export",stack); source.setDimensions(2,1,1);
    source.getCalibration().setUnit("µm"); source.getCalibration().pixelWidth=.25;
    InputImageManager.Source s=inputs.snapshot(source,null);
    FigureConfiguration c=new FigureConfiguration();
    c.conditions.add(new ConditionConfig("Control & <test> 日本語",s.id));
    c.channels.add(new ChannelConfig(1,"DNA",ChannelConfig.Lut.Blue));
    c.channels.add(new ChannelConfig(2,"Marker",ChannelConfig.Lut.Green));
    c.displayChannels.add(new DisplayChannel("DNA",false,1));
    c.displayChannels.add(new DisplayChannel("Merge",true,1,2));
    c.labels.showRows=c.labels.showColumns=true;
    c.scaleBar.show=true; AppearanceDefaults.initialize(c,s);
    File pptx=temp.resolve("figure.pptx").toFile();
    new PptxExporter().save(pptx,c,inputs);
    try(ZipFile z=new ZipFile(pptx)) {
      String p="http://schemas.openxmlformats.org/presentationml/2006/main";
      String a="http://schemas.openxmlformats.org/drawingml/2006/main";
      Document slide=parse(z,"ppt/slides/slide1.xml");
      assertEquals(2,slide.getElementsByTagNameNS(p,"pic").getLength());
      assertEquals(7,slide.getElementsByTagNameNS(p,"sp").getLength());
      assertTrue(slide.getDocumentElement().getTextContent().contains("Control & <test> 日本語"));
      assertTrue(slide.getDocumentElement().getTextContent().contains("DNA/Marker"));
      assertTrue(slide.getDocumentElement().getTextContent().contains("20 µm"));
      boolean blue=false;
      NodeList colors=slide.getElementsByTagNameNS(a,"srgbClr");
      for(int i=0;i<colors.getLength();i++) blue|=((Element)colors.item(i)).getAttribute("val").equals("6666FF");
      assertTrue(blue);
      try(InputStream png=z.getInputStream(z.getEntry("ppt/media/figure1.png"))) {
        BufferedImage cell=ImageIO.read(png); assertEquals(512,cell.getWidth());
        assertEquals(0,cell.getRGB(490,470)&0xffffff); // scale is not baked into the pixels
      }
      java.util.Enumeration<? extends ZipEntry> entries=z.entries();
      while(entries.hasMoreElements()) { String name=entries.nextElement().getName(); if(name.endsWith(".xml")||name.endsWith(".rels")) parse(z,name); }
    }
    c.labels.transparentBackground=true; c.rowsAreChannels=false;
    new PptxExporter().save(pptx,c,inputs);
    try(ZipFile z=new ZipFile(pptx)) {
      assertEquals(0,parse(z,"ppt/slides/slide1.xml").getElementsByTagNameNS("http://schemas.openxmlformats.org/presentationml/2006/main","bg").getLength());
    }
  }
}
