package org.microscopy.figure;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Narrow right-pane regression at both inset size modes, using an isolated test window. */
public class InsetUiValidation {
  public static void main(String[] args) throws Exception {
    Path output=Files.createTempDirectory(Paths.get("artifacts"),"inset-ui-");
    final FigurePanelBuilderDialog[] window={null};
    try {
      SwingUtilities.invokeAndWait(()->{
        FigurePanelBuilderDialog dialog=new FigurePanelBuilderDialog(false);window[0]=dialog;
        dialog.setExtendedState(JFrame.NORMAL);dialog.setSize(1280,800);dialog.setVisible(true);
        call(dialog,"addFiles",new Class<?>[]{java.util.List.class},java.util.Arrays.asList(new File("test-data/Image A.tif")));
        FigureConfiguration c=(FigureConfiguration)FreeBuildUiValidation.field(dialog,"config");
        c.displayChannels.add(new DisplayChannel("Second",false,2));call(dialog,"refresh",new Class<?>[0]);
        call(dialog,"selectDisplay",new Class<?>[]{int.class,int.class},0,0);
        ((JTabbedPane)FreeBuildUiValidation.field(dialog,"tabs")).setSelectedIndex(2);
        FreeBuildUiValidation.button(dialog,"Show inset").doClick();
        FreeBuildUiValidation.button(dialog,"Share ROI within this condition").doClick();
      });
      Thread.sleep(700);
      for(int mode=0;mode<2;mode++) {
        final int index=mode;
        SwingUtilities.invokeAndWait(()->{
          InsetPanel panel=(InsetPanel)FreeBuildUiValidation.field(window[0],"insetPanel");
          for(JComboBox<?> combo:FreeBuildUiValidation.all(panel,JComboBox.class))if("insetSizeMode".equals(combo.getName()))combo.setSelectedIndex(index);
          window[0].validate();
        });
        Thread.sleep(300);
        SwingUtilities.invokeAndWait(()->{
          InsetPanel panel=(InsetPanel)FreeBuildUiValidation.field(window[0],"insetPanel");
          for(JComponent control:FreeBuildUiValidation.all(panel,JComponent.class))
            if((control instanceof JSpinner||control instanceof JComboBox)&&control.isShowing()) {
              Rectangle b=SwingUtilities.convertRectangle(control.getParent(),control.getBounds(),panel);
              FreeBuildUiValidation.check(b.x>=0&&b.x+b.width<=panel.getWidth(),"Inset control clipped: "+control.getName());
            }
          AbstractButton modeButton=FreeBuildUiValidation.button(window[0],"Free mode OFF");
          Rectangle b=SwingUtilities.convertRectangle(modeButton.getParent(),modeButton.getBounds(),window[0].getContentPane());
          FreeBuildUiValidation.check(b.x+b.width==window[0].getContentPane().getWidth(),"Mode button must be at right edge");
          try {
            Container pane=window[0].getContentPane();BufferedImage image=new BufferedImage(pane.getWidth(),pane.getHeight(),BufferedImage.TYPE_INT_RGB);
            Graphics2D g=image.createGraphics();pane.printAll(g);g.dispose();ImageIO.write(image,"png",output.resolve(index==0?"same-roi.png":"custom-size.png").toFile());
          } catch(Exception ex){throw new RuntimeException(ex);}
        });
      }
      System.out.println("PASS inset controls contained at 490px right pane, both size modes; right-edge Free toggle. "+output);
    } finally {SwingUtilities.invokeAndWait(()->{if(window[0]!=null)window[0].dispose();});}
    System.exit(0);
  }
  static void call(Object target,String name,Class<?>[] signature,Object...args) {
    try {Method m=target.getClass().getDeclaredMethod(name,signature);m.setAccessible(true);m.invoke(target,args);}catch(Exception ex){throw new RuntimeException(ex);}
  }
}
