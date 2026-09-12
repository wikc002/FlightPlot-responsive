package me.drton.flightplot;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FieldsPanel extends JPanel {
    private JTree fieldsTree;
    private JTextField textSearch;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode treeRoot;
    private String uiLanguage = "en";
    private JLabel searchLabel;
    private JButton buttonAdd;
    private Runnable callbackAdd;
    private JLabel titleLabel;
    private Map<String, String> allFields = new LinkedHashMap<>();
    private Map<String, String> fieldTypes = new HashMap<>();
    private Map<String, TreeMap<String, List<String>>> cachedModuleMap = null;
    private List<String> cachedUnmatchedFields = null;
    private String lastFilter = null;
    private javax.swing.Timer filterTimer = null;

    public FieldsPanel(Runnable callbackAdd) {
        this.callbackAdd = callbackAdd;
        initializeUi();
    }

    private void initializeUi() {
        setLayout(new BorderLayout(8, 8));
        setBorder(new CompoundBorder(
                new MatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                new EmptyBorder(16, 0, 0, 0)
        ));

        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        titleLabel = new JLabel("Fields List");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        topPanel.add(titleLabel, BorderLayout.NORTH);

        JPanel searchPanel = new JPanel(new BorderLayout(5, 5));
        searchLabel = new JLabel("Search");
        searchPanel.add(searchLabel, BorderLayout.WEST);
        textSearch = new JTextField(24);
        searchPanel.add(textSearch, BorderLayout.CENTER);

        buttonAdd = new JButton("Add");
        buttonAdd.addActionListener(e -> {
            if (callbackAdd != null) callbackAdd.run();
        });
        searchPanel.add(buttonAdd, BorderLayout.EAST);

        topPanel.add(searchPanel, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        treeRoot = new DefaultMutableTreeNode("Root");
        treeModel = new DefaultTreeModel(treeRoot);
        fieldsTree = new JTree(treeModel) {
            @Override
            public String getToolTipText(MouseEvent e) {
                if (getRowForLocation(e.getX(), e.getY()) == -1) return null;
                TreePath path = getPathForLocation(e.getX(), e.getY());
                if (path == null) return null;
                Object node = path.getLastPathComponent();
                if (node instanceof DefaultMutableTreeNode) {
                    Object uo = ((DefaultMutableTreeNode) node).getUserObject();
                    return uo != null ? uo.toString() : null;
                }
                return super.getToolTipText(e);
            }
        };
        fieldsTree.setRowHeight(22);
        fieldsTree.setRootVisible(false);
        fieldsTree.setShowsRootHandles(true);
        fieldsTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        fieldsTree.setCellRenderer(new FieldTreeCellRenderer());
        fieldsTree.setDragEnabled(false);
        fieldsTree.setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) { return COPY; }

            @Override
            protected Transferable createTransferable(JComponent c) {
                List<String> selected = getSelectedFields();
                if (selected.isEmpty()) return null;
                return new StringSelection(String.join("\n", selected));
            }
        });

        JScrollPane scrollPane = new JScrollPane(fieldsTree);
        scrollPane.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        scrollPane.setPreferredSize(new Dimension(0, 200));
        add(scrollPane, BorderLayout.CENTER);

        MouseAdapter mouseAdapter = new MouseAdapter() {
            private Point firstPoint;

            private boolean isToggleArea(MouseEvent me) {
                int row = fieldsTree.getRowForLocation(me.getX(), me.getY());
                if (row < 0) return false;
                Rectangle bounds = fieldsTree.getRowBounds(row);
                if (bounds == null) return false;
                int indent = UIManager.getInt("Tree.leftChildIndent");
                if (indent <= 0) indent = 18;
                return me.getX() < bounds.x + indent;
            }

            @Override
            public void mousePressed(MouseEvent me) {
                firstPoint = me.getPoint();
                if (me.getClickCount() == 2 && callbackAdd != null) {
                    if (isToggleArea(me)) return;
                    callbackAdd.run();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (firstPoint == null) return;
                int dx = Math.abs(e.getX() - firstPoint.x);
                int dy = Math.abs(e.getY() - firstPoint.y);
                if (dx > 5 || dy > 5) {
                    int row = fieldsTree.getRowForLocation(firstPoint.x, firstPoint.y);
                    if (row >= 0) {
                        TreePath path = fieldsTree.getPathForRow(row);
                        if (path != null && !fieldsTree.isPathSelected(path)) {
                            fieldsTree.setSelectionPath(path);
                        }
                        TransferHandler th = fieldsTree.getTransferHandler();
                        th.exportAsDrag(fieldsTree, e, TransferHandler.COPY);
                        firstPoint = null;
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) { firstPoint = null; }
        };
        fieldsTree.addMouseListener(mouseAdapter);
        fieldsTree.addMouseMotionListener(mouseAdapter);

        textSearch.getDocument().addDocumentListener(new DocumentListener() {
            public void changedUpdate(DocumentEvent e) { scheduleFilter(); }
            public void removeUpdate(DocumentEvent e) { scheduleFilter(); }
            public void insertUpdate(DocumentEvent e) { scheduleFilter(); }
        });
    }

    private void scheduleFilter() {
        if (filterTimer != null && filterTimer.isRunning()) {
            filterTimer.stop();
        }
        filterTimer = new javax.swing.Timer(150, e -> {
            filterFields(textSearch.getText());
            filterTimer = null;
        });
        filterTimer.setRepeats(false);
        filterTimer.start();
    }

    public void setFieldsList(Map<String, String> fields) {
        if (allFields.equals(fields)) return;
        this.allFields.clear();
        this.fieldTypes.clear();
        this.allFields.putAll(fields);
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            fieldTypes.put(entry.getKey(), entry.getValue());
        }
        cachedModuleMap = null;
        cachedUnmatchedFields = null;
        lastFilter = null;
        // Force replacement of the old log's tree.  Without clearing it the
        // no-filter fast path can retain the previous file's visible fields.
        treeRoot.removeAllChildren();
        rebuildTree(textSearch.getText());
    }

    private static final Pattern FIELD_PATTERN = Pattern.compile("^([A-Za-z_][A-Za-z0-9_-]*)(?:\\[(\\d+)\\])?\\.(.+)$");

    private void buildFieldCache() {
        if (cachedModuleMap != null) return;

        cachedModuleMap = new TreeMap<>(NaturalFieldOrder.INSTANCE);
        cachedUnmatchedFields = new ArrayList<>();

        List<String> sortedFields = new ArrayList<>(allFields.keySet());
        sortedFields.sort(NaturalFieldOrder.INSTANCE);
        for (String field : sortedFields) {
            Matcher m = FIELD_PATTERN.matcher(field);
            if (m.matches()) {
                String mod = m.group(1);
                String inst = m.group(2);
                String instKey = (inst != null) ? "[" + inst + "]" : "";
                TreeMap<String, List<String>> instanceMap = cachedModuleMap.computeIfAbsent(mod, k -> new TreeMap<>(NaturalFieldOrder.INSTANCE));
                List<String> list = instanceMap.computeIfAbsent(instKey, k -> new ArrayList<>());
                list.add(field);
            } else {
                cachedUnmatchedFields.add(field);
            }
        }
    }

    private void rebuildTree(String filterStr) {
        buildFieldCache();

        boolean hasFilter = filterStr != null && filterStr.length() > 0;
        String filterLower = hasFilter ? filterStr.toLowerCase(Locale.ROOT) : "";

        if (!hasFilter && lastFilter == null && treeRoot.getChildCount() > 0) {
            return;
        }
        lastFilter = hasFilter ? filterLower : null;

        treeRoot.removeAllChildren();

        for (Map.Entry<String, TreeMap<String, List<String>>> modEntry : cachedModuleMap.entrySet()) {
            String moduleName = modEntry.getKey();
            boolean modMatches = !hasFilter || moduleName.toLowerCase(Locale.ROOT).contains(filterLower);

            if (modEntry.getValue().size() == 1 && modEntry.getValue().containsKey("")) {
                List<String> fieldList = modEntry.getValue().get("");
                DefaultMutableTreeNode moduleNode = new DefaultMutableTreeNode(new TreeNodeData(moduleName, "", true));
                for (String fullField : fieldList) {
                    if (addLeafIfMatch(moduleNode, fullField, hasFilter, filterLower, modMatches)) {}
                }
                if (moduleNode.getChildCount() > 0) treeRoot.add(moduleNode);
            } else {
                DefaultMutableTreeNode moduleNode = new DefaultMutableTreeNode(new TreeNodeData(moduleName, "", true));
                for (Map.Entry<String, List<String>> instEntry : modEntry.getValue().entrySet()) {
                    String instLabel = instEntry.getKey();
                    List<String> fieldList = instEntry.getValue();

                    if (!instLabel.isEmpty() && fieldList.size() > 0) {
                        String instFullName = moduleName + instLabel;
                        boolean instMatches = modMatches ||
                            instFullName.toLowerCase(Locale.ROOT).contains(filterLower);
                        DefaultMutableTreeNode instNode = new DefaultMutableTreeNode(
                            new TreeNodeData(instFullName, "", true));
                        for (String fullField : fieldList) {
                            if (addLeafIfMatch(instNode, fullField, hasFilter, filterLower, instMatches)) {}
                        }
                        if (instNode.getChildCount() > 0) moduleNode.add(instNode);
                    } else {
                        for (String fullField : fieldList) {
                            if (addLeafIfMatch(moduleNode, fullField, hasFilter, filterLower, modMatches)) {}
                        }
                    }
                }
                if (moduleNode.getChildCount() > 0) treeRoot.add(moduleNode);
            }
        }

        if (!cachedUnmatchedFields.isEmpty()) {
            DefaultMutableTreeNode otherNode = new DefaultMutableTreeNode(new TreeNodeData("Other", "", true));
            for (String fullField : cachedUnmatchedFields) {
                String display = toDisplayField(fullField);
                if (!hasFilter ||
                    display.toLowerCase(Locale.ROOT).contains(filterLower) ||
                    fullField.toLowerCase(Locale.ROOT).contains(filterLower) ||
                    "other".contains(filterLower)) {
                    String type = fieldTypes.getOrDefault(fullField, "");
                    otherNode.add(new DefaultMutableTreeNode(
                        new TreeNodeData(display, type, false, fullField)));
                }
            }
            if (otherNode.getChildCount() > 0) treeRoot.add(otherNode);
        }

        treeModel.reload();

        if (!hasFilter) {
            int rowsToExpand = Math.min(20, fieldsTree.getRowCount());
            for (int i = 0; i < rowsToExpand; i++) {
                fieldsTree.expandRow(i);
            }
        }
    }

    private boolean addLeafIfMatch(DefaultMutableTreeNode parent, String fullField,
                                     boolean hasFilter, String filterLower, boolean parentMatches) {
        if (hasFilter && !parentMatches) {
            String display = toDisplayField(fullField);
            if (!display.toLowerCase(Locale.ROOT).contains(filterLower) &&
                !fullField.toLowerCase(Locale.ROOT).contains(filterLower)) {
                return false;
            }
        }
        String display = toDisplayField(fullField);
        String type = fieldTypes.getOrDefault(fullField, "");
        parent.add(new DefaultMutableTreeNode(new TreeNodeData(display, type, false, fullField)));
        return true;
    }

    private void filterFields(String str) {
        rebuildTree(str.trim());
    }

    public List<String> getSelectedFields() {
        List<String> selectedFields = new ArrayList<String>();
        TreeSelectionModel tsm = fieldsTree.getSelectionModel();
        TreePath[] paths = tsm.getSelectionPaths();
        if (paths == null) return selectedFields;

        for (TreePath path : paths) {
            collectLeafFields((DefaultMutableTreeNode) path.getLastPathComponent(), selectedFields);
        }
        return selectedFields;
    }

    private void collectLeafFields(DefaultMutableTreeNode node, List<String> result) {
        if (node == null) return;
        Object uo = node.getUserObject();
        if (uo instanceof TreeNodeData) {
            TreeNodeData data = (TreeNodeData) uo;
            if (!data.isBranch && data.rawField != null) {
                result.add(data.rawField);
                return;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectLeafFields((DefaultMutableTreeNode) node.getChildAt(i), result);
        }
    }

    public void scrollToField(String fieldName) {
        searchNode(treeRoot, fieldName);
    }

    private boolean searchNode(DefaultMutableTreeNode node, String fieldName) {
        if (node == null) return false;
        Object uo = node.getUserObject();
        if (uo instanceof TreeNodeData) {
            TreeNodeData data = (TreeNodeData) uo;
            if (!data.isBranch && fieldName.equals(data.rawField)) {
                TreePath path = new TreePath(treeModel.getPathToRoot(node));
                fieldsTree.setSelectionPath(path);
                fieldsTree.scrollPathToVisible(path);
                return true;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (searchNode((DefaultMutableTreeNode) node.getChildAt(i), fieldName)) {
                return true;
            }
        }
        return false;
    }

    public void setUiLanguage(String uiLanguage) {
        if (Objects.equals(this.uiLanguage, uiLanguage)) return;
        this.uiLanguage = uiLanguage;
        boolean zh = "zh_CN".equals(uiLanguage);
        titleLabel.setText(zh ? "字段列表" : "Fields List");
        searchLabel.setText(zh ? "搜索" : "Search");
        buttonAdd.setText(zh ? "添加" : "Add");
        if (!allFields.isEmpty()) {
            cachedModuleMap = null;
            cachedUnmatchedFields = null;
            treeRoot.removeAllChildren();
            rebuildTree(textSearch.getText().trim());
        }
    }

    private String toDisplayField(String field) {
        if (!"zh_CN".equals(uiLanguage)) return field;
        return field + "  " + FieldNameLocalizer.toZhCn(field);
    }

    private static class TreeNodeData {
        final String displayName;
        final String fieldType;
        final boolean isBranch;
        final String rawField;

        TreeNodeData(String displayName, String fieldType, boolean isBranch) {
            this(displayName, fieldType, isBranch, null);
        }

        TreeNodeData(String displayName, String fieldType, boolean isBranch, String rawField) {
            this.displayName = displayName;
            this.fieldType = fieldType;
            this.isBranch = isBranch;
            this.rawField = rawField;
        }

        @Override
        public String toString() { return displayName; }
    }

    private static class FieldTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Component c = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            if (value instanceof DefaultMutableTreeNode) {
                Object uo = ((DefaultMutableTreeNode) value).getUserObject();
                if (uo instanceof TreeNodeData) {
                    TreeNodeData data = (TreeNodeData) uo;
                    setText(data.displayName);
                    if (!data.isBranch && data.fieldType != null && !data.fieldType.isEmpty()) {
                        setToolTipText(data.displayName + "  [" + data.fieldType + "]");
                    } else {
                        setToolTipText(data.displayName);
                    }
                }
            }
            return c;
        }
    }
}
