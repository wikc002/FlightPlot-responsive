package me.drton.flightplot;

import me.drton.jmavlib.log.LogReader;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.BorderLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * User: ton Date: 27.10.13 Time: 17:45
 */
public class LogInfo {
    private JFrame mainFrame;
    private JPanel mainPanel;
    private JSplitPane splitPane;
    private JTable infoTable;
    private DefaultTableModel infoTableModel;
    private JTable parametersTable;
    private DefaultTableModel parametersTableModel;
    private JScrollPane infoScrollPane;
    private JScrollPane parametersScrollPane;
    private DateFormat dateFormat;
    private String uiLanguage = "en";

    public LogInfo() {
        initializeUi();
        mainFrame = new JFrame("Log Info");
        mainFrame.setContentPane(mainPanel);
        mainFrame.pack();
        dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        applyLanguageTexts();
    }

    public JFrame getFrame() {
        return mainFrame;
    }

    public void setVisible(boolean visible) {
        if (visible) {
            mainFrame.validate();
            mainFrame.repaint();
            mainFrame.setVisible(true);
            mainFrame.toFront();
            adjustSplitPaneDivider();
            alignValueColumns();
        } else {
            mainFrame.setVisible(false);
        }
    }

    public void dispose() {
        mainFrame.dispose();
    }

    public void setUiLanguage(String uiLanguage) {
        this.uiLanguage = uiLanguage;
        applyLanguageTexts();
    }

    public void updateInfo(LogReader logReader) {
        while (infoTableModel.getRowCount() > 0) {
            infoTableModel.removeRow(0);
        }
        while (parametersTableModel.getRowCount() > 0) {
            parametersTableModel.removeRow(0);
        }
        applyLanguageTexts();
        if (logReader != null) {
            infoTableModel.addRow(new Object[]{trInfo("Format"), logReader.getFormat()});
            infoTableModel.addRow(new Object[]{trInfo("System"), logReader.getSystemName()});
            infoTableModel.addRow(new Object[]{
                    trInfo("Length, s"), String.format(Locale.ROOT, "%.3f", logReader.getSizeMicroseconds() * 1e-6)});
            String startTimeStr = "";
            if (logReader.getUTCTimeReferenceMicroseconds() > 0) {
                startTimeStr = dateFormat.format(
                        new Date((logReader.getStartMicroseconds() + logReader.getUTCTimeReferenceMicroseconds()) / 1000)) + " UTC";
            }
            infoTableModel.addRow(new Object[]{
                    trInfo("Start Time"), startTimeStr});
            infoTableModel.addRow(new Object[]{trInfo("Updates count"), logReader.getSizeUpdates()});
            infoTableModel.addRow(new Object[]{trInfo("Errors"), logReader.getErrors().size()});
            Map<String, Object> ver = logReader.getVersion();
            Object hw = ver == null ? null : ver.get("HW");
            Object fw = ver == null ? null : ver.get("FW");
            infoTableModel.addRow(new Object[]{trInfo("Hardware Version"), hw == null ? "" : hw});
            infoTableModel.addRow(new Object[]{trInfo("Firmware Version"), fw == null ? "" : fw});
            Map<String, Object> parameters = logReader.getParameters();
            if (parameters != null) {
                List<String> keys = new ArrayList<String>(parameters.keySet());
                Collections.sort(keys);
                for (String key : keys) {
                    Object v = parameters.get(key);
                    parametersTableModel.addRow(new Object[]{toDisplayParameterName(key), v == null ? "" : v.toString()});
                }
            }
        }
        adjustSplitPaneDivider();
        alignValueColumns();
    }

    private void createUIComponents() {
        // Info table
        infoTableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        infoTableModel.addColumn("Property");
        infoTableModel.addColumn("Value");
        infoTable = new JTable(infoTableModel) {
            @Override
            public String getToolTipText(java.awt.event.MouseEvent e) {
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
        infoTable.setRowHeight(24);
        infoTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        parametersTableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        parametersTableModel.addColumn("Parameter");
        parametersTableModel.addColumn("Value");
        parametersTable = new JTable(parametersTableModel) {
            @Override
            public String getToolTipText(java.awt.event.MouseEvent e) {
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
        parametersTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    }

    private void initializeUi() {
        if (mainPanel != null) {
            return;
        }
        createUIComponents();
        mainPanel = new JPanel(new BorderLayout(4, 4));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                infoScrollPane = new JScrollPane(infoTable),
                parametersScrollPane = new JScrollPane(parametersTable));
        infoScrollPane.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        parametersScrollPane.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        infoScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        parametersScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        splitPane.setResizeWeight(0.0);
        splitPane.setDividerSize(3);
        splitPane.setContinuousLayout(true);
        splitPane.setBorder(null);
        mainPanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                alignValueColumns();
            }
        });
        mainPanel.add(splitPane, BorderLayout.CENTER);
        applyLanguageTexts();
    }

    private void adjustSplitPaneDivider() {
        if (splitPane == null) {
            return;
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                int rows = Math.max(1, infoTableModel.getRowCount());
                int headerHeight = infoTable.getTableHeader().getPreferredSize().height;
                int contentHeight = headerHeight + rows * infoTable.getRowHeight() + 4;
                int target = Math.max(90, Math.min(contentHeight, 180));
                int available = splitPane.getHeight();
                if (available > 0) {
                    target = Math.min(target, Math.max(90, available - 100));
                }
                splitPane.setDividerLocation(target);
            }
        });
    }

    private void applyLanguageTexts() {
        boolean zh = "zh_CN".equals(uiLanguage);
        if (mainFrame != null) {
            mainFrame.setTitle(zh ? "日志信息 / Log Info" : "Log Info");
        }
        if (infoTableModel != null) {
            infoTableModel.setColumnIdentifiers(new Object[]{zh ? "属性 / Property" : "Property", zh ? "值 / Value" : "Value"});
        }
        if (parametersTableModel != null) {
            parametersTableModel.setColumnIdentifiers(new Object[]{zh ? "参数 / Parameter" : "Parameter", zh ? "值 / Value" : "Value"});
        }
        alignValueColumns();
    }

    private void alignValueColumns() {
        if (infoTable == null || parametersTable == null) {
            return;
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (infoTable.getColumnModel().getColumnCount() < 2 || parametersTable.getColumnModel().getColumnCount() < 2) {
                    return;
                }
                int infoViewportWidth = infoScrollPane == null ? infoTable.getWidth() : infoScrollPane.getViewport().getExtentSize().width;
                int parametersViewportWidth = parametersScrollPane == null ? parametersTable.getWidth() : parametersScrollPane.getViewport().getExtentSize().width;
                int commonWidth = Math.min(infoViewportWidth, parametersViewportWidth);
                if (commonWidth <= 0) {
                    commonWidth = Math.min(infoTable.getWidth(), parametersTable.getWidth());
                }
                if (commonWidth <= 0) {
                    return;
                }
                int leftWidth = commonWidth / 2;
                int rightWidth = commonWidth - leftWidth;
                setColumnWidth(infoTable, 0, leftWidth);
                setColumnWidth(parametersTable, 0, leftWidth);
                setColumnWidth(infoTable, 1, rightWidth);
                setColumnWidth(parametersTable, 1, rightWidth);
            }
        });
    }

    private void setColumnWidth(JTable table, int index, int width) {
        TableColumn col = table.getColumnModel().getColumn(index);
        col.setMinWidth(width);
        col.setMaxWidth(width);
        col.setPreferredWidth(width);
        col.setWidth(width);
        col.setResizable(false);
    }

    private String trInfo(String key) {
        if (!"zh_CN".equals(uiLanguage)) {
            return key;
        }
        if ("Format".equals(key)) return "Format / 格式";
        if ("System".equals(key)) return "System / 系统";
        if ("Length, s".equals(key)) return "Length, s / 时长(秒)";
        if ("Start Time".equals(key)) return "Start Time / 开始时间";
        if ("Updates count".equals(key)) return "Updates count / 更新数";
        if ("Errors".equals(key)) return "Errors / 错误数";
        if ("Hardware Version".equals(key)) return "Hardware Version / 硬件版本";
        if ("Firmware Version".equals(key)) return "Firmware Version / 固件版本";
        return key;
    }

    private String toDisplayParameterName(String key) {
        if (!"zh_CN".equals(uiLanguage)) {
            return key;
        }
        String zh = ParameterNameLocalizer.toZhCn(key);
        if (zh.equals(key)) {
            return key;
        }
        return key + "  " + zh;
    }
}
