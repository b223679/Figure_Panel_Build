package org.microscopy.figure;

import ij.ImagePlus;
import ij.io.FileSaver;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Free build is deliberately isolated from the shared Condition x Channel editor. */
public final class FreeBuildDialog extends JFrame {
  private FreeBuildConfiguration config;
  private InputImageManager inputs = new InputImageManager();
  private int selected = -1;
  private final Canvas canvas = new Canvas();
  private final JPanel editor = new JPanel(new BorderLayout());
  private final JLabel status = new JLabel("Click an empty panel to select a TIFF, then its channels.");
  private ContrastPanel contrast;
  private BufferedImage preview;
  private final javax.swing.Timer previewTimer = new javax.swing.Timer(160, e -> preview());
  private SwingWorker<BufferedImage,Void> previewWorker;
  private long revision;
  private boolean busy;
  private final ArrayList<String> history = new ArrayList<>();
  private int cursor = -1;
  private String lastGroup;
  private long lastEdit;
  private final JButton undo = new JButton(new FigureIcons(FigureIcons.Kind.UNDO,20)), redo = new JButton(new FigureIcons(FigureIcons.Kind.REDO,20));

  public static int[] chooseGrid(Component parent) {
    JSpinner rows = new JSpinner(new SpinnerNumberModel(3,1,20,1));
    JSpinner columns = new JSpinner(new SpinnerNumberModel(3,1,20,1));
    JPanel form = new JPanel(new GridLayout(2,2,8,8));
    form.add(new JLabel("Rows")); form.add(rows); form.add(new JLabel("Columns")); form.add(columns);
    return JOptionPane.showConfirmDialog(parent, form, "Free build — panel grid", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION
        ? new int[] {(Integer)rows.getValue(),(Integer)columns.getValue()} : null;
  }

  public FreeBuildDialog(int rows, int columns) {
    super("Visual Fig Builder — Free build");
    config = new FreeBuildConfiguration(rows,columns);
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
    JToggleButton mode = new JToggleButton("Free mode ON", true);
    mode.addActionListener(e -> {
      mode.setSelected(true);
      if (busy || !confirm("Free modeをOFFにすると、現在のFigureをすべて消去します。続けますか？")) return;
      FigurePanelBuilderDialog normal = new FigurePanelBuilderDialog(false);
      normal.setVisible(true); dispose();
    });
    button(top,"New grid", () -> {
      if (!confirm("現在のFigureをすべて消去して、新しいグリッドを作成しますか？")) return;
      int[] size = chooseGrid(this); if (size == null) return;
      config = new FreeBuildConfiguration(size[0],size[1]); selected = -1;
      changed(); rebuildEditor();
    });
    button(top,"Select Image", this::chooseImage);
    button(top,"Load settings", () -> settings(true));
    button(top,"Save settings", () -> settings(false));
    top.add(undo); top.add(redo);
    undo.setToolTipText("Undo (Ctrl+Z)"); redo.setToolTipText("Redo (Ctrl+Y)");
    undo.addActionListener(e -> restore(-1)); redo.addActionListener(e -> restore(1));
    JPanel menu = new JPanel(new BorderLayout());
    menu.add(top,BorderLayout.CENTER); menu.add(mode,BorderLayout.EAST);
    add(menu,BorderLayout.NORTH);
    JScrollPane scroll = new JScrollPane(canvas); scroll.setBorder(BorderFactory.createEmptyBorder());
    add(scroll,BorderLayout.CENTER);
    editor.setPreferredSize(new Dimension(560,650)); add(editor,BorderLayout.EAST);
    JPanel footer = new JPanel(new BorderLayout());
    JPanel output = new JPanel(new FlowLayout(FlowLayout.LEFT));
    button(output,"Generate TIF", () -> export(null));
    button(output,"Save RGB TIFF...", () -> export("tif"));
    button(output,"Save PNG...", () -> export("png"));
    button(output,"Save PPTX...", () -> export("pptx"));
    footer.add(output,BorderLayout.NORTH); footer.add(status,BorderLayout.SOUTH); add(footer,BorderLayout.SOUTH);
    previewTimer.setRepeats(false);
    canvas.addComponentListener(new ComponentAdapter() { public void componentResized(ComponentEvent e) { schedule(); } });
    getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("control Z"),"undoFree");
    getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("control Y"),"redoFree");
    getRootPane().getActionMap().put("undoFree",new AbstractAction() { public void actionPerformed(ActionEvent e) { restore(-1); } });
    getRootPane().getActionMap().put("redoFree",new AbstractAction() { public void actionPerformed(ActionEvent e) { restore(1); } });
    setSize(1400,900); setMinimumSize(new Dimension(1000,720));
    Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
    setMaximizedBounds(usable); setExtendedState(MAXIMIZED_BOTH); setLocationByPlatform(true);
    rebuildEditor(); changed();
  }

  private boolean confirm(String message) {
    return JOptionPane.showConfirmDialog(this,message,"Visual Fig Builder",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
  }
  private void attempt(Runnable run) { if (!busy) try { run.run(); } catch (Exception ex) { error(ex); } }
  private void error(Exception ex) {
    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
    status.setText(cause.getMessage()); JOptionPane.showMessageDialog(this,cause.getMessage(),"Visual Fig Builder",JOptionPane.ERROR_MESSAGE);
  }
  private void button(JPanel p,String text,Runnable run) {
    JButton b = new JButton(text); b.addActionListener(e -> attempt(run)); p.add(b);
  }
  private void changed() { changed(null); }
  private void changed(String group) {
    String value = FreeBuildSettings.snapshot(config);
    if (cursor < 0 || !history.get(cursor).equals(value)) {
      long now = System.currentTimeMillis();
      boolean coalesce = group != null && group.equals(lastGroup) && now-lastEdit < 600 && cursor > 0 && cursor == history.size()-1;
      while (history.size() > cursor + 1) history.remove(history.size()-1);
      if (coalesce) history.set(cursor,value); else history.add(value);
      if (history.size() > 101) history.remove(0);
      cursor = history.size()-1;
      lastGroup=group; lastEdit=now;
    }
    undo.setEnabled(cursor > 0); redo.setEnabled(cursor+1 < history.size()); schedule();
  }
  private void restore(int delta) {
    if (busy || cursor+delta < 0 || cursor+delta >= history.size()) return;
    lastGroup=null;
    cursor += delta; config = FreeBuildSettings.restore(history.get(cursor));
    if (selected >= config.panels.size()) selected = -1;
    undo.setEnabled(cursor > 0); redo.setEnabled(cursor+1 < history.size()); rebuildEditor(); schedule();
  }
  private void schedule() { revision++; previewTimer.restart(); canvas.repaint(); }
  private void preview() {
    if (previewWorker != null && !previewWorker.isDone()) previewWorker.cancel(true);
    final long ticket = revision;
    FreeBuildConfiguration snapshot = FreeBuildSettings.restore(FreeBuildSettings.snapshot(config));
    InputImageManager sources = inputs.copy();
    Dimension d = snapshot.dimensions();
    double scale = Math.min(1,Math.min((double)Math.max(200,canvas.getWidth()-32)/d.width,(double)Math.max(200,canvas.getHeight()-32)/d.height));
    previewWorker = new SwingWorker<BufferedImage,Void>() {
      protected BufferedImage doInBackground() { return snapshot.render(sources,scale); }
      protected void done() {
        if (isCancelled() || ticket != revision || !isDisplayable()) return;
        try { preview = get(); canvas.repaint(); } catch (Exception ex) { status.setText(ex.getCause()==null?ex.getMessage():ex.getCause().getMessage()); }
      }
    };
    previewWorker.execute();
  }

  private File chooseFile(boolean save,String extension) {
    ArrayList<String> ids = new ArrayList<>();
    if (selected >= 0 && selected < config.panels.size()) {
      FigureConfiguration image = config.panels.get(selected).image;
      if (image != null) for (ConditionConfig condition : image.conditions) ids.add(condition.sourceId);
    }
    for (FreeBuildConfiguration.Panel panel : config.panels)
      if (panel.image != null) for (ConditionConfig condition : panel.image.conditions) ids.add(condition.sourceId);
    JFileChooser chooser = new JFileChooser(ImageFileDialogs.directory(inputs, ids));
    chooser.setFileFilter(new FileNameExtensionFilter(extension.equals("tif")?"TIFF images":"Figure "+extension, extension.equals("tif")?new String[]{"tif","tiff"}:new String[]{extension}));
    if ((save?chooser.showSaveDialog(this):chooser.showOpenDialog(this)) != JFileChooser.APPROVE_OPTION) return null;
    File f = chooser.getSelectedFile();
    if (save && !f.getName().toLowerCase(java.util.Locale.ROOT).endsWith("."+extension)) f = new File(f.getPath()+"."+extension);
    if (save && f.exists() && !confirm("既存の出力ファイルを上書きしますか？\n"+f.getName())) return null;
    return f;
  }
  private void chooseImage() {
    if (selected < 0) { status.setText("Select a panel first."); return; }
    OpenImageSelection selection = OpenImageSelection.choose(this, false);
    File file = selection.browse ? chooseFile(false,"tif") : null;
    if (file == null && selection.images.isEmpty()) return;
    final int slot = selected;
    InputImageManager candidate = inputs.copy();
    // Snapshot open ImageJ pixels before the asynchronous workflow; never retain a mutable source.
    InputImageManager.Source open = selection.images.isEmpty() ? null : candidate.snapshot(selection.images.get(0),null);
    busy = true; status.setText("Loading " + (file == null ? open.title : file.getName()));
    new SwingWorker<InputImageManager.Source,Void>() {
      protected InputImageManager.Source doInBackground() { return open == null ? candidate.load(file) : open; }
      protected void done() {
        busy = false; if (!isDisplayable()) return;
        try {
          InputImageManager.Source source = get();
          String[] labels = new String[source.channels];
          for (int c = 0; c < labels.length; c++) labels[c] = (c+1)+": "+source.channelNames.get(c);
          JList<String> channels = new JList<>(labels); channels.setSelectedIndex(0);
          JPanel form = new JPanel(new BorderLayout()); form.add(new JLabel("Select channel(s); multiple selection creates a Merge."),BorderLayout.NORTH);
          form.add(new JScrollPane(channels));
          if (JOptionPane.showConfirmDialog(FreeBuildDialog.this,form,"Select Channels",JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) { status.setText("Image selection cancelled."); return; }
          int[] selectedIds = channels.getSelectedIndices(); Integer[] ids = new Integer[selectedIds.length];
          for (int n = 0; n < ids.length; n++) ids[n] = selectedIds[n]+1;
          FigureConfiguration image = FreeBuildConfiguration.image(source,ids,candidate);
          inputs = candidate; config.panels.get(slot).image = image;
          boolean first = true; for (int n=0;n<config.panels.size();n++) if (n!=slot && config.panels.get(n).image!=null) first=false;
          if (first) {
            config.cellWidth = Math.max(16,Math.min(8192,source.width)); config.cellHeight = Math.max(16,Math.min(8192,source.height));
            config.fontSize = Math.max(1,(int)Math.round(Math.max(config.cellWidth,config.cellHeight)*0.1));
          }
          selected = slot; changed(); rebuildEditor(); status.setText("Image added. B&C changes affect this panel only.");
        } catch (Exception ex) { error(ex); }
      }
    }.execute();
  }

  private void text(JPanel p,String title,String value,java.util.function.Consumer<String> set) {
    JTextField field = new JTextField(value,20); p.add(new JLabel(title)); p.add(field);
    field.getDocument().addDocumentListener(new DocumentListener() {
      private void update() { set.accept(field.getText()); changed("text:"+selected+":"+title); }
      public void insertUpdate(DocumentEvent e) { update(); }
      public void removeUpdate(DocumentEvent e) { update(); }
      public void changedUpdate(DocumentEvent e) { update(); }
    });
  }
  private void number(JPanel p,String title,int value,int min,int max,java.util.function.IntConsumer set) {
    JSpinner field = new JSpinner(new SpinnerNumberModel(value,min,max,1));
    p.add(new JLabel(title)); p.add(field); field.addChangeListener(e -> { set.accept((Integer)field.getValue()); changed("number:"+selected+":"+title); });
  }
  private void rebuildEditor() {
    if (contrast != null) contrast.stop(); contrast = null;
    editor.removeAll();
    JPanel grid = new JPanel(new GridLayout(0,2,5,5));
    number(grid,"Panel width px",config.cellWidth,16,8192,v->config.cellWidth=v);
    number(grid,"Panel height px",config.cellHeight,16,8192,v->config.cellHeight=v);
    number(grid,"Gap px",config.gap,0,4096,v->config.gap=v);
    number(grid,"Label size",config.fontSize,1,4096,v->config.fontSize=v);
    JCheckBox white = new JCheckBox("White background",config.whiteBackground); grid.add(white); grid.add(new JLabel("Otherwise black"));
    white.addActionListener(e->{config.whiteBackground=white.isSelected();changed();});
    JPanel properties = new FormPanel(); properties.setLayout(new BoxLayout(properties,BoxLayout.Y_AXIS)); properties.add(grid);
    JTabbedPane tabs = new JTabbedPane(); tabs.addTab("Panel / Labels",new JScrollPane(properties)); editor.add(tabs);
    if (selected >= 0) {
      final int index = selected, row = index/config.columns, col = index%config.columns;
      FreeBuildConfiguration.Panel panel = config.panels.get(index);
      JPanel names = new JPanel(new GridLayout(0,2,5,5));
      names.setBorder(BorderFactory.createTitledBorder("Panel " +(row+1)+" × "+(col+1)+" — manual labels"));
      text(names,"Row label",config.rowLabels.get(row),v->config.rowLabels.set(row,v));
      text(names,"Column label",config.columnLabels.get(col),v->config.columnLabels.set(col,v));
      if (panel.image != null) text(names,"Panel label",panel.label,v->panel.label=v);
      properties.add(names);
      JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
      button(actions,"Select Image",this::chooseImage);
      button(actions,"Remove",()->{ if (panel.image != null && confirm("このパネルを空白にしますか？元画像は削除されません。")) { config.panels.set(index,new FreeBuildConfiguration.Panel()); changed(); rebuildEditor(); } });
      properties.add(actions);
      if (panel.image != null) {
        JCheckBox mode = new JCheckBox("Show image name instead of image",panel.showName);
        mode.addActionListener(e->{panel.showName=mode.isSelected();changed();}); properties.add(mode);
        contrast = new ContrastPanel(panel.image,inputs,()->changed("bc:"+index),true); contrast.setIndividual(); properties.add(contrast);
        JPanel apply = new JPanel(new FlowLayout(FlowLayout.LEFT));
        button(apply,"Apply B&C / LUT to row",()->apply(true)); button(apply,"Apply B&C / LUT to column",()->apply(false)); properties.add(apply);
        JPanel scale = new JPanel(new BorderLayout());
        JPanel scaleDetails = new JPanel(new GridLayout(0,2,5,5));
        ScaleBarConfig bar = panel.image.scaleBar;
        JCheckBox show = new JCheckBox("Show scale bar",bar.show); scale.add(show,BorderLayout.NORTH);
        scale.add(scaleDetails,BorderLayout.CENTER); scaleDetails.setVisible(bar.show);
        show.addActionListener(e->{bar.show=show.isSelected();scaleDetails.setVisible(bar.show);
          scale.setMaximumSize(new Dimension(Integer.MAX_VALUE,scale.getPreferredSize().height));properties.revalidate();changed();});
        JSpinner length = new JSpinner(new SpinnerNumberModel(bar.lengthUm,0.01,100000,1));
        scaleDetails.add(new JLabel("Length µm")); scaleDetails.add(length); length.addChangeListener(e->{bar.lengthUm=((Number)length.getValue()).doubleValue();changed();});
        number(scaleDetails,"Width px",bar.thickness,1,4096,v->bar.thickness=v);
        JCheckBox whiteBar=new JCheckBox("White bar",bar.white), textBar=new JCheckBox("Show text",bar.showText);
        scaleDetails.add(whiteBar);scaleDetails.add(textBar);
        whiteBar.addActionListener(e->{bar.white=whiteBar.isSelected();changed();});
        textBar.addActionListener(e->{bar.showText=textBar.isSelected();changed();});
        number(scaleDetails,"Text size",bar.fontSize,1,4096,v->bar.fontSize=v);
        JComboBox<ScaleBarConfig.Position> position=new JComboBox<>(ScaleBarConfig.Position.values());position.setSelectedItem(bar.position);
        scaleDetails.add(new JLabel("Position"));scaleDetails.add(position);position.addActionListener(e->{bar.position=(ScaleBarConfig.Position)position.getSelectedItem();changed();});
        number(scaleDetails,"Margin X px",bar.marginX,0,4096,v->bar.marginX=v);
        number(scaleDetails,"Margin Y px",bar.marginY,0,4096,v->bar.marginY=v);
        JSpinner pixelSize=new JSpinner(new SpinnerNumberModel(bar.manualPixelSizeUm,0.0,100000.0,0.01));
        scaleDetails.add(new JLabel("Fallback µm / px (0 = unset)"));scaleDetails.add(pixelSize);
        pixelSize.addChangeListener(e->{bar.manualPixelSizeUm=((Number)pixelSize.getValue()).doubleValue();changed();});
        properties.add(scale);
        InsetPanel inset = new InsetPanel(panel.image,inputs,()->changed("inset:"+index)); inset.selectCell(0,0); tabs.addTab("Inset",new JScrollPane(inset));
      }
    } else properties.add(new JLabel("Select a grid panel to edit its labels and image."));
    // Avoid tall controls stretching to fill unused space.
    for (Component child : properties.getComponents()) if (child instanceof JComponent) {
      ((JComponent)child).setAlignmentX(Component.LEFT_ALIGNMENT);
      ((JComponent)child).setMaximumSize(new Dimension(Integer.MAX_VALUE,child.getPreferredSize().height));
    }
    properties.add(Box.createVerticalGlue());
    editor.revalidate(); editor.repaint(); canvas.repaint();
  }
  private void apply(boolean row) {
    int count = config.applyTone(selected,row); changed(); status.setText("Copied matching channel B&C / LUT to "+count+" panel(s). Empty / unmatched panels unchanged.");
  }
  private void settings(boolean load) {
    File file = chooseFile(!load,"json"); if (file == null) return;
    if (load && !confirm("現在のFree buildを保存済み設定で置き換えますか？")) return;
    busy = true;
    FreeBuildConfiguration snapshot = FreeBuildSettings.restore(FreeBuildSettings.snapshot(config));
    InputImageManager sources = inputs.copy();
    new SwingWorker<FreeBuildSettings.Loaded,Void>() {
      protected FreeBuildSettings.Loaded doInBackground() throws Exception {
        if (load) return FreeBuildSettings.load(file);
        FreeBuildSettings.save(file,snapshot,sources); return null;
      }
      protected void done() {
        busy=false; if (!isDisplayable()) return;
        try {
          FreeBuildSettings.Loaded result=get();
          if (result!=null) {
            // Retain prior source snapshots for Undo after load.
            InputImageManager combined=inputs.copy(); combined.include(result.inputs); inputs=combined;
            config=result.configuration; selected=-1; changed(); rebuildEditor();
          }
          status.setText(load?"Settings loaded.":"Settings saved.");
        } catch(Exception ex) { error(ex); }
      }
    }.execute();
  }
  private void export(String format) {
    File file = format==null?null:chooseFile(true,format); if(format!=null && file==null) return;
    FreeBuildConfiguration snapshot=FreeBuildSettings.restore(FreeBuildSettings.snapshot(config)); InputImageManager sources=inputs.copy();
    busy=true; status.setText("Generating figure...");
    new SwingWorker<BufferedImage,Void>() {
      protected BufferedImage doInBackground() throws Exception {
        if (file!=null) OutputSafety.checkDestination(file,sources);
        if ("pptx".equals(format)) { new PptxExporter().saveFree(file,snapshot,sources); return null; }
        BufferedImage image=snapshot.render(sources,1); if(file==null) return image;
        Path dest=file.toPath().toAbsolutePath(), temp=Files.createTempFile(dest.getParent(),"free-output-","."+format);
        try {
          if ("png".equals(format)) { if(!ImageIO.write(image,"png",temp.toFile())) throw new IOException("PNG export failed."); }
          else { ImagePlus plus=new ImagePlus("Free build",image); try { if(!new FileSaver(plus).saveAsTiff(temp.toString())) throw new IOException("TIFF export failed."); } finally { plus.flush(); } }
          Files.move(temp,dest,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
        return null;
      }
      protected void done() {
        busy=false; if(!isDisplayable()) return;
        try { BufferedImage image=get(); if(image!=null) new ImagePlus("Free build",image).show(); status.setText(file==null?"Figure generated.":"Saved: "+file.getName()); }
        catch(Exception ex) { error(ex); }
      }
    }.execute();
  }
  public void dispose() {
    previewTimer.stop(); if(previewWorker!=null) previewWorker.cancel(true); if(contrast!=null) contrast.stop(); super.dispose();
  }

  private static final class FormPanel extends JPanel implements Scrollable {
    public Dimension getPreferredScrollableViewportSize() { return new Dimension(540,650); }
    public boolean getScrollableTracksViewportWidth() { return true; }
    public boolean getScrollableTracksViewportHeight() { return false; }
    public int getScrollableUnitIncrement(Rectangle r,int axis,int direction) { return 24; }
    public int getScrollableBlockIncrement(Rectangle r,int axis,int direction) { return Math.max(24,r.height-24); }
  }

  private final class Canvas extends JPanel {
    double fit=1; int ox,oy;
    Canvas() {
      setBackground(new Color(45,45,45));
      addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent e) {
        if(busy || fit<=0) return;
        Point p=new Point((int)((e.getX()-ox)/fit),(int)((e.getY()-oy)/fit));
        for(int n=0;n<config.panels.size();n++) if(config.bounds(n).contains(p)) {
          selected=n; rebuildEditor(); if(config.panels.get(n).image==null || e.getClickCount()==2) attempt(FreeBuildDialog.this::chooseImage); return;
        }
      } });
    }
    protected void paintComponent(Graphics graphics) {
      super.paintComponent(graphics); Dimension d=config.dimensions();
      fit=Math.min((double)Math.max(1,getWidth()-32)/d.width,(double)Math.max(1,getHeight()-32)/d.height);
      ox=(getWidth()-(int)(d.width*fit))/2; oy=(getHeight()-(int)(d.height*fit))/2;
      Graphics2D g=(Graphics2D)graphics.create();
      try {
        g.translate(ox,oy); g.scale(fit,fit);
        g.setColor(config.whiteBackground?Color.WHITE:Color.BLACK); g.fillRect(0,0,d.width,d.height);
        if(preview!=null) g.drawImage(preview,0,0,d.width,d.height,null);
        for(int n=0;n<config.panels.size();n++) {
          Rectangle b=config.bounds(n);
          if(config.panels.get(n).image==null) {
            g.setColor(new Color(190,190,190));g.fill(b);g.setColor(Color.DARK_GRAY);
            g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,Math.max(12,Math.min(b.width,b.height)/4)));
            FontMetrics fm=g.getFontMetrics();g.drawString("+",(int)b.getCenterX()-fm.stringWidth("+")/2,(int)b.getCenterY()+(fm.getAscent()-fm.getDescent())/2);
          }
          g.setStroke(new BasicStroke((float)(1/fit))); g.setColor(new Color(130,130,130));g.draw(b);
          if(n==selected) {g.setColor(Color.CYAN);g.setStroke(new BasicStroke((float)(3/fit)));g.draw(b);}
        }
      } finally { g.dispose(); }
    }
  }
}
