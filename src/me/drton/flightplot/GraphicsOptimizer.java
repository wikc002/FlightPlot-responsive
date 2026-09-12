package me.drton.flightplot;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public final class GraphicsOptimizer {
    private static final Map<RenderingHints.Key, Object> RENDERING_HINTS = new HashMap<>();

    static {
        RENDERING_HINTS.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        RENDERING_HINTS.put(RenderingHints.KEY_TEXT_ANTIALIASING, getTextAntialiasingHint());
        RENDERING_HINTS.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        // Pixel-normalized strokes keep 1px axes and glyph stems crisp on fractional DPI scales.
        RENDERING_HINTS.put(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
        RENDERING_HINTS.put(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        RENDERING_HINTS.put(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        RENDERING_HINTS.put(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        RENDERING_HINTS.put(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
    }

    private GraphicsOptimizer() {}

    public static void applyHighQualityRendering(Graphics2D g2d) {
        for (Map.Entry<RenderingHints.Key, Object> hint : RENDERING_HINTS.entrySet()) {
            g2d.setRenderingHint(hint.getKey(), hint.getValue());
        }
        Object desktopHints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
        if (desktopHints instanceof Map) {
            g2d.addRenderingHints((Map<?, ?>) desktopHints);
        }
    }

    public static Object getTextAntialiasingHint() {
        Object desktopHints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
        if (desktopHints instanceof Map) {
            Object hint = ((Map<?, ?>) desktopHints).get(RenderingHints.KEY_TEXT_ANTIALIASING);
            if (hint != null) return hint;
        }
        return RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB;
    }

    public static Font getOptimizedFont(String fontName, int style, int size) {
        return new Font(fontName, style, size);
    }

    public static Stroke getOptimizedStroke(float width) {
        return new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }

}
