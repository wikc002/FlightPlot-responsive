package me.drton.flightplot;
import javax.swing.*;
import java.awt.*;

/** Sidebar follows viewport width; expanded sections remain reachable by scrolling. */
final class ScrollableSidebar extends JPanel implements Scrollable {
    ScrollableSidebar() { super(new BorderLayout()); }
    public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    public int getScrollableUnitIncrement(Rectangle r,int orientation,int direction) { return 20; }
    public int getScrollableBlockIncrement(Rectangle r,int orientation,int direction) { return Math.max(20,r.height-20); }
    public boolean getScrollableTracksViewportWidth() { return true; }
    public boolean getScrollableTracksViewportHeight() { return getParent()!=null && getPreferredSize().height<getParent().getHeight(); }
}
