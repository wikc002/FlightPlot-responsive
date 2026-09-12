package me.drton.flightplot;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.prefs.Preferences;

public class PlotExportDialog extends JDialog {
    private static final String DIALOG_SETTING = "PlotExportDialog";
    private static final String LAST_EXPORT_DIRECTORY_SETTING = "LastExportDirectory";

    private JPanel contentPane;
    private JButton buttonExport;
    private JButton buttonClose;
    private JTextField widthField;
    private JTextField heightField;
    private JComboBox formatComboBox;
    private JTextField scaleField;
    private FlightPlot app;
    private File lastExportDirectory;
    private String uiLanguage = "en";
    private JLabel widthLabel;
    private JLabel heightLabel;
    private JLabel formatLabel;
    private JLabel scaleLabel;

    public PlotExportDialog(FlightPlot app) {
        this.app = app;
        initializeUi();
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonExport);

        buttonExport.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
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
        applyLanguageTexts();
        pack();
    }

    private void onOK() {
        String format = ((String) formatComboBox.getSelectedItem()).toLowerCase();
        JFileChooser fc = new JFileChooser();
        if (lastExportDirectory != null) {
            fc.setCurrentDirectory(lastExportDirectory);
        }
        FileNameExtensionFilter extensionFilter = new FileNameExtensionFilter(format.toUpperCase() + " Image (*." + format + ")", format);
        fc.setFileFilter(extensionFilter);
        fc.setDialogTitle(tr("export_plot"));
        int returnVal = fc.showDialog(null, tr("export"));
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            lastExportDirectory = fc.getCurrentDirectory();
            String fileName = fc.getSelectedFile().toString();
            if (extensionFilter == fc.getFileFilter() && !fileName.toLowerCase().endsWith("." + format)) {
                fileName += "." + format;
            }
            try {
                int width = Integer.parseInt(widthField.getText());
                int height = Integer.parseInt(heightField.getText());

                BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2 = img.createGraphics();
                double scale = Double.parseDouble(scaleField.getText());
                AffineTransform st = AffineTransform.getScaleInstance(scale, scale);
                g2.transform(st);
                app.getChart().draw(g2, new Rectangle2D.Double(0.0D, 0.0D, width / scale, height / scale), null, null);
                g2.dispose();

                ImageWriter imgWriter = ImageIO.getImageWritersByFormatName(format).next();
                ImageWriteParam imgWriteParam = imgWriter.getDefaultWriteParam();
                if ("jpg".equals(format)) {
                    imgWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    imgWriteParam.setCompressionQuality(1.0f);
                }
                ImageOutputStream outputStream = new FileImageOutputStream(new File(fileName));
                imgWriter.setOutput(outputStream);
                IIOImage outputImage = new IIOImage(img, null, null);
                imgWriter.write(null, outputImage, imgWriteParam);
                imgWriter.dispose();

                app.setStatus(String.format(tr("exported_to"), fileName));

            } catch (Exception e) {
                app.setStatus(tr("error_prefix") + e);
            }
        }
        dispose();
    }

    private void onClose() {
        dispose();
    }

    public void savePreferences(Preferences preferences) {
        PreferencesUtil.saveWindowPreferences(this, preferences.node(DIALOG_SETTING));
        if (lastExportDirectory != null) {
            preferences.put(LAST_EXPORT_DIRECTORY_SETTING, lastExportDirectory.getAbsolutePath());
        }
    }

    public void loadPreferences(Preferences preferences) {
        PreferencesUtil.loadWindowPreferences(this, preferences.node(DIALOG_SETTING), -1, -1);
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
        JPanel formPanel = new JPanel(new GridLayout(4, 2, 8, 8));
        widthLabel = new JLabel("Width");
        formPanel.add(widthLabel);
        widthField = new JTextField("1600");
        formPanel.add(widthField);
        heightLabel = new JLabel("Height");
        formPanel.add(heightLabel);
        heightField = new JTextField("900");
        formPanel.add(heightField);
        formatLabel = new JLabel("Format");
        formPanel.add(formatLabel);
        formatComboBox = new JComboBox(new String[]{"PNG", "JPG"});
        formPanel.add(formatComboBox);
        scaleLabel = new JLabel("Scale");
        formPanel.add(scaleLabel);
        scaleField = new JTextField("1.0");
        formPanel.add(scaleField);
        contentPane.add(formPanel, BorderLayout.CENTER);
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonExport = new JButton("Export");
        buttonClose = new JButton("Close");
        buttonsPanel.add(buttonExport);
        buttonsPanel.add(buttonClose);
        contentPane.add(buttonsPanel, BorderLayout.SOUTH);
    }

    private void applyLanguageTexts() {
        setTitle(tr("export_image_settings"));
        if (widthLabel != null) widthLabel.setText(tr("width"));
        if (heightLabel != null) heightLabel.setText(tr("height"));
        if (formatLabel != null) formatLabel.setText(tr("format"));
        if (scaleLabel != null) scaleLabel.setText(tr("scale"));
        if (buttonExport != null) buttonExport.setText(tr("export"));
        if (buttonClose != null) buttonClose.setText(tr("close"));
    }

    private String tr(String key) {
        boolean zh = "zh_CN".equals(uiLanguage);
        if ("export_image_settings".equals(key)) return zh ? "导出图片设置 / Export Image Settings" : "Export Image Settings";
        if ("width".equals(key)) return zh ? "宽度 / Width" : "Width";
        if ("height".equals(key)) return zh ? "高度 / Height" : "Height";
        if ("format".equals(key)) return zh ? "格式 / Format" : "Format";
        if ("scale".equals(key)) return zh ? "缩放 / Scale" : "Scale";
        if ("export".equals(key)) return zh ? "导出 / Export" : "Export";
        if ("close".equals(key)) return zh ? "关闭 / Close" : "Close";
        if ("export_plot".equals(key)) return zh ? "导出图表 / Export Plot" : "Export Plot";
        if ("exported_to".equals(key)) return zh ? "已导出到 \"%s\"" : "Exported to \"%s\"";
        if ("error_prefix".equals(key)) return zh ? "错误: " : "Error: ";
        return key;
    }
}
