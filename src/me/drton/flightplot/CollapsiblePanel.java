package me.drton.flightplot;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class CollapsiblePanel extends JPanel {
    private boolean expanded = false;
    private final int collapsedHeight = 36;
    private int expandedHeight = 250;
    private final JPanel contentPanel;
    private final JLabel titleLabel;

    public CollapsiblePanel(String title, JComponent content) {
        setLayout(new BorderLayout());

        // Title Bar
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setPreferredSize(new Dimension(0, collapsedHeight));
        titleBar.setBackground(UIManager.getColor("Panel.background"));
        titleBar.setBorder(new MatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));
        titleBar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        titleLabel = new JLabel(title);
        titleLabel.setIcon(new DisclosureIcon());
        titleLabel.setIconTextGap(8);
        titleLabel.setBorder(new EmptyBorder(0, 10, 0, 0));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        titleBar.add(titleLabel, BorderLayout.WEST);

        // Content Panel Wrapper
        contentPanel = new JPanel(new BorderLayout());
        contentPanel.add(content, BorderLayout.CENTER);
        contentPanel.setVisible(false); // Default collapsed

        add(titleBar, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);

        titleBar.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggle();
            }
        });

        // Initial state setup
        setPreferredSize(new Dimension(getPreferredSize().width, collapsedHeight));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, collapsedHeight));
        setMinimumSize(new Dimension(0, collapsedHeight));
    }

    public void setExpandedHeight(int height) {
        this.expandedHeight = Math.max(collapsedHeight, height);
        if (expanded) updateHeight();
    }

    public void setTitle(String title) {
        titleLabel.setText(title);
    }

    public void toggle() {
        expanded = !expanded;
        titleLabel.repaint();
        contentPanel.setVisible(expanded);
        updateHeight();
    }

    private class DisclosureIcon implements Icon {
        public int getIconWidth() { return 16; }
        public int getIconHeight() { return 16; }
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D pen = (Graphics2D) g.create();
            try {
                pen.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                pen.setColor(c.getForeground());
                pen.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                if (expanded) {
                    pen.drawLine(x+3, y+5, x+8, y+10); pen.drawLine(x+8, y+10, x+13, y+5);
                } else {
                    pen.drawLine(x+5, y+3, x+10, y+8); pen.drawLine(x+10, y+8, x+5, y+13);
                }
            } finally { pen.dispose(); }
        }
    }

    private void updateHeight() {
        // Apply once: repeated clicks must not be swallowed by a resize animation.
        int height = expanded ? expandedHeight : collapsedHeight;
        setPreferredSize(new Dimension(0, height));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        setMinimumSize(new Dimension(0, height));
        revalidate();
        repaint();
        if (getParent() != null) getParent().revalidate();
    }
}
