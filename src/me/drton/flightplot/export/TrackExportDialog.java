package me.drton.flightplot.export;

import me.drton.flightplot.PreferencesUtil;
import me.drton.jmavlib.log.FormatErrorException;
import me.drton.jmavlib.log.LogReader;
import org.jfree.data.Range;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.prefs.Preferences;

public class TrackExportDialog extends JDialog {
    private static final String DIALOG_SETTING = "TrackExportDialog";
    private static final String EXPORTER_CONFIGURATION_SETTING = "ExporterConfiguration";
    private static final String READER_CONFIGURATION_SETTING = "ReaderConfiguration";
    private static final String LAST_EXPORT_DIRECTORY_SETTING = "LastExportDirectory";

    private JPanel contentPane;
    private JButton buttonExport;
    private JCheckBox splitTrackByFlightCheckBox;
    private JComboBox exportFormatComboBox;
    private JSlider samplesPerSecond;
    private JLabel samplesPerSecondValue;
    private JLabel logEndTimeValue;
    private JTextField timeEndField;
    private JTextField timeStartField;
    private JLabel timeStartLabel;
    private JLabel timeEndLabel;
    private JTextField altOffsetField;
    private JLabel statusLabel;
    private JButton buttonClose;
    private JCheckBox exportDataInChartCheckBox;
    private File lastExportDirectory;
    private Map<String, TrackExporter> exporters;

    private TrackExporterConfiguration exporterConfiguration = new TrackExporterConfiguration();
    private TrackReaderConfiguration readerConfiguration = new TrackReaderConfiguration();
    private LogReader logReader;
    private Range chartRange;
    private String uiLanguage = "en";
    private JLabel formatLabel;
    private JLabel startTimeTextLabel;
    private JLabel endTimeTextLabel;
    private JLabel altitudeOffsetLabel;
    private JLabel samplesPerSecondLabel;
    private SwingWorker<Void, Void> exportWorker;

    private class ExportFormatItem {
        String description;
        String formatName;

        ExportFormatItem(String description, String formatName) {
            this.description = description;
            this.formatName = formatName;
        }

        @Override
        public String toString() {
            return getLocalizedExportFormatDescription(description, formatName);
        }
    }

    public TrackExportDialog(Map<String, TrackExporter> exporters) {
        this.exporters = exporters;
        initializeUi();
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonExport);
        setTitle("Export settings");
        initExportersList(exporters);
        initSampleSlider();
        buttonExport.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                export();
            }
        });
        buttonClose.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onClose();
            }
        });
        // call onClose() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onClose();
            }
        });
        // call onClose() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onClose();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        samplesPerSecond.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent changeEvent) {
                updateForSamplesPerSecond();
            }
        });
        exportDataInChartCheckBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent itemEvent) {
                validateTimeRange(null);
            }
        });
        logEndTimeValue.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
                if (!exportDataInChartCheckBox.isSelected()) {
                    timeEndField.setText(stringFromMicroseconds(logReader.getSizeMicroseconds()));
                }
            }
        });

        DocumentListener timeChangedListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                validateTimeRange(null);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                validateTimeRange(null);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                validateTimeRange(null);
            }
        };
        timeStartField.getDocument().addDocumentListener(timeChangedListener);
        timeEndField.getDocument().addDocumentListener(timeChangedListener);
        applyLanguageTexts();
        pack();
    }

    private void initExportersList(Map<String, TrackExporter> exporters) {
        for (TrackExporter exporter : exporters.values()) {
            exportFormatComboBox.addItem(new ExportFormatItem(exporter.getDescription(), exporter.getName()));
        }
    }

    private void initSampleSlider() {
        Dictionary<Integer, JLabel> labels = new Hashtable<Integer, JLabel>();
        labels.put(1, new JLabel("0.1"));
        labels.put(10, new JLabel("1"));
        labels.put(19, new JLabel("10"));
        samplesPerSecond.setLabelTable(labels);
    }

    private String stringFromMicroseconds(long us) {
        return String.format(Locale.ROOT, "%.3f", us / 1000000.0);
    }

    private long getTimeInterval() {
        int value = samplesPerSecond.getValue();
        if (value <= 10) {
            return 10000000 / value;
        } else if (value == 20) {
            return 0;
        } else {
            return 1000000 / (value - 9);
        }
    }

    private void setTimeInterval(long interval) {
        if (interval == 0) {
            samplesPerSecond.setValue(20);
        } else if (interval <= 1000000) {
            samplesPerSecond.setValue((int) (1000000 / interval + 9));
        } else {
            samplesPerSecond.setValue((int) (10000000 / interval));
        }
        updateForSamplesPerSecond();
    }

    private void updateForSamplesPerSecond() {
        if (getTimeInterval() == 0) {
            samplesPerSecondValue.setText("max");
        } else {
            samplesPerSecondValue.setText(String.format(Locale.ROOT, "%.1f", 1000000.0 / getTimeInterval()));
        }
    }

    private String formatTime(long time) {
        long s = time / 1000000;
        long ms = (time / 1000) % 1000;
        return String.format(Locale.ROOT, "%02d:%02d:%02d.%03d",
                (int) (s / 3600), s / 60 % 60, s % 60, ms);
    }

    public void display(LogReader logReader, Range chartRange) {
        if (logReader == null) {
            throw new RuntimeException("Log not opened");
        }
        this.logReader = logReader;
        this.chartRange = chartRange;
        readerConfiguration.setTimeStart(logReader.getStartMicroseconds());
        readerConfiguration.setTimeEnd(logReader.getStartMicroseconds() + logReader.getSizeMicroseconds());
        updateDialogFromConfiguration();
        setVisible(true);
    }

    private double getLogSizeInSeconds() {
        return logReader.getSizeMicroseconds() / 1000000.0;
    }

    private File getDestinationFile(String extension, String description) {
        JFileChooser fc = new JFileChooser();
        if (lastExportDirectory != null) {
            fc.setCurrentDirectory(lastExportDirectory);
        }
        FileNameExtensionFilter extensionFilter = new FileNameExtensionFilter(description, extension);
        fc.setFileFilter(extensionFilter);
        fc.setDialogTitle(tr("export_track"));
        int returnVal = fc.showDialog(null, tr("export"));
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            lastExportDirectory = fc.getCurrentDirectory();
            String exportFileName = fc.getSelectedFile().toString();
            String exportFileExtension = extensionFilter.getExtensions()[0];
            if (extensionFilter == fc.getFileFilter() && !exportFileName.toLowerCase().endsWith(exportFileExtension)) {
                exportFileName += ("." + exportFileExtension);
            }
            File exportFile = new File(exportFileName);
            if (!exportFile.exists()) {
                return exportFile;
            } else {
                int result = JOptionPane.showConfirmDialog(null,
                        tr("overwrite_file_ask") + "\n" + exportFile.getAbsoluteFile(),
                        tr("file_exists"), JOptionPane.YES_NO_OPTION);
                if (JOptionPane.YES_OPTION == result) {
                    return exportFile;
                }
            }
        }
        return null;
    }

    private void export() {
        if (exportWorker != null && !exportWorker.isDone()) {
            return;
        }
        updateConfiguration();
        final TrackExporter exporter = exporters.get(exporterConfiguration.getExportFormat());
        if (exporter != null) {
            final File file = getDestinationFile(exporter.getFileExtension(), exporter.getDescription());
            if (null != file) {
                setStatus(tr("exporting"), false);
                buttonExport.setEnabled(false);
                buttonClose.setEnabled(false);
                final LogReader reader = logReader;
                final TrackReaderConfiguration readerConfig = readerConfiguration;
                final TrackExporterConfiguration exporterConfig = exporterConfiguration;
                exportWorker = new SwingWorker<Void, Void>() {
                    @Override
                    protected Void doInBackground() throws Exception {
                        synchronized (reader) {
                            TrackReader trackReader = TrackReaderFactory.getTrackReader(reader, readerConfig);
                            String trackTitle = "Track";
                            exporter.export(trackReader, exporterConfig, file, trackTitle);
                        }
                        return null;
                    }

                    @Override
                    protected void done() {
                        try {
                            get();
                            setStatus(String.format(tr("exported_to"), file), false);
                        } catch (Exception e) {
                            Throwable cause = e.getCause() == null ? e : e.getCause();
                            setStatus(String.format(tr("export_failed"), cause.getMessage()), true);
                        } finally {
                            buttonExport.setEnabled(true);
                            buttonClose.setEnabled(true);
                            exportWorker = null;
                        }
                    }
                };
                exportWorker.execute();
            }
        }
    }

    private void setStatus(String status, boolean error) {
        statusLabel.setText(status);
        if (error) {
            statusLabel.setForeground(Color.RED);
        } else {
            statusLabel.setForeground(Color.BLACK);
        }
    }

    private Long parseExportTime(JTextField field, JLabel label) {
        try {
            long time = (long) (Double.parseDouble(field.getText()) * 1000000);
            label.setText(formatTime(time));
            return time;
        } catch (NumberFormatException e) {
            label.setText("-");
            return null;
        }
    }

    private boolean validateTimeRange(TrackReaderConfiguration configuration) {
        String errorMsg = null;
        if (exportDataInChartCheckBox.isSelected()) {
            timeStartField.setEnabled(false);
            timeEndField.setEnabled(false);
            timeStartLabel.setText(formatTime((long) (chartRange.getLowerBound() * 1000000) - logReader.getStartMicroseconds()));
            timeEndLabel.setText(formatTime((long) (chartRange.getUpperBound() * 1000000) - logReader.getStartMicroseconds()));
        } else {
            timeStartField.setEnabled(true);
            timeEndField.setEnabled(true);
            Long timeStart;
            Long timeEnd;
            timeStart = parseExportTime(timeStartField, timeStartLabel);
            timeEnd = parseExportTime(timeEndField, timeEndLabel);
            if (timeStart == null || timeEnd == null) {
                errorMsg = "Invalid export time format";
                if ("zh_CN".equals(uiLanguage)) {
                    errorMsg = "导出时间格式无效 / Invalid export time format";
                }
            } else {
                if (timeStart < 0 || timeEnd <= timeStart) {
                    errorMsg = "Invalid export time range";
                    if ("zh_CN".equals(uiLanguage)) {
                        errorMsg = "导出时间范围无效 / Invalid export time range";
                    }
                } else if (configuration != null) {
                    configuration.setTimeStart(timeStart + logReader.getStartMicroseconds());
                    configuration.setTimeEnd(timeEnd + logReader.getStartMicroseconds());
                }
            }
        }
        if (errorMsg != null) {
            buttonExport.setEnabled(false);
            setStatus(errorMsg, true);
            return false;
        } else {
            buttonExport.setEnabled(true);
            setStatus(tr("ready_to_export"), false);
            return true;
        }
    }

    private boolean updateConfiguration() {
        String errorMsg = null;
        exporterConfiguration.setSplitTracksByFlightMode(splitTrackByFlightCheckBox.isSelected());
        ExportFormatItem item = (ExportFormatItem) exportFormatComboBox.getSelectedItem();
        exporterConfiguration.setExportFormat(item.formatName);

        readerConfiguration.setTimeInterval(getTimeInterval());
        if (exportDataInChartCheckBox.isSelected()) {
            readerConfiguration.setTimeStart((long) (chartRange.getLowerBound() * 1000000));
            readerConfiguration.setTimeEnd((long) (chartRange.getUpperBound() * 1000000));
        } else {
            if (!validateTimeRange(readerConfiguration)) {
                return false;
            }
        }
        double altOffset;
        try {
            altOffset = Double.parseDouble(altOffsetField.getText());
            readerConfiguration.setAltitudeOffset(altOffset);
        } catch (NumberFormatException e) {
            errorMsg = "Invalid altitude offset format";
            if ("zh_CN".equals(uiLanguage)) {
                errorMsg = "高度偏移格式无效 / Invalid altitude offset format";
            }
        }
        if (errorMsg != null) {
            buttonExport.setEnabled(false);
            setStatus(errorMsg, true);
            return false;
        } else {
            buttonExport.setEnabled(true);
            setStatus(tr("ready_to_export"), false);
            return true;
        }
    }

    private void updateDialogFromConfiguration() {
        splitTrackByFlightCheckBox.setSelected(exporterConfiguration.isSplitTracksByFlightMode());
        if (exporterConfiguration.getExportFormat() != null) {
            for (int index = 0; index < exportFormatComboBox.getItemCount(); index++) {
                ExportFormatItem item = (ExportFormatItem) exportFormatComboBox.getItemAt(index);
                if (exporterConfiguration.getExportFormat().equals(item.formatName)) {
                    exportFormatComboBox.setSelectedIndex(index);
                    break;
                }
            }
        }
        timeStartField.setText(stringFromMicroseconds(readerConfiguration.getTimeStart() - logReader.getStartMicroseconds()));
        timeEndField.setText(stringFromMicroseconds(readerConfiguration.getTimeEnd() - logReader.getStartMicroseconds()));
        altOffsetField.setText(String.valueOf(readerConfiguration.getAltitudeOffset()));

        setTimeInterval(readerConfiguration.getTimeInterval());
        logEndTimeValue.setText(String.format(tr("log_end"), stringFromMicroseconds(logReader.getSizeMicroseconds())));
        validateTimeRange(null);
    }

    private void onClose() {
        dispose();
    }

    public void savePreferences(Preferences preferences) {
        PreferencesUtil.saveWindowPreferences(this, preferences.node(DIALOG_SETTING));
        exporterConfiguration.saveConfiguration(preferences.node(EXPORTER_CONFIGURATION_SETTING));
        readerConfiguration.saveConfiguration(preferences.node(READER_CONFIGURATION_SETTING));
        if (lastExportDirectory != null) {
            preferences.put(LAST_EXPORT_DIRECTORY_SETTING, lastExportDirectory.getAbsolutePath());
        }
    }

    public void loadPreferences(Preferences preferences) {
        PreferencesUtil.loadWindowPreferences(this, preferences.node(DIALOG_SETTING), -1, -1);
        exporterConfiguration.loadConfiguration(preferences.node(EXPORTER_CONFIGURATION_SETTING));
        readerConfiguration.loadConfiguration(preferences.node(READER_CONFIGURATION_SETTING));
        String lastExportDirectoryPath = preferences.get(LAST_EXPORT_DIRECTORY_SETTING, null);
        if (null != lastExportDirectoryPath) {
            lastExportDirectory = new File(lastExportDirectoryPath);
        }
    }

    public void setUiLanguage(String uiLanguage) {
        this.uiLanguage = uiLanguage == null ? "en" : uiLanguage;
        applyLanguageTexts();
    }

    private void initializeUi() {
        if (contentPane != null) {
            return;
        }
        contentPane = new JPanel(new BorderLayout(8, 8));
        contentPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.anchor = GridBagConstraints.WEST;
        left.insets = new Insets(4, 4, 4, 8);
        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.weightx = 1.0;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.insets = new Insets(4, 0, 4, 4);

        int row = 0;
        formatLabel = new JLabel("Format");
        addRow(formPanel, left, right, row++, formatLabel, exportFormatComboBox = new JComboBox());
        splitTrackByFlightCheckBox = new JCheckBox("Split track by flight mode");
        addFullWidth(formPanel, row++, splitTrackByFlightCheckBox);
        exportDataInChartCheckBox = new JCheckBox("Export current chart time range");
        addFullWidth(formPanel, row++, exportDataInChartCheckBox);

        timeStartField = new JTextField("0.000");
        timeStartLabel = new JLabel("-");
        startTimeTextLabel = new JLabel("Start time, s");
        addRow(formPanel, left, right, row++, startTimeTextLabel, wrapFieldWithStatus(timeStartField, timeStartLabel));

        timeEndField = new JTextField("0.000");
        timeEndLabel = new JLabel("-");
        logEndTimeValue = new JLabel();
        endTimeTextLabel = new JLabel("End time, s");
        addRow(formPanel, left, right, row++, endTimeTextLabel, wrapFieldWithStatus(timeEndField, timeEndLabel, logEndTimeValue));

        altOffsetField = new JTextField("0.0");
        altitudeOffsetLabel = new JLabel("Altitude offset");
        addRow(formPanel, left, right, row++, altitudeOffsetLabel, altOffsetField);

        samplesPerSecond = new JSlider(1, 20, 10);
        samplesPerSecond.setPaintLabels(true);
        samplesPerSecond.setPaintTicks(true);
        samplesPerSecond.setMajorTickSpacing(9);
        samplesPerSecond.setMinorTickSpacing(1);
        samplesPerSecondValue = new JLabel("1.0");
        samplesPerSecondLabel = new JLabel("Samples / s");
        addRow(formPanel, left, right, row++, samplesPerSecondLabel, wrapFieldWithStatus(samplesPerSecond, samplesPerSecondValue));

        contentPane.add(formPanel, BorderLayout.CENTER);

        statusLabel = new JLabel("Ready to export");
        contentPane.add(statusLabel, BorderLayout.NORTH);

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonExport = new JButton("Export");
        buttonClose = new JButton("Close");
        buttonsPanel.add(buttonExport);
        buttonsPanel.add(buttonClose);
        contentPane.add(buttonsPanel, BorderLayout.SOUTH);
    }

    private void addRow(JPanel panel, GridBagConstraints left, GridBagConstraints right, int row, JLabel label, Component component) {
        GridBagConstraints labelConstraints = (GridBagConstraints) left.clone();
        labelConstraints.gridy = row;
        panel.add(label, labelConstraints);
        GridBagConstraints componentConstraints = (GridBagConstraints) right.clone();
        componentConstraints.gridy = row;
        panel.add(component, componentConstraints);
    }

    private void addRow(JPanel panel, GridBagConstraints left, GridBagConstraints right, int row, String label, Component component) {
        addRow(panel, left, right, row, new JLabel(label), component);
    }

    private void addFullWidth(JPanel panel, int row, Component component) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.gridwidth = 2;
        constraints.weightx = 1.0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(4, 4, 4, 4);
        panel.add(component, constraints);
    }

    private JComponent wrapFieldWithStatus(Component mainComponent, Component... extraComponents) {
        JPanel panel = new JPanel(new BorderLayout(6, 0));
        panel.add(mainComponent, BorderLayout.CENTER);
        if (extraComponents.length > 0) {
            JPanel trailing = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            for (Component component : extraComponents) {
                trailing.add(component);
            }
            panel.add(trailing, BorderLayout.EAST);
        }
        return panel;
    }

    private void applyLanguageTexts() {
        setTitle(tr("export_settings_title"));
        if (formatLabel != null) formatLabel.setText(tr("format"));
        if (splitTrackByFlightCheckBox != null) splitTrackByFlightCheckBox.setText(tr("split_track"));
        if (exportDataInChartCheckBox != null) exportDataInChartCheckBox.setText(tr("export_current_range"));
        if (startTimeTextLabel != null) startTimeTextLabel.setText(tr("start_time_s"));
        if (endTimeTextLabel != null) endTimeTextLabel.setText(tr("end_time_s"));
        if (altitudeOffsetLabel != null) altitudeOffsetLabel.setText(tr("altitude_offset"));
        if (samplesPerSecondLabel != null) samplesPerSecondLabel.setText(tr("samples_per_s"));
        if (buttonExport != null) buttonExport.setText(tr("export"));
        if (buttonClose != null) buttonClose.setText(tr("close"));
        if (statusLabel != null && statusLabel.getText() != null) {
            String s = statusLabel.getText();
            if ("Ready to export".equals(s) || "准备导出 / Ready to export".equals(s)) {
                statusLabel.setText(tr("ready_to_export"));
            } else if ("Exporting...".equals(s) || "导出中... / Exporting...".equals(s)) {
                statusLabel.setText(tr("exporting"));
            }
        }
        if (logReader != null) {
            logEndTimeValue.setText(String.format(tr("log_end"), stringFromMicroseconds(logReader.getSizeMicroseconds())));
        }
        if (exportFormatComboBox != null) {
            exportFormatComboBox.repaint();
        }
    }

    private String getLocalizedExportFormatDescription(String description, String formatName) {
        if (!"zh_CN".equals(uiLanguage)) {
            return description;
        }
        if ("KML".equalsIgnoreCase(formatName)) {
            return description + " / 谷歌地球轨迹";
        }
        if ("GPX".equalsIgnoreCase(formatName)) {
            return description + " / GPS交换格式";
        }
        return description;
    }

    private String tr(String key) {
        boolean zh = "zh_CN".equals(uiLanguage);
        if ("export_settings_title".equals(key)) return zh ? "导出设置 / Export settings" : "Export settings";
        if ("format".equals(key)) return zh ? "格式 / Format" : "Format";
        if ("split_track".equals(key)) return zh ? "按飞行模式分段轨迹 / Split track by flight mode" : "Split track by flight mode";
        if ("export_current_range".equals(key)) return zh ? "导出当前图表时间范围 / Export current chart time range" : "Export current chart time range";
        if ("start_time_s".equals(key)) return zh ? "开始时间(秒) / Start time, s" : "Start time, s";
        if ("end_time_s".equals(key)) return zh ? "结束时间(秒) / End time, s" : "End time, s";
        if ("altitude_offset".equals(key)) return zh ? "高度偏移 / Altitude offset" : "Altitude offset";
        if ("samples_per_s".equals(key)) return zh ? "采样率(每秒) / Samples / s" : "Samples / s";
        if ("ready_to_export".equals(key)) return zh ? "准备导出 / Ready to export" : "Ready to export";
        if ("exporting".equals(key)) return zh ? "导出中... / Exporting..." : "Exporting...";
        if ("exported_to".equals(key)) return zh ? "已导出到 \"%s\"" : "Exported to \"%s\"";
        if ("export_failed".equals(key)) return zh ? "导出失败: %s" : "Export failed: %s";
        if ("log_end".equals(key)) return zh ? "（日志结束: %s）" : " (log end: %s)";
        if ("export_track".equals(key)) return zh ? "导出轨迹 / Export Track" : "Export Track";
        if ("export".equals(key)) return zh ? "导出 / Export" : "Export";
        if ("close".equals(key)) return zh ? "关闭 / Close" : "Close";
        if ("overwrite_file_ask".equals(key)) return zh ? "是否覆盖已存在文件？ / Do you want to overwrite the existing file?" : "Do you want to overwrite the existing file?";
        if ("file_exists".equals(key)) return zh ? "文件已存在 / File already exists" : "File already exists";
        return key;
    }
}
