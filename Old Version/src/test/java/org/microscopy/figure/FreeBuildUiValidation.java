package org.microscopy.figure;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Actual isolated Swing windows, dialogs, event dispatch, file choices and export buttons. */
public class FreeBuildUiValidation {
  static FreeBuildDialog dialog;
  static Path output;
  static AtomicReference<Throwable> failure=new AtomicReference<>();
  public static void main(String[] args) throws Exception {
    output=Files.createTempDirectory(Paths.get("artifacts"),"free-ui-");
    byte[] original=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Paths.get("test-data/Control.tif")));
    try {
      edt(()->{
        new ij.io.Opener().openImage(new File("test-data/Control.tif").getAbsolutePath()).show();
        FigurePanelBuilderDialog normal=new FigurePanelBuilderDialog(false);normal.setVisible(true);
        respond(w->title(w).equals("Free build"),w->pane(w).setValue(JOptionPane.CANCEL_OPTION));
        button(normal,"Free mode OFF").doClick();check(normal.isDisplayable(),"Cancelled switch retains normal editor");
        respond(w->title(w).equals("Free build"),w->pane(w).setValue(JOptionPane.OK_OPTION));
        respond(w->title(w).equals("Free build — panel grid"),w->{List<JSpinner> fields=all(w,JSpinner.class);fields.get(0).setValue(2);fields.get(1).setValue(3);pane(w).setValue(JOptionPane.OK_OPTION);});
        button(normal,"Free mode OFF").doClick();check(!normal.isDisplayable(),"Normal mode replaced only after confirmation");
        for(Window window:Window.getWindows())if(window instanceof FreeBuildDialog && window.isDisplayable())dialog=(FreeBuildDialog)window;
        check(dialog!=null,"Free editor opened");
      });
      await(()->field(dialog,"preview")!=null,"empty preview");capture("free-empty.png");
      add(0,0,1);add(1,0);add(3,0);
      edt(()->{
        select(0);
        List<JTextField> text=all((Container)field(dialog,"editor"),JTextField.class);
        // Text fields with 20 columns are the manual label fields, excluding spinner editors.
        List<JTextField> names=new ArrayList<>();for(JTextField f:text)if(f.getColumns()==20)names.add(f);
        names.get(0).setText("Control row");names.get(1).setText("Condition A");names.get(2).setText("Manual panel label");
        ContrastPanel bc=(ContrastPanel)field(dialog,"contrast");
        JSpinner min=(JSpinner)field(bc,"min");min.setValue(10.0);
        check(config().panels.get(1).image.channel(1).min!=10,"B&C initially independent");
        button(dialog,"Apply B&C / LUT to row").doClick();check(config().panels.get(1).image.channel(1).min==10,"Explicit row copy");
        check(config().panels.get(3).image.channel(1).min!=10,"Other row unchanged");
        button(dialog,"Apply B&C / LUT to column").doClick();check(config().panels.get(3).image.channel(1).min==10,"Explicit column copy");
        JTabbedPane tabs=all((Container)field(dialog,"editor"),JTabbedPane.class).get(0);tabs.setSelectedIndex(1);
        AbstractButton show=button(dialog,"Show inset");check(!show.isSelected(),"Inset defaults off");show.doClick();
      });
      ready();capture("free-inset.png");
      edt(()->{
        button(dialog,"Show inset").doClick();select(1);button(dialog,"Show image name instead of image").doClick();
      });
      ready();capture("free-layout.png");
      edt(()->{select(3);respond(w->title(w).equals("Figure Panel Builder"),w->pane(w).setValue(JOptionPane.CANCEL_OPTION));button(dialog,"Remove").doClick();check(config().panels.get(3).image!=null,"Remove cancellation");
        respond(w->title(w).equals("Figure Panel Builder"),w->pane(w).setValue(JOptionPane.OK_OPTION));button(dialog,"Remove").doClick();check(config().panels.get(3).image==null,"Remove clears slot");
        shortcut("control Z");check(config().panels.get(3).image!=null,"Undo remove");shortcut("control Y");check(config().panels.get(3).image==null,"Redo remove");shortcut("control Z");
      });
      for(String format:new String[]{"tif","png","pptx","json"}) {
        String caption=format.equals("tif")?"Save RGB TIFF...":format.equals("png")?"Save PNG...":format.equals("pptx")?"Save PPTX...":"Save settings";
        Path file=output.resolve("free-figure."+format);
        edt(()->{choose(file.toFile());button(dialog,caption).doClick();});await(()->!(Boolean)field(dialog,"busy"),"export "+format);
        check(Files.exists(file)&&Files.size(file)>0,"Output saved: "+format);
      }
      edt(()->{
        choose(output.resolve("free-figure.json").toFile());respond(w->title(w).equals("Figure Panel Builder"),w->pane(w).setValue(JOptionPane.OK_OPTION));button(dialog,"Load settings").doClick();
      });await(()->!(Boolean)field(dialog,"busy"),"load settings");
      check(config().panels.get(1).showName,"Name mode restored");
      edt(()->button(dialog,"Generate TIF").doClick());await(()->!(Boolean)field(dialog,"busy"),"Generate TIF");
      edt(()->{
        check(ij.WindowManager.getImage("Free build")!=null,"Generated ImagePlus exists");
        respond(w->title(w).equals("Figure Panel Builder"),w->pane(w).setValue(JOptionPane.CANCEL_OPTION));button(dialog,"Free mode ON").doClick();check(dialog.isDisplayable(),"Off cancellation preserves free grid");
        respond(w->title(w).equals("Figure Panel Builder"),w->pane(w).setValue(JOptionPane.OK_OPTION));button(dialog,"Free mode ON").doClick();check(!dialog.isDisplayable(),"Off clears free editor");
        for(Window window:Window.getWindows())if(window instanceof FigurePanelBuilderDialog && window.isDisplayable())check(((FigureConfiguration)field(window,"config")).conditions.isEmpty(),"Normal figure reset");
      });
      check(Arrays.equals(original,MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Paths.get("test-data/Control.tif")))) ,"Source TIFF unchanged");
      System.out.println("PASS Free build GUI: mode confirmation, selection, Merge, per-panel B&C, axis copy, manual labels, inset, name mode, remove, Undo/Redo, all exports, settings, source integrity. " + output);
    } finally { edt(()->{for(Window window:Window.getWindows())window.dispose();}); }
    System.exit(0);
  }
  static void add(int slot,int...channels)throws Exception {
    edt(()->{
      if (slot == 1) {
        respond(w->title(w).equals("Select Images"),w->pane(w).setValue("Open TIFF files..."));
        choose(new File("test-data/Control.tif"));
      } else respond(w->title(w).equals("Select Images"),w->{all(w,JList.class).get(0).setSelectedIndex(0);pane(w).setValue("Add selected");});
      respond(w->title(w).equals("Select Channels"),w->{all(w,JList.class).get(0).setSelectedIndices(channels);pane(w).setValue(JOptionPane.OK_OPTION);});
      click(slot);
    });await(()->!(Boolean)field(dialog,"busy")&&config().panels.get(slot).image!=null,"add panel");ready();
  }
  static void select(int slot) { click(slot); }
  static void click(int slot) {
    Component canvas=(Component)field(dialog,"canvas");Rectangle b=config().bounds(slot);double fit=(Double)field(canvas,"fit");
    int x=(Integer)field(canvas,"ox")+(int)(b.getCenterX()*fit),y=(Integer)field(canvas,"oy")+(int)(b.getCenterY()*fit);
    canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,x,y,1,false,MouseEvent.BUTTON1));
  }
  static FreeBuildConfiguration config(){return (FreeBuildConfiguration)field(dialog,"config");}
  static void shortcut(String key){Object action=dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(key));check(action!=null,"Shortcut exists");dialog.getRootPane().getActionMap().get(action).actionPerformed(new ActionEvent(dialog,0,key));}
  static String title(Container c){return c instanceof JDialog?((JDialog)c).getTitle():"";}
  static void respond(Predicate<Container> match,Consumer<Container> action){
    int[] attempts={0};javax.swing.Timer timer=new javax.swing.Timer(50,null);timer.addActionListener(e->{
      for(Window w:Window.getWindows())if(w instanceof JDialog&&w.isVisible()&&match.test(w)){timer.stop();try{action.accept(w);}catch(Throwable ex){failure.set(ex);w.dispose();}return;}
      if(++attempts[0]>300){timer.stop();failure.set(new AssertionError("Expected dialog not opened"));}
    });timer.start();
  }
  static void choose(File file){respond(w->!all(w,JFileChooser.class).isEmpty(),w->{JFileChooser chooser=all(w,JFileChooser.class).get(0);chooser.setSelectedFile(file.getAbsoluteFile());chooser.approveSelection();});}
  static JOptionPane pane(Container c){return all(c,JOptionPane.class).get(0);}
  static <T extends Component> List<T> all(Container c,Class<T> type){List<T> list=new ArrayList<>();if(type.isInstance(c))list.add(type.cast(c));for(Component child:c.getComponents()){if(child instanceof Container)list.addAll(all((Container)child,type));else if(type.isInstance(child))list.add(type.cast(child));}return list;}
  static AbstractButton button(Container c,String text){for(AbstractButton b:all(c,AbstractButton.class))if(text.equals(b.getText()))return b;throw new AssertionError("Missing button "+text);}
  static Object field(Object object,String name){try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(Exception ex){throw new RuntimeException(ex);}}
  static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
  static void edt(Runnable action)throws Exception{if(failure.get()!=null)throw new AssertionError(failure.get());SwingUtilities.invokeAndWait(action);if(failure.get()!=null)throw new AssertionError(failure.get());}
  static void await(BooleanSupplier ready,String text)throws Exception{for(int i=0;i<400;i++){AtomicReference<Boolean> result=new AtomicReference<>();edt(()->result.set(ready.getAsBoolean()));if(result.get())return;Thread.sleep(50);}throw new AssertionError("Timed out: "+text);}
  static void ready()throws Exception{await(()->!((javax.swing.Timer)field(dialog,"previewTimer")).isRunning()&&field(dialog,"previewWorker")!=null&&((SwingWorker<?,?>)field(dialog,"previewWorker")).isDone(),"preview");Thread.sleep(80);}
  static void capture(String name)throws Exception{edt(()->{try{Container c=dialog.getContentPane();BufferedImage image=new BufferedImage(c.getWidth(),c.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();c.printAll(g);g.dispose();ImageIO.write(image,"png",output.resolve(name).toFile());}catch(Exception ex){throw new RuntimeException(ex);}});}
}
