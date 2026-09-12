package me.drton.flightplot;

import me.drton.jmavlib.log.DataFlashTextLogReader;
import me.drton.jmavlib.log.FormatErrorException;
import me.drton.jmavlib.log.LogReader;
import me.drton.jmavlib.log.px4.PX4LogMessage;
import me.drton.jmavlib.log.px4.PX4LogReader;
import me.drton.jmavlib.log.px4.PX4LogReaderOptimized;
import me.drton.jmavlib.log.ulog.MessageLog;
import me.drton.jmavlib.log.ulog.ULogReader;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One format-aware entry point shared by toolbar, menu, command line and drag/drop. */
final class LogService {
    static final String DATAFLASH_BIN = "DATAFLASH_BIN";
    static final String DATAFLASH_TEXT = "DATAFLASH_TEXT";
    static final String ULOG = "ULOG";

    static final class OpenedLog {
        final String fileName;
        final LogReader reader;
        final String type;
        final ArrayList<Object[]> messages;

        OpenedLog(String fileName, LogReader reader, String type, ArrayList<Object[]> messages) {
            this.fileName = fileName;
            this.reader = reader;
            this.type = type;
            this.messages = messages;
        }
    }

    boolean supports(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".bin") || lower.endsWith(".px4log") || lower.endsWith(".log") || lower.endsWith(".ulg");
    }

    OpenedLog open(String fileName, boolean chinese) throws Exception {
        checkCancelled();
        if (emptyOrZero(fileName)) throw new FormatErrorException(0, "Log file is empty or invalid");
        String lower = fileName.toLowerCase(Locale.ROOT);
        LogReader reader = lower.endsWith(".log") ? openText(fileName) :
                (lower.endsWith(".ulg") ? new ULogReader(fileName) : openBinary(fileName));
        if (!usable(reader)) { close(reader); throw new FormatErrorException(0, "Log contains no fields"); }
        ArrayList<Object[]> rows = messages(reader, chinese);
        String type = reader instanceof ULogReader ? ULOG :
                (reader instanceof DataFlashTextLogReader ? DATAFLASH_TEXT : DATAFLASH_BIN);
        return new OpenedLog(fileName, reader, type, rows);
    }

    private LogReader openBinary(String fileName) throws Exception {
        Exception primary = null;
        LogReader reader = null;
        try {
            reader = new PX4LogReaderOptimized(fileName);
            if (usable(reader)) return reader;
        } catch (Exception error) { primary = error; }
        close(reader); checkCancelled();
        try {
            reader = new ULogReader(fileName);
            if (usable(reader)) return reader;
        } catch (Exception ignored) { }
        close(reader); checkCancelled();
        if (primary != null) throw primary;
        throw new FormatErrorException(0, "Unsupported BIN log format");
    }

    private LogReader openText(String fileName) throws Exception {
        Exception primary = null;
        LogReader reader = null;
        try {
            reader = new DataFlashTextLogReader(fileName);
            if (usable(reader)) return reader;
        } catch (Exception error) { primary = error; }
        close(reader); checkCancelled();
        try { return openBinary(fileName); }
        catch (Exception error) { if (primary != null) throw primary; throw error; }
    }

    private ArrayList<Object[]> messages(LogReader reader, boolean chinese) {
        ArrayList<Object[]> rows = new ArrayList<Object[]>();
        if (reader instanceof ULogReader) {
            ULogReader ulog = (ULogReader) reader;
            for (MessageLog message : ulog.loggedMessages)
                rows.add(new Object[]{time(message.timestamp), message.getLevelStr(), message.message});
            String hardfault = ulog.getHardfaultPlainText();
            if (hardfault != null && !hardfault.trim().isEmpty()) rows.add(new Object[]{"", "CRITICAL", hardfault.trim()});
        } else if (reader instanceof PX4LogReader) {
            PX4LogReader px4 = (PX4LogReader) reader;
            int index = 0;
            for (PX4LogMessage message : px4.getLogMessages()) {
                String name = message.description.name;
                String text = "MSG".equals(name) ? String.valueOf(message.get("Message")) :
                        ("ERR".equals(name) ? "Subsys=" + message.get("Subsys") + ", ECode=" + message.get("ECode") : "Id=" + message.get("Id"));
                rows.add(new Object[]{time(px4.getLogMessageTime(index++)), "MSG".equals(name) ? "INFO" : name, text});
            }
        } else if (reader instanceof DataFlashTextLogReader) {
            for (DataFlashTextLogReader.LogMessageEntry message : ((DataFlashTextLogReader) reader).getLogMessages())
                rows.add(new Object[]{time(message.timeUS), message.level, message.message});
        }
        List<Exception> errors = reader.getErrors();
        int limit = Math.min(100, errors.size());
        for (int i = 0; i < limit; i++) rows.add(new Object[]{"", "ERROR", errors.get(i).getMessage()});
        if (errors.size() > limit) rows.add(new Object[]{"", "ERROR",
                (chinese ? "其余解析提示：" : "Additional parser notices: ") + (errors.size() - limit)});
        return rows;
    }

    private static String time(long microseconds) {
        if (microseconds <= 0) return "";
        long millis = microseconds / 1000;
        return String.format("%2d:%02d:%03d", millis / 60000, (millis / 1000) % 60, millis % 1000);
    }

    private static boolean usable(LogReader reader) {
        Map<String, String> fields = reader == null ? null : reader.getFields();
        return fields != null && !fields.isEmpty();
    }

    private static boolean emptyOrZero(String fileName) throws IOException {
        File file = new File(fileName);
        if (!file.isFile() || file.length() == 0) return true;
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] block = new byte[65536];
            for (int count; (count = input.read(block)) >= 0;) {
                checkCancelled();
                for (int i = 0; i < count; i++) if (block[i] != 0) return false;
            }
        }
        return true;
    }

    static void close(LogReader reader) {
        if (reader != null) try { reader.close(); } catch (IOException ignored) { }
    }

    private static void checkCancelled() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Log operation cancelled");
    }
}
