package me.drton.flightplot;
import java.awt.*;

/** FlowLayout with a preferred height that includes all wrapped toolbar rows. */
public final class WrapLayout extends FlowLayout {
    public WrapLayout() { super(LEFT,6,4); }
    @Override public Dimension preferredLayoutSize(Container target) { return measure(target); }
    @Override public Dimension minimumLayoutSize(Container target) { return new Dimension(0,measure(target).height); }
    private Dimension measure(Container target) {
        synchronized(target.getTreeLock()) {
            int width=target.getWidth();
            if(width<=0 && target.getParent()!=null) width=target.getParent().getWidth();
            if(width<=0) width=1000;
            Insets in=target.getInsets();
            int available=Math.max(1,width-in.left-in.right-2*getHgap());
            int rowWidth=0,rowHeight=0,height=0,maxWidth=0;
            for(Component c:target.getComponents()) if(c.isVisible()) {
                Dimension d=c.getPreferredSize();
                int gap=rowWidth==0?0:getHgap();
                if(rowWidth>0 && rowWidth+gap+d.width>available) {
                    height+=rowHeight+getVgap(); maxWidth=Math.max(maxWidth,rowWidth); rowWidth=0; rowHeight=0; gap=0;
                }
                rowWidth+=gap+d.width; rowHeight=Math.max(rowHeight,d.height);
            }
            return new Dimension(Math.max(maxWidth,rowWidth)+in.left+in.right+2*getHgap(),height+rowHeight+in.top+in.bottom+2*getVgap());
        }
    }
}
