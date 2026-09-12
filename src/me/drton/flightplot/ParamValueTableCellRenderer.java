package me.drton.flightplot;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

/**
* Created by ton on 13.03.15.
*/
class ParamValueTableCellRenderer extends JLabel implements TableCellRenderer {
    private DefaultTableCellRenderer defaultTableCellRenderer = new DefaultTableCellRenderer();

    public ParamValueTableCellRenderer() {
        setOpaque(true);
    }

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected
            , boolean hasFocus, int row, int column) {
        if (value instanceof Color) {
            Color color = (Color) value;
            setBackground(color);
            setText(String.format("#%06X  …", color.getRGB() & 0xffffff));
            setForeground(color.getRed()*299+color.getGreen()*587+color.getBlue()*114 < 128000 ? Color.WHITE : Color.BLACK);
            setToolTipText("双击更改颜色 / Double-click to change color");
        } else {
            return defaultTableCellRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        }
        return this;
    }
}
