package me.drton.flightplot;

import me.drton.flightplot.export.GPXTrackExporter;
import me.drton.flightplot.export.KMLTrackExporter;
import me.drton.flightplot.export.TrackExportDialog;
import me.drton.flightplot.export.TrackExporter;
import me.drton.flightplot.processors.PlotProcessor;
import me.drton.flightplot.processors.ProcessorsList;
import me.drton.flightplot.processors.Simple;
import me.drton.jmavlib.log.LogReader;
import me.drton.jmavlib.log.px4.PX4LogReader;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.event.ChartChangeEvent;
import org.jfree.chart.event.ChartChangeEventType;
import org.jfree.chart.event.ChartChangeListener;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.AbstractRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.Range;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.Layer;
import org.jfree.ui.RectangleAnchor;
import org.jfree.ui.TextAnchor;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.io.*;
import java.nio.charset.Charset;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * User: ton Date: 03.06.13 Time: 23:24
 */
public class FlightPlot {
    private static final int TIME_MODE_LOG_START = 0;
    private static final int TIME_MODE_BOOT = 1;
    private static final int TIME_MODE_GPS = 2;
    private static final double SECONDS_PER_MINUTE = 60.0;
    private static final int MIN_TOP_MINUTE_LABELS = 6;
    private static final int MAX_TOP_MINUTE_LABELS = 24;
    private static final int TOP_MINUTE_LABEL_PIXEL_SPACING = 120;
    private static final NumberFormat doubleNumberFormat = NumberFormat.getInstance(Locale.ROOT);

    private JTabbedPane chartTabbedPane;
    private final Workspace workspace = new Workspace();
    private int tabCounter = 1;
    private boolean addingTab;
    private JDialog removeConfirmDialog;
    private JDialog fieldColorDialog;
    private JButton fieldColorButton;
    private DialogCoordinator dialogs;
    private final LogService logService = new LogService();



    static {
        doubleNumberFormat.setGroupingUsed(false);
        doubleNumberFormat.setMinimumFractionDigits(1);
        doubleNumberFormat.setMaximumFractionDigits(10);
    }

    private static String appName = "FlightPlot";
    private static String version = "0.5.8";
    private static String appNameAndVersion = appName + " v." + version;
    private static String colorParamPrefix = "Color ";
    private final Preferences preferences;
    private JFrame mainFrame;
    private JLabel statusLabel;
    private JPanel mainPanel;
    private JTable parametersTable;
    private JTable logTable;
    private DefaultTableModel parametersTableModel;
    private DefaultTableModel logsTableModel;
    private ChartPanel chartPanel;
    private JTable processorsList;
    private DefaultTableModel processorsListModel;
    private TableModelListener parameterChangedListener;
    private JButton addProcessorButton;
    private JButton removeProcessorButton;
    private JButton removeAllProcessorsButton;
    private JButton openLogButton;
    private JComboBox presetComboBox;
    private List<Preset> presetsList = new ArrayList<Preset>();
    private JButton deletePresetButton;
    private JButton logInfoButton;
    private JCheckBox markerCheckBox;
    private JCheckBox topMinuteCheckBox;
    private JButton languageToggleButton;
    private JButton savePresetButton;
    private JCheckBoxMenuItem autosavePresets;
    private JCheckBoxMenuItem showLegendItem;
    private JRadioButtonMenuItem[] timeModeItems;
    private LogReader logReader = null;
    private XYSeriesCollection dataset;
    private JFreeChart chart;
    private ColorSupplier colorSupplier;
    private ProcessorsList processorsTypesList;
    private File lastPresetDirectory = null;
    private AddProcessorDialog addProcessorDialog;
    private LogInfo logInfo;
    private File lastLogDirectory = null;
    private JFileChooser logFileChooser;
    private JDialog openLogDialog;
    private boolean openLogDialogShowing;
    private FileNameExtensionFilter presetExtensionFilter = new FileNameExtensionFilter("FlightPlot Presets (*.fplot)",
            "fplot");
    private FileNameExtensionFilter parametersExtensionFilter = new FileNameExtensionFilter("Parameters (*.txt)", "txt");
    private AtomicBoolean invokeProcessFile = new AtomicBoolean(false);
    private AtomicBoolean processFilePending = new AtomicBoolean(false);
    private AtomicBoolean topMinuteMarkerUpdating = new AtomicBoolean(false);
    private AtomicBoolean suppressRangeProcess = new AtomicBoolean(false);
    private AtomicInteger openLogRequestCounter = new AtomicInteger(0);
    private volatile int latestOpenLogRequestId = 0;
    private Thread openLogThread;
    private ChartTab openingLogTab;
    private String openingLogPath;
    private String openingOriginalTabTitle;
    private boolean openingCreatedTab;
    private JDialog messageDialog;
    private javax.swing.Timer zoomDebounceTimer = null;
    private static final int ZOOM_DEBOUNCE_MS = 150;
    private TrackExportDialog trackExportDialog;
    private PlotExportDialog plotExportDialog;
    private NumberAxis domainAxisSeconds;
    private DateAxis domainAxisDate;
    private int timeMode = 0;
    private boolean autosave = false;
    private boolean showLegend = true;
    private List<Map<String, Integer>> seriesIndex = new ArrayList<Map<String, Integer>>();
    private ProcessorPreset editingProcessor = null;
    private List<ProcessorPreset> activeProcessors = new ArrayList<ProcessorPreset>();
    private List<ValueMarker> topMinuteMarkers = new ArrayList<ValueMarker>();
    private Range lastTimeRange = null;
    private String currentPreset = null;
    private String currentLogType = null;
    private final ProcessorSelectionMemory selectionMemory = new ProcessorSelectionMemory();
    private boolean restoringProcessorSelection = false;
    private String uiLanguage = "en";
    private FieldsPanel fieldsPanel;
    private Font chartTextFont = null;
    private JLabel presetLabel;
    private JLabel processorsLabel;
    private CollapsiblePanel parametersPanel;
    private CollapsiblePanel logsPanel;
    private JMenu fileMenu;
    private JMenu viewMenu;
    private JMenuItem fileOpenItem;
    private JMenuItem importPresetItem;
    private JMenuItem exportPresetItem;
    private JMenuItem exportAsImageItem;
    private JMenuItem exportTrackItem;
    private JMenuItem exportParametersItem;
    private JMenuItem exitMenuItem;

    public FlightPlot() {
        this(true);
    }

    FlightPlot(boolean showWindow) {
        initializeUi();
        addingTab = true;
        chartTabbedPane.addTab("+", new JPanel());
        chartTabbedPane.setEnabledAt(0, false);
        JButton newTabButton = new JButton("+");
        newTabButton.setMargin(new Insets(0, 6, 0, 6));
        newTabButton.addActionListener(e -> {
            for (int i = 0; i < workspace.size(); i++) {
                if (workspace.tabs().get(i).logReader == null && workspace.tabs().get(i) != openingLogTab) {
                    chartTabbedPane.setSelectedIndex(i);
                    return;
                }
            }
            addNewTab();
        });
        chartTabbedPane.setTabComponentAt(0, newTabButton);
        addingTab = false;
        Map<String, TrackExporter> exporters = new LinkedHashMap<String, TrackExporter>();
        for (TrackExporter exporter : new TrackExporter[]{
                new KMLTrackExporter(),
                new GPXTrackExporter()
        }) {
            exporters.put(exporter.getName(), exporter);
        }
        trackExportDialog = new TrackExportDialog(exporters);
        plotExportDialog = new PlotExportDialog(this);
        trackExportDialog.setUiLanguage(uiLanguage);
        plotExportDialog.setUiLanguage(uiLanguage);

        preferences = Preferences.userRoot().node(appName);
        mainFrame = new JFrame(appNameAndVersion);
        dialogs = new DialogCoordinator(mainFrame);
        mainFrame.setContentPane(mainPanel);
        mainFrame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        mainFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onQuit();
            }
        });
        mainFrame.setDropTarget(new DropTarget() {
            public synchronized void drop(DropTargetDropEvent evt) {
                boolean accepted = false;
                try {
                    evt.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> droppedFiles = (List<File>) evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    accepted = handleDroppedFiles(droppedFiles);
                } catch (Exception ex) {
                    setStatus("zh_CN".equals(uiLanguage) ? "无法处理拖入的日志文件" : "Unable to process dropped log file");
                } finally {
                    evt.dropComplete(accepted);
                }
            }
        });

        createMenuBar();
        java.util.List<String> processors = new ArrayList<String>(processorsTypesList.getProcessorsList());
        Collections.sort(processors);
        addProcessorDialog = new AddProcessorDialog(mainFrame, processors.toArray(new String[processors.size()]));
        logInfo = new LogInfo();
        addProcessorButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showAddProcessorDialog(false);
            }
        });
        removeProcessorButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                removeSelectedProcessor();
            }
        });
        removeAllProcessorsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                removeAllProcessors();
            }
        });
        openLogButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showOpenLogDialog();
            }
        });
        logInfoButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                logInfo.setVisible(true);
            }
        });
        processorsList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        processorsList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent listSelectionEvent) {
                // If processor changed during editing skip this event to avoid inconsistent editor state
                if (editingProcessor == null) {
                    showProcessorParameters();
                }
            }
        });
        processorsList.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "Enter");
        processorsList.getActionMap().put("Enter", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                showAddProcessorDialog(true);
            }
        });
        processorsList.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "DeleteRows");
        processorsList.getActionMap().put("DeleteRows", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                removeSelectedProcessor();
            }
        });
        processorsList.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_A, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()), "SelectAllRows");
        processorsList.getActionMap().put("SelectAllRows", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                if (processorsListModel.getRowCount() > 0) {
                    processorsList.setRowSelectionInterval(0, processorsListModel.getRowCount() - 1);
                }
            }
        });
        processorsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                JTable target = (JTable) e.getSource();
                if (e.getClickCount() > 1 && target.getSelectedColumn() == 1 && target.getSelectedRowCount() == 1) {
                    showAddProcessorDialog(true);
                }
            }
        });
        processorsListModel.addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                if (e.getType() == TableModelEvent.UPDATE) {
                    if (e.getColumn() == 0) {
                        // Update processor preset field here to remember visibility state
                        ProcessorPreset pp = (ProcessorPreset) processorsListModel.getValueAt(e.getFirstRow(), 1);
                        if ((Boolean) processorsListModel.getValueAt(e.getFirstRow(), 0)) {
                            pp.setVisible(true);
                        } else {
                            pp.setVisible(false);
                        }

                        updatePresetEdited(true);
                        processFile();
                    }
                }
            }
        });
        parameterChangedListener = new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                if (e.getType() == TableModelEvent.UPDATE) {
                    int row = e.getFirstRow();
                    onParameterChanged(row);
                    editingProcessor = null;
                }
            }
        };
        parametersTableModel.addTableModelListener(parameterChangedListener);

        presetComboBox.setMaximumRowCount(30);
        presetComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onPresetAction(e);
            }
        });
        savePresetButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onSavePreset();
            }
        });
        deletePresetButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onDeletePreset();
            }
        });
        markerCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                setChartMarkers();
            }
        });
        topMinuteCheckBox.setSelected(true);
        topMinuteCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                updateTopMinuteMarkersForCurrentRange();
            }
        });
        languageToggleButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                toggleLanguage();
            }
        });

        addNewTab();
        applyLanguageTexts();
        mainFrame.pack();
        mainFrame.setVisible(showWindow);

        // Load preferences
        try {
            if (showWindow) loadPreferences();
        } catch (BackingStoreException e) {
            setStatus("zh_CN".equals(uiLanguage) ? "设置读取失败，已使用默认设置" : "Settings could not be read; defaults are in use");
        }
        fitWindowToScreen();
    }

    private void fitWindowToScreen() {
        GraphicsConfiguration gc = mainFrame.getGraphicsConfiguration();
        Rectangle screen = gc.getBounds();
        Insets in = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        Rectangle usable = new Rectangle(screen.x+in.left, screen.y+in.top,
                screen.width-in.left-in.right, screen.height-in.top-in.bottom);
        mainFrame.setMinimumSize(new Dimension(Math.min(640,usable.width), Math.min(420,usable.height)));
        int width=Math.min(mainFrame.getWidth(),usable.width), height=Math.min(mainFrame.getHeight(),usable.height);
        mainFrame.setBounds(Math.max(usable.x,Math.min(mainFrame.getX(),usable.x+usable.width-width)),
                Math.max(usable.y,Math.min(mainFrame.getY(),usable.y+usable.height-height)),width,height);
    }

    private void updateAndSavePresets() {
        // Update and save current preset if selected
        if (currentPreset != null) {
            Preset preset = formatPreset(currentPreset);
            updatePreset(preset);
            loadPresetsList();
            savePreferences();
        }
    }

    public static void main(String[] args)
            throws ClassNotFoundException, UnsupportedLookAndFeelException, InstantiationException,
            IllegalAccessException {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (OSValidator.isMac()) {
                    System.setProperty("apple.laf.useScreenMenuBar", "true");
                }
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {}
                setGlobalUIFont(new javax.swing.plaf.FontUIResource(new Font("Microsoft YaHei", Font.PLAIN, 12)));
                FlightPlot app = new FlightPlot();
                if (args.length > 0) SwingUtilities.invokeLater(() -> app.openLogAsync(new File(args[0]).getAbsolutePath()));
            }
        });
    }

    private static void setGlobalUIFont(javax.swing.plaf.FontUIResource f) {
        java.util.Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
            if (value instanceof javax.swing.plaf.FontUIResource) {
                UIManager.put(key, f);
            }
        }
    }

    private static Object formatParameterValue(Object value) {
        Object returnValue;
        if (value instanceof Double) {
            returnValue = doubleNumberFormat.format(value);
        } else if (value instanceof Color) {
            returnValue = value;
        } else {
            returnValue = value.toString();
        }
        return returnValue;
    }

    private void onQuit() {
        dialogs.disposeAll();
        if(addProcessorDialog!=null) addProcessorDialog.dispose();
        if(logInfo!=null) logInfo.dispose();
        cancelOpeningLog(false);
        synchronized(seriesWorkerLock) {
            if(seriesWorker!=null && !seriesWorker.isDone()) seriesWorker.cancel(true);
        }

        workspace.closeAll();

        savePreferences();
        System.exit(0);
    }

    private void onPresetAction(ActionEvent e) {
        if ("comboBoxEdited".equals(e.getActionCommand())) {
            // Save preset
            onSavePreset();
        } else if ("comboBoxChanged".equals(e.getActionCommand())) {
            String oldPreset = currentPreset;
            Object selection = presetComboBox.getSelectedItem();

            // Load selected preset
            if (selection == null) {
                processorsListModel.setRowCount(0);
                updateUsedColors();
                currentPreset = null;
            } else if (selection instanceof Preset) {
                loadPreset((Preset) selection);
                currentPreset = ((Preset) selection).getTitle();
            }
            updatePresetEdited(false);
            if ((currentPreset == null && oldPreset != null) || (currentPreset != null && !currentPreset.equals(oldPreset))) {
                processFile();
            }
        }
    }

    private void onSavePreset() {
        String presetTitle = presetComboBox.getSelectedItem().toString();
        if (presetTitle.isEmpty()) {
            setStatus("Enter preset name first");
            return;
        }
        Preset preset = formatPreset(presetTitle);
        updatePreset(preset);
        loadPresetsList();
        updatePresetEdited(false);
        savePreferences();
    }

    private void updatePreset(Preset preset) {
        boolean addNew = true;
        for (int i = 0; i < presetsList.size(); i++) {
            if (preset.getTitle().equals(presetsList.get(i).getTitle())) {
                // Update existing preset
                addNew = false;
                presetsList.set(i, preset);
                setStatus("Preset \"" + preset.getTitle() + "\" updated");
                break;
            }
        }
        if (addNew) {
            // Add new preset
            presetsList.add(preset);
            currentPreset = preset.getTitle();
            setStatus("Preset \"" + preset.getTitle() + "\" added");
        }
    }

    private void onDeletePreset() {
        int i = presetComboBox.getSelectedIndex();
        Preset removedPreset = null;
        if (i > 0) {
            removedPreset = presetsList.remove(i - 1);
        }
        if (removedPreset != null) {
            loadPresetsList();
            setStatus("Preset \"" + removedPreset.getTitle() + "\" deleted");
            savePreferences();
        }
    }

    private void updatePresetEdited(boolean edited) {
        presetComboBox.getEditor().getEditorComponent().setForeground(edited ? Color.GRAY : Color.BLACK);

        if (edited && autosave) {
            updateAndSavePresets();
        }
    }

    private void loadPreferences() throws BackingStoreException {
        uiLanguage = preferences.get("UiLanguage", "en");
        I18n.setLanguage(uiLanguage);
        applyLanguageTexts();
        PreferencesUtil.loadWindowPreferences(mainFrame, preferences.node("MainWindow"), 800, 600);
        PreferencesUtil.loadWindowPreferences(logInfo.getFrame(), preferences.node("LogInfoFrame"), 600, 600);
        String logDirectoryStr = preferences.get("LogDirectory", null);
        if (logDirectoryStr != null) {
            lastLogDirectory = new File(logDirectoryStr);
        }
        String presetDirectoryStr = preferences.get("PresetDirectory", null);
        if (presetDirectoryStr != null) {
            lastPresetDirectory = new File(presetDirectoryStr);
        }
        Preferences presets = preferences.node("Presets");
        presetsList.clear();
        for (String p : presets.keys()) {
            try {
                Preset preset = Preset.unpackJSONObject(new JSONObject(presets.get(p, "{}")));
                if (preset != null) {
                    presetsList.add(preset);
                }
            } catch (Exception e) {
                setStatus("zh_CN".equals(uiLanguage) ? "有一项预设无法读取，已跳过" : "One preset could not be read and was skipped");
            }
        }
        loadPresetsList();
        timeMode = Integer.parseInt(preferences.get("TimeMode", "0"));
        timeModeItems[timeMode].setSelected(true);
        autosave = preferences.getBoolean("Autosave", false);
        if (autosavePresets != null) autosavePresets.setState(autosave);
        markerCheckBox.setSelected(preferences.getBoolean("ShowMarkers", false));
        topMinuteCheckBox.setSelected(preferences.getBoolean("ShowTopMinute", true));
        showLegend = preferences.getBoolean("ShowLegend", true);
        if (showLegendItem != null) showLegendItem.setSelected(showLegend);
        applyLegendVisibility();
        trackExportDialog.loadPreferences(preferences);
        plotExportDialog.loadPreferences(preferences);
    }

    private void loadPresetsList() {
        Comparator<Preset> presetComparator = new Comparator<Preset>() {
            @Override
            public int compare(Preset o1, Preset o2) {
                return o1.getTitle().compareToIgnoreCase(o2.getTitle());
            }
        };
        Collections.sort(presetsList, presetComparator);

        // need to keep this because the change logic of the list will clean it
        String currentPresetCached = currentPreset;

        presetComboBox.removeAllItems();
        presetComboBox.addItem(null);
        Preset selectPreset = null;
        for (Preset preset : presetsList) {
            presetComboBox.addItem(preset);
            if (preset.getTitle().equals(currentPresetCached)) {
                currentPreset = currentPresetCached;
                selectPreset = preset;
            }
        }
        presetComboBox.setSelectedItem(selectPreset);
    }

    private void savePreferences() {
        try {
            preferences.clear();
            for (String child : preferences.childrenNames()) {
                preferences.node(child).removeNode();
            }
            PreferencesUtil.saveWindowPreferences(mainFrame, preferences.node("MainWindow"));
            PreferencesUtil.saveWindowPreferences(logInfo.getFrame(), preferences.node("LogInfoFrame"));
            if (lastLogDirectory != null) {
                preferences.put("LogDirectory", lastLogDirectory.getAbsolutePath());
            }
            if (lastPresetDirectory != null) {
                preferences.put("PresetDirectory", lastPresetDirectory.getAbsolutePath());
            }
            Preferences presetsPref = preferences.node("Presets");
            for (int i = 0; i < presetComboBox.getItemCount(); i++) {
                Object object = presetComboBox.getItemAt(i);
                if (object != null) {
                    Preset preset = (Preset) object;
                    try {
                        presetsPref.put(preset.getTitle(), preset.packJSONObject().toString());
                    } catch (IOException e) {
                        setStatus("zh_CN".equals(uiLanguage) ? "预设保存失败" : "Unable to save preset");
                    }
                }
            }
            preferences.put("TimeMode", Integer.toString(timeMode));
            preferences.put("UiLanguage", uiLanguage);
            preferences.putBoolean("Autosave", autosave);
            preferences.putBoolean("ShowMarkers", markerCheckBox.isSelected());
            preferences.putBoolean("ShowTopMinute", topMinuteCheckBox.isSelected());
            preferences.putBoolean("ShowLegend", showLegend);
            trackExportDialog.savePreferences(preferences);
            plotExportDialog.savePreferences(preferences);
            preferences.sync();
        } catch (BackingStoreException e) {
            setStatus("zh_CN".equals(uiLanguage) ? "设置保存失败" : "Unable to save settings");
        }
    }

    private void toggleLanguage() {
        uiLanguage = "zh_CN".equals(uiLanguage) ? "en" : "zh_CN";
        I18n.setLanguage(uiLanguage);
        applyLanguageTexts();
    }

    private void applyLanguageTexts() {
        if (processorsListModel != null) {
            for (int i = 0; i < processorsListModel.getRowCount(); i++) {
                ProcessorPreset pp = (ProcessorPreset) processorsListModel.getValueAt(i, 1);
                pp.setUiLanguage(uiLanguage);
            }
            processorsListModel.fireTableDataChanged();
            processorsList.repaint();
        }
        openLogButton.setText(tr("open_log"));
        addProcessorButton.setText(tr("add_processor"));
        removeProcessorButton.setText(tr("remove_processor"));
        removeProcessorButton.setToolTipText("zh_CN".equals(uiLanguage)
                ? "多行高亮时移除高亮行；否则移除已勾选项（Ctrl/Shift 可多选行）"
                : "Remove highlighted rows when multiple rows are selected; otherwise remove checked items (Ctrl/Shift selects rows)");
        fieldColorButton.setText("zh_CN".equals(uiLanguage) ? "字段颜色" : "Field Color");
        removeAllProcessorsButton.setText(tr("remove_all_processors"));
        logInfoButton.setText(tr("log_info"));
        savePresetButton.setText(tr("save_preset"));
        deletePresetButton.setText(tr("delete_preset"));
        markerCheckBox.setText(tr("markers"));
        if (topMinuteCheckBox != null) {
            topMinuteCheckBox.setText("zh_CN".equals(uiLanguage) ? "显示分钟" : "Minute");
        }
        if (presetLabel != null) {
            presetLabel.setText(tr("preset"));
        }
        if (processorsLabel != null) {
            processorsLabel.setText(tr("processors"));
        }
        if (parametersPanel != null) {
            parametersPanel.setTitle(tr("parameters"));
        }
        if (logsPanel != null) {
            logsPanel.setTitle(tr("log_messages"));
        }
        if (languageToggleButton != null) {
            languageToggleButton.setText("zh_CN".equals(uiLanguage) ? "EN" : "中文");
        }
        if (fileMenu != null) {
            fileMenu.setText(tr("menu_file"));
        }
        if (viewMenu != null) {
            viewMenu.setText(tr("menu_view"));
        }
        if (showLegendItem != null) {
            showLegendItem.setText(tr("show_legend"));
        }
        if (fileOpenItem != null) {
            fileOpenItem.setText(tr("open_log_menu"));
        }
        if (importPresetItem != null) {
            importPresetItem.setText(tr("import_preset"));
        }
        if (exportPresetItem != null) {
            exportPresetItem.setText(tr("export_preset"));
        }
        if (autosavePresets != null) {
            autosavePresets.setText(tr("autosave_presets"));
        }
        if (exportAsImageItem != null) {
            exportAsImageItem.setText(tr("export_as_image"));
        }
        if (exportTrackItem != null) {
            exportTrackItem.setText(tr("export_track"));
        }
        if (exportParametersItem != null) {
            exportParametersItem.setText(tr("export_parameters"));
        }
        if (exitMenuItem != null) {
            exitMenuItem.setText(tr("exit"));
        }
        if (timeModeItems != null && timeModeItems.length == 3) {
            timeModeItems[TIME_MODE_LOG_START].setText(tr("time_mode_log_start"));
            timeModeItems[TIME_MODE_BOOT].setText(tr("time_mode_boot"));
            timeModeItems[TIME_MODE_GPS].setText(tr("time_mode_gps"));
        }
        if (processorsList != null && processorsList.getColumnModel().getColumnCount() >= 2) {
            processorsList.getColumnModel().getColumn(0).setHeaderValue(tr("enabled"));
            processorsList.getColumnModel().getColumn(1).setHeaderValue(tr("processor"));
            processorsList.getTableHeader().repaint();
            adjustProcessorsColumnWidth();
            processorsList.repaint();
        }
        if (parametersTable != null && parametersTable.getColumnModel().getColumnCount() >= 2) {
            parametersTable.getColumnModel().getColumn(0).setHeaderValue(tr("parameter"));
            parametersTable.getColumnModel().getColumn(1).setHeaderValue(tr("value"));
            parametersTable.getTableHeader().repaint();
        }
        if (logTable != null && logTable.getColumnModel().getColumnCount() >= 3) {
            logTable.getColumnModel().getColumn(0).setHeaderValue(tr("time"));
            logTable.getColumnModel().getColumn(1).setHeaderValue(tr("level"));
            logTable.getColumnModel().getColumn(2).setHeaderValue(tr("message"));
            logTable.getTableHeader().repaint();
        }
        if (fieldsPanel != null) {
            fieldsPanel.setUiLanguage(uiLanguage);
            if (logReader != null) {
                fieldsPanel.setFieldsList(logReader.getFields());
            }
        }
        if (addProcessorDialog != null) {
            addProcessorDialog.setUiLanguage(uiLanguage);
        }
        if (logInfo != null) {
            logInfo.setUiLanguage(uiLanguage);
            if (logReader != null) {
                logInfo.updateInfo(logReader);
            }
        }
        if (trackExportDialog != null) {
            trackExportDialog.setUiLanguage(uiLanguage);
        }
        if (plotExportDialog != null) {
            plotExportDialog.setUiLanguage(uiLanguage);
        }
        applyChartTextFont();
        if (logReader != null) {
            processFile();
        } else if (chartPanel != null) {
            chartPanel.repaint();
        }
    }

    private String buildProcessorDisplayText(ProcessorPreset processorPreset) {
        String title = processorPreset.getTitle();
        String processorType = processorPreset.getProcessorType();
        if ("zh_CN".equals(uiLanguage) && title != null) {
            String zh = FieldNameLocalizer.toZhCn(title);
            if (!zh.equals(title)) {
                return title + "  |  " + zh + " [" + processorType + "]";
            }
        }
        return title + " [" + processorType + "]";
    }

    private String ellipsizeMiddle(String text, FontMetrics metrics, int maxWidth) {
        if (text == null || maxWidth <= 0) {
            return text;
        }
        if (metrics.stringWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = metrics.stringWidth(ellipsis);
        if (ellipsisWidth >= maxWidth) {
            return ellipsis;
        }
        int left = 0;
        int right = text.length() - 1;
        String leftPart = "";
        String rightPart = "";
        while (left <= right) {
            if (metrics.stringWidth(leftPart + ellipsis + rightPart) >= maxWidth) {
                break;
            }
            if (leftPart.length() <= rightPart.length()) {
                leftPart += text.charAt(left++);
            } else {
                rightPart = text.charAt(right--) + rightPart;
            }
            if (metrics.stringWidth(leftPart + ellipsis + rightPart) > maxWidth) {
                if (leftPart.length() > rightPart.length() && leftPart.length() > 0) {
                    leftPart = leftPart.substring(0, leftPart.length() - 1);
                } else if (rightPart.length() > 0) {
                    rightPart = rightPart.substring(1);
                }
                break;
            }
        }
        return leftPart + ellipsis + rightPart;
    }

    private void adjustProcessorsColumnWidth() {
        if (processorsList == null || processorsList.getColumnModel().getColumnCount() < 2) {
            return;
        }
        int tableWidth = processorsList.getWidth();
        if (tableWidth <= 0) {
            tableWidth = processorsList.getPreferredScrollableViewportSize().width;
        }
        int firstColWidth = processorsList.getColumnModel().getColumn(0).getWidth();
        int desired = "zh_CN".equals(uiLanguage) ? 330 : 240;
        int maxFit = Math.max(120, tableWidth - firstColWidth - 8);
        int finalWidth = Math.min(desired, maxFit);
        processorsList.getColumnModel().getColumn(1).setPreferredWidth(finalWidth);
        processorsList.getColumnModel().getColumn(1).setMinWidth(Math.min(finalWidth, 140));
    }

    private String tr(String key) {
        return I18n.tr(key);
    }

    private void loadPreset(Preset preset) {
        processorsListModel.setRowCount(0);
        for (ProcessorPreset pp : preset.getProcessorPresets()) {
            updatePresetParameters(pp, null);
            processorsListModel.addRow(new Object[]{pp.isVisible(), pp.clone()});
        }
        updateUsedColors();
    }

    private Preset formatPreset(String title) {
        List<ProcessorPreset> processorPresets = new ArrayList<ProcessorPreset>();
        for (int i = 0; i < processorsListModel.getRowCount(); i++) {
            processorPresets.add(((ProcessorPreset) processorsListModel.getValueAt(i, 1)).clone());
        }
        return new Preset(title, processorPresets);
    }

    private void createUIComponents() throws IllegalAccessException, InstantiationException {
        processorsTypesList = new ProcessorsList();
        colorSupplier = new ColorSupplier();

        chartTabbedPane = new JTabbedPane();
        chartTabbedPane.setTransferHandler(createDataTransferHandler());
        chartTabbedPane.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (addingTab) return;
                int index = chartTabbedPane.getSelectedIndex();
                if (index == -1) return;
                if (index < workspace.size() && index != workspace.currentIndex()) {
                    switchTab(index);
                }
            }
        });

        processorsListModel = new DefaultTableModel();
        processorsList = new JTable(processorsListModel);
        processorsList.setRowHeight(24);

        parametersTableModel = new DefaultTableModel();
        parametersTable = new JTable(parametersTableModel) {
            @Override
            public boolean editCellAt(int row, int column, java.util.EventObject event) {
                if (row >= 0 && column >= 0 && getValueAt(row, column) instanceof Color) {
                    if (!(event instanceof MouseEvent) || ((MouseEvent) event).getClickCount() >= 2) {
                        final ChartTab owner = getCurrentTab();
                        final ProcessorPreset preset = getSelectedProcessor();
                        final String key = getValueAt(row, 0).toString().substring(colorParamPrefix.length());
                        // A modal color choice must survive table layout/focus changes.
                        // Use the same owned dialog as the toolbar, without a cell edit transaction.
                        SwingUtilities.invokeLater(() -> {
                            if (getCurrentTab() == owner && getSelectedProcessor() == preset)
                                showFieldColorDialog(key);
                        });
                    }
                    return false;
                }
                return super.editCellAt(row, column, event);
            }
            @Override
            public String getToolTipText(MouseEvent e) {
                java.awt.Point p = e.getPoint();
                int rowIndex = rowAtPoint(p);
                int colIndex = columnAtPoint(p);
                if (rowIndex >= 0 && colIndex >= 0) {
                    Object value = getValueAt(rowIndex, colIndex);
                    return value != null ? value.toString() : null;
                }
                return super.getToolTipText(e);
            }
        };
        parametersTable.setRowHeight(24);
        parametersTable.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "startEditing");
        parametersTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        parametersTable.putClientProperty("JTable.autoStartsEdit", false);
        parametersTable.putClientProperty("terminateEditOnFocusLost", true);

        logsTableModel = new DefaultTableModel();
        logTable = new JTable(logsTableModel) {
            @Override
            public String getToolTipText(MouseEvent e) {
                java.awt.Point p = e.getPoint();
                int rowIndex = rowAtPoint(p);
                int colIndex = columnAtPoint(p);
                if (rowIndex >= 0 && colIndex >= 0) {
                    Object value = getValueAt(rowIndex, colIndex);
                    return value != null ? value.toString() : null;
                }
                return super.getToolTipText(e);
            }
        };
        logTable.setRowHeight(24);
        logTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        logTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        logTable.getSelectionModel().addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            @Override
            public void valueChanged(javax.swing.event.ListSelectionEvent e) {
                if (!e.getValueIsAdjusting() && logTable.getSelectedRow() >= 0 && fieldsPanel != null) {
                    int modelRow = logTable.convertRowIndexToModel(logTable.getSelectedRow());
                    Object message = logsTableModel.getValueAt(modelRow, 2);
                    if (message != null) {
                        String msgStr = message.toString();
                        String[] words = msgStr.split("[\\s:=,]+");
                        for (String word : words) {
                            if (word.length() > 2 && word.matches("[A-Za-z0-9_\\.]+")) {
                                fieldsPanel.scrollToField(word);
                            }
                        }
                    }
                }
            }
        });

        parameterChangedListener = new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                if (e.getType() == TableModelEvent.UPDATE) {
                    if (e.getColumn() == 1) {
                        if (editingProcessor != null) {
                            processFile();
                        }
                    }
                }
            }
        };
    }

    private void addNewTab() {
        if (addingTab) return;
        addingTab = true;
        try {
        int newIndex = workspace.size();
        final ChartTab tab = createChartTab("Tab " + (tabCounter++));
        workspace.add(tab);
        chartTabbedPane.insertTab(tab.title, null, tab.chartPanel, null, chartTabbedPane.getTabCount() - 1);

        JPanel tabHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabHeader.setOpaque(false);
        JLabel titleLabel = new JLabel(tab.title);
        tabHeader.add(titleLabel);

        // Add close button only for tabs after the first one
        if (newIndex > 0) {
            JButton closeButton = new JButton("x");
            closeButton.setMargin(new Insets(0, 2, 0, 2));
            closeButton.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
            closeButton.setFocusable(false);
            closeButton.setOpaque(false);
            closeButton.setContentAreaFilled(false);
            closeButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    closeTab(tab);
                }
            });
            tabHeader.add(closeButton);
        }

        chartTabbedPane.setTabComponentAt(chartTabbedPane.getTabCount() - 2, tabHeader);
        chartTabbedPane.setSelectedIndex(chartTabbedPane.getTabCount() - 2);
        } finally { addingTab = false; }
        switchTab(workspace.size()-1);
    }

    private void closeTab(ChartTab tab) {
        if (workspace.size() <= 1) return; // Don't close the last tab
        int index = workspace.indexOf(tab);
        if (index <= 0) return; // Don't close the first tab (index 0)

        if(tab==openingLogTab) cancelOpeningLog(false);

        tab.closeLogReader();

        tab.processorsListModel.setRowCount(0);
        tab.parametersTableModel.setRowCount(0);
        tab.logsTableModel.setRowCount(0);
        tab.dataset.removeAllSeries();
        tab.seriesIndex.clear();
        tab.activeProcessors.clear();
        tab.chart.getXYPlot().clearDomainMarkers();

        addingTab = true;
        try {
        int selectedIndex = chartTabbedPane.getSelectedIndex();
        int newIndex = selectedIndex;
        if (selectedIndex == index) {
            newIndex = index - 1;
            if (newIndex < 0) newIndex = 0;
            chartTabbedPane.setSelectedIndex(newIndex);
        } else if (selectedIndex > index) {
            newIndex = selectedIndex - 1;
        }

        chartTabbedPane.remove(tab.chartPanel);
        workspace.remove(tab);
        workspace.select(newIndex);
        } finally { addingTab = false; }
        switchTab(workspace.currentIndex());
    }

    private ChartTab createChartTab(String title) {
        final ChartTab tab = new ChartTab(title);
        applyChartTextFont(tab.chart);
        if (tab.chart.getLegend() != null) {
            tab.chart.getLegend().setVisible(showLegend);
        }

        tab.chartPanel.setTransferHandler(createDataTransferHandler());
        tab.chart.addChangeListener(new ChartChangeListener() {
            @Override
            public void chartChanged(ChartChangeEvent chartChangeEvent) {
                if (chartChangeEvent.getType() == ChartChangeEventType.GENERAL) {
                    if (suppressRangeProcess.get() || topMinuteMarkerUpdating.get()) return;
                    Range timeRange = tab.chart.getXYPlot().getDomainAxis().getRange();
                    if (!timeRange.equals(tab.lastTimeRange)) {
                        tab.lastTimeRange = timeRange;
                        if (tab == workspace.current()) {
                            scheduleZoomUpdate();
                        }
                    }
                }
            }
        });
        tab.chartPanel.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                if (getCurrentTab() == tab && tab.logReader != null) scheduleZoomUpdate();
            }
        });

        tab.processorsListModel.addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                if (e.getType() == TableModelEvent.UPDATE && e.getColumn() == 0) {
                    if (restoringProcessorSelection || e.getFirstRow() < 0
                            || e.getFirstRow() >= tab.processorsListModel.getRowCount()) return;
                    ProcessorPreset pp = (ProcessorPreset) tab.processorsListModel.getValueAt(e.getFirstRow(), 1);
                    pp.setVisible((Boolean) tab.processorsListModel.getValueAt(e.getFirstRow(), 0));
                    if (tab == workspace.current()) {
                        clearSelectionRestoreNotice();
                        updatePresetEdited(true);
                        processFile();
                    }
                }
            }
        });

        if (parameterChangedListener != null) {
            tab.parametersTableModel.addTableModelListener(parameterChangedListener);
        }

        return tab;
    }

    private void applyChartTextFont(JFreeChart targetChart) {
        if (chartTextFont != null && targetChart != null) {
            targetChart.getTitle().setFont(chartTextFont);
            if (targetChart.getLegend() != null) targetChart.getLegend().setItemFont(chartTextFont);
            targetChart.getXYPlot().getDomainAxis().setLabelFont(chartTextFont);
            targetChart.getXYPlot().getDomainAxis().setTickLabelFont(chartTextFont);
            targetChart.getXYPlot().getRangeAxis().setLabelFont(chartTextFont);
            targetChart.getXYPlot().getRangeAxis().setTickLabelFont(chartTextFont);
        }
    }

    private void switchTab(int index) {
        if (index < 0 || index >= workspace.size()) return;
        if (parametersTable.isEditing()) parametersTable.getCellEditor().cancelCellEditing();
        if (processorsList.isEditing()) processorsList.getCellEditor().stopCellEditing();
        editingProcessor = null;
        if (invokeProcessFile.get()) processFilePending.set(true);
        workspace.select(index);
        ChartTab tab = workspace.current();

        this.logReader = tab.logReader;
        this.currentLogType = tab.currentLogType;
        this.dataset = tab.dataset;
        this.chart = tab.chart;
        this.chartPanel = tab.chartPanel;
        this.domainAxisSeconds = tab.domainAxisSeconds;
        this.domainAxisDate = tab.domainAxisDate;

        this.processorsListModel = tab.processorsListModel;
        this.parametersTableModel = tab.parametersTableModel;
        this.logsTableModel = tab.logsTableModel;

        this.seriesIndex = tab.seriesIndex;
        this.activeProcessors = tab.activeProcessors;
        this.topMinuteMarkers = tab.topMinuteMarkers;
        this.lastTimeRange = tab.lastTimeRange;
        this.timeMode = tab.timeMode;

        processorsList.setModel(processorsListModel);
        parametersTable.setModel(parametersTableModel);
        logTable.setModel(logsTableModel);

        applyTableRenderers();

        if (logInfo != null) logInfo.updateInfo(logReader);
        if (fieldsPanel != null) {
            if (logReader != null) {
                fieldsPanel.setFieldsList(logReader.getFields());
            } else {
                fieldsPanel.setFieldsList(Collections.<String, String>emptyMap());
            }
        }

        if (timeMode >= 0 && timeMode < timeModeItems.length) {
            timeModeItems[timeMode].setSelected(true);
        }

        if (tab.logFileName != null) {
            mainFrame.setTitle(appNameAndVersion + " - " + tab.logFileName);
        } else {
            mainFrame.setTitle(appNameAndVersion);
        }

        applyLanguageTexts();

        // Re-apply parameter changed listener if not already present
        boolean hasListener = false;
        if (parametersTableModel != null && parametersTableModel.getTableModelListeners() != null) {
            for (TableModelListener l : parametersTableModel.getTableModelListeners()) {
                if (l == parameterChangedListener) hasListener = true;
            }
        }
        if (!hasListener && parameterChangedListener != null) {
            parametersTableModel.addTableModelListener(parameterChangedListener);
        }
    }

    private void applyTableRenderers() {
        if (processorsList.getColumnModel().getColumnCount() >= 2) {
            processorsList.getColumnModel().getColumn(0).setMinWidth(20);
            processorsList.getColumnModel().getColumn(0).setMaxWidth(20);
            processorsList.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    if (value instanceof ProcessorPreset) {
                        ProcessorPreset processorPreset = (ProcessorPreset) value;
                        String fullText = buildProcessorDisplayText(processorPreset);
                        int availableWidth = table.getColumnModel().getColumn(column).getWidth() - 16;
                        String displayText = ellipsizeMiddle(fullText, getFontMetrics(getFont()), availableWidth);
                        setText(displayText);
                        setToolTipText(fullText);
                    } else {
                        setToolTipText(null);
                    }
                    return this;
                }
            });
        }
        if (parametersTable.getColumnModel().getColumnCount() >= 2) {
            parametersTable.getColumnModel().getColumn(1).setCellEditor(new ParamValueTableCellEditor(FlightPlot.this));
            parametersTable.getColumnModel().getColumn(1).setCellRenderer(new ParamValueTableCellRenderer());
        }
        if (logTable.getColumnModel().getColumnCount() >= 3) {
            logTable.getColumnModel().getColumn(2).setMinWidth(350);
            logTable.getColumnModel().getColumn(2).setPreferredWidth(800);
        }
    }


    private void initializeUi() {
        if (mainPanel != null) {
            return;
        }
        try {
            createUIComponents();
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        }

        mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        openLogButton = new JButton("Open Log");
        addProcessorButton = new JButton("Add Processor");
        removeProcessorButton = new JButton("Remove Processor");
        removeAllProcessorsButton = new JButton("Remove All");
        fieldColorButton = new JButton("Field Color");
        fieldColorButton.addActionListener(e -> showFieldColorDialog());
        logInfoButton = new JButton("Log Info");
        savePresetButton = new JButton("Save Preset");
        deletePresetButton = new JButton("Delete Preset");
        languageToggleButton = new JButton("中文");
        presetComboBox = new JComboBox();
        markerCheckBox = new JCheckBox("Markers");
        topMinuteCheckBox = new JCheckBox("Top Minute");
        statusLabel = new JLabel(" ");

        JPanel toolbarPanel = new JPanel(new WrapLayout());
        toolbarPanel.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { toolbarPanel.revalidate(); }
        });
        toolbarPanel.add(openLogButton);
        toolbarPanel.add(addProcessorButton);
        toolbarPanel.add(removeProcessorButton);
        toolbarPanel.add(removeAllProcessorsButton);
        toolbarPanel.add(fieldColorButton);
        toolbarPanel.add(logInfoButton);
        toolbarPanel.add(markerCheckBox);
        toolbarPanel.add(topMinuteCheckBox);
        toolbarPanel.add(languageToggleButton);
        mainPanel.add(toolbarPanel, BorderLayout.NORTH);

        JPanel processorsPanel = new JPanel(new BorderLayout(6, 6));
        processorsLabel = new JLabel("Processors");
        processorsPanel.add(processorsLabel, BorderLayout.NORTH);
        JScrollPane processorsScroll = new JScrollPane(processorsList);
        processorsScroll.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        processorsPanel.add(processorsScroll, BorderLayout.CENTER);

        // 1. Collapsible Parameters Panel
        JScrollPane parametersScroll = new JScrollPane(parametersTable);
        parametersScroll.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        parametersPanel = new CollapsiblePanel("Parameters / 参数", parametersScroll);

        // 2. Collapsible Logs Panel
        JScrollPane logsScroll = new JScrollPane(logTable);
        logsScroll.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        logsPanel = new CollapsiblePanel("Log Messages / 日志消息", logsScroll);

        // Wrap collapsibles in a container
        JPanel collapsibleContainer = new JPanel();
        collapsibleContainer.setLayout(new BoxLayout(collapsibleContainer, BoxLayout.Y_AXIS));
        collapsibleContainer.add(parametersPanel);
        collapsibleContainer.add(logsPanel);

        // 3. Fields Panel (always visible, below logs)
        fieldsPanel = new FieldsPanel(new Runnable() {
            @Override
            public void run() {
                addFields(fieldsPanel.getSelectedFields());
            }
        });

        JPanel lowerLeftPanel = new ScrollableSidebar();
        lowerLeftPanel.add(collapsibleContainer, BorderLayout.NORTH);
        lowerLeftPanel.add(fieldsPanel, BorderLayout.CENTER);

        fieldsPanel.setPreferredSize(new Dimension(300,260));
        JScrollPane sidebarScroll = new JScrollPane(lowerLeftPanel);
        sidebarScroll.setMinimumSize(new Dimension(180,100));
        sidebarScroll.getVerticalScrollBar().setUnitIncrement(20);
        processorsPanel.setMinimumSize(new Dimension(180,80));
        JSplitPane leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, processorsPanel, sidebarScroll);
        leftSplit.setResizeWeight(0.45);
        leftSplit.setBorder(null);
        leftSplit.setPreferredSize(new Dimension(330, 600));
        leftSplit.setMinimumSize(new Dimension(240,180));

        chartTabbedPane.setPreferredSize(new Dimension(800, 600));
        chartTabbedPane.setMinimumSize(new Dimension(180,180));
        chartTabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        JSplitPane contentSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplit, chartTabbedPane);
        contentSplit.setResizeWeight(0.28);
        contentSplit.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                contentSplit.setDividerLocation(Math.max(240,Math.min(480,(int)(contentSplit.getWidth()*0.28))));
            }
        });
        contentSplit.setBorder(null);

        mainPanel.add(contentSplit, BorderLayout.CENTER);
        mainPanel.add(statusLabel, BorderLayout.SOUTH);
    }

    public void addFields(List<String> fields) {
        clearSelectionRestoreNotice();
        Set<String> allAddedFields = getAlreadyAddedFieldSet();
        LinkedHashSet<String> newFields = new LinkedHashSet<String>();
        Set<Integer> existingRows = new LinkedHashSet<Integer>();
        boolean restored = false;
        for (String field : fields) {
            if (logReader == null || !logReader.getFields().containsKey(field)) continue;
            if (!allAddedFields.contains(field)) {
                newFields.add(field);
            } else {
                int row = findProcessorRowForField(field);
                if (row >= 0) existingRows.add(row);
            }
        }
        restoringProcessorSelection = true;
        try {
            for (int row : existingRows) {
                if (!Boolean.TRUE.equals(processorsListModel.getValueAt(row, 0))) {
                    ((ProcessorPreset) processorsListModel.getValueAt(row, 1)).setVisible(true);
                    processorsListModel.setValueAt(true, row, 0);
                    restored = true;
                }
            }
        } finally { restoringProcessorSelection = false; }
        if (!existingRows.isEmpty()) {
            processorsList.clearSelection();
            for (int row : existingRows) processorsList.addRowSelectionInterval(row, row);
            processorsList.scrollRectToVisible(processorsList.getCellRect(existingRows.iterator().next(), 1, true));
            processorsList.requestFocusInWindow();
        }
        if (newFields.isEmpty() && existingRows.isEmpty()) {
            return;
        }
        if (newFields.isEmpty()) {
            if (restored) { updatePresetEdited(true); processFile(); }
            else setStatus("zh_CN".equals(uiLanguage) ? "该字段已显示，已高亮对应处理器" : "Field is already displayed; its processor is highlighted");
            return;
        }
        StringBuilder fieldsValue = new StringBuilder();
        String processorTitle = "New";
        if (newFields.size() == 1) {
            processorTitle = newFields.iterator().next();
        }
        for (String field : newFields) {
            if (fieldsValue.length() > 0) {
                fieldsValue.append(" ");
            }
            fieldsValue.append(field);
        }
        PlotProcessor processor = new Simple();
        processor.setParameters(Collections.<String, Object>singletonMap("Fields", fieldsValue.toString()));
        ProcessorPreset pp = new ProcessorPreset(processorTitle, processor.getProcessorType(),
                processor.getParameters(), Collections.<String, Color>emptyMap(), true);
        pp.setUiLanguage(uiLanguage);
        updatePresetParameters(pp, null);
        int i = processorsListModel.getRowCount();
        processorsListModel.addRow(new Object[]{pp.isVisible(), pp});
        processorsList.getSelectionModel().setSelectionInterval(i, i);
        processorsList.repaint();
        updateUsedColors();
        processFile();
    }

    private int findProcessorRowForField(String targetField) {
        for (int i = 0; i < processorsListModel.getRowCount(); i++) {
            Object rowValue = processorsListModel.getValueAt(i, 1);
            if (!(rowValue instanceof ProcessorPreset)) continue;
            ProcessorPreset pp = (ProcessorPreset) rowValue;
            Map<String, Object> params = pp.getParameters();
            if (params == null) continue;
            Object fieldsParam = params.get("Fields");
            if (fieldsParam == null) continue;
            String[] split = fieldsParam.toString().trim().split("\\s+");
            for (String f : split) {
                if (!f.isEmpty() && f.equals(targetField)) return i;
            }
        }
        return -1;
    }

    private void createMenuBar() {
        // File menu
        fileMenu = new JMenu("File");

        fileOpenItem = new JMenuItem("Open Log...");
        fileOpenItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showOpenLogDialog();
            }
        });
        fileMenu.add(fileOpenItem);

        exportAsImageItem = new JMenuItem("Export As Image...");
        exportAsImageItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showExportAsImageDialog();
            }
        });
        fileMenu.add(exportAsImageItem);

        exportTrackItem = new JMenuItem("Export Track...");
        exportTrackItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showExportTrackDialog();
            }
        });
        fileMenu.add(exportTrackItem);

        exportParametersItem = new JMenuItem("Export Parameters...");
        exportParametersItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showExportParametersDialog();
            }
        });
        fileMenu.add(exportParametersItem);

        if (!OSValidator.isMac()) {
            fileMenu.add(new JPopupMenu.Separator());
            exitMenuItem = new JMenuItem("Exit");
            exitMenuItem.setAccelerator(KeyStroke.getKeyStroke('Q', Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
            exitMenuItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent actionEvent) {
                    onQuit();
                }
            });
            fileMenu.add(exitMenuItem);
        }

        // View menu
        viewMenu = new JMenu("View");
        timeModeItems = new JRadioButtonMenuItem[3];
        timeModeItems[TIME_MODE_LOG_START] = new JRadioButtonMenuItem("Log Start Time");
        timeModeItems[TIME_MODE_BOOT] = new JRadioButtonMenuItem("Boot Time");
        timeModeItems[TIME_MODE_GPS] = new JRadioButtonMenuItem("GPS Time");
        ButtonGroup timeModeGroup = new ButtonGroup();
        for (JRadioButtonMenuItem item : timeModeItems) {
            timeModeGroup.add(item);
            item.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    onTimeModeChanged();
                    processFile();
                }
            });
            viewMenu.add(item);
        }
        viewMenu.addSeparator();
        showLegendItem = new JCheckBoxMenuItem("Show Field Colors", true);
        showLegendItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showLegend = showLegendItem.isSelected();
                applyLegendVisibility();
            }
        });
        viewMenu.add(showLegendItem);

        // Menu bar
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(fileMenu);
        menuBar.add(viewMenu);
        mainFrame.setJMenuBar(menuBar);
    }

    private void onTimeModeChanged() {
        int timeModeOld = timeMode;
        for (int i = 0; i < timeModeItems.length; i++) {
            if (timeModeItems[i].isSelected()) {
                timeMode = i;
                break;
            }
        }

        long timeOffset = 0;
        long logStart = 0;
        long logSize = 1000000;
        Range rangeOld = new Range(0.0, 1.0);

        if (logReader != null) {
            timeOffset = getTimeOffset(timeMode);
            logStart = logReader.getStartMicroseconds() + timeOffset;
            logSize = logReader.getSizeMicroseconds();
            if (logSize == 0) {
                logSize = 1000;
            }
            rangeOld = getLogRange(timeModeOld);
        }

        ValueAxis domainAxis = selectDomainAxis(timeMode);
        // Set axis type according to selected time mode
        chart.getXYPlot().setDomainAxis(0, domainAxis, false);

        if (domainAxis == domainAxisDate) {
            // DateAxis uses ms instead of seconds
            domainAxis.setRange(new Range(rangeOld.getLowerBound() * 1e3 + timeOffset * 1e-3,
                    rangeOld.getUpperBound() * 1e3 + timeOffset * 1e-3), true, false);
            domainAxis.setDefaultAutoRange(new Range(logStart * 1e-3, (logStart + logSize) * 1e-3));
        } else {
            domainAxis.setRange(new Range(rangeOld.getLowerBound() + timeOffset * 1e-6,
                    rangeOld.getUpperBound() + timeOffset * 1e-6), true, false);
            domainAxis.setDefaultAutoRange(new Range(logStart * 1e-6, (logStart + logSize) * 1e-6));
        }
    }

    /**
     * Displayed log range in seconds of native log time
     *
     * @param tm time mode
     * @return displayed log range [s]
     */
    private Range getLogRange(int tm) {
        Range range = selectDomainAxis(tm).getRange();
        if (tm == TIME_MODE_GPS) {
            long timeOffset = getTimeOffset(tm);
            return new Range((range.getLowerBound() * 1e3 - timeOffset) * 1e-6,
                    (range.getUpperBound() * 1e3 - timeOffset) * 1e-6);
        } else {
            long timeOffset = getTimeOffset(tm);
            return new Range(range.getLowerBound() - timeOffset * 1e-6, range.getUpperBound() - timeOffset * 1e-6);
        }
    }

    public void setStatus(String status) {
        statusLabel.setText(status);
    }

    public void showOpenLogDialog() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::showOpenLogDialog);
            return;
        }
        if (!dialogs.claim("open-log")) return;
        openLogDialogShowing = true;
        setOpenActionsEnabled(false);
        setStatus("zh_CN".equals(uiLanguage) ? "请选择日志文件" : "Choose a log file");
        final int[] result = {JFileChooser.CANCEL_OPTION};
        try {
            logFileChooser = createLogFileChooser();
            final JDialog dialog = dialogs.register("open-log", new JDialog(mainFrame, tr("open_log")));
            openLogDialog = dialog;
            dialog.setContentPane(logFileChooser);
            logFileChooser.addActionListener(e -> {
                if (JFileChooser.APPROVE_SELECTION.equals(e.getActionCommand())) {
                    result[0] = JFileChooser.APPROVE_OPTION;
                    dialog.dispose();
                } else if (JFileChooser.CANCEL_SELECTION.equals(e.getActionCommand())) {
                    dialog.dispose();
                }
            });
            dialog.pack();
            configureFileChooserScrolling(logFileChooser);
            fitDialogToScreen(dialog);
            dialog.setLocationRelativeTo(mainFrame);
            dialog.setVisible(true);

            if (result[0] == JFileChooser.APPROVE_OPTION) {
                File file = logFileChooser.getSelectedFile();
                if (file != null) {
                    lastLogDirectory = logFileChooser.getCurrentDirectory();
                    openLogAsync(file.getAbsolutePath());
                }
            } else {
                setStatus("zh_CN".equals(uiLanguage) ? "已取消打开日志" : "Open cancelled");
            }
        } finally {
            if (openLogDialog != null) openLogDialog.dispose();
            if (logFileChooser != null) logFileChooser.setSelectedFile(null);
            openLogDialogShowing = false;
            openLogDialog = null;
            logFileChooser = null;
            dialogs.release("open-log", null);
            setOpenActionsEnabled(true);
        }
    }

    private JFileChooser createLogFileChooser() {
        JFileChooser chooser = new JFileChooser();
        FileNameExtensionFilter[] filters = new FileNameExtensionFilter[]{
                new FileNameExtensionFilter("All known Log files (*.px4log, *.bin, *.ulg, *.log)", "px4log", "bin", "ulg", "log"),
                new FileNameExtensionFilter("PX4/APM Log (*.px4log, *.bin)", "px4log", "bin"),
                new FileNameExtensionFilter("ULog (*.ulg)", "ulg"),
                new FileNameExtensionFilter("APM Text Log (*.log)", "log")
        };
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setMultiSelectionEnabled(false);
        for (FileNameExtensionFilter filter : filters) chooser.addChoosableFileFilter(filter);
        chooser.setFileFilter(filters[0]);
        chooser.setDialogTitle(tr("open_log"));
        chooser.setApproveButtonText("zh_CN".equals(uiLanguage) ? "打开" : "Open");
        if (lastLogDirectory != null && lastLogDirectory.isDirectory()) chooser.setCurrentDirectory(lastLogDirectory);
        return chooser;
    }

    private void configureFileChooserScrolling(Component component) {
        if (component instanceof JViewport) {
            ((JViewport) component).setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                configureFileChooserScrolling(child);
            }
        }
    }

    private void fitDialogToScreen(JDialog dialog) {
        GraphicsConfiguration gc = mainFrame.getGraphicsConfiguration();
        Rectangle screen = gc.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        int usableWidth = screen.width - insets.left - insets.right;
        int usableHeight = screen.height - insets.top - insets.bottom;
        dialog.setSize(Math.min(dialog.getWidth(), usableWidth), Math.min(dialog.getHeight(), usableHeight));
    }

    private void setOpenActionsEnabled(boolean enabled) {
        if (openLogButton != null) openLogButton.setEnabled(enabled);
        if (fileOpenItem != null) fileOpenItem.setEnabled(enabled);
    }

    private TransferHandler createDataTransferHandler() {
        return new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                        || support.isDataFlavorSupported(DataFlavor.stringFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        List<File> files = (List<File>) support.getTransferable()
                                .getTransferData(DataFlavor.javaFileListFlavor);
                        return handleDroppedFiles(files);
                    }
                    String data = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                    if (data == null || data.trim().isEmpty()) return false;
                    addFields(Arrays.asList(data.split("\\r?\\n")));
                    return true;
                } catch (Exception e) {
                    setStatus("zh_CN".equals(uiLanguage) ? "无法处理拖入内容" : "Unable to process dropped content");
                    return false;
                }
            }
        };
    }

    private boolean handleDroppedFiles(final List<File> files) {
        if (files == null || files.size() != 1) {
            setStatus("zh_CN".equals(uiLanguage) ? "请一次拖入一个日志文件" : "Drop one log file at a time");
            return false;
        }
        final File file=files.get(0);
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> openLogAsync(file.getAbsolutePath()));
        } else {
            openLogAsync(file.getAbsolutePath());
        }
        return true;
    }

    private void openLogAsync(final String logFileName) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> openLogAsync(logFileName));
            return;
        }
        if (workspace.current() == null) return;
        final String normalizedPath = normalizeLogPath(logFileName);
        String logFileNameLower = normalizedPath.toLowerCase(Locale.ROOT);
        if (!logService.supports(logFileNameLower)) {
            setStatus("Log format not supported: " + normalizedPath);
            showUnsupportedLogDialog(normalizedPath);
            return;
        }

        for (int i=0;i<workspace.size();i++) {
            ChartTab tab=workspace.tabs().get(i);
            if (tab.logFileName != null && normalizeLogPath(tab.logFileName).equalsIgnoreCase(normalizedPath)) {
                chartTabbedPane.setSelectedIndex(i);
                setStatus("zh_CN".equals(uiLanguage) ? "该日志已打开" : "Log is already open");
                return;
            }
        }
        if (openingLogTab != null && openingLogPath != null && openingLogPath.equalsIgnoreCase(normalizedPath)) {
            int index=workspace.indexOf(openingLogTab);
            if(index>=0) chartTabbedPane.setSelectedIndex(index);
            setStatus("zh_CN".equals(uiLanguage) ? "正在打开该日志…" : "This log is already opening...");
            return;
        }

        ChartTab sourceTab=getCurrentTab();
        rememberProcessorSelection(sourceTab);
        final String sourceLogType=sourceTab==null ? null : sourceTab.currentLogType;
        final List<ProcessorPreset> sourceSelection=selectionMemory.snapshot(sourceTab == null ? null : sourceTab.processorsListModel);

        cancelOpeningLog(true);
        ChartTab targetTab=getCurrentTab();
        if(targetTab==null) return;
        boolean createdTab=false;
        if(targetTab.logFileName!=null) {
            addNewTab();
            targetTab=getCurrentTab();
            createdTab=true;
        }
        final ChartTab finalTargetTab=targetTab;
        openingLogTab=finalTargetTab;
        openingLogPath=normalizedPath;
        openingOriginalTabTitle=finalTargetTab.title;
        openingCreatedTab=createdTab;
        setTabTitle(finalTargetTab,("zh_CN".equals(uiLanguage) ? "正在打开 " : "Opening ")+new File(normalizedPath).getName()+"…");

        final int requestId = openLogRequestCounter.incrementAndGet();
        latestOpenLogRequestId = requestId;
        setStatus(("zh_CN".equals(uiLanguage) ? "正在打开日志：" : "Opening log: ")+normalizedPath);
        Thread openThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final LogService.OpenedLog openLogData = logService.open(normalizedPath, "zh_CN".equals(uiLanguage));
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            if (requestId != latestOpenLogRequestId || !workspace.contains(finalTargetTab)) {
                                closeLogReaderQuietly(openLogData.reader);
                                return;
                            }
                            clearOpeningLogState();
                            applyLoadedLog(openLogData, finalTargetTab, sourceLogType, sourceSelection);
                        }
                    });
                } catch (final Exception e) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            if (requestId != latestOpenLogRequestId) {
                                return;
                            }
                            handleOpenFailure(finalTargetTab);
                            setStatus(("zh_CN".equals(uiLanguage) ? "日志打开失败：" : "Unable to open log: ") + e.getMessage());
                            showInvalidLogDialog(normalizedPath);
                        }
                    });
                }
            }
        }, "FlightPlot-OpenLog");
        openThread.setDaemon(true);
        openLogThread = openThread;
        openThread.start();
    }

    private String normalizeLogPath(String path) {
        try { return new File(path).getCanonicalPath(); }
        catch (IOException ignored) { return new File(path).getAbsolutePath(); }
    }

    private void setTabTitle(ChartTab tab,String title) {
        tab.title=title;
        int index=workspace.indexOf(tab);
        if(index<0) return;
        Component header=chartTabbedPane.getTabComponentAt(index);
        if(header instanceof JPanel && ((JPanel)header).getComponentCount()>0 && ((JPanel)header).getComponent(0) instanceof JLabel) {
            ((JLabel)((JPanel)header).getComponent(0)).setText(title);
        } else chartTabbedPane.setTitleAt(index,title);
    }

    private void handleOpenFailure(ChartTab tab) {
        boolean remove=tab==openingLogTab && openingCreatedTab && workspace.indexOf(tab)>0;
        String original=openingOriginalTabTitle;
        clearOpeningLogState();
        if(remove) closeTab(tab);
        else if(workspace.contains(tab)) setTabTitle(tab,original==null ? "Tab" : original);
    }

    private void clearOpeningLogState() {
        openingLogTab=null;
        openingLogPath=null;
        openingOriginalTabTitle=null;
        openingCreatedTab=false;
        openLogThread=null;
    }

    private void cancelOpeningLog(boolean removeAbandonedTab) {
        if(openingLogTab==null && (openLogThread==null || !openLogThread.isAlive())) return;
        latestOpenLogRequestId=openLogRequestCounter.incrementAndGet();
        if(openLogThread!=null) openLogThread.interrupt();
        ChartTab abandoned=openingLogTab;
        String original=openingOriginalTabTitle;
        boolean created=openingCreatedTab;
        clearOpeningLogState();
        if(abandoned!=null && workspace.contains(abandoned) && abandoned.logFileName==null) {
            setTabTitle(abandoned,original==null ? "Tab" : original);
            int index=workspace.indexOf(abandoned);
            if(removeAbandonedTab && created && abandoned!=getCurrentTab() && index>0) closeTab(abandoned);
        }
    }

    private void closeLogReaderQuietly(LogReader reader) { LogService.close(reader); }

    private void applyLoadedLog(LogService.OpenedLog data, ChartTab tab, String sourceLogType,
                                List<ProcessorPreset> sourceSelection) {
        if (tab.currentLogType != null && !tab.currentLogType.equals(data.type)) {
            clearViewForFormatSwitch(tab);
        }
        tab.logsTableModel.setRowCount(0);
        if (!data.messages.isEmpty()) {
            tab.logsTableModel.setDataVector(
                data.messages.stream().map(row -> row).toArray(Object[][]::new),
                new Object[]{"Time", "Level", "Message"}
            );
        }
        tab.logFileName = data.fileName;
        tab.closeLogReader();
        tab.logReader = data.reader;
        tab.currentLogType = data.type;
        List<ProcessorPreset> selection = data.type.equals(sourceLogType)
                ? selectionMemory.copy(sourceSelection) : selectionMemory.forType(data.type);
        ProcessorSelectionMemory.Result restoreResult = restoreProcessorSelection(tab, selection, data.reader.getFields());
        tab.selectionRestoreNoticeZh = restoreResult.notice(true);
        tab.selectionRestoreNoticeEn = restoreResult.notice(false);
        rememberProcessorSelection(tab);

        File f = new File(data.fileName);
        setTabTitle(tab,f.getName());

        if (tab == workspace.current()) {
            this.logReader = tab.logReader;
            this.currentLogType = tab.currentLogType;
            mainFrame.setTitle(appNameAndVersion + " - " + tab.logFileName);

            if (logReader.getErrors().size() > 0) {
                setStatus(("zh_CN".equals(uiLanguage) ? "日志已打开：" : "Log opened: ") + data.fileName
                        + ("zh_CN".equals(uiLanguage) ? "（解析提示 " : " (parser notices: ") + logReader.getErrors().size() + ")");
            } else {
                setStatus(("zh_CN".equals(uiLanguage) ? "日志已打开：" : "Log opened: ") + data.fileName);
            }
            logInfo.updateInfo(logReader);
            final Map<String, String> fields = logReader.getFields();
            if (fieldsPanel != null) fieldsPanel.setFieldsList(fields);
            onTimeModeChanged();
            chart.getXYPlot().getDomainAxis().setAutoRange(true);
            chart.getXYPlot().getRangeAxis().setAutoRange(true);
            processFile();
        }
    }

    private void showInvalidLogDialog(String logFileName) {
        boolean zh="zh_CN".equals(uiLanguage);
        showSingleMessageDialog((zh ? "该日志不完整、已损坏或格式不受支持，请检查后重试。\n文件："
                        : "The log is incomplete, damaged, or unsupported. Please check it and try again.\nFile: ") + logFileName,
                zh ? "日志解析失败" : "Log parsing failed",JOptionPane.ERROR_MESSAGE);
    }

    private void showUnsupportedLogDialog(String logFileName) {
        boolean zh="zh_CN".equals(uiLanguage);
        showSingleMessageDialog((zh ? "不支持该类型文件，请选择 .bin、.px4log、.ulg 或 .log。\n文件："
                        : "Unsupported file type. Choose a .bin, .px4log, .ulg, or .log file.\nFile: ") + logFileName,
                zh ? "不支持的日志类型" : "Unsupported log type",JOptionPane.WARNING_MESSAGE);
    }

    private void showSingleMessageDialog(String message,String title,int messageType) {
        if(!dialogs.claim("message")) return;
        JOptionPane pane=new JOptionPane(message,messageType,JOptionPane.DEFAULT_OPTION);
        messageDialog=dialogs.register("message",pane.createDialog(mainFrame,title));
        try { messageDialog.setVisible(true); }
        finally { messageDialog.dispose(); messageDialog=null; dialogs.release("message",null); }
    }

    private void clearViewForFormatSwitch(ChartTab tab) {
        tab.processorsListModel.setRowCount(0);
        tab.parametersTableModel.setRowCount(0);
        tab.logsTableModel.setRowCount(0);
        tab.dataset.removeAllSeries();
        tab.seriesIndex.clear();
        tab.activeProcessors.clear();
        tab.chart.getXYPlot().clearDomainMarkers();
    }

    public void showImportPresetDialog() {
        JFileChooser fc = new JFileChooser();
        if (lastPresetDirectory != null) {
            fc.setCurrentDirectory(lastPresetDirectory);
        }
        fc.setFileFilter(presetExtensionFilter);
        fc.setDialogTitle("Import Preset");
        int returnVal = fc.showDialog(mainFrame, "Import");
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            lastPresetDirectory = fc.getCurrentDirectory();
            File file = fc.getSelectedFile();
            try {
                byte[] b = new byte[(int) file.length()];
                FileInputStream fileInputStream = new FileInputStream(file);
                int n = 0;
                while (n < b.length) {
                    int r = fileInputStream.read(b, n, b.length - n);
                    if (r <= 0) {
                        throw new Exception("Read error");
                    }
                    n += r;
                }
                Preset preset = Preset.unpackJSONObject(new JSONObject(new String(b, Charset.forName("utf8"))));
                loadPreset(preset);
                processFile();
            } catch (Exception e) {
                setStatus(("zh_CN".equals(uiLanguage) ? "预设导入失败：" : "Unable to import preset: ")+e.getMessage());
            }
        }
    }

    public void showExportPresetDialog() {
        JFileChooser fc = new JFileChooser();
        if (lastPresetDirectory != null) {
            fc.setCurrentDirectory(lastPresetDirectory);
        }
        fc.setFileFilter(presetExtensionFilter);
        fc.setDialogTitle("Export Preset");
        int returnVal = fc.showDialog(mainFrame, "Export");
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            lastPresetDirectory = fc.getCurrentDirectory();
            String fileName = fc.getSelectedFile().toString();
            if (presetExtensionFilter == fc.getFileFilter() && !fileName.toLowerCase().endsWith(".fplot")) {
                fileName += ".fplot";
            }
            try {
                Object item = presetComboBox.getSelectedItem();
                String presetTitle = item == null ? "" : item.toString();
                Preset preset = formatPreset(presetTitle);
                FileWriter fileWriter = new FileWriter(new File(fileName));
                fileWriter.write(preset.packJSONObject().toString(1));
                fileWriter.close();
                setStatus("Preset saved to: " + fileName);
            } catch (Exception e) {
                setStatus(("zh_CN".equals(uiLanguage) ? "预设保存失败：" : "Unable to save preset: ")+e.getMessage());
            }
        }
    }

    public void showExportAsImageDialog() {
        if (logReader == null) {
            JOptionPane.showMessageDialog(mainFrame, "Log file must be opened first.", "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            plotExportDialog.setVisible(true);
        } catch (Exception e) {
            setStatus(("zh_CN".equals(uiLanguage) ? "图片导出失败：" : "Unable to export image: ")+e.getMessage());
        }
    }

    public void showExportTrackDialog() {
        if (logReader == null) {
            JOptionPane.showMessageDialog(mainFrame, "Log file must be opened first.", "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            trackExportDialog.display(logReader, getLogRange(timeMode));
        } catch (Exception e) {
            showExportTrackStatusMessage(("zh_CN".equals(uiLanguage) ? "导出失败：" : "Export failed: ")+e.getMessage());
        }
    }

    private void showExportTrackStatusMessage(String message) {
        setStatus(("zh_CN".equals(uiLanguage) ? "轨迹导出：" : "Track export: ") + message);
    }

    public void showExportParametersDialog() {
        if (logReader == null) {
            JOptionPane.showMessageDialog(mainFrame, "Log file must be opened first.", "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(parametersExtensionFilter);
        fc.setDialogTitle("Export Parameters");
        int returnVal = fc.showDialog(mainFrame, "Export");
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            String fileName = fc.getSelectedFile().toString();
            if (parametersExtensionFilter == fc.getFileFilter() && !fileName.toLowerCase().endsWith(".txt")) {
                fileName += ".txt";
            }
            try {
                FileWriter fileWriter = new FileWriter(new File(fileName));
                List<Map.Entry<String, Object>> paramsList = new ArrayList<Map.Entry<String, Object>>(logReader.getParameters().entrySet());
                Collections.sort(paramsList, new Comparator<Map.Entry<String, Object>>() {
                    @Override
                    public int compare(Map.Entry<String, Object> o1, Map.Entry<String, Object> o2) {
                        return o1.getKey().compareTo(o2.getKey());
                    }
                });
                for (Map.Entry<String, Object> param : paramsList) {
                    int typeID = 0;
                    Object value = param.getValue();
                    if (value instanceof Float) {
                        typeID = 1;
                    }
                    fileWriter.write(String.format("%s\t%s\t%s\n", param.getKey(), typeID, param.getValue()));
                }
                fileWriter.close();
            } catch (Exception e) {
                setStatus(("zh_CN".equals(uiLanguage) ? "参数导出失败：" : "Unable to export parameters: ")+e.getMessage());
            }
        }
    }

    private SwingWorker<List<XYSeries>, Void> seriesWorker = null;
    private final Object seriesWorkerLock = new Object();

    private void scheduleZoomUpdate() {

        if (zoomDebounceTimer != null && zoomDebounceTimer.isRunning()) {
            zoomDebounceTimer.restart();
            return;
        }
        zoomDebounceTimer = new javax.swing.Timer(ZOOM_DEBOUNCE_MS, e -> {
            zoomDebounceTimer = null;
            updateTopMinuteMarkersForCurrentRange();
            processFile();
        });
        zoomDebounceTimer.setRepeats(false);
        zoomDebounceTimer.start();
    }

    private ChartTab getCurrentTab() {
        return workspace.current();
    }

    private void processFile() {
        if (restoringProcessorSelection) return;
        final ChartTab targetTab = getCurrentTab();
        final LogReader logReader = this.logReader;
        rememberProcessorSelection(targetTab);
        if (logReader != null) {
            if (hasCurrentPlot(targetTab)) {
                setChartColors();
                return;
            }
            if (invokeProcessFile.compareAndSet(false, true)) {
                final boolean notEmptyPlot = (getActiveProcessors().size() > 0);
                if (notEmptyPlot) {
                    setStatus("zh_CN".equals(uiLanguage) ? "正在更新图表…" : "Updating chart...");
                }
                synchronized (seriesWorkerLock) {
                    if (seriesWorker != null && !seriesWorker.isDone()) {
                        seriesWorker.cancel(true);
                    }
                }
                // Keep range changes observable while the worker reads the log.
                final List<ProcessorPreset> activeProcessorsCopy = getActiveProcessors();
                for (int i=0;i<activeProcessorsCopy.size();i++) activeProcessorsCopy.set(i,activeProcessorsCopy.get(i).clone());
                final long timeOffset = getTimeOffset(timeMode);
                final Range range = getLogRange(timeMode);
                final int timeModeCopy = timeMode;
                final String languageCopy = uiLanguage;

                long timeStart = (long) ((range.getLowerBound() - range.getLength()) * 1e6);
                long timeStop = (long) ((range.getUpperBound() + range.getLength()) * 1e6);
                timeStart = Math.max(logReader.getStartMicroseconds(), timeStart);
                timeStop = Math.min(logReader.getStartMicroseconds() + logReader.getSizeMicroseconds(), timeStop);
                final long timeStartFinal = timeStart;
                final long timeStopFinal = timeStop;

                final double timeScale = (selectDomainAxis(timeMode) == domainAxisDate) ? 1000.0 : 1.0;
                final int displayPixels = Math.max(300, Math.min(4000, targetTab.chartPanel.getWidth()));
                final double skip = range.getLength() / displayPixels;

                seriesWorker = new SwingWorker<List<XYSeries>, Void>() {
                    private PlotProcessor[] processors;
                    private List<Map<String, Integer>> seriesIndexResult = new ArrayList<>();
                    private List<PlotItem> plotItems = new ArrayList<>();

                    @Override
                    protected List<XYSeries> doInBackground() throws Exception {
                        synchronized (logReader) {
                        if (logReader == null || activeProcessorsCopy.isEmpty()) {
                            return new ArrayList<>();
                        }

                        long perfStart = System.currentTimeMillis();

                        processors = new PlotProcessor[activeProcessorsCopy.size()];
                        Set<String> neededFields = new HashSet<String>();

                        for (int i = 0; i < activeProcessorsCopy.size(); i++) {
                            ProcessorPreset pp = activeProcessorsCopy.get(i);
                            PlotProcessor processor = processorsTypesList.getProcessorInstance(pp, skip, logReader.getFields());
                            processor.setFieldsList(logReader.getFields());
                            processors[i] = processor;
                            Map<String, Object> params = pp.getParameters();
                            Object fieldsParam = params.get("Fields");
                            if (fieldsParam instanceof String) {
                                for (String f : ((String) fieldsParam).split("[ \t]+")) {
                                    if (!f.isEmpty()) neededFields.add(f);
                                }
                            }
                            Object fieldParam = params.get("Field");
                            if (fieldParam instanceof String && !((String) fieldParam).isEmpty()) {
                                neededFields.add((String) fieldParam);
                            }
                            Object fieldXParam = params.get("Field_X");
                            if (fieldXParam instanceof String && !((String) fieldXParam).isEmpty()) {
                                neededFields.add((String) fieldXParam);
                            }
                            Object fieldYParam = params.get("Field_Y");
                            if (fieldYParam instanceof String && !((String) fieldYParam).isEmpty()) {
                                neededFields.add((String) fieldYParam);
                            }
                            Object fieldVParam = params.get("Field_Voltage");
                            if (fieldVParam instanceof String && !((String) fieldVParam).isEmpty()) {
                                neededFields.add((String) fieldVParam);
                            }
                            Object fieldCParam = params.get("Field_Current");
                            if (fieldCParam instanceof String && !((String) fieldCParam).isEmpty()) {
                                neededFields.add((String) fieldCParam);
                            }
                            Object fieldDParam = params.get("Field_Discharged");
                            if (fieldDParam instanceof String && !((String) fieldDParam).isEmpty()) {
                                neededFields.add((String) fieldDParam);
                            }
                        }

                        if (logReader instanceof PX4LogReader) {
                            // Complex processors may have implicit dependencies; filter only plain field plots.
                            boolean simpleOnly = true;
                            for (PlotProcessor processor : processors) if (processor.getClass() != Simple.class) simpleOnly=false;
                            ((PX4LogReader) logReader).setNeededFields(simpleOnly ? neededFields : null);
                        }

                        logReader.seek(timeStartFinal);
                        logReader.clearErrors();
                        Map<String, Object> data = new HashMap<String, Object>();

                        while (!isCancelled() && !processFilePending.get()) {
                            long t;
                            data.clear();
                            try {
                                t = logReader.readUpdate(data);
                            } catch (EOFException e) {
                                break;
                            }
                            if (t > timeStopFinal) {
                                break;
                            }
                            for (PlotProcessor processor : processors) {
                                processor.process((t + timeOffset) * 1e-6, data);
                            }
                        }

                        if (isCancelled()) {
                            return new ArrayList<>();
                        }

                        List<XYSeries> result = new ArrayList<>();
                        for (int i = 0; i < activeProcessorsCopy.size(); i++) {
                            PlotProcessor processor = processors[i];
                            String processorTitle = activeProcessorsCopy.get(i).getTitle();
                            Map<String, Integer> processorSeriesIndex = new HashMap<String, Integer>();
                            seriesIndexResult.add(processorSeriesIndex);

                            for (PlotItem item : processor.getSeriesList()) {
                                if (item instanceof Series) {
                                    Series series = (Series) item;
                                    series.finish();
                                    processorSeriesIndex.put(series.getTitle(), result.size());
                                    XYSeries jseries = new XYSeries(
                                        getSeriesDisplayTitle(processorTitle, series.getTitle()), false);
                                    jseries.setNotify(false);
                                    for (XYPoint point : series) {
                                        jseries.add(point.x * timeScale, point.y, false);
                                    }
                                    jseries.setNotify(true);
                                    result.add(jseries);
                                    plotItems.add(item);
                                } else if (item instanceof MarkersList) {
                                    MarkersList markers = (MarkersList) item;
                                    processorSeriesIndex.put(markers.getTitle(), result.size());
                                    XYSeries jseries = new XYSeries(
                                        getSeriesDisplayTitle(processorTitle, markers.getTitle()), false);
                                    result.add(jseries);
                                    plotItems.add(item);
                                }
                            }
                        }
                        return result;
                        }
                    }

                    @Override
                    protected void done() {
                        try {
                            if (isCancelled() || processFilePending.get() || getCurrentTab() != targetTab || targetTab.logReader != logReader) {
                                return;
                            }
                            List<XYSeries> seriesList = get();
                            suppressRangeProcess.set(true);
                            chart.getXYPlot().setNotify(false);
                            activeProcessors.clear();
                            activeProcessors.addAll(activeProcessorsCopy);
                            dataset.removeAllSeries();
                            seriesIndex.clear();
                            chart.getXYPlot().clearDomainMarkers();
                            topMinuteMarkers.clear();

                            XYPlot plot = chart.getXYPlot();
                            for (XYSeries series : seriesList) dataset.addSeries(series);

                            seriesIndex.clear();
                            seriesIndex.addAll(seriesIndexResult);

                            setChartColors();
                            setChartMarkers();
                            updateAxisUnitLabels();
                            targetTab.renderedRange = range;
                            targetTab.renderedWidth = displayPixels;
                            targetTab.renderedTimeMode = timeModeCopy;
                            targetTab.renderedLanguage = languageCopy;

                            Range displayRange = selectDomainAxis(timeModeCopy).getRange();
                            addTopMinuteMarkers(displayRange, timeScale);
                            plot.setNotify(true);

                            if (notEmptyPlot) {
                                int notices=logReader==null ? 0 : logReader.getErrors().size();
                                String baseStatus = notices>0
                                        ? (("zh_CN".equals(uiLanguage) ? "图表已更新，日志含解析提示：" : "Chart updated; parser notices: ")+notices)
                                        : ("zh_CN".equals(uiLanguage) ? "图表已更新" : "Chart updated");
                                String restoreNotice = "zh_CN".equals(uiLanguage)
                                        ? targetTab.selectionRestoreNoticeZh : targetTab.selectionRestoreNoticeEn;
                                setStatus(appendSelectionRestoreNotice(baseStatus, restoreNotice));
                            }

                        } catch (Exception e) {
                            if (!(e.getCause() instanceof InterruptedException)) {
                                setStatus(("zh_CN".equals(uiLanguage) ? "图表更新失败：" : "Unable to update chart: ")+e.getMessage());
                            }
                        } finally {
                            targetTab.chart.getXYPlot().setNotify(true);
                            suppressRangeProcess.set(false);
                            invokeProcessFile.lazySet(false);
                            if (processFilePending.getAndSet(false)) {
                                processFile();
                            }
                        }
                    }
                };
                seriesWorker.execute();
            } else {
                processFilePending.set(true);
            }
        }
    }

    private boolean hasCurrentPlot(ChartTab tab) {
        if (tab.renderedRange == null || !tab.renderedRange.equals(getLogRange(timeMode))
                || tab.renderedWidth != Math.max(300, Math.min(4000, tab.chartPanel.getWidth()))
                || tab.renderedTimeMode != timeMode || !Objects.equals(tab.renderedLanguage, uiLanguage)) return false;
        List<ProcessorPreset> selected = getActiveProcessors();
        if (selected.size() != tab.activeProcessors.size()) return false;
        for (int i = 0; i < selected.size(); i++) {
            ProcessorPreset a = selected.get(i), b = tab.activeProcessors.get(i);
            if (!Objects.equals(a.getTitle(), b.getTitle()) || !a.getProcessorType().equals(b.getProcessorType())
                    || !a.getParameters().equals(b.getParameters())) return false;
        }
        return true;
    }

    private long getTimeOffset(int tm) {
        if (logReader == null) return 0;
        // Set time offset according t selected time mode
        long timeOffset = 0;
        if (tm == TIME_MODE_GPS) {
            // GPS time
            timeOffset = logReader.getUTCTimeReferenceMicroseconds();
            if (timeOffset < 0) {
                timeOffset = 0;
            }
        } else if (tm == TIME_MODE_LOG_START) {
            // Log start time
            timeOffset = -logReader.getStartMicroseconds();
        }
        return timeOffset;
    }

    private ValueAxis selectDomainAxis(int tm) {
        if (tm == TIME_MODE_GPS) {
            return domainAxisDate;
        } else {
            return domainAxisSeconds;
        }
    }

    private List<ProcessorPreset> getActiveProcessors() {
        List<ProcessorPreset> processors = new ArrayList<ProcessorPreset>();
        for (int row = 0; row < processorsListModel.getRowCount(); row++) {
            ProcessorPreset pp = (ProcessorPreset) processorsListModel.getValueAt(row, 1);
            if ((Boolean) processorsListModel.getValueAt(row, 0)) {
                processors.add(pp);
            }
        }
        return processors;
    }

    private void rememberProcessorSelection(ChartTab tab) {
        if (restoringProcessorSelection || tab == null || tab.currentLogType == null) return;
        selectionMemory.remember(tab.currentLogType, tab.processorsListModel);
    }

    private ProcessorSelectionMemory.Result restoreProcessorSelection(ChartTab tab, List<ProcessorPreset> presets,
                                                               Map<String, String> availableFields) {
        ProcessorSelectionMemory.Result result;
        restoringProcessorSelection = true;
        try {
            tab.processorsListModel.setRowCount(0);
            tab.parametersTableModel.setRowCount(0);
            tab.dataset.removeAllSeries();
            tab.seriesIndex.clear();
            tab.activeProcessors.clear();
            result = selectionMemory.adapt(presets, availableFields, processorsTypesList, uiLanguage);
            for (ProcessorPreset preset : result.processors) tab.processorsListModel.addRow(new Object[]{true, preset});
        } finally {
            restoringProcessorSelection = false;
        }
        if (tab == getCurrentTab()) {
            processorsList.clearSelection();
            updateUsedColors();
        }
        return result;
    }

    private String appendSelectionRestoreNotice(String base, String notice) {
        return notice == null || notice.isEmpty() ? base : base + " · " + notice;
    }

    private void updateTopMinuteMarkersForCurrentRange() {
        if (logReader == null || chart == null) {
            return;
        }
        if (!topMinuteMarkerUpdating.compareAndSet(false, true)) {
            return;
        }
        try {
            Range displayRange = selectDomainAxis(timeMode).getRange();
            double timeScale = (selectDomainAxis(timeMode) == domainAxisDate) ? 1000.0 : 1.0;
            addTopMinuteMarkers(displayRange, timeScale);
        } finally {
            topMinuteMarkerUpdating.lazySet(false);
        }
    }

    private void clearTopMinuteMarkers() {
        if (chart == null || topMinuteMarkers.isEmpty()) {
            return;
        }
        XYPlot xyPlot = chart.getXYPlot();
        for (ValueMarker marker : topMinuteMarkers) {
            xyPlot.removeDomainMarker(marker);
        }
        topMinuteMarkers.clear();
    }

    private void addTopMinuteMarkers(Range displayRange, double timeScale) {
        if (timeMode == TIME_MODE_GPS || displayRange == null || (topMinuteCheckBox != null && !topMinuteCheckBox.isSelected())) {
            clearTopMinuteMarkers();
            return;
        }
        clearTopMinuteMarkers();
        Range markerRange = resolveTopMinuteMarkerRange(displayRange);
        if (markerRange == null) {
            return;
        }

        double displayLower = displayRange.getLowerBound();
        double displayUpper = displayRange.getUpperBound();

        double logLower = markerRange.getLowerBound();
        double logUpper = markerRange.getUpperBound();

        if (!Double.isFinite(displayLower) || !Double.isFinite(displayUpper) || displayUpper <= displayLower) {
            return;
        }

        long logMinMinute = (long) Math.floor(logLower / SECONDS_PER_MINUTE);
        long logMaxMinute = (long) Math.ceil(logUpper / SECONDS_PER_MINUTE);

        long displayMinMinute = (long) Math.ceil(displayLower / SECONDS_PER_MINUTE);
        long displayMaxMinute = (long) Math.floor(displayUpper / SECONDS_PER_MINUTE);

        long startMinute = Math.max(logMinMinute, displayMinMinute);
        long stopMinute = Math.min(logMaxMinute, displayMaxMinute);

        Stroke lineStroke = new BasicStroke(1.5f);
        Color lineColor = new Color(120, 120, 120);

        long totalMinutes = stopMinute >= startMinute ? (stopMinute - startMinute + 1) : 0;

        if (totalMinutes > 0) {
            long minuteStep = Math.max(1L, (long) Math.ceil((double) totalMinutes / getMaxTopMinuteLabelsByWidth()));
            for (long minute = startMinute; minute <= stopMinute; minute += minuteStep) {
                double second = minute * SECONDS_PER_MINUTE;
                boolean nearLeftEdge = (second - displayLower) < (displayUpper - displayLower) * 0.05;
                addTopMinuteMarker(second, minute, timeScale, lineColor, lineStroke, nearLeftEdge);
            }
            if ((stopMinute - startMinute) % minuteStep != 0) {
                double second = stopMinute * SECONDS_PER_MINUTE;
                boolean nearLeftEdge = (second - displayLower) < (displayUpper - displayLower) * 0.05;
                addTopMinuteMarker(second, stopMinute, timeScale, lineColor, lineStroke, nearLeftEdge);
            }
        } else {
            long currentMinute = (long) Math.floor(displayLower / SECONDS_PER_MINUTE);
            if (currentMinute >= logMinMinute && currentMinute <= logMaxMinute) {
                addTopMinuteMarker(displayLower, currentMinute, timeScale, lineColor, lineStroke, true);
            }
        }
    }

    private void addTopMinuteMarker(double second, long minute, double timeScale, Color lineColor, Stroke lineStroke, boolean leftAnchor) {
        ValueMarker minuteMarker = new ValueMarker(second * timeScale);
        minuteMarker.setPaint(lineColor);
        minuteMarker.setStroke(lineStroke);
        minuteMarker.setLabel(minute + ("zh_CN".equals(uiLanguage) ? "分钟" : " min"));
        if (leftAnchor) {
            minuteMarker.setLabelAnchor(RectangleAnchor.TOP_RIGHT);
            minuteMarker.setLabelTextAnchor(TextAnchor.TOP_LEFT);
        } else {
            minuteMarker.setLabelAnchor(RectangleAnchor.TOP);
            minuteMarker.setLabelTextAnchor(TextAnchor.TOP_CENTER);
        }
        minuteMarker.setLabelPaint(Color.BLACK);
        chart.getXYPlot().addDomainMarker(minuteMarker);
        topMinuteMarkers.add(minuteMarker);
    }

    private Range resolveTopMinuteMarkerRange(Range visibleRange) {
        if (visibleRange == null) {
            return null;
        }
        Range logRange = null;
        if (logReader != null) {
            long startUs = logReader.getStartMicroseconds();
            long sizeUs = logReader.getSizeMicroseconds();
            if (sizeUs > 0) {
                long timeOffset = getTimeOffset(timeMode);
                double logLower = (startUs + timeOffset) * 1e-6;
                double logUpper = (startUs + sizeUs + timeOffset) * 1e-6;
                logRange = new Range(logLower, logUpper);
            }
        }
        if (logRange == null && dataset != null && dataset.getSeriesCount() > 0) {
            double dataLower = dataset.getDomainLowerBound(true);
            double dataUpper = dataset.getDomainUpperBound(true);
            if (Double.isFinite(dataLower) && Double.isFinite(dataUpper) && dataUpper > dataLower) {
                logRange = new Range(dataLower, dataUpper);
            }
        }
        if (logRange != null) {
            double lower = Math.max(visibleRange.getLowerBound(), logRange.getLowerBound());
            double upper = Math.min(visibleRange.getUpperBound(), logRange.getUpperBound());
            if (upper > lower) {
                return new Range(lower, upper);
            } else {
                return null;
            }
        }
        return visibleRange;
    }

    private int getMaxTopMinuteLabelsByWidth() {
        int width = chartPanel == null ? 0 : chartPanel.getWidth();
        if (width <= 0) {
            return 10;
        }
        int byWidth = Math.max(MIN_TOP_MINUTE_LABELS, width / TOP_MINUTE_LABEL_PIXEL_SPACING);
        return Math.min(MAX_TOP_MINUTE_LABELS, byWidth);
    }

    private String getSeriesDisplayTitle(String processorTitle, String seriesTitle) {
        if ("New".equals(processorTitle) || processorTitle.equals(seriesTitle)) {
            return getLocalizedFieldTitle(seriesTitle);
        }
        return getLocalizedSeriesTitle(processorTitle + (seriesTitle.isEmpty() ? "" : ":" + seriesTitle));
    }

    private String getLocalizedFieldTitle(String fieldTitle) {
        if (!"zh_CN".equals(uiLanguage)) {
            return fieldTitle;
        }
        String localized = FieldNameLocalizer.toZhCn(fieldTitle);
        return localized.equals(fieldTitle) ? fieldTitle : fieldTitle + " | " + localized;
    }

    private String getLocalizedSeriesTitle(String fullTitle) {
        if (!"zh_CN".equals(uiLanguage)) {
            return fullTitle;
        }
        String[] parts = fullTitle.split(":", 2);
        String processorTitle = parts[0];
        String seriesTitle = parts.length > 1 ? parts[1] : "";
        String localizedProcessor = FieldNameLocalizer.toZhCn(processorTitle);
        String localizedSeries = FieldNameLocalizer.toZhCn(seriesTitle);
        String localized = localizedProcessor + (seriesTitle.isEmpty() ? "" : ":" + localizedSeries);
        return localized.equals(fullTitle) ? fullTitle : fullTitle + " | " + localized;
    }

    private void updateAxisUnitLabels() {
        if (domainAxisSeconds != null) {
            domainAxisSeconds.setLabel("zh_CN".equals(uiLanguage) ? "时间 / Time (s)" : "Time (s)");
        }
        if (domainAxisDate != null) {
            domainAxisDate.setLabel("zh_CN".equals(uiLanguage) ? "时间 / Time (UTC)" : "Time (UTC)");
        }
        ValueAxis rangeAxis = chart == null ? null : chart.getXYPlot().getRangeAxis();
        if (rangeAxis != null) {
            rangeAxis.setLabel(buildRangeAxisLabel());
        }
    }

    private String buildRangeAxisLabel() {
        Set<String> units = new LinkedHashSet<String>();
        for (ProcessorPreset preset : activeProcessors) {
            if (preset == null || preset.getParameters() == null) {
                continue;
            }
            Object fieldsObj = preset.getParameters().get("Fields");
            if (fieldsObj == null) {
                continue;
            }
            String[] fields = fieldsObj.toString().trim().split("\\s+");
            for (String field : fields) {
                String unit = inferUnitFromField(field);
                if (unit != null && unit.length() > 0) {
                    units.add(unit);
                }
            }
        }
        if (units.isEmpty()) {
            return "zh_CN".equals(uiLanguage) ? "数值 / Value" : "Value";
        }
        if (units.size() == 1) {
            String unit = units.iterator().next();
            return "zh_CN".equals(uiLanguage) ? ("数值 / Value (" + unit + ")") : ("Value (" + unit + ")");
        }
        StringBuilder unitText = new StringBuilder();
        int i = 0;
        for (String unit : units) {
            if (i > 0) {
                unitText.append(" / ");
            }
            unitText.append(unit);
            i++;
            if (i >= 3) {
                break;
            }
        }
        if (units.size() > 3) {
            unitText.append(" ...");
        }
        return "zh_CN".equals(uiLanguage) ? ("数值 / Value (混合单位 / " + unitText + ")") : ("Value (Mixed Units: " + unitText + ")");
    }

    private String inferUnitFromField(String fieldName) {
        if (fieldName == null) {
            return "";
        }
        String f = fieldName.toLowerCase(Locale.ROOT);
        if (f.contains("_cm") || f.endsWith("cm")) return "cm";
        if (f.contains("_mm") || f.endsWith("mm")) return "mm";
        if (f.contains("latitude") || f.contains("longitude") || f.endsWith(".lat") || f.endsWith(".lng") || f.endsWith(".lon")) return "deg";
        if (f.contains("yaw_rate") || f.contains("roll_rate") || f.contains("pitch_rate") || f.contains("gyro")) return "deg/s";
        if (f.contains("yaw") || f.contains("roll") || f.contains("pitch") || f.contains("heading") || f.contains("course")) return "deg";
        if (f.contains("velocity") || f.contains("vel_") || f.contains("ground_speed") || f.contains("climb")) return "m/s";
        if (f.contains("accel") || f.contains("acc[" ) || f.contains("acc_") || f.contains("raw_acc")) return "m/s²";
        if (f.contains("alt") || f.contains("height") || f.contains("hgt") || f.contains("distance") || f.contains("dist")) return "m";
        if (f.contains("volt") || f.contains("vcc")) return "V";
        if (f.contains("curr") || f.contains("current")) return "A";
        if (f.contains("temp")) return "°C";
        if (f.contains("rpm")) return "rpm";
        if (f.contains("rcin") || f.matches(".*\\.ch\\d+.*") || f.contains("moto")) return "µs";
        if (f.contains("time") || f.contains("timestamp")) return "s";
        return "";
    }

    private Font resolveChartTextFont() {
        if (chartTextFont != null) {
            return chartTextFont;
        }
        String[] preferred = new String[]{"Microsoft YaHei UI", "Microsoft YaHei", "SimHei", "SimSun", "Dialog"};
        Set<String> installed = new HashSet<String>(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        String family = "Dialog";
        for (String name : preferred) {
            if (installed.contains(name)) {
                family = name;
                break;
            }
        }
        chartTextFont = new Font(family, Font.PLAIN, 12);
        return chartTextFont;
    }

    private void applyChartTextFont() {
        if (chart == null) {
            return;
        }
        Font f = resolveChartTextFont();
        if (chart.getLegend() != null) {
            chart.getLegend().setItemFont(f);
        }
        if (chart.getTitle() != null) {
            chart.getTitle().setFont(f);
        }
        XYPlot p = chart.getXYPlot();
        if (p == null) {
            return;
        }
        ValueAxis domainAxis = p.getDomainAxis();
        if (domainAxis != null) {
            domainAxis.setLabelFont(f);
            domainAxis.setTickLabelFont(f);
        }
        ValueAxis rangeAxis = p.getRangeAxis();
        if (rangeAxis != null) {
            rangeAxis.setLabelFont(f);
            rangeAxis.setTickLabelFont(f);
        }
    }

    private void setChartColors() {
        if (dataset.getSeriesCount() > 0) {
            Collection<ValueMarker> markers = chart.getXYPlot().getDomainMarkers(0, Layer.BACKGROUND);
            List<ProcessorPreset> visiblePresets = getVisibleProcessorPresets();
            List<ProcessorPreset> colorPresets = visiblePresets.size() == seriesIndex.size()
                    ? visiblePresets : activeProcessors;
            for (int i = 0; i < colorPresets.size() && i < seriesIndex.size(); i++) {
                for (Map.Entry<String, Integer> entry : seriesIndex.get(i).entrySet()) {
                    ProcessorPreset processorPreset = colorPresets.get(i);
                    AbstractRenderer renderer = (AbstractRenderer) chart.getXYPlot().getRendererForDataset(dataset);
                    Paint color = processorPreset.getColors().get(entry.getKey());
                    renderer.setSeriesPaint(entry.getValue(), color, false);
                    if (markers != null) {
                        for (ValueMarker marker : markers) {
                            if (marker instanceof TaggedValueMarker && ((TaggedValueMarker) marker).tag == i) {
                                marker.setPaint(color);
                            }
                        }
                    }
                }
            }
            chartPanel.repaint();
        }
    }

    private List<ProcessorPreset> getVisibleProcessorPresets() {
        List<ProcessorPreset> visible = new ArrayList<ProcessorPreset>();
        for (int row = 0; row < processorsListModel.getRowCount(); row++) {
            Object enabled = processorsListModel.getValueAt(row, 0);
            Object value = processorsListModel.getValueAt(row, 1);
            if (Boolean.TRUE.equals(enabled) && value instanceof ProcessorPreset) {
                visible.add((ProcessorPreset) value);
            }
        }
        return visible;
    }

    private void applyLegendVisibility() {
        for (ChartTab tab : workspace.view()) {
            if (tab != null && tab.chart != null && tab.chart.getLegend() != null) {
                tab.chart.getLegend().setVisible(showLegend);
            }
            if (tab != null && tab.chartPanel != null) {
                tab.chartPanel.revalidate();
                tab.chartPanel.repaint();
            }
        }
    }

    private void setChartMarkers() {
        if (dataset.getSeriesCount() > 0) {
            boolean showMarkers = markerCheckBox.isSelected();
            Shape marker = new Ellipse2D.Double(-1.5, -1.5, 3, 3);
            Object renderer = chart.getXYPlot().getRendererForDataset(dataset);
            if (renderer instanceof XYLineAndShapeRenderer) {
                for (int j = 0; j < dataset.getSeriesCount(); j++) {
                    if (showMarkers) {
                        ((XYLineAndShapeRenderer) renderer).setSeriesShape(j, marker, false);
                    }
                    ((XYLineAndShapeRenderer) renderer).setSeriesShapesVisible(j, showMarkers);
                }
            }
        }
    }

    private Set<String> getAlreadyAddedFieldSet() {
        Set<String> fields = new HashSet<String>();
        for (int i = 0; i < processorsListModel.getRowCount(); i++) {
            Object rowValue = processorsListModel.getValueAt(i, 1);
            if (!(rowValue instanceof ProcessorPreset)) {
                continue;
            }
            ProcessorPreset pp = (ProcessorPreset) rowValue;
            Map<String, Object> params = pp.getParameters();
            if (params == null) {
                continue;
            }
            Object fieldsParam = params.get("Fields");
            if (fieldsParam == null) {
                continue;
            }
            String[] split = fieldsParam.toString().trim().split("\\s+");
            for (String field : split) {
                if (!field.isEmpty()) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    private void showAddProcessorDialog(boolean editMode) {
        if (!dialogs.claim("processor")) return;
        ProcessorPreset selectedProcessor = editMode ? getSelectedProcessor() : null;
        try {
            addProcessorDialog.display(new Runnable() {
                @Override public void run() { onAddProcessorDialogOK(); }
            }, selectedProcessor);
        } finally { dialogs.release("processor", null); }
    }

    private void onAddProcessorDialogOK() {
        ProcessorPreset processorPreset = addProcessorDialog.getOrigProcessorPreset();
        String title = addProcessorDialog.getProcessorTitle();
        String processorType = addProcessorDialog.getProcessorType();
        if (processorPreset != null) {
            // Edit processor
            ProcessorPreset processorPresetNew = processorPreset;
            if (!processorPreset.getProcessorType().equals(processorType)) {
                // Processor type changed
                Map<String, Object> parameters = processorPreset.getParameters();
                processorPresetNew = new ProcessorPreset(title, processorType, new HashMap<String, Object>(), Collections.<String, Color>emptyMap(), true);
                processorPresetNew.setUiLanguage(uiLanguage);
                updatePresetParameters(processorPresetNew, parameters);
                for (int row = 0; row < processorsListModel.getRowCount(); row++) {
                    if (processorsListModel.getValueAt(row, 1) == processorPreset) {
                        processorsListModel.setValueAt(processorPresetNew, row, 1);
                        processorsList.setRowSelectionInterval(row, row);
                        break;
                    }
                }
                showProcessorParameters();
            } else {
                // Only change title
                processorPresetNew.setTitle(title);
            }
        } else {
            processorPreset = new ProcessorPreset(title, processorType, Collections.<String, Object>emptyMap(), Collections.<String, Color>emptyMap(), true);
            processorPreset.setUiLanguage(uiLanguage);
            updatePresetParameters(processorPreset, null);
            int i = processorsListModel.getRowCount();
            processorsListModel.addRow(new Object[]{true, processorPreset});
            processorsList.setRowSelectionInterval(i, i);
        }
        updateUsedColors();
        updatePresetEdited(true);
        processFile();
    }

    private void updatePresetParameters(ProcessorPreset processorPreset, Map<String, Object> parametersUpdate) {
        if (parametersUpdate != null) {
            // Update parameters of preset
            processorPreset.getParameters().putAll(parametersUpdate);
        }
        // Construct and initialize processor to cleanup parameters list and get list of series
        PlotProcessor p;
        try {
            p = processorsTypesList.getProcessorInstance(processorPreset, 0.0, null);
        } catch (Exception e) {
            setStatus(("zh_CN".equals(uiLanguage) ? "处理器配置错误：" : "Processor configuration error: ")+processorPreset);
            return;
        }
        processorPreset.setParameters(p.getParameters());
        Map<String, Color> colorsNew = new HashMap<String, Color>();
        for (PlotItem series : p.getSeriesList()) {
            Color color = processorPreset.getColors().get(series.getTitle());
            if (color == null) {
                color = colorSupplier.getNextColor(series.getTitle());
            }
            colorsNew.put(series.getTitle(), color);
        }
        processorPreset.setColors(colorsNew);
    }

    private void removeSelectedProcessor() {
        if (removeConfirmDialog != null) { removeConfirmDialog.toFront(); return; }
        if (processorsList.isEditing()) processorsList.getCellEditor().stopCellEditing();
        int[] rows = processorsList.getSelectedRows();
        SortedSet<Integer> targets = new TreeSet<Integer>(Collections.reverseOrder());
        if (rows.length > 1) {
            for (int row : rows) targets.add(processorsList.convertRowIndexToModel(row));
        } else {
            for (int row = 0; row < processorsListModel.getRowCount(); row++) {
                if (Boolean.TRUE.equals(processorsListModel.getValueAt(row, 0))) targets.add(row);
            }
            if (targets.isEmpty() && rows.length == 1) targets.add(processorsList.convertRowIndexToModel(rows[0]));
        }
        if (!targets.isEmpty()) {
            for (int row : targets) processorsListModel.removeRow(row);
            clearSelectionRestoreNotice();
            updatePresetEdited(true);
            updateUsedColors();
            processFile();
        } else {
            setStatus("zh_CN".equals(uiLanguage) ? "请先勾选或高亮要移除的处理器" : "Check or select processors to remove");
        }
    }

    private void removeAllProcessors() {
        if (removeConfirmDialog != null) { removeConfirmDialog.toFront(); return; }
        final ChartTab target = getCurrentTab();
        if (processorsListModel.getRowCount() > 0) {
            if (!confirmRemoveAllProcessors(processorsListModel.getRowCount())) {
                return;
            }
            if (getCurrentTab() != target) return;
            processorsListModel.setRowCount(0);
            clearSelectionRestoreNotice();
            updatePresetEdited(true);
            updateUsedColors();
            processFile();
        }
    }

    private boolean confirmRemoveAllProcessors(int count) {
        if (!dialogs.claim("remove-all")) return false;
        String title = "zh_CN".equals(uiLanguage) ? "删除确认" : "Confirm Remove";
        String message;
        if ("zh_CN".equals(uiLanguage)) {
            message = String.format("将移除全部处理器，共 %d 项，是否继续？", count);
        } else {
            message = String.format("Remove all processors (%d items)?", count);
        }
        JOptionPane pane = new JOptionPane(message, JOptionPane.WARNING_MESSAGE, JOptionPane.YES_NO_OPTION);
        JDialog dialog = dialogs.register("remove-all", pane.createDialog(mainFrame, title));
        removeConfirmDialog = dialog;
        removeAllProcessorsButton.setEnabled(false);
        removeProcessorButton.setEnabled(false);
        try {
            fitDialogToScreen(dialog);
            dialog.setVisible(true);
            return Integer.valueOf(JOptionPane.YES_OPTION).equals(pane.getValue());
        } finally {
            dialog.dispose();
            removeConfirmDialog = null;
            dialogs.release("remove-all", null);
            removeAllProcessorsButton.setEnabled(true);
            removeProcessorButton.setEnabled(true);
            mainFrame.repaint();
        }
    }

    private void clearSelectionRestoreNotice() {
        ChartTab tab = getCurrentTab();
        if (tab != null) { tab.selectionRestoreNoticeZh = null; tab.selectionRestoreNoticeEn = null; }
    }

    private void showFieldColorDialog() {
        showFieldColorDialog(null);
    }

    private void showFieldColorDialog(String requestedField) {
        if (!dialogs.claim("field-color")) return;
        ProcessorPreset preset = getSelectedProcessor();
        if (preset == null && processorsListModel.getRowCount() > 0) {
            processorsList.setRowSelectionInterval(0, 0);
            preset = getSelectedProcessor();
        }
        if (preset == null || preset.getColors().isEmpty()) {
            dialogs.release("field-color", null);
            setStatus("zh_CN".equals(uiLanguage) ? "请先选择含曲线的处理器" : "Select a processor with plotted fields first");
            return;
        }
        final ChartTab ownerTab = getCurrentTab();
        final ProcessorPreset ownerPreset = preset;
        List<String> names = new ArrayList<String>(preset.getColors().keySet());
        names.sort(NaturalFieldOrder.INSTANCE);
        JComboBox<String> fieldChoice = new JComboBox<String>(names.toArray(new String[0]));
        if (requestedField != null && names.contains(requestedField)) fieldChoice.setSelectedItem(requestedField);

        JDialog dialog = dialogs.register("field-color", new JDialog(mainFrame,
                "zh_CN".equals(uiLanguage) ? "自定义字段颜色" : "Custom Field Color"));
        dialog.setResizable(false);
        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel fieldPanel = new JPanel(new BorderLayout(8, 0));
        fieldPanel.add(new JLabel("zh_CN".equals(uiLanguage) ? "字段" : "Field"), BorderLayout.WEST);
        fieldPanel.add(fieldChoice, BorderLayout.CENTER);
        content.add(fieldPanel, BorderLayout.NORTH);

        JPanel preview = new JPanel();
        preview.setName("colorPreview");
        preview.setPreferredSize(new Dimension(88, 54));
        preview.setBorder(BorderFactory.createTitledBorder("zh_CN".equals(uiLanguage) ? "预览" : "Preview"));
        JSpinner red = colorSpinner("redSpinner");
        JSpinner green = colorSpinner("greenSpinner");
        JSpinner blue = colorSpinner("blueSpinner");
        JLabel hex = new JLabel("#000000", SwingConstants.CENTER);
        hex.setName("hexColorLabel");
        JPanel rgb = new JPanel(new GridLayout(4, 2, 8, 6));
        rgb.setBorder(BorderFactory.createTitledBorder("RGB"));
        rgb.add(new JLabel("R")); rgb.add(red);
        rgb.add(new JLabel("G")); rgb.add(green);
        rgb.add(new JLabel("B")); rgb.add(blue);
        rgb.add(new JLabel("HEX")); rgb.add(hex);
        JPanel editor = new JPanel(new BorderLayout(10, 0));
        editor.add(preview, BorderLayout.WEST);
        editor.add(rgb, BorderLayout.CENTER);

        Color[] common = new Color[]{Color.BLACK, Color.DARK_GRAY, Color.GRAY, Color.WHITE,
                Color.RED, new Color(255, 128, 0), Color.YELLOW, Color.GREEN,
                Color.CYAN, Color.BLUE, new Color(128, 0, 255), Color.MAGENTA};
        JPanel swatches = new JPanel(new GridLayout(2, 6, 5, 5));
        swatches.setBorder(BorderFactory.createTitledBorder("zh_CN".equals(uiLanguage) ? "常用颜色" : "Common Colors"));
        final boolean[] updating = {false};
        Runnable updatePreview = () -> {
            if (updating[0]) return;
            Color c = new Color((Integer) red.getValue(), (Integer) green.getValue(), (Integer) blue.getValue());
            preview.setBackground(c);
            hex.setText(String.format("#%06X", c.getRGB() & 0xffffff));
            preview.repaint();
        };
        Runnable loadSelectedColor = () -> {
            Color c = ownerPreset.getColors().get(fieldChoice.getSelectedItem());
            if (c == null) return;
            updating[0] = true;
            red.setValue(c.getRed()); green.setValue(c.getGreen()); blue.setValue(c.getBlue());
            updating[0] = false;
            updatePreview.run();
        };
        javax.swing.event.ChangeListener rgbListener = e -> updatePreview.run();
        red.addChangeListener(rgbListener); green.addChangeListener(rgbListener); blue.addChangeListener(rgbListener);
        for (Color c : common) {
            JButton swatch = new JButton();
            swatch.setName(String.format("swatch-%06X", c.getRGB() & 0xffffff));
            swatch.setPreferredSize(new Dimension(38, 28));
            swatch.setBackground(c);
            swatch.setOpaque(true);
            swatch.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
            swatch.setToolTipText(String.format("#%06X", c.getRGB() & 0xffffff));
            swatch.addActionListener(e -> {
                updating[0] = true;
                red.setValue(c.getRed()); green.setValue(c.getGreen()); blue.setValue(c.getBlue());
                updating[0] = false;
                updatePreview.run();
            });
            swatches.add(swatch);
        }
        fieldChoice.addActionListener(e -> loadSelectedColor.run());
        loadSelectedColor.run();
        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.add(editor, BorderLayout.NORTH);
        center.add(swatches, BorderLayout.CENTER);
        content.add(center, BorderLayout.CENTER);

        JButton ok = new JButton("zh_CN".equals(uiLanguage) ? "确定" : "OK");
        ok.setName("colorOkButton");
        JButton cancel = new JButton("zh_CN".equals(uiLanguage) ? "取消" : "Cancel");
        cancel.setName("colorCancelButton");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(ok); buttons.add(cancel);
        content.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(ok);
        ok.addActionListener(e -> {
            if (getCurrentTab() == ownerTab && containsProcessor(ownerTab, ownerPreset)) {
                Color c = new Color((Integer) red.getValue(), (Integer) green.getValue(), (Integer) blue.getValue());
                applyFieldColor(ownerPreset, (String) fieldChoice.getSelectedItem(), c);
            }
            dialog.dispose();
        });
        cancel.addActionListener(e -> dialog.dispose());
        fieldColorDialog = dialog;
        fieldColorButton.setEnabled(false);
        try {
            dialog.pack();
            fitDialogToScreen(dialog);
            dialog.setLocationRelativeTo(mainFrame);
            dialog.validate();
            dialog.repaint();
            dialog.setVisible(true);
        }
        finally { dialog.dispose(); fieldColorDialog = null; dialogs.release("field-color", null); fieldColorButton.setEnabled(true); }
    }

    private JSpinner colorSpinner(String name) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(0, 0, 255, 1));
        spinner.setName(name);
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "0");
        spinner.setEditor(editor);
        editor.getTextField().setColumns(4);
        return spinner;
    }

    private boolean containsProcessor(ChartTab tab, ProcessorPreset preset) {
        for (int row = 0; row < tab.processorsListModel.getRowCount(); row++)
            if (tab.processorsListModel.getValueAt(row, 1) == preset) return true;
        return false;
    }

    private void applyFieldColor(ProcessorPreset preset, String field, Color color) {
        if (color == null || !preset.getColors().containsKey(field)) return;
        preset.getColors().put(field, color);
        clearSelectionRestoreNotice();
        setChartColors();
        chart.fireChartChanged();
        updateUsedColors();
        rememberProcessorSelection(getCurrentTab());
        updatePresetEdited(true);
        if (!parametersTable.isEditing()) showProcessorParameters();
        setStatus(("zh_CN".equals(uiLanguage) ? "已更新字段颜色：" : "Field color updated: ") + field);
    }

    private void updateUsedColors() {
        colorSupplier.resetColorsUsed();
        for (int i = 0; i < processorsListModel.getRowCount(); i++) {
            ProcessorPreset pp = (ProcessorPreset) processorsListModel.getValueAt(i, 1);
            for (Color color : pp.getColors().values()) {
                colorSupplier.markColorUsed(color);
            }
        }
    }

    private ProcessorPreset getSelectedProcessor() {
        int row = processorsList.getSelectedRow();
        return row < 0 ? null : (ProcessorPreset) processorsListModel.getValueAt(row, 1);
    }

    private void showProcessorParameters() {
        while (parametersTableModel.getRowCount() > 0) {
            parametersTableModel.removeRow(0);
        }
        ProcessorPreset selectedProcessor = getSelectedProcessor();
        if (selectedProcessor != null) {
            // Parameters
            Map<String, Object> params = selectedProcessor.getParameters();
            List<String> param_keys = new ArrayList<String>(params.keySet());
            Collections.sort(param_keys);
            for (String key : param_keys) {
                parametersTableModel.addRow(new Object[]{key, formatParameterValue(params.get(key))});
            }
            // Colors
            Map<String, Color> colors = selectedProcessor.getColors();
            List<String> color_keys = new ArrayList<String>(colors.keySet());
            Collections.sort(color_keys);
            for (String key : color_keys) {
                parametersTableModel.addRow(new Object[]{colorParamPrefix + key, colors.get(key)});
            }
        }
    }

    private void onParameterChanged(int row) {
        boolean changed = false;
        if (editingProcessor != null && editingProcessor == getSelectedProcessor()) {
            String key = parametersTableModel.getValueAt(row, 0).toString();
            Object value = parametersTableModel.getValueAt(row, 1);
            if (value instanceof Color) {
                applyFieldColor(editingProcessor, key.substring(colorParamPrefix.length()), (Color) value);
                return;
            }
            try {
                updatePresetParameters(editingProcessor, Collections.<String, Object>singletonMap(key, value.toString()));
                changed = true;
            } catch (Exception e) {
                setStatus(("zh_CN".equals(uiLanguage) ? "参数更新失败：" : "Unable to update parameter: ")+e.getMessage());
            }
            if (!(value instanceof Color)) {
                parametersTableModel.removeTableModelListener(parameterChangedListener);
                showProcessorParameters(); // refresh all parameters because changing one param might influence others (e.g. color)
                parametersTableModel.addTableModelListener(parameterChangedListener);
                parametersTable.addRowSelectionInterval(row, row);
                processFile();
            }
        }

        if (changed) {
            updatePresetEdited(true);
        }
    }

    void setEditingProcessor() {
        editingProcessor = getSelectedProcessor();
    }

    public JFreeChart getChart() {
        return chart;
    }
}
