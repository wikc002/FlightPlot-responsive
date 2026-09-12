package me.drton.flightplot;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** User-level regression for the interaction reports covered by 0.5.8. */
public class InteractionRegression extends UiRegression {
    interface Checked { void run() throws Exception; }
    static void edt(Checked task) throws Exception {
        SwingUtilities.invokeAndWait(() -> {try {task.run();}catch(Exception e){throw new RuntimeException(e);}});
    }
    static Object member(Object object, String name) throws Exception {
        Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);
    }
    static <T> T component(Container parent, Class<T> type) {
        for(Component c:parent.getComponents()) {
            if(type.isInstance(c))return type.cast(c);
            if(c instanceof Container) {T found=component((Container)c,type);if(found!=null)return found;}
        }
        return null;
    }
    static Component named(Container parent, String name) {
        for(Component c:parent.getComponents()) {
            if(name.equals(c.getName()))return c;
            if(c instanceof Container) {Component found=named((Container)c,name);if(found!=null)return found;}
        }
        return null;
    }
    static void setDialogRgb(JDialog dialog, Color color) {
        ((JSpinner)named(dialog,"redSpinner")).setValue(color.getRed());
        ((JSpinner)named(dialog,"greenSpinner")).setValue(color.getGreen());
        ((JSpinner)named(dialog,"blueSpinner")).setValue(color.getBlue());
    }
    static void captureScreen(Window window, String name) throws Exception {
        Thread.sleep(200);
        AtomicReference<Rectangle> bounds=new AtomicReference<>();
        edt(()->bounds.set(new Rectangle(window.getLocationOnScreen(),window.getSize())));
        ImageIO.write(new Robot().createScreenCapture(bounds.get()),"png",outputFile(name));
    }
    static JDialog waitDialog(String name) throws Exception {
        AtomicReference<JDialog> found=new AtomicReference<>();
        for(int i=0;i<500;i++) {
            edt(()->{JDialog d=(JDialog)field(name);if(d!=null && d.isShowing())found.set(d);});
            if(found.get()!=null)return found.get();
            Thread.sleep(10);
        }
        throw new AssertionError("dialog timeout: "+name);
    }
    static void asyncCall(String name) {
        SwingUtilities.invokeLater(()->{try{call(name,new Class[]{});}catch(Exception e){throw new RuntimeException(e);}});
    }
    static void checkSortedTree(Object owner) throws Exception {
        DefaultMutableTreeNode root=(DefaultMutableTreeNode)member(owner,"treeRoot");
        Enumeration<?> nodes=root.depthFirstEnumeration();
        while(nodes.hasMoreElements()) {
            DefaultMutableTreeNode node=(DefaultMutableTreeNode)nodes.nextElement();
            String previous=null;
            for(int i=0;i<node.getChildCount();i++) {
                String name=node.getChildAt(i).toString().split("  ")[0];
                if(previous!=null)check(NaturalFieldOrder.INSTANCE.compare(previous,name)<0,"natural tree order: "+previous+" / "+name);
                previous=name;
            }
        }
    }
    static void doubleClickField(String name) throws Exception {
        FieldsPanel panel=(FieldsPanel)field("fieldsPanel");
        panel.scrollToField(name);
        JTree tree=(JTree)member(panel,"fieldsTree");
        Rectangle rect=tree.getPathBounds(tree.getSelectionPath());
        tree.dispatchEvent(new MouseEvent(tree,MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),0,
                rect.x+Math.max(35,rect.width/2),rect.y+rect.height/2,2,false,MouseEvent.BUTTON1));
    }
    public static void main(String[] args) throws Exception {
        int result=0;
        AtomicReference<Throwable> uncaught=new AtomicReference<>();
        Thread.setDefaultUncaughtExceptionHandler((t,e)->{e.printStackTrace();uncaught.set(e);});
        try {
            edt(()->{
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                call("setGlobalUIFont",new Class[]{javax.swing.plaf.FontUIResource.class},
                        new javax.swing.plaf.FontUIResource(new Font("Microsoft YaHei",Font.PLAIN,12)));
                app=new FlightPlot(false);
                JFrame frame=(JFrame)field("mainFrame");frame.setSize(1280,800);
                call("fitWindowToScreen",new Class[]{});frame.setVisible(true);frame.validate();
                if (!"zh_CN".equals(field("uiLanguage"))) call("toggleLanguage",new Class[]{});
                Map<String,String> sample=new HashMap<>();
                for(String module:Arrays.asList("RCIN","RCOU","motor_2","motor_10"))
                    for(int i=20;i>=1;i--)sample.put(module+".C"+i,"float");
                for(int inst:new int[]{12,2,1})for(int i:new int[]{11,2,1})sample.put("IMU["+inst+"].acc["+i+"]","float");
                ((FieldsPanel)field("fieldsPanel")).setFieldsList(sample);
                checkSortedTree(field("fieldsPanel"));
                check(NaturalFieldOrder.INSTANCE.compare("RCOU.C9","RCOU.C10")<0,"channel 9 before 10");
                System.out.println("PASS natural ordering in both field lists, channels, arrays and instances");
            });

            // Exercise every visible toolbar button and its window lifecycle.
            asyncCall("showOpenLogDialog");
            JDialog openDialog=waitDialog("openLogDialog");
            edt(()->{
                call("showOpenLogDialog",new Class[]{});
                check(field("openLogDialog")==openDialog,"open log dialog is single instance");
                ((JFileChooser)field("logFileChooser")).cancelSelection();
            });
            edt(()->check(((JButton)field("openLogButton")).isEnabled(),"open button recovers after cancel"));

            SwingUtilities.invokeLater(()->{try{call("showAddProcessorDialog",new Class[]{boolean.class},false);}catch(Exception e){throw new RuntimeException(e);}});
            JDialog addDialog=waitDialog("addProcessorDialog");
            captureScreen(addDialog,"add-processor-screen-"+System.getProperty("sun.java2d.uiScale","1")+".png");
            edt(()->{
                check(addDialog.getOwner()==field("mainFrame"),"add processor dialog belongs to main window");
                call("showAddProcessorDialog",new Class[]{boolean.class},false);
                check(field("addProcessorDialog")==addDialog && addDialog.isShowing(),"add processor dialog is single instance");
                check(component(addDialog,JTable.class).getRowCount()>0,"processor choices visible");
                BufferedImage picture=new BufferedImage(addDialog.getWidth(),addDialog.getHeight(),BufferedImage.TYPE_INT_RGB);
                Graphics2D g=picture.createGraphics();addDialog.printAll(g);g.dispose();
                ImageIO.write(picture,"png",outputFile("add-processor-"+System.getProperty("sun.java2d.uiScale","1")+".png"));
                addDialog.setVisible(false);
            });
            edt(()->{
                ((JButton)field("removeProcessorButton")).doClick();
                ((JButton)field("removeAllProcessorsButton")).doClick();
                ((JButton)field("fieldColorButton")).doClick();
                check(field("removeConfirmDialog")==null && field("fieldColorDialog")==null,"empty actions do not open stray windows");
                ((JButton)field("logInfoButton")).doClick();
                LogInfo info=(LogInfo)field("logInfo");
                JFrame infoFrame=info.getFrame();
                check(infoFrame.isShowing(),"log info opens its existing window");
                for(int i=0;i<10;i++)((JButton)field("logInfoButton")).doClick();
                check(info.getFrame()==infoFrame,"repeated log info clicks reuse one window");
                info.setVisible(false);
                System.out.println("PASS six toolbar buttons, ownership, empty states and single-window guards");
            });
            File dir=new File(args[0]);
            File[] files=dir.listFiles((d,n)->n.toLowerCase(Locale.ROOT).endsWith(".bin"));
            Arrays.sort(files,Comparator.comparingLong(File::length));
            check(files.length>=20,"20 real BIN fixtures required");
            load(files[0].getAbsolutePath());
            edt(()->{
                app.addFields(Collections.singletonList("ATT.Roll"));
                app.addFields(Collections.singletonList("ATT.Pitch"));
                app.addFields(Collections.singletonList("ATT.Yaw"));
            });waitIdle();
            edt(()->{
                check(tab().processorsListModel.getRowCount()==3,"three separate processor rows");
                tab().processorsListModel.setValueAt(false,0,0);
            });waitIdle();
            edt(()->doubleClickField("ATT.Roll"));waitIdle();
            edt(()->{
                check(Boolean.TRUE.equals(tab().processorsListModel.getValueAt(0,0)),"double-click rechecks hidden field");
                check(tab().dataset.getSeriesCount()==3,"reactivated field replots");
                Object worker=field("seriesWorker");
                doubleClickField("ATT.Roll");
                check(tab().processorsListModel.getRowCount()==3,"double-click cannot duplicate processor");
                check(((JTable)field("processorsList")).isRowSelected(0),"already visible field highlights existing row");
                check(worker==field("seriesWorker"),"already visible field does not rescan log");
                System.out.println("PASS real field double-click rechecks hidden rows and highlights visible rows");
            });

            Color custom=new Color(37,91,173);
            asyncCall("showFieldColorDialog");
            final JDialog colorDialog=waitDialog("fieldColorDialog");
            captureScreen(colorDialog,"field-color-screen-"+System.getProperty("sun.java2d.uiScale","1")+".png");
            edt(()->{
                call("showFieldColorDialog",new Class[]{});
                check(field("fieldColorDialog")==colorDialog,"color dialog is single instance");
                check(named(colorDialog,"redSpinner")!=null,"compact RGB color editor visible");
                check(colorDialog.getOwner()==field("mainFrame"),"color dialog belongs to main window");
                BufferedImage picture=new BufferedImage(colorDialog.getWidth(),colorDialog.getHeight(),BufferedImage.TYPE_INT_RGB);
                Graphics2D g=picture.createGraphics();colorDialog.printAll(g);g.dispose();
                ImageIO.write(picture,"png",outputFile("field-color-"+System.getProperty("sun.java2d.uiScale","1")+".png"));
                setDialogRgb(colorDialog,custom);
                colorDialog.getRootPane().getDefaultButton().doClick();
            });
            edt(()->{
                ProcessorPreset p=(ProcessorPreset)tab().processorsListModel.getValueAt(0,1);
                check(custom.equals(p.getColors().get("ATT.Roll")),"custom RGB committed from UI");
                check(custom.equals(tab().chart.getXYPlot().getRenderer().getSeriesPaint(0)),"curve color updates without reread");
                check(custom.equals(tab().chart.getXYPlot().getLegendItems().get(0).getLinePaint()),"legend color follows chooser");
            });
            asyncCall("showFieldColorDialog");
            final JDialog cancelColor=waitDialog("fieldColorDialog");
            edt(()->{setDialogRgb(cancelColor,Color.PINK);((JButton)named(cancelColor,"colorCancelButton")).doClick();});
            edt(()->check(custom.equals(((ProcessorPreset)tab().processorsListModel.getValueAt(0,1)).getColors().get("ATT.Roll")),"cancel preserves custom color"));

            // Drive the actual parameter table editor, not the color setter.
            edt(()->{
                JTable params=(JTable)field("parametersTable");
                ((CollapsiblePanel)field("parametersPanel")).toggle();
                int colorRow=-1;
                for(int i=0;i<params.getRowCount();i++)if("Color ATT.Roll".equals(params.getValueAt(i,0)))colorRow=i;
                check(colorRow>=0,"color row available");
                Rectangle cell=params.getCellRect(colorRow,1,true);
                MouseEvent click=new MouseEvent(params,MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),0,cell.x+2,cell.y+2,2,false);
                check(!params.editCellAt(colorRow,1,click),"color opens a dialog independent of table layout");
            });
            JDialog parameterDialog=waitDialog("fieldColorDialog");
            check(parameterDialog!=null,"parameter editor opens compact color dialog");
            Color paramColor=new Color(177,66,99);
            edt(()->{
                setDialogRgb(parameterDialog,paramColor);
                parameterDialog.getRootPane().getDefaultButton().doClick();
            });
            edt(()->{
                ProcessorPreset p=(ProcessorPreset)tab().processorsListModel.getValueAt(0,1);
                check(paramColor.equals(p.getColors().get("ATT.Roll")),"parameter editor commits arbitrary RGB");
                check(!p.getParameters().containsKey("Color ATT.Roll"),"color must not be inserted into numeric parameters");
                call("applyFieldColor",new Class[]{ProcessorPreset.class,String.class,Color.class},p,"ATT.Roll",custom);
                ((CollapsiblePanel)field("parametersPanel")).toggle();
                System.out.println("PASS toolbar and parameter color chooser, arbitrary RGB, OK/cancel, curve/legend and inheritance");
            });

            for(int i=1;i<20;i++)load(files[i].getAbsolutePath());
            edt(()->{
                check(tabs().size()==20,"20 logs and no phantom tabs");
                check(custom.equals(((ProcessorPreset)tab().processorsListModel.getValueAt(0,1)).getColors().get("ATT.Roll")),"custom color inherits through 20 logs");
            });
            // Settle first-show layout changes before measuring repeated tab navigation.
            for(int i=0;i<20;i++){final int index=i;edt(()->((JTabbedPane)field("chartTabbedPane")).setSelectedIndex(index));waitIdle();}
            AtomicLong maxSwitch=new AtomicLong();AtomicInteger rescan=new AtomicInteger();
            for(int i=0;i<60;i++) {
                final int index=i%20;
                edt(()->{
                    Object worker=field("seriesWorker");long start=System.nanoTime();
                    ((JTabbedPane)field("chartTabbedPane")).setSelectedIndex(index);
                    maxSwitch.accumulateAndGet(System.nanoTime()-start,Math::max);
                    if(worker!=field("seriesWorker"))rescan.incrementAndGet();
                });
            }waitIdle();
            edt(()->{
                check(tabs().size()==20,"60 switches do not spawn empty tabs");
                check(rescan.get()==0,"unchanged tabs reuse plots instead of rescanning: "+rescan.get());
                System.out.printf(Locale.ROOT,"PASS 20 real logs / 60 switches / rescans=%d max_switch_ms=%.1f heap_MB=%d%n",rescan.get(),maxSwitch.get()/1e6,(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1024/1024);
            });

            asyncCall("removeAllProcessors");final JDialog confirm=waitDialog("removeConfirmDialog");
            Thread.sleep(200);
            AtomicReference<Rectangle> confirmBounds=new AtomicReference<>();
            edt(()->confirmBounds.set(new Rectangle(confirm.getLocationOnScreen(),confirm.getSize())));
            ImageIO.write(new Robot().createScreenCapture(confirmBounds.get()),"png",
                    outputFile("remove-confirm-screen-"+System.getProperty("sun.java2d.uiScale","1")+".png"));
            edt(()->{
                for(int i=0;i<20;i++)call("removeAllProcessors",new Class[]{});
                int visible=0;for(Window w:Window.getWindows())if(w instanceof JDialog&&w.isShowing())visible++;
                check(visible==1,"20 repeated removes only show one confirmation");
                check(!((JButton)field("removeAllProcessorsButton")).isEnabled(),"remove button disabled while confirmation is open");
                BufferedImage snapshot=new BufferedImage(confirm.getWidth(),confirm.getHeight(),BufferedImage.TYPE_INT_RGB);
                Graphics2D pen=snapshot.createGraphics();confirm.printAll(pen);pen.dispose();
                ImageIO.write(snapshot,"png",outputFile("remove-confirm-"+System.getProperty("sun.java2d.uiScale","1")+".png"));
                JOptionPane pane=component(confirm,JOptionPane.class);pane.setValue(JOptionPane.NO_OPTION);
            });
            edt(()->check(tab().processorsListModel.getRowCount()==3,"cancel remove preserves all processors"));
            edt(()->{
                tab().processorsListModel.setValueAt(false,1,0);
                ((JTable)field("processorsList")).clearSelection();
                call("removeSelectedProcessor",new Class[]{});
                check(tab().processorsListModel.getRowCount()==1,"batch removal deletes two checked rows");
                check(((ProcessorPreset)tab().processorsListModel.getValueAt(0,1)).getParameters().get("Fields").equals("ATT.Pitch"),"unchecked row preserved");
                app.addFields(Collections.singletonList("ATT.Roll"));app.addFields(Collections.singletonList("ATT.Yaw"));
                JTable table=(JTable)field("processorsList");table.clearSelection();table.addRowSelectionInterval(0,0);table.addRowSelectionInterval(2,2);
                call("removeSelectedProcessor",new Class[]{});
                check(tab().processorsListModel.getRowCount()==1,"noncontiguous highlighted rows removed together");
                check(((ProcessorPreset)tab().processorsListModel.getValueAt(0,1)).getParameters().get("Fields").equals("ATT.Roll"),"unhighlighted row preserved");
            });waitIdle();
            asyncCall("removeAllProcessors");final JDialog yes=waitDialog("removeConfirmDialog");
            edt(()->component(yes,JOptionPane.class).setValue(JOptionPane.YES_OPTION));waitIdle();
            edt(()->{
                check(tab().processorsListModel.getRowCount()==0 && tab().dataset.getSeriesCount()==0,"confirm clears rows and curves");
                app.addFields(Arrays.asList("ATT.Roll","ATT.Pitch"));
            });waitIdle();
            asyncCall("showFieldColorDialog");final JDialog groupedColor=waitDialog("fieldColorDialog");
            edt(()->{
                JComboBox<?> choice=component(groupedColor,JComboBox.class);
                check(choice.getItemCount()==2,"grouped processor exposes each field for recoloring");
                choice.setSelectedItem("ATT.Pitch");
                setDialogRgb(groupedColor,Color.ORANGE);
                groupedColor.getRootPane().getDefaultButton().doClick();
            });
            edt(()->{
                ProcessorPreset p=(ProcessorPreset)tab().processorsListModel.getValueAt(0,1);
                check(Color.ORANGE.equals(p.getColors().get("ATT.Pitch")),"only requested grouped field recolored");
                check(!Color.ORANGE.equals(p.getColors().get("ATT.Roll")),"sibling field color preserved");
                call("toggleLanguage",new Class[]{});
                CollapsiblePanel panel=(CollapsiblePanel)field("parametersPanel");
                JLabel label=(JLabel)member(panel,"titleLabel");
                check(label.getIcon()!=null,"collapse affordance is a drawn icon");
                String title=label.getText();for(int i=0;i<40;i++)panel.toggle();
                check(title.equals(label.getText()),"toggle does not corrupt title");
                call("toggleLanguage",new Class[]{});
                JTabbedPane tabsUI=(JTabbedPane)field("chartTabbedPane");
                JButton plus=(JButton)tabsUI.getTabComponentAt(tabsUI.getTabCount()-1);
                for(int i=0;i<15;i++)plus.doClick(0);
                check(tabs().size()==21,"repeated plus reuses one empty tab");
                ChartTab empty=tab();call("closeTab",new Class[]{ChartTab.class},empty);
                check(tabs().size()==20,"closing empty tab creates no phantom tab");
            });waitIdle();
            edt(()->{
                ((JFrame)field("mainFrame")).validate();
                JPanel panel=(JPanel)field("mainPanel");
                BufferedImage picture=new BufferedImage(panel.getWidth(),panel.getHeight(),BufferedImage.TYPE_INT_RGB);
                Graphics2D g=picture.createGraphics();panel.printAll(g);g.dispose();
                ImageIO.write(picture,"png",outputFile("interaction-20logs-"+System.getProperty("sun.java2d.uiScale","1")+".png"));
                ImageIO.write(picture,"png",outputFile("top-buttons-"+System.getProperty("sun.java2d.uiScale","1")+".png"));
                System.out.println("PASS batch removal, single confirmation, vector disclosure icons and explicit plus button");
            });
            check(uncaught.get()==null,"async error: "+uncaught.get());
            System.out.println("PASS all six interaction regressions");
        } catch(Throwable e){e.printStackTrace();result=1;}
        finally {SwingUtilities.invokeAndWait(()->{for(Window w:Window.getWindows())w.dispose();});System.exit(result);}
    }
}
