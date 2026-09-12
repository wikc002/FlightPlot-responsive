package me.drton.flightplot;

import me.drton.jmavlib.log.LogReader;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.LegendTitle;
import org.jfree.data.Range;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.RectangleEdge;

import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChartTab {
    String title;
    LogReader logReader = null;
    String currentLogType = null;
    String logFileName = null;
    XYSeriesCollection dataset;
    JFreeChart chart;
    ChartPanel chartPanel;
    NumberAxis domainAxisSeconds;
    DateAxis domainAxisDate;
    DefaultTableModel processorsListModel;
    DefaultTableModel parametersTableModel;
    DefaultTableModel logsTableModel;
    List<Map<String, Integer>> seriesIndex = new ArrayList<>();
    List<ProcessorPreset> activeProcessors = new ArrayList<>();
    List<ValueMarker> topMinuteMarkers = new ArrayList<>();
    Range lastTimeRange = null;
    int timeMode = 0;
    Range renderedRange;
    int renderedWidth;
    int renderedTimeMode;
    String renderedLanguage;
    String selectionRestoreNoticeZh = null;
    String selectionRestoreNoticeEn = null;

    public ChartTab(String title) {
        this.title = title;
        initChart();
        initModels();
    }

    private void initChart() {
        dataset = new XYSeriesCollection();
        chart = org.jfree.chart.ChartFactory.createXYLineChart("", "", "", null,
                org.jfree.chart.plot.PlotOrientation.VERTICAL, true, true, false);
        chart.getXYPlot().setDataset(dataset);

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(new Color(220, 220, 220));
        plot.setRangeGridlinePaint(new Color(220, 220, 220));
        plot.setDomainCrosshairVisible(false);
        plot.setRangeCrosshairVisible(false);
        plot.setDomainPannable(true);
        plot.setRangePannable(true);
        plot.setOutlineVisible(false);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        renderer.setDrawSeriesLineAsPath(true);
        renderer.setBaseShapesVisible(false);
        renderer.setSeriesStroke(0, GraphicsOptimizer.getOptimizedStroke(1.5f));
        renderer.setSeriesStroke(1, GraphicsOptimizer.getOptimizedStroke(1.5f));
        renderer.setSeriesStroke(2, GraphicsOptimizer.getOptimizedStroke(1.5f));
        plot.setRenderer(renderer);

        chart.setAntiAlias(true);
        chart.setBorderVisible(false);
        chart.setTextAntiAlias(GraphicsOptimizer.getTextAntialiasingHint());

        LegendTitle legend = chart.getLegend();
        if (legend != null) {
            legend.setPosition(RectangleEdge.BOTTOM);
            legend.setItemFont(GraphicsOptimizer.getOptimizedFont("Microsoft YaHei UI", Font.PLAIN, 12));
            legend.setItemPaint(new Color(45, 45, 45));
        }

        domainAxisSeconds = new NumberAxis("T") {
            protected void autoAdjustRange() { setRange(getDefaultAutoRange()); }
        };
        domainAxisSeconds.setLowerMargin(0.0);
        domainAxisSeconds.setUpperMargin(0.0);
        domainAxisSeconds.setAutoRangeStickyZero(false);
        domainAxisSeconds.setAutoRangeIncludesZero(false);
        domainAxisSeconds.setTickLabelFont(GraphicsOptimizer.getOptimizedFont("Microsoft YaHei UI", Font.PLAIN, 12));
        domainAxisSeconds.setLabelFont(GraphicsOptimizer.getOptimizedFont("Microsoft YaHei UI", Font.PLAIN, 12));

        domainAxisDate = new DateAxis("T") {
            protected void autoAdjustRange() { setRange(getDefaultAutoRange()); }
        };
        domainAxisDate.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
        domainAxisDate.setLowerMargin(0.0);
        domainAxisDate.setUpperMargin(0.0);
        domainAxisDate.setTickLabelFont(GraphicsOptimizer.getOptimizedFont("Microsoft YaHei UI", Font.PLAIN, 12));
        domainAxisDate.setLabelFont(GraphicsOptimizer.getOptimizedFont("Microsoft YaHei UI", Font.PLAIN, 12));

        plot.setDomainAxis(domainAxisSeconds);
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setAutoRangeIncludesZero(false);
        rangeAxis.setAutoRangeStickyZero(false);
        rangeAxis.setTickLabelFont(GraphicsOptimizer.getOptimizedFont("Microsoft YaHei UI", Font.PLAIN, 12));
        rangeAxis.setLabelFont(GraphicsOptimizer.getOptimizedFont("Microsoft YaHei UI", Font.PLAIN, 12));

        // JFreeChart 1.0.x allocates its chart buffer in Swing logical pixels.
        // On a HiDPI monitor that bitmap is enlarged by Java2D and becomes blurry,
        // so draw the chart directly into the device-scaled Graphics2D surface.
        chartPanel = new ChartPanel(chart, false) {
            @Override
            public void paintComponent(java.awt.Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                try {
                    GraphicsOptimizer.applyHighQualityRendering(g2d);
                    super.paintComponent(g2d);
                } finally {
                    g2d.dispose();
                }
            }
        };


        chartPanel.setDoubleBuffered(true);
        chartPanel.setMouseWheelEnabled(true);
        chartPanel.setMouseZoomable(true, false);
        chartPanel.setPopupMenu(null);
        chartPanel.setDisplayToolTips(false);
        chartPanel.setDomainZoomable(true);
        chartPanel.setRangeZoomable(true);
        chartPanel.setFillZoomRectangle(false);
        chartPanel.setDefaultDirectoryForSaveAs(null);
        chartPanel.setMinimumDrawWidth(0);
        chartPanel.setMinimumDrawHeight(0);
        chartPanel.setMaximumDrawWidth(Integer.MAX_VALUE);
        chartPanel.setMaximumDrawHeight(Integer.MAX_VALUE);
        chartPanel.setMinimumSize(new Dimension(160,120));
        chartPanel.setPreferredSize(new Dimension(800,600));
    }

    private void initModels() {
        processorsListModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int col) { return col == 0; }
            @Override
            public Class<?> getColumnClass(int col) { return col == 0 ? Boolean.class : String.class; }
        };
        processorsListModel.addColumn("");
        processorsListModel.addColumn("Processor");

        parametersTableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int col) { return col == 1; }
        };
        parametersTableModel.addColumn("Parameter");
        parametersTableModel.addColumn("Value");

        logsTableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        logsTableModel.addColumn("Time");
        logsTableModel.addColumn("Level");
        logsTableModel.addColumn("Message");
    }

    public void closeLogReader() {
        if (logReader != null) {
            final LogReader old = logReader;
            logReader = null;
            Thread closer = new Thread(() -> {
                synchronized (old) { try { old.close(); } catch (Exception ignored) {} }
            }, "FlightPlot-CloseLog");
            closer.setDaemon(true);
            closer.start();
        }
    }
}
