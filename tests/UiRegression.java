package me.drton.flightplot;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.imageio.ImageIO;
import javax.swing.tree.DefaultMutableTreeNode;
import org.jfree.data.Range;
import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.ui.RectangleEdge;
import me.drton.jmavlib.log.LogReader;
import me.drton.jmavlib.log.px4.PX4LogReaderOptimized;

public class UiRegression {
    static FlightPlot app;
    static JFileChooser previousChooser;
    static File outputFile(String name) { return new File(System.getProperty("flightplot.testOutput", "verification"), name); }
    static Object field(String name) throws Exception { Field f=FlightPlot.class.getDeclaredField(name);f.setAccessible(true);return f.get(app); }
    static void call(String name,Class<?>[] types,Object... args) throws Exception { Method m=FlightPlot.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(app,args); }
    static void check(boolean condition,String text) {if(!condition)throw new AssertionError(text);}
    static Workspace workspace() throws Exception { return (Workspace)field("workspace"); }
    static java.util.List<ChartTab> tabs() throws Exception { return workspace().view(); }
    static ChartTab tab() throws Exception { return workspace().current(); }
    static int visibleFieldCount() throws Exception {
        FieldsPanel panel=(FieldsPanel)field("fieldsPanel");
        Field rootField=FieldsPanel.class.getDeclaredField("treeRoot");rootField.setAccessible(true);
        DefaultMutableTreeNode root=(DefaultMutableTreeNode)rootField.get(panel);
        int count=0;
        Enumeration<?> nodes=root.depthFirstEnumeration();
        while(nodes.hasMoreElements()) {
            DefaultMutableTreeNode node=(DefaultMutableTreeNode)nodes.nextElement();
            if(node!=root && node.isLeaf()) count++;
        }
        return count;
    }
    static Set<String> selectedSimpleFields() throws Exception {
        Set<String> fields=new LinkedHashSet<>();
        for(int row=0;row<tab().processorsListModel.getRowCount();row++) {
            if(!Boolean.TRUE.equals(tab().processorsListModel.getValueAt(row,0)))continue;
            ProcessorPreset pp=(ProcessorPreset)tab().processorsListModel.getValueAt(row,1);
            Object value=pp.getParameters().get("Fields");
            if(value==null)continue;
            for(String name:value.toString().trim().split("\\s+"))if(!name.isEmpty())fields.add(name);
        }
        return fields;
    }
    static void waitIdle() throws Exception {
        long deadline=System.currentTimeMillis()+20000;
        while(System.currentTimeMillis()<deadline) {
            AtomicBoolean idle=new AtomicBoolean();
            SwingUtilities.invokeAndWait(()-> { try { idle.set(!((AtomicBoolean)field("invokeProcessFile")).get() && field("zoomDebounceTimer")==null); }catch(Exception e){throw new RuntimeException(e);} });
            if(idle.get()) {Thread.sleep(100);return;} Thread.sleep(20);
        } throw new AssertionError("worker did not complete");
    }
    static void load(String file) throws Exception {
        long began=System.nanoTime();
        SwingUtilities.invokeAndWait(()-> {try {call("openLogAsync",new Class[]{String.class},file);}catch(Exception e){throw new RuntimeException(e);}});
        waitLoaded(file);
        waitIdle();System.out.printf(Locale.ROOT,"UI opened_ms=%.1f%n",(System.nanoTime()-began)/1e6);
    }
    static void waitLoaded(String file) throws Exception {
        long deadline=System.currentTimeMillis()+20000;
        while(true) {
            AtomicBoolean ready=new AtomicBoolean();
            SwingUtilities.invokeAndWait(()-> {try {ready.set(file.equals(tab().logFileName));}catch(Exception e){throw new RuntimeException(e);}});
            if(ready.get())break; if(System.currentTimeMillis()>deadline)throw new AssertionError("load timeout");Thread.sleep(20);
        }
    }
    static void collectComponents(Component component,java.util.List<JViewport> viewports,java.util.List<JScrollPane> scrollPanes) {
        if(component instanceof JViewport)viewports.add((JViewport)component);
        if(component instanceof JScrollPane)scrollPanes.add((JScrollPane)component);
        if(component instanceof Container)for(Component child:((Container)component).getComponents())collectComponents(child,viewports,scrollPanes);
    }
    static void testSingleOpenDialog() throws Exception {
        SwingUtilities.invokeLater(app::showOpenLogDialog);
        long deadline=System.currentTimeMillis()+10000;
        while(System.currentTimeMillis()<deadline) {
            AtomicBoolean showing=new AtomicBoolean();
            SwingUtilities.invokeAndWait(()-> {try {showing.set((Boolean)field("openLogDialogShowing"));}catch(Exception e){throw new RuntimeException(e);}});
            if(showing.get()) break;
            Thread.sleep(20);
        }
        SwingUtilities.invokeAndWait(app::showOpenLogDialog);
        Thread.sleep(150);
        SwingUtilities.invokeAndWait(()-> {try {
            int visibleDialogs=0;
            for(Window window:Window.getWindows()) if(window instanceof JDialog && window.isShowing()) visibleDialogs++;
            check(visibleDialogs==1,"only one open-log dialog may be visible");
            check(!((JButton)field("openLogButton")).isEnabled(),"open action locked while chooser is visible");
            String language=(String)field("uiLanguage");
            JFileChooser chooser=(JFileChooser)field("logFileChooser");
            check(chooser!=previousChooser,"each open uses a fresh file chooser");
            previousChooser=chooser;
            check(("zh_CN".equals(language) ? "打开" : "Open").equals(chooser.getApproveButtonText()),"open action follows UI language");
            JDialog dialog=(JDialog)field("openLogDialog");
            dialog.validate();
            Rectangle screen=dialog.getGraphicsConfiguration().getBounds();
            Insets insets=Toolkit.getDefaultToolkit().getScreenInsets(dialog.getGraphicsConfiguration());
            check(dialog.getWidth()<=screen.width-insets.left-insets.right,"open dialog fits screen width");
            check(dialog.getHeight()<=screen.height-insets.top-insets.bottom,"open dialog fits screen height");
            java.util.List<JViewport> viewports=new ArrayList<>();java.util.List<JScrollPane> scrollPanes=new ArrayList<>();
            collectComponents(chooser,viewports,scrollPanes);
            check(!viewports.isEmpty(),"file chooser contains scrollable views");
            for(JViewport viewport:viewports)check(viewport.getScrollMode()==JViewport.SIMPLE_SCROLL_MODE,"chooser viewport uses repaint-safe scrolling");
            for(JScrollPane scrollPane:scrollPanes) {
                JScrollBar bar=scrollPane.getVerticalScrollBar();
                if(!scrollPane.isShowing() || bar==null || bar.getMaximum()<=bar.getVisibleAmount())continue;
                for(int i=0;i<60;i++) {
                    int rotation=(i%2==0)?1:-1;
                    MouseWheelEvent wheel=new MouseWheelEvent(scrollPane,MouseWheelEvent.MOUSE_WHEEL,System.currentTimeMillis(),0,
                            scrollPane.getWidth()/2,scrollPane.getHeight()/2,0,false,MouseWheelEvent.WHEEL_UNIT_SCROLL,3,rotation);
                    scrollPane.dispatchEvent(wheel);
                }
            }
            dialog.validate();dialog.repaint();Toolkit.getDefaultToolkit().sync();
            int afterScrollDialogs=0;
            for(Window window:Window.getWindows())if(window instanceof JDialog && window.isShowing())afterScrollDialogs++;
            check(afterScrollDialogs==1,"mouse-wheel scrolling never creates another dialog");
            BufferedImage chooserImage=new BufferedImage(dialog.getWidth(),dialog.getHeight(),BufferedImage.TYPE_INT_RGB);
            Graphics2D chooserGraphics=chooserImage.createGraphics();dialog.printAll(chooserGraphics);chooserGraphics.dispose();
            ImageIO.write(chooserImage,"png",outputFile("open-dialog-"+System.getProperty("sun.java2d.uiScale","1")+".png"));
            Point screenPoint=dialog.getLocationOnScreen();
            BufferedImage screenImage=new Robot().createScreenCapture(new Rectangle(screenPoint.x,screenPoint.y,dialog.getWidth(),dialog.getHeight()));
            ImageIO.write(screenImage,"png",outputFile("open-dialog-screen-"+System.getProperty("sun.java2d.uiScale","1")+".png"));
            chooser.cancelSelection();
        }catch(Exception e){throw new RuntimeException(e);}});
        deadline=System.currentTimeMillis()+5000;
        while((Boolean)field("openLogDialogShowing") && System.currentTimeMillis()<deadline) Thread.sleep(20);
        check(!(Boolean)field("openLogDialogShowing"),"open dialog closed cleanly");
        check(((JButton)field("openLogButton")).isEnabled(),"open action restored after chooser closes");
        System.out.println("PASS single-instance open dialog and repeated-click guard");
    }
    static void testInvalidOpenCleanup() throws Exception {
        File invalid=outputFile("invalid-open.bin");
        try(FileOutputStream out=new FileOutputStream(invalid)) {out.write(new byte[]{1,2,3,4,5});}
        String path=invalid.getAbsolutePath();
        SwingUtilities.invokeAndWait(()-> {try {
            call("openLogAsync",new Class[]{String.class},path);
            call("openLogAsync",new Class[]{String.class},path);
        }catch(Exception e){throw new RuntimeException(e);}});
        long deadline=System.currentTimeMillis()+10000;
        while(System.currentTimeMillis()<deadline) {
            AtomicBoolean showing=new AtomicBoolean();
            SwingUtilities.invokeAndWait(()-> {try {JDialog d=(JDialog)field("messageDialog");showing.set(d!=null && d.isShowing());}catch(Exception e){throw new RuntimeException(e);}});
            if(showing.get()) break;
            Thread.sleep(20);
        }
        SwingUtilities.invokeAndWait(()-> {try {
            check(tabs().size()==1,"failed auto-created tab removed");
            int visibleMessages=0;
            for(Window window:Window.getWindows()) if(window instanceof JDialog && window.isShowing()) visibleMessages++;
            check(visibleMessages==1,"duplicate invalid-log messages suppressed");
            ((JDialog)field("messageDialog")).dispose();
        }catch(Exception e){throw new RuntimeException(e);}});
        Thread.sleep(100);
        check(tabs().size()==1,"original tab retained after invalid open");
        System.out.println("PASS invalid open keeps current analysis and leaves no extra window/tab");
    }
    public static void main(String[] args) throws Exception {
        int exitCode=0;
        AtomicReference<Throwable> uncaught=new AtomicReference<>();
        Thread.setDefaultUncaughtExceptionHandler((thread,failure)-> { failure.printStackTrace();uncaught.set(failure); });
        try {
            SwingUtilities.invokeAndWait(()-> {try {UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                call("setGlobalUIFont",new Class[]{javax.swing.plaf.FontUIResource.class},new javax.swing.plaf.FontUIResource(new Font("Microsoft YaHei",Font.PLAIN,12)));
                app=new FlightPlot(false);
            }catch(Exception e){throw new RuntimeException(e);}});
            SwingUtilities.invokeAndWait(()-> {try {check(tabs().size()==1,"exactly one initial tab");
                System.out.println("UI scale="+((JFrame)field("mainFrame")).getGraphicsConfiguration().getDefaultTransform().getScaleX());
                Field lastDirectory=FlightPlot.class.getDeclaredField("lastLogDirectory");lastDirectory.setAccessible(true);
                lastDirectory.set(app,new File(args[0]).getAbsoluteFile().getParentFile());
            }catch(Exception e){throw new RuntimeException(e);}});
            testSingleOpenDialog();
            AtomicLong previous=new AtomicLong(System.nanoTime()),maxGap=new AtomicLong();
            javax.swing.Timer pulse=new javax.swing.Timer(20,e->{long now=System.nanoTime();long gap=now-previous.getAndSet(now);maxGap.accumulateAndGet(gap,Math::max);});
            SwingUtilities.invokeAndWait(pulse::start);
            load(new File(args[0]).getAbsolutePath());
            AtomicInteger firstCatalogSize=new AtomicInteger();
            SwingUtilities.invokeAndWait(()-> {try {
                firstCatalogSize.set(tab().logReader.getFields().size());
                check(visibleFieldCount()==firstCatalogSize.get(),"field tree must match current file catalog");
                Field useBuffer=org.jfree.chart.ChartPanel.class.getDeclaredField("useBuffer");useBuffer.setAccessible(true);
                check(!useBuffer.getBoolean(tab().chartPanel),"HiDPI chart must render directly without logical-pixel buffer");
                check(GraphicsOptimizer.getTextAntialiasingHint()!=RenderingHints.VALUE_TEXT_ANTIALIAS_OFF,"font antialiasing enabled");
                System.out.println("PASS dynamic field tree and direct HiDPI chart rendering");
            }catch(Exception e){throw new RuntimeException(e);}});
            String unavailableInSmall=null;
            if(args.length>1) {
                LogReader smallCatalog=new PX4LogReaderOptimized(new File(args[1]).getAbsolutePath());
                try {
                    for(String candidate:tab().logReader.getFields().keySet()) {
                        if(!smallCatalog.getFields().containsKey(candidate)) {unavailableInSmall=candidate;break;}
                    }
                } finally {smallCatalog.close();}
            }
            final String fieldMissingFromSmall=unavailableInSmall;
            SwingUtilities.invokeAndWait(()-> {
                java.util.List<String> selected=new ArrayList<>(Arrays.asList("ATT.Roll","ATT.Pitch","PIDR.I"));
                if(fieldMissingFromSmall!=null)selected.add(fieldMissingFromSmall);
                app.addFields(selected);
            });
            waitIdle();
            testInvalidOpenCleanup();
            waitIdle();
            System.out.printf(Locale.ROOT,"UI load_and_plot_max_EDT_gap_ms=%.1f%n",maxGap.get()/1e6);
            SwingUtilities.invokeAndWait(()-> {try {
                check(tab().dataset.getSeriesCount()>=2,"plotted series");
                check(tab().dataset.getSeries(0).getItemCount()>0,"plotted points");
                check(tab().chart.getLegend()!=null && tab().chart.getLegend().isVisible(),"field color legend visible by default");
                check(RectangleEdge.BOTTOM.equals(tab().chart.getLegend().getPosition()),"field color legend below time axis");
                LegendItemCollection legendItems=tab().chart.getXYPlot().getLegendItems();
                check(legendItems.getItemCount()==tab().dataset.getSeriesCount(),"one legend color per visible field");
                for(int i=0;i<legendItems.getItemCount();i++) {
                    check(!legendItems.get(i).getLabel().startsWith("New:"),"friendly field legend label");
                }
                ProcessorPreset pp=(ProcessorPreset)tab().processorsListModel.getValueAt(0,1);
                String colorKey="ATT.Roll";
                check(tab().seriesIndex.get(0).containsKey(colorKey),"roll series color mapping");
                int colorSeries=tab().seriesIndex.get(0).get(colorKey);
                pp.getColors().put(colorKey,Color.BLACK);
                call("setChartColors",new Class[]{});
                check(Color.BLACK.equals(((XYLineAndShapeRenderer)tab().chart.getXYPlot().getRenderer()).getSeriesPaint(colorSeries)),"legend and curve color update immediately");
                check(Color.BLACK.equals(tab().chart.getXYPlot().getLegendItems().get(colorSeries).getLinePaint()),"legend indicator uses roll curve color");
                JCheckBoxMenuItem legendToggle=(JCheckBoxMenuItem)field("showLegendItem");
                legendToggle.doClick();
                check(!tab().chart.getLegend().isVisible(),"legend can be hidden");
                legendToggle.doClick();
                check(tab().chart.getLegend().isVisible(),"legend can be shown again");
                System.out.println("PASS field colors, legend position, live recolor and visibility toggle");
            }catch(Exception e){throw new RuntimeException(e);}});
            for(int[] size:new int[][]{{640,480},{800,600},{1280,720},{1920,1080},{2560,1440},{800,600}}) {
                SwingUtilities.invokeAndWait(()-> {try {JFrame frame=(JFrame)field("mainFrame");frame.setSize(size[0],size[1]);frame.validate();}catch(Exception e){throw new RuntimeException(e);}});
                Thread.sleep(40);
                SwingUtilities.invokeAndWait(()-> {try {
                    JFrame frame=(JFrame)field("mainFrame");frame.setSize(size[0],size[1]);frame.validate();
                    JPanel panel=(JPanel)field("mainPanel");panel.doLayout();frame.validate();
                    Container toolbar=(Container)((BorderLayout)panel.getLayout()).getLayoutComponent(BorderLayout.NORTH);
                    for(Component c:toolbar.getComponents())check(c.getY()+c.getHeight()<=toolbar.getHeight(),"toolbar clipped at "+size[0]);
                    check(tab().chartPanel.getWidth()>160 && tab().chartPanel.getHeight()>100,"chart size");
                    BufferedImage img=new BufferedImage(panel.getWidth(),panel.getHeight(),BufferedImage.TYPE_INT_RGB);
                    Graphics2D g=img.createGraphics();panel.printAll(g);g.dispose();ImageIO.write(img,"png",outputFile("ui-scale-"+System.getProperty("sun.java2d.uiScale","1")+"-"+size[0]+"x"+size[1]+".png"));
                    check(tab().chartPanel.getScaleX()==1.0 && tab().chartPanel.getScaleY()==1.0,"chart stretched");
                    System.out.println("PASS layout "+size[0]+"x"+size[1]);
                }catch(Exception e){throw new RuntimeException(e);}});
            }
            waitIdle();
            SwingUtilities.invokeAndWait(()-> {try {
                call("toggleLanguage",new Class[]{});
                ((CollapsiblePanel)field("parametersPanel")).toggle();
                ((CollapsiblePanel)field("logsPanel")).toggle();
            }catch(Exception e){throw new RuntimeException(e);}});
            Thread.sleep(350);
            SwingUtilities.invokeAndWait(()-> {try {
                JComponent fields=(JComponent)field("fieldsPanel");
                JScrollPane scroll=(JScrollPane)SwingUtilities.getAncestorOfClass(JScrollPane.class,fields);
                check(scroll!=null && fields.getHeight()>=200,"expanded sidebar keeps fields reachable");
                scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum());
                check(scroll.getViewport().getViewPosition().y>0,"expanded sidebar scrolls");
                ((CollapsiblePanel)field("parametersPanel")).toggle();
                ((CollapsiblePanel)field("logsPanel")).toggle();
                JFrame frame=(JFrame)field("mainFrame");frame.setSize(1280,720);frame.validate();
            }catch(Exception e){throw new RuntimeException(e);}});
            Thread.sleep(350);waitIdle();
            SwingUtilities.invokeAndWait(()-> {try {
                check(tab().dataset.getSeries(0).getKey().toString().contains(" | "),"legend label follows Chinese language");
                check(((CollapsiblePanel)field("parametersPanel")).getPreferredSize().height==36,"parameters collapsed");
                check(((CollapsiblePanel)field("logsPanel")).getPreferredSize().height==36,"messages collapsed");
                JComponent fields=(JComponent)field("fieldsPanel");
                JScrollPane sidebar=(JScrollPane)SwingUtilities.getAncestorOfClass(JScrollPane.class,fields);
                sidebar.getViewport().setViewPosition(new Point(0,0));
                JFrame frame=(JFrame)field("mainFrame");
                JPanel panel=(JPanel)field("mainPanel");BufferedImage img=new BufferedImage(panel.getWidth(),panel.getHeight(),BufferedImage.TYPE_INT_RGB);
                Graphics2D g=img.createGraphics();panel.printAll(g);g.dispose();ImageIO.write(img,"png",outputFile("final-preview.png"));
                double sx=frame.getGraphicsConfiguration().getDefaultTransform().getScaleX();
                double sy=frame.getGraphicsConfiguration().getDefaultTransform().getScaleY();
                BufferedImage nativeImage=new BufferedImage((int)Math.ceil(panel.getWidth()*sx),(int)Math.ceil(panel.getHeight()*sy),BufferedImage.TYPE_INT_RGB);
                Graphics2D nativeGraphics=nativeImage.createGraphics();nativeGraphics.scale(sx,sy);panel.printAll(nativeGraphics);nativeGraphics.dispose();
                ImageIO.write(nativeImage,"png",outputFile("hidpi-native-"+System.getProperty("sun.java2d.uiScale","1")+".png"));
                check(nativeImage.getWidth()>=(int)(panel.getWidth()*sx),"physical-pixel width");
                check(nativeImage.getHeight()>=(int)(panel.getHeight()*sy),"physical-pixel height");
                System.out.println("PASS expanded sidebar and Chinese UI");
            }catch(Exception e){throw new RuntimeException(e);}});
            testSingleOpenDialog();
            SwingUtilities.invokeAndWait(()-> {try {Range r=tab().chart.getXYPlot().getDomainAxis().getRange(); tab().chart.getXYPlot().getDomainAxis().setRange(r.getCentralValue(),r.getCentralValue()+r.getLength()/100); }catch(Exception e){throw new RuntimeException(e);}});
            waitIdle();
            SwingUtilities.invokeAndWait(()-> {try {check(tab().dataset.getSeries(0).getItemCount()>0,"zoom data");}catch(Exception e){throw new RuntimeException(e);}});
            if(args.length>1) {
                String small=new File(args[1]).getAbsolutePath();
                ChartTab original=tab();
                Set<String> originalSelection=selectedSimpleFields();
                load(small);
                SwingUtilities.invokeAndWait(()-> {try {
                    check(tabs().size()==2,"new log opens in a new tab");
                    check(original.logFileName.equals(new File(args[0]).getAbsolutePath()),"existing analysis remains open");
                    int catalogSize=tab().logReader.getFields().size();
                    check(catalogSize!=firstCatalogSize.get(),"test files need different schemas");
                    check(visibleFieldCount()==catalogSize,"field tree refreshed after switching files");
                    Set<String> expected=new LinkedHashSet<>(originalSelection);
                    expected.retainAll(tab().logReader.getFields().keySet());
                    check(!expected.isEmpty(),"same-format fixtures need common selected fields");
                    check(expected.size()<originalSelection.size(),"same-format fixtures exercise unavailable-field filtering");
                    check(selectedSimpleFields().equals(expected),"same-format open automatically restores every available selected field");
                    check(tab().dataset.getSeriesCount()==expected.size(),"restored fields are plotted immediately");
                    if(expected.contains("ATT.Roll")) {
                        ProcessorPreset restored=(ProcessorPreset)tab().processorsListModel.getValueAt(0,1);
                        check(Color.BLACK.equals(restored.getColors().get("ATT.Roll")),"restored field keeps its selected color");
                    }
                }catch(Exception e){throw new RuntimeException(e);}});
                SwingUtilities.invokeAndWait(()-> {try {
                    call("openLogAsync",new Class[]{String.class},small);
                    check(tabs().size()==2,"same file focuses existing tab without duplicate");
                }catch(Exception e){throw new RuntimeException(e);}});
                if(args.length>3) {
                    String alternate=new File(args[2]).getAbsolutePath();
                    String latest=new File(args[3]).getAbsolutePath();
                    java.util.List<Thread> superseded=new ArrayList<>();
                    SwingUtilities.invokeAndWait(()-> {try {
                        call("addNewTab",new Class[]{});
                        for(int i=0;i<12;i++) {
                            call("openLogAsync",new Class[]{String.class},i%2==0 ? alternate : latest);
                            Thread worker=(Thread)field("openLogThread");
                            if(worker!=null)superseded.add(worker);
                        }
                    }catch(Exception e){throw new RuntimeException(e);}});
                    for(int i=0;i<superseded.size()-1;i++) {
                        superseded.get(i).join(5000);
                        check(!superseded.get(i).isAlive(),"superseded log load cancels promptly");
                    }
                    waitLoaded(latest);waitIdle();
                    SwingUtilities.invokeAndWait(()-> {try {
                        check(tabs().size()==3,"rapid replacement reuses the pending tab");
                        check(latest.equals(tab().logFileName),"latest open request wins");
                        check(!selectedSimpleFields().isEmpty(),"rapid same-format opens never clear the remembered selection");
                        check(tab().dataset.getSeriesCount()==selectedSimpleFields().size(),"rapid latest log plots restored fields immediately");
                        ChartTab closing=tab(); call("closeTab",new Class[]{ChartTab.class},closing);
                    }catch(Exception e){throw new RuntimeException(e);}});
                }
                if(args.length>4) {
                    String textLog=new File(args[4]).getAbsolutePath();
                    load(textLog);
                    SwingUtilities.invokeAndWait(()-> {try {
                        check("DATAFLASH_TEXT".equals(tab().currentLogType),"text LOG has an independent format selection scope");
                        check(tab().processorsListModel.getRowCount()==0,"BIN selections never leak into text LOG files");
                        ChartTab closing=tab();call("closeTab",new Class[]{ChartTab.class},closing);
                    }catch(Exception e){throw new RuntimeException(e);}});
                    waitIdle();
                }
                SwingUtilities.invokeAndWait(()-> {try {
                    call("processFile",new Class[]{}); ChartTab closing=tab(); call("closeTab",new Class[]{ChartTab.class},closing);
                }catch(Exception e){throw new RuntimeException(e);}});
                waitIdle();
                SwingUtilities.invokeAndWait(()-> {try {check(tabs().size()==1,"tab closed");check(tab().dataset.getSeriesCount()>=2,"original tab remains usable");}catch(Exception e){throw new RuntimeException(e);}});
                System.out.println("PASS open-new-tab, same-file focus, rapid replacement, switch and close flows");
            }
            if(args.length>5) {
                String ulog=new File(args[5]).getAbsolutePath();
                load(ulog);
                SwingUtilities.invokeAndWait(()-> {try {
                    check(tab().processorsListModel.getRowCount()==0,"BIN selections never leak into ULog files");
                    check(tab().logReader.getErrors().size()>0,"truncated ULog keeps parser notices");
                    boolean errorRow=false;
                    for(int row=0;row<tab().logsTableModel.getRowCount();row++) {
                        if("ERROR".equals(tab().logsTableModel.getValueAt(row,1))) {errorRow=true;break;}
                    }
                    check(errorRow,"ULog parser notice is visible in log messages");
                    check(tab().logReader.getFields().containsKey("flight_0.roll"),"ULog fixture has attitude field");
                    app.addFields(Collections.singletonList("flight_0.roll"));
                }catch(Exception e){throw new RuntimeException(e);}});
                waitIdle();
                SwingUtilities.invokeAndWait(()-> {try {check(tab().dataset.getSeriesCount()==1,"ULog selected field plots");}catch(Exception e){throw new RuntimeException(e);}});
                if(args.length>6) {
                    String secondUlog=new File(args[6]).getAbsolutePath();
                    load(secondUlog);
                    SwingUtilities.invokeAndWait(()-> {try {
                        check(selectedSimpleFields().equals(Collections.singleton("flight_0.roll")),"same-format ULog restores its own prior selection");
                        check(tab().dataset.getSeriesCount()==1,"restored ULog field plots immediately");
                        ChartTab closing=tab();call("closeTab",new Class[]{ChartTab.class},closing);
                    }catch(Exception e){throw new RuntimeException(e);}});
                    waitIdle();
                }
                SwingUtilities.invokeAndWait(()-> {try {ChartTab closing=tab();call("closeTab",new Class[]{ChartTab.class},closing);}catch(Exception e){throw new RuntimeException(e);}});
                waitIdle();
                System.out.println("PASS format-scoped BIN/LOG/ULog selection restore, colors, rapid opens and parser notices");
            }
            SwingUtilities.invokeAndWait(pulse::stop);
            check(uncaught.get()==null,"uncaught async exception: "+uncaught.get());
            System.out.printf(Locale.ROOT,"PASS UI zoom and background load, max_EDT_gap_ms=%.1f (includes offscreen PNG rendering)%n",maxGap.get()/1e6);
        } catch(Throwable failure) { failure.printStackTrace();exitCode=1;
        } finally {SwingUtilities.invokeAndWait(()-> {for(Window w:Window.getWindows())w.dispose();});System.exit(exitCode);}
    }
}
