package me.drton.flightplot;

import me.drton.flightplot.processors.PlotProcessor;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;

public class AddProcessorDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextField titleField;
    private JTable processorTypesTable;
    private DefaultTableModel processorTypesTableModel;
    private String[] processorTypes;
    private String uiLanguage = "en";
    private JLabel titleLabel;
    private JScrollPane processorListScrollPane;

    private ProcessorPreset origProcessorPreset = null;
    private Runnable callback;

    public AddProcessorDialog(Window owner, String[] processorTypes) {
        super(owner, Dialog.ModalityType.DOCUMENT_MODAL);
        this.processorTypes = processorTypes;
        initializeUi();
        setContentPane(contentPane);
        setTitle("Add Processor");
        getRootPane().setDefaultButton(buttonOK);
        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });
        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });
        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });
        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    public String getProcessorTitle() {
        return titleField.getText();
    }

    public ProcessorPreset getOrigProcessorPreset() {
        return origProcessorPreset;
    }

    public String getProcessorType() {
        int viewRow = processorTypesTable.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        int modelRow = processorTypesTable.convertRowIndexToModel(viewRow);
        return (String) processorTypesTableModel.getValueAt(modelRow, 0);
    }

    public void setUiLanguage(String uiLanguage) {
        this.uiLanguage = uiLanguage;
        applyLanguageTexts();
    }

    public void display(Runnable callback, ProcessorPreset processorPreset) {
        if (isShowing()) {
            toFront();
            requestFocus();
            return;
        }
        if (processorTypesTableModel.getRowCount() == 0) {
            for (String processorType : processorTypes) {
                processorTypesTableModel.addRow(new Object[]{processorType, localizeProcessorType(processorType)});
            }
            processorTypesTable.setRowSelectionInterval(0, 0);
            updateProcessorTableLayout();
        }
        this.callback = callback;
        if (processorPreset != null) {
            origProcessorPreset = processorPreset;
            titleField.setText(processorPreset.getTitle());
            selectProcessorType(processorPreset.getProcessorType());
        } else {
            origProcessorPreset = null;
            titleField.setText("");
            selectProcessorType("Simple");
        }
        updateProcessorTableLayout();
        pack();
        fitToOwnerScreen();
        setLocationRelativeTo(getOwner());
        validate();
        repaint();
        SwingUtilities.invokeLater(titleField::requestFocusInWindow);
        setVisible(true);
    }

    private void onOK() {
        if (processorTypesTable.getSelectedRow() < 0 && processorTypesTableModel.getRowCount() > 0) {
            processorTypesTable.setRowSelectionInterval(0, 0);
        }
        setVisible(false);
        Runnable completed = callback;
        callback = null;
        if (completed != null) completed.run();
    }

    private void onCancel() {
        callback = null;
        setVisible(false);
    }

    private void createUIComponents() {
        processorTypesTableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        processorTypesTableModel.addColumn("Processor");
        processorTypesTableModel.addColumn("Description");
        processorTypesTable = new JTable(processorTypesTableModel);
        processorTypesTable.setRowHeight(24);
        processorTypesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        processorTypesTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        processorTypesTable.setRowHeight(24);
        processorTypesTable.getTableHeader().setReorderingAllowed(false);
        processorTypesTable.getColumnModel().getColumn(0).setPreferredWidth(220);
        processorTypesTable.getColumnModel().getColumn(1).setPreferredWidth(260);
        DefaultTableCellRenderer ellipsisRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String text = value == null ? "" : value.toString();
                int availableWidth = table.getColumnModel().getColumn(column).getWidth() - 12;
                String display = ellipsizeEnd(text, table.getFontMetrics(table.getFont()), availableWidth);
                setText(display);
                setToolTipText(text);
                return this;
            }
        };
        processorTypesTable.getColumnModel().getColumn(0).setCellRenderer(ellipsisRenderer);
        processorTypesTable.getColumnModel().getColumn(1).setCellRenderer(ellipsisRenderer);
        processorTypesTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() > 1 && processorTypesTable.getSelectedRow() >= 0) {
                    onOK();
                }
            }
        });
    }

    private void initializeUi() {
        if (contentPane != null) {
            return;
        }
        createUIComponents();
        contentPane = new JPanel(new BorderLayout(8, 8));
        contentPane.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        JPanel titlePanel = new JPanel(new BorderLayout(4, 4));
        titleLabel = new JLabel("Title");
        titlePanel.add(titleLabel, BorderLayout.WEST);
        titleField = new JTextField(24);
        titlePanel.add(titleField, BorderLayout.CENTER);
        contentPane.add(titlePanel, BorderLayout.NORTH);
        processorListScrollPane = new JScrollPane(processorTypesTable);
        processorListScrollPane.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        contentPane.add(processorListScrollPane, BorderLayout.CENTER);
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttonOK = new JButton("OK");
        buttonCancel = new JButton("Cancel");
        buttonsPanel.add(buttonOK);
        buttonsPanel.add(buttonCancel);
        contentPane.add(buttonsPanel, BorderLayout.SOUTH);
        applyLanguageTexts();
    }

    private void applyLanguageTexts() {
        boolean zh = "zh_CN".equals(uiLanguage);
        setTitle(zh ? "添加处理器 / Add Processor" : "Add Processor");
        if (titleLabel != null) {
            titleLabel.setText(zh ? "标题 / Title" : "Title");
        }
        if (buttonOK != null) {
            buttonOK.setText(zh ? "确定 / OK" : "OK");
        }
        if (buttonCancel != null) {
            buttonCancel.setText(zh ? "取消 / Cancel" : "Cancel");
        }
        if (processorTypesTable != null) {
            processorTypesTableModel.setColumnIdentifiers(new Object[]{
                    zh ? "处理器 / Processor" : "Processor",
                    zh ? "说明 / Description" : "Description"
            });
            for (int i = 0; i < processorTypesTableModel.getRowCount(); i++) {
                String processorType = (String) processorTypesTableModel.getValueAt(i, 0);
                processorTypesTableModel.setValueAt(localizeProcessorType(processorType), i, 1);
            }
            processorTypesTable.getTableHeader().repaint();
            processorTypesTable.repaint();
        }
        if (processorListScrollPane != null) {
            updateProcessorTableLayout();
            pack();
        }
    }

    private void updateProcessorTableLayout() {
        if (processorListScrollPane == null || processorTypesTable == null) {
            return;
        }
        boolean zh = "zh_CN".equals(uiLanguage);
        int col0 = zh ? 230 : 230;
        int col1 = zh ? 350 : 260;
        int tableWidth = col0 + col1;
        processorTypesTable.getColumnModel().getColumn(0).setPreferredWidth(col0);
        processorTypesTable.getColumnModel().getColumn(1).setPreferredWidth(col1);

        int rows = Math.max(1, processorTypesTableModel.getRowCount());
        int visibleRows = Math.min(rows, zh ? 10 : 10);
        int tableHeight = visibleRows * processorTypesTable.getRowHeight() + processorTypesTable.getTableHeader().getPreferredSize().height + 6;
        int minHeight = zh ? 260 : 250;
        int maxHeight = zh ? 320 : 300;
        int viewportHeight = Math.max(minHeight, Math.min(tableHeight, maxHeight));

        processorTypesTable.setPreferredScrollableViewportSize(new Dimension(tableWidth, viewportHeight));
        processorListScrollPane.setPreferredSize(new Dimension(tableWidth + 24, viewportHeight + 4));
    }

    private void fitToOwnerScreen() {
        GraphicsConfiguration gc = getOwner() == null ? getGraphicsConfiguration() : getOwner().getGraphicsConfiguration();
        if (gc == null) return;
        Rectangle screen = gc.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        int width = Math.min(getWidth(), screen.width - insets.left - insets.right);
        int height = Math.min(getHeight(), screen.height - insets.top - insets.bottom);
        setSize(Math.max(1, width), Math.max(1, height));
    }

    private void selectProcessorType(String processorType) {
        if (processorType == null || processorTypesTableModel.getRowCount() == 0) {
            return;
        }
        for (int i = 0; i < processorTypesTableModel.getRowCount(); i++) {
            String value = (String) processorTypesTableModel.getValueAt(i, 0);
            if (processorType.equals(value)) {
                int viewRow = processorTypesTable.convertRowIndexToView(i);
                processorTypesTable.setRowSelectionInterval(viewRow, viewRow);
                processorTypesTable.scrollRectToVisible(processorTypesTable.getCellRect(viewRow, 0, true));
                return;
            }
        }
    }

    private String localizeProcessorType(String processorType) {
        if ("Simple".equals(processorType)) return "简单字段绘图";
        if ("Derivative".equals(processorType)) return "导数";
        if ("Abs".equals(processorType)) return "绝对值";
        if ("ATan2".equals(processorType)) return "反正切";
        if ("PosPIDControlSimulator".equals(processorType)) return "位置PID控制仿真";
        if ("PosRatePIDControlSimulator".equals(processorType)) return "位置速率PID控制仿真";
        if ("PositionEstimator".equals(processorType)) return "位置估计器";
        if ("GlobalPositionProjection".equals(processorType)) return "全球坐标投影";
        if ("LandDetector".equals(processorType)) return "着陆检测";
        if ("Expression".equals(processorType)) return "表达式";
        if ("NEDFromBodyProjection".equals(processorType)) return "机体系到NED投影";
        if ("Integral".equals(processorType)) return "积分";
        if ("Battery".equals(processorType)) return "电池";
        if ("PositionEstimatorKF".equals(processorType)) return "卡尔曼位置估计";
        if ("EulerFromQuaternion".equals(processorType)) return "四元数转欧拉角";
        if ("Text".equals(processorType)) return "文本";
        return processorType;
    }

    private String ellipsizeEnd(String text, FontMetrics metrics, int maxWidth) {
        if (text == null || maxWidth <= 0) {
            return text;
        }
        if (metrics.stringWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        if (metrics.stringWidth(ellipsis) >= maxWidth) {
            return ellipsis;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            sb.append(text.charAt(i));
            if (metrics.stringWidth(sb.toString() + ellipsis) > maxWidth) {
                sb.setLength(Math.max(0, sb.length() - 1));
                break;
            }
        }
        return sb.toString() + ellipsis;
    }
}
