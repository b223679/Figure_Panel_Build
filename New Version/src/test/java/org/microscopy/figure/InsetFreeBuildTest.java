package org.microscopy.figure;

import static org.junit.jupiter.api.Assertions.*;
import ij.ImagePlus;
import ij.process.ByteProcessor;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InsetFreeBuildTest {
  @TempDir Path temp;
  private InputImageManager.Source source(InputImageManager inputs, int width, int height) {
    byte[] pixels = new byte[width*height];
    for(int y=0;y<height;y++) for(int x=0;x<width;x++) pixels[y*width+x]=(byte)(x*255/width);
    return inputs.snapshot(new ImagePlus("gradient.tif",new ByteProcessor(width,height,pixels,null)),null);
  }
  @Test void insetDefaultsAspectAndCustomSize() {
    InsetConfig c = new InsetConfig(); assertFalse(new InsetCell().enabled); assertTrue(c.sameAsRoi);
    c.roiWidth=20;c.roiHeight=10;c.magnification=2.5;
    assertEquals(50,c.boxWidth());assertEquals(25,c.boxHeight());
    c.sameAsRoi=false;c.insetWidth=70;c.insetHeight=90;
    assertEquals(70,c.boxWidth());assertEquals(90,c.boxHeight());
    c.magnification=Double.NaN;assertThrows(IllegalArgumentException.class,c::validate);
  }
  @Test void roiStrokeScalesWithImageAndNewLocationIsTopRight() {
    InputImageManager inputs=new InputImageManager();
    FigureConfiguration c=new FigureConfiguration();
    AppearanceDefaults.initialize(c,source(inputs,1024,512));
    assertEquals(5,c.inset.roiStrokeWidth);assertEquals(c.inset.insetStrokeWidth,c.inset.roiStrokeWidth);
    assertEquals(InsetConfig.Position.TOP_RIGHT,c.inset.position);
    c.inset.roiStrokeWidth=9;AppearanceDefaults.initialize(c,source(inputs,2048,1024));
    assertEquals(9,c.inset.roiStrokeWidth,"Manual stroke is preserved");
  }
  @Test void sharedConditionRoiTracksMovesAndPreservesIndependentVisibility() throws Exception {
    SwingUtilities.invokeAndWait(()->{
      InputImageManager inputs=new InputImageManager();InputImageManager.Source source=source(inputs,200,200);
      FigureConfiguration c=FreeBuildConfiguration.image(source,new Integer[]{1},inputs);
      c.displayChannels.add(new DisplayChannel("second",false,1));
      c.conditions.add(new ConditionConfig("Other condition",source.id));
      InsetPanel panel=new InsetPanel(c,inputs,()->{});panel.selectCell(0,0);
      ((JCheckBox)find(panel,"insetEnabled")).doClick();
      ((JCheckBox)find(panel,"shareInsetRoi")).doClick();
      press(panel,"Right");
      assertEquals(81,c.insetCell(0,0).x);assertEquals(81,c.insetCell(0,1).x);
      assertFalse(c.insetCell(0,1).enabled);assertNull(c.insetCell(1,0).x);
      panel.selectCell(0,1);((JCheckBox)find(panel,"insetEnabled")).doClick();press(panel,"Right");
      assertEquals(82,c.insetCell(0,0).x);assertTrue(c.insetCell(0,1).enabled);
      FigureConfiguration copy=new SettingsSerializer().copy(c);assertEquals(82,copy.insetCell(0,1).x);
      ((JCheckBox)find(panel,"shareInsetRoi")).doClick();press(panel,"Right");
      assertEquals(82,c.insetCell(0,0).x);assertEquals(83,c.insetCell(0,1).x);
    });
  }
  private static boolean press(Container p,String title) {
    for(Component c:p.getComponents()) {
      if(c instanceof AbstractButton && title.equals(((AbstractButton)c).getText())) {((AbstractButton)c).doClick();return true;}
      if(c instanceof Container && press((Container)c,title))return true;
    }return false;
  }
  @Test void pptxKeepsInsetAndRoiSeparateFromBaseImageInBothModes() throws Exception {
    InputImageManager inputs=new InputImageManager();InputImageManager.Source source=source(inputs,200,200);
    FigureConfiguration c=FreeBuildConfiguration.image(source,new Integer[]{1},inputs);
    InsetCell inset=new InsetCell();inset.enabled=true;inset.x=10;inset.y=10;c.conditions.get(0).insets.add(inset);
    c.inset.shape=InsetConfig.Shape.CIRCLE;
    Path normal=temp.resolve("inset.pptx");new PptxExporter().save(normal.toFile(),c,inputs);
    try(ZipFile zip=new ZipFile(normal.toFile())) {
      String slide=read(zip,"ppt/slides/slide1.xml");
      assertEquals(2,slide.split("<p:pic>",-1).length-1);
      assertTrue(slide.contains("Inset — movable crop"));assertTrue(slide.contains("Inset ROI outline"));assertTrue(slide.contains("prst=\"ellipse\""));
      BufferedImage base=javax.imageio.ImageIO.read(zip.getInputStream(zip.getEntry("ppt/media/figure1.png")));
      BufferedImage expected=new ImageRenderer().render(source,c.displayChannels.get(0),c);
      for(int y=0;y<200;y++)for(int x=0;x<200;x++)assertEquals(expected.getRGB(x,y),base.getRGB(x,y),"Base must not contain baked-in inset or ROI");
    }
    FreeBuildConfiguration free=new FreeBuildConfiguration(1,1);free.cellWidth=100;free.cellHeight=100;free.panels.get(0).image=c;
    Path file=temp.resolve("free-inset.pptx");new PptxExporter().saveFree(file.toFile(),free,inputs);
    try(ZipFile zip=new ZipFile(file.toFile())) {
      String slide=read(zip,"ppt/slides/slide1.xml");assertEquals(2,slide.split("<p:pic>",-1).length-1);
      assertTrue(slide.contains("Inset ROI outline"));
      BufferedImage base=javax.imageio.ImageIO.read(zip.getInputStream(zip.getEntry("ppt/media/free1.png")));
      FigureConfiguration disabled=new SettingsSerializer().copy(c);disabled.conditions.get(0).insets.clear();
      BufferedImage expected=new PanelLayoutEngine().render(disabled,inputs,0.5);
      for(int y=0;y<100;y++)for(int x=0;x<100;x++)assertEquals(expected.getRGB(x,y),base.getRGB(x,y));
    }
  }
  @Test void circularInsetClipsCropAndOffIsInvisible() {
    InputImageManager inputs=new InputImageManager(); InputImageManager.Source source=source(inputs,200,200);
    FigureConfiguration c=FreeBuildConfiguration.image(source,new Integer[]{1},inputs);
    c.inset.roiWidth=20;c.inset.roiHeight=10;c.inset.magnification=3;c.inset.shape=InsetConfig.Shape.CIRCLE;
    c.inset.insetStrokeWidth=0;c.inset.roiStrokeWidth=0;
    InsetCell cell=new InsetCell();cell.x=10;cell.y=10;c.conditions.get(0).insets.add(cell);
    BufferedImage original=new PanelLayoutEngine().render(c,inputs);
    assertFalse(new InsetRenderer().applies(cell,source,c.inset));cell.enabled=true;
    BufferedImage out=new PanelLayoutEngine().render(c,inputs);Point p=InsetRenderer.boxOrigin(c.inset,200,200);
    assertEquals(original.getRGB(p.x,p.y),out.getRGB(p.x,p.y),"Ellipse corner retains base image");
    assertNotEquals(original.getRGB(p.x+30,p.y+15),out.getRGB(p.x+30,p.y+15),"Ellipse center contains enlarged ROI");
    c.inset.sameAsRoi=false;c.inset.insetWidth=60;c.inset.insetHeight=30;
    BufferedImage rectangular=new PanelLayoutEngine().render(c,inputs);
    assertNotEquals(original.getRGB(p.x,p.y),rectangular.getRGB(p.x,p.y));
  }
  @Test void insetControlsExpandOnlyWhenEnabled() throws Exception {
    SwingUtilities.invokeAndWait(()->{
      InputImageManager inputs=new InputImageManager(); FigureConfiguration c=FreeBuildConfiguration.image(source(inputs,200,200),new Integer[]{1},inputs);
      InsetPanel panel=new InsetPanel(c,inputs,()->{});panel.selectCell(0,0);
      JCheckBox enable=(JCheckBox)find(panel,"insetEnabled");
      JComponent zoom=(JComponent)find(panel,"insetMagnification");
      assertFalse(enable.isSelected());assertFalse(ancestorsVisible(zoom,panel));
      enable.doClick();assertTrue(c.insetCell(0,0).enabled);assertTrue(ancestorsVisible(zoom,panel));
      ((JComboBox<?>)find(panel,"insetSizeMode")).setSelectedIndex(1);
      assertFalse(ancestorsVisible(zoom,panel));assertTrue(ancestorsVisible(find(panel,"insetBoxWidth"),panel));
      enable.doClick();assertFalse(c.insetCell(0,0).enabled);
    });
  }
  private static Component find(Container p,String name) {
    for(Component c:p.getComponents()) {if(name.equals(c.getName()))return c;if(c instanceof Container){Component result=find((Container)c,name);if(result!=null)return result;}}return null;
  }
  private static boolean ancestorsVisible(Component c,Component stop) {for(;c!=stop;c=c.getParent())if(!c.isVisible())return false;return true;}

  @Test void independentToneAndExplicitAxisCopy() {
    InputImageManager inputs=new InputImageManager();InputImageManager.Source s=source(inputs,100,80);
    FreeBuildConfiguration c=new FreeBuildConfiguration(2,2);
    for(int n=0;n<4;n++)c.panels.get(n).image=FreeBuildConfiguration.image(s,new Integer[]{1},inputs);
    ChannelConfig first=c.panels.get(0).image.channel(1);first.min=10;first.max=90;first.lut=ChannelConfig.Lut.Red;first.invert=true;
    assertNotEquals(10,c.panels.get(1).image.channel(1).min);
    assertEquals(1,c.applyTone(0,true));assertEquals(10,c.panels.get(1).image.channel(1).min);
    assertNotEquals(10,c.panels.get(2).image.channel(1).min);
    assertEquals(1,c.applyTone(0,false));assertEquals(ChannelConfig.Lut.Red,c.panels.get(2).image.channel(1).lut);
    assertNotEquals(10,c.panels.get(3).image.channel(1).min);
    first.min=20;assertEquals(10,c.panels.get(1).image.channel(1).min,"Copied values are not shared mutable objects");
  }
  @Test void mixedSizesAndEmptySlotsExportWithoutPlaceholders() throws Exception {
    InputImageManager inputs=new InputImageManager();FreeBuildConfiguration c=new FreeBuildConfiguration(2,2);c.cellWidth=100;c.cellHeight=100;
    c.panels.get(0).image=FreeBuildConfiguration.image(source(inputs,160,80),new Integer[]{1},inputs);
    c.panels.get(1).image=FreeBuildConfiguration.image(source(inputs,60,120),new Integer[]{1},inputs);c.panels.get(1).showName=true;
    c.panels.get(0).label="Manual label";c.rowLabels.set(0,"Row A");c.columnLabels.set(0,"Col A");
    BufferedImage out=c.render(inputs,1);Rectangle blank=c.bounds(3);
    for(int y=blank.y;y<blank.y+blank.height;y++)for(int x=blank.x;x<blank.x+blank.width;x++)assertEquals(0xffffff,out.getRGB(x,y)&0xffffff);
    Path pptx=temp.resolve("free.pptx");new PptxExporter().saveFree(pptx.toFile(),c,inputs);
    try(ZipFile zip=new ZipFile(pptx.toFile())) {
      String slide=read(zip,"ppt/slides/slide1.xml");
      assertEquals(1,slide.split("<p:pic>",-1).length-1);
      assertTrue(slide.contains("Manual label"));assertTrue(slide.contains("gradient.tif"));
      assertTrue(slide.contains("Row A"));assertFalse(slide.contains("<a:t xml:space=\"preserve\">+"));
    }
  }
  private static String read(ZipFile zip,String path) throws IOException {
    try(InputStream in=zip.getInputStream(zip.getEntry(path));ByteArrayOutputStream out=new ByteArrayOutputStream()) {
      byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return new String(out.toByteArray(),StandardCharsets.UTF_8);
    }
  }
  @Test void settingsPreservePerPanelToneNamesInsetAndEmptySlots() throws Exception {
    InputImageManager inputs=new InputImageManager();InputImageManager.Source s=inputs.load(new File("test-data/Control.tif"));
    FreeBuildConfiguration c=new FreeBuildConfiguration(2,3);
    c.panels.get(0).image=FreeBuildConfiguration.image(s,new Integer[]{1,2},inputs);c.panels.get(0).image.channel(1).max=123;
    c.panels.get(0).image.conditions.get(0).insets.add(new InsetCell());c.panels.get(0).image.insetCell(0,0).enabled=true;
    c.panels.get(0).image.inset.magnification=2;c.panels.get(0).label="Manual";c.rowLabels.set(1,"Second");
    Path file=temp.resolve("free.json");FreeBuildSettings.save(file.toFile(),c,inputs);
    FreeBuildSettings.Loaded loaded=FreeBuildSettings.load(file.toFile());
    assertEquals(123,loaded.configuration.panels.get(0).image.channel(1).max);
    assertTrue(loaded.configuration.panels.get(0).image.insetCell(0,0).enabled);
    assertNull(loaded.configuration.panels.get(5).image);assertEquals("Second",loaded.configuration.rowLabels.get(1));
    assertThrows(IllegalArgumentException.class,()->FreeBuildSettings.save(new File(s.path),c,inputs));
  }
  @Test void oldInsetSettingsKeepExplicitDimensions() throws Exception {
    InputImageManager inputs=new InputImageManager();InputImageManager.Source s=inputs.load(new File("test-data/Control.tif"));
    FigureConfiguration c=FreeBuildConfiguration.image(s,new Integer[]{1},inputs);c.inset.insetWidth=73;
    Path file=temp.resolve("legacy.json");new SettingsSerializer().save(file.toFile(),c,inputs);
    String json=new String(Files.readAllBytes(file),StandardCharsets.UTF_8).replaceAll("(?m)^.*\\\"sameAsRoi\\\".*\\R","");
    Files.write(file,json.getBytes(StandardCharsets.UTF_8));
    FigureConfiguration loaded=new SettingsSerializer().load(file.toFile()).configuration;
    assertFalse(loaded.inset.sameAsRoi);assertEquals(73,loaded.inset.boxWidth());
  }
}
