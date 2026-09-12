package me.drton.jmavlib.log;

import java.io.EOFException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.util.*;

public class DataFlashTextLogReader implements LogReader {
    private static final String FMT = "FMT";
    private static final Set<String> HIDDEN_MSGS = new HashSet<String>(Arrays.asList("FMT", "FMTU", "UNIT", "MULT", "TIME", "VER"));
    private static final Set<String> TIME_FIELD_NAMES = new HashSet<String>(Arrays.asList("TimeUS", "TimeMS"));

    private final RandomAccessFile file;
    private final byte[] inputBuffer = new byte[256 * 1024];
    private int inputPosition = 0;
    private int inputLimit = 0;
    private final Map<String, MessageFormat> formatsByName = new HashMap<String, MessageFormat>();
    private final Map<Integer, MessageFormat> formatsByType = new HashMap<Integer, MessageFormat>();
    private final Map<String, String> fields = new HashMap<String, String>();
    private final List<SeekPoint> seekPoints = new ArrayList<SeekPoint>();
    private final Map<String, Object> parameters = new HashMap<String, Object>();
    private final List<Exception> errors = new ArrayList<Exception>();
    private final List<LogMessageEntry> logMessages = new ArrayList<LogMessageEntry>();

    private long dataStart = 0;
    private long time = 0;
    private long sizeUpdates = 0;
    private long startMicroseconds = 0;
    private long sizeMicroseconds = 0;
    private long utcTimeReference = -1;
    private long lastReadTime = 0;
    private static final int MAX_LOG_MESSAGES = 50000;

    public DataFlashTextLogReader(String fileName) throws IOException, FormatErrorException {
        file = new RandomAccessFile(fileName, "r");
        try {
            scan();
            if (fields.isEmpty()) throw new FormatErrorException(0, "No valid fields found in text log");
            seek(0);
        } catch(IOException | FormatErrorException | RuntimeException failure) {
            try { file.close(); } catch(IOException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }

    @Override
    public void close() throws IOException {
        file.close();
    }

    @Override
    public boolean seek(long seekTime) throws IOException {
        if (seekTime <= 0) {
            seekFile(dataStart);
            time = startMicroseconds;
            lastReadTime = startMicroseconds;
            return true;
        }

        long startPosition = dataStart;
        for (SeekPoint point : seekPoints) {
            if (point.time <= seekTime) {
                startPosition = point.position;
            } else {
                break;
            }
        }
        seekFile(startPosition);

        while (true) {
            checkInterrupted();
            long linePos = logicalPosition();
            String line = readAsciiLine();
            if (line == null) {
                return false;
            }
            ParsedLine parsedLine = parseDataLine(line);
            if (parsedLine == null) {
                continue;
            }
            long t = parsedLine.time > 0 ? parsedLine.time : time;
            if (t >= seekTime) {
                seekFile(linePos);
                time = t;
                lastReadTime = t;
                return true;
            }
            time = t;
        }
    }

    @Override
    public long readUpdate(Map<String, Object> update) throws IOException, FormatErrorException {
        while (true) {
            checkInterrupted();
            String line = readAsciiLine();
            if (line == null) {
                throw new EOFException();
            }
            ParsedLine parsedLine = parseDataLine(line);
            if (parsedLine == null) {
                continue;
            }
            for (Map.Entry<String, Object> entry : parsedLine.update.entrySet()) {
                update.put(entry.getKey(), entry.getValue());
            }
            long t = parsedLine.time > 0 ? parsedLine.time : lastReadTime;
            time = t;
            lastReadTime = t;
            return t;
        }
    }

    @Override
    public Map<String, String> getFields() {
        return fields;
    }

    @Override
    public String getFormat() {
        return "APM-Text";
    }

    @Override
    public String getSystemName() {
        return "APM";
    }

    @Override
    public long getSizeUpdates() {
        return sizeUpdates;
    }

    @Override
    public long getStartMicroseconds() {
        return startMicroseconds;
    }

    @Override
    public long getSizeMicroseconds() {
        return sizeMicroseconds;
    }

    @Override
    public long getUTCTimeReferenceMicroseconds() {
        return utcTimeReference;
    }

    @Override
    public Map<String, Object> getVersion() {
        return Collections.emptyMap();
    }

    @Override
    public Map<String, Object> getParameters() {
        return parameters;
    }

    @Override
    public List<Exception> getErrors() {
        return errors;
    }

    @Override
    public void clearErrors() {
        errors.clear();
    }

    public List<LogMessageEntry> getLogMessages() {
        return logMessages;
    }

    private void scan() throws IOException {
        seekFile(0);
        long updates = 0;
        long timeStart = -1;
        long timeEnd = -1;
        long firstDataPosition = -1;
        while (true) {
            checkInterrupted();
            long linePos = logicalPosition();
            String line = readAsciiLine();
            if (line == null) {
                break;
            }
            String[] parts = splitCsv(line);
            if (parts.length == 0) {
                continue;
            }
            if (FMT.equals(parts[0])) {
                parseFormat(parts);
                continue;
            }
            if (firstDataPosition < 0) {
                firstDataPosition = linePos;
            }

            MessageFormat format = formatsByName.get(parts[0]);
            if (format == null) {
                continue;
            }
            if ("FMTU".equals(format.name)) {
                applyFormatUnits(parts, format);
                continue;
            }
            int instance = extractInstance(parts, format);
            if(format.observedInstances.add(instance)) catalogFields(format, instance);
            long t = extractTime(parts, format);
            collectLogMessage(parts, format, t);
            if ("PARM".equals(format.name)) {
                readParameter(parts, format);
            }
            if (t > 0) {
                if (timeStart < 0) {
                    timeStart = t;
                }
                timeEnd = t;
                updates++;
                if (updates % 5000 == 0) {
                    seekPoints.add(new SeekPoint(linePos, t));
                }
            }
        }
        // Keep schema-only messages visible even if the log contains no sample.
        for (MessageFormat format : formatsByName.values()) {
            if (!HIDDEN_MSGS.contains(format.name) && format.observedInstances.isEmpty()) {
                catalogFields(format, -1);
            }
        }
        dataStart = firstDataPosition >= 0 ? firstDataPosition : 0;
        sizeUpdates = updates;
        startMicroseconds = timeStart > 0 ? timeStart : 0;
        sizeMicroseconds = (timeStart > 0 && timeEnd >= timeStart) ? (timeEnd - timeStart) : 0;
    }

    private void parseFormat(String[] parts) {
        if (parts.length < 6) {
            return;
        }
        int type;
        try { type = Integer.parseInt(parts[1]); }
        catch (NumberFormatException ignored) { return; }
        String name = parts[3];
        String formatString = parts[4];
        String[] columns = Arrays.copyOfRange(parts, 5, parts.length);
        MessageFormat messageFormat = new MessageFormat(type, name, formatString, columns);
        formatsByName.put(name, messageFormat);
        formatsByType.put(type, messageFormat);
    }

    private void applyFormatUnits(String[] parts, MessageFormat fmtu) {
        Integer typeIndex=fmtu.fieldsMap.get("FmtType"), unitsIndex=fmtu.fieldsMap.get("UnitIds");
        if(typeIndex==null || unitsIndex==null || typeIndex+1>=parts.length || unitsIndex+1>=parts.length) return;
        try {
            MessageFormat target=formatsByType.get(Integer.parseInt(parts[typeIndex+1]));
            if(target==null) return;
            int marker=parts[unitsIndex+1].indexOf('#');
            int instanceIndex=marker>=0 && marker<target.columns.length &&
                    "bBhHiI".indexOf(target.getFormatAt(marker))>=0 ? marker : -1;
            if(target.instanceIndex!=instanceIndex) {
                target.instanceIndex=instanceIndex;
                // FMTU normally precedes data, but rebuild safely if metadata arrives late.
                removeCatalog(target.name+".");
                for(Integer observed : target.observedInstances) catalogFields(target,observed);
            }
        } catch(NumberFormatException ignored) {}
    }

    private void removeCatalog(String prefix) {
        Iterator<String> iterator=fields.keySet().iterator();
        while(iterator.hasNext()) if(iterator.next().startsWith(prefix)) iterator.remove();
    }

    private int extractInstance(String[] parts, MessageFormat format) {
        if(format.instanceIndex<0 || format.instanceIndex+1>=parts.length) return -1;
        try { return Integer.parseInt(parts[format.instanceIndex+1]); }
        catch(NumberFormatException ignored) { return -1; }
    }

    private void catalogFields(MessageFormat format,int instance) {
        if(HIDDEN_MSGS.contains(format.name)) return;
        String prefix=format.name+(format.instanceIndex>=0 && instance>=0 ? "["+instance+"]" : "")+".";
        int n=Math.min(format.format.length(),format.columns.length);
        for(int i=0;i<n;i++) {
            char type=format.getFormatAt(i);
            if(type=='a') for(int j=0;j<32;j++) fields.put(prefix+format.columns[i]+"["+j+"]","int16");
            else fields.put(prefix+format.columns[i],formatName(type));
        }
    }

    private void readParameter(String[] parts, MessageFormat format) {
        Integer nameIdx = format.fieldsMap.get("Name");
        Integer valueIdx = format.fieldsMap.get("Value");
        if (nameIdx == null || valueIdx == null) {
            return;
        }
        if (nameIdx + 1 >= parts.length || valueIdx + 1 >= parts.length) {
            return;
        }
        String key = parts[nameIdx + 1];
        Object value = parseValue(format.getFormatAt(valueIdx), parts[valueIdx + 1]);
        parameters.put(key, value);
    }

    private ParsedLine parseDataLine(String line) {
        String[] parts = splitCsv(line);
        if (parts.length == 0) {
            return null;
        }
        if (FMT.equals(parts[0])) {
            parseFormat(parts);
            return null;
        }
        MessageFormat format = formatsByName.get(parts[0]);
        if (format == null) {
            return null;
        }
        if ("FMTU".equals(format.name)) {
            applyFormatUnits(parts, format);
            return null;
        }
        if (HIDDEN_MSGS.contains(format.name)) return null;

        long t = extractTime(parts, format);
        int instance=extractInstance(parts,format);
        String prefix=format.name+(format.instanceIndex>=0 && instance>=0 ? "["+instance+"]" : "")+".";
        LinkedHashMap<String, Object> update = new LinkedHashMap<String, Object>();
        int n = Math.min(format.columns.length, format.format.length());
        for (int i = 0; i < n; i++) {
            int valueIdx = i + 1;
            if (valueIdx >= parts.length) {
                break;
            }
            String fieldName = format.columns[i];
            char type=format.getFormatAt(i);
            if(type=='a') {
                short[] values=parseShortArray(parts[valueIdx]);
                if(values!=null) for(int j=0;j<values.length;j++) update.put(prefix+fieldName+"["+j+"]",(int)values[j]);
            } else update.put(prefix + fieldName, parseValue(type, parts[valueIdx]));
        }
        return new ParsedLine(t, update);
    }

    private short[] parseShortArray(String raw) {
        String cleaned=raw.trim().replaceAll("^[\\[\\{(]|[\\]\\})]$","").trim();
        String[] values=cleaned.split("[\\s;:]+");
        if(values.length!=32) return null;
        short[] result=new short[32];
        try { for(int i=0;i<32;i++) result[i]=Short.parseShort(values[i]); }
        catch(NumberFormatException ignored) { return null; }
        return result;
    }

    private long extractTime(String[] parts, MessageFormat format) {
        Integer idx = format.fieldsMap.get("TimeUS");
        boolean micros = true;
        if (idx == null) {
            idx = format.fieldsMap.get("TimeMS");
            micros = false;
        }
        if (idx == null) {
            return -1;
        }
        int valueIdx = idx + 1;
        if (valueIdx >= parts.length) {
            return -1;
        }
        try {
            long t = Long.parseLong(parts[valueIdx]);
            return micros ? t : t * 1000L;
        } catch (Exception ignored) {
            return -1;
        }
    }

    private Object parseValue(char format, String raw) {
        String value = raw.trim();
        if (value.isEmpty()) {
            return "";
        }
        try {
            switch (format) {
                case 'b':
                case 'h':
                case 'i':
                    return Integer.parseInt(value);
                case 'B':
                case 'H':
                case 'I':
                case 'M':
                case 'q':
                case 'Q':
                    return Long.parseLong(value);
                case 'f':
                case 'g':
                case 'd':
                case 'c':
                case 'C':
                case 'e':
                case 'E':
                case 'L':
                    return Double.parseDouble(value);
                default:
                    return value;
            }
        } catch (Exception ignored) {
            return value;
        }
    }

    private String[] splitCsv(String line) {
        String[] parts = line.split(",", -1);
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        return parts;
    }

    private void seekFile(long position) throws IOException {
        file.seek(position);
        inputPosition=0;
        inputLimit=0;
    }

    private void checkInterrupted() throws InterruptedIOException {
        if(Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Log operation cancelled");
    }

    private long logicalPosition() throws IOException {
        return file.getFilePointer()-(inputLimit-inputPosition);
    }

    /** Buffered ISO-8859-1 line reader; RandomAccessFile.readLine performs a native read per byte. */
    private String readAsciiLine() throws IOException {
        ByteArrayOutputStream overflow=null;
        while(true) {
            if(inputPosition>=inputLimit) {
                checkInterrupted();
                inputLimit=file.read(inputBuffer);
                inputPosition=0;
                if(inputLimit<0) {
                    if(overflow==null || overflow.size()==0) return null;
                    return new String(overflow.toByteArray(),java.nio.charset.StandardCharsets.ISO_8859_1);
                }
            }
            int start=inputPosition;
            while(inputPosition<inputLimit && inputBuffer[inputPosition]!='\n') inputPosition++;
            int end=inputPosition;
            if(inputPosition<inputLimit) inputPosition++;
            if(overflow==null && inputPosition<=inputLimit && end<inputLimit) {
                if(end>start && inputBuffer[end-1]=='\r') end--;
                return new String(inputBuffer,start,end-start,java.nio.charset.StandardCharsets.ISO_8859_1);
            }
            if(overflow==null) overflow=new ByteArrayOutputStream(Math.max(128,end-start+64));
            overflow.write(inputBuffer,start,end-start);
            if(end<inputLimit) {
                byte[] value=overflow.toByteArray();
                int length=value.length;
                if(length>0 && value[length-1]=='\r') length--;
                return new String(value,0,length,java.nio.charset.StandardCharsets.ISO_8859_1);
            }
        }
    }

    private void collectLogMessage(String[] parts, MessageFormat format, long timeUS) {
        if (logMessages.size() >= MAX_LOG_MESSAGES) {
            return;
        }
        String msgName = format.name;
        if ("MSG".equals(msgName)) {
            Integer msgIdx = format.fieldsMap.get("Message");
            if (msgIdx == null) {
                msgIdx = format.fieldsMap.get("Text");
            }
            if (msgIdx != null) {
                int dataIdx = msgIdx + 1;
                if (dataIdx < parts.length) {
                    logMessages.add(new LogMessageEntry(timeUS, "INFO", parts[dataIdx]));
                }
            }
        } else if ("ERR".equals(msgName)) {
            Integer subsysIdx = format.fieldsMap.get("Subsys");
            Integer ecodeIdx = format.fieldsMap.get("ECode");
            if (subsysIdx != null && ecodeIdx != null) {
                String subsys = subsysIdx + 1 < parts.length ? parts[subsysIdx + 1] : "";
                String ecode = ecodeIdx + 1 < parts.length ? parts[ecodeIdx + 1] : "";
                logMessages.add(new LogMessageEntry(timeUS, "ERR", String.format("Subsys=%s, ECode=%s", subsys, ecode)));
            }
        } else if ("EV".equals(msgName)) {
            Integer idIdx = format.fieldsMap.get("Id");
            if (idIdx != null) {
                String idValue = idIdx + 1 < parts.length ? parts[idIdx + 1] : "";
                logMessages.add(new LogMessageEntry(timeUS, "EV", String.format("Id=%s", idValue)));
            }
        }
    }

    private String formatName(char format) {
        switch (format) {
            case 'b':
                return "int8";
            case 'B':
                return "uint8";
            case 'h':
                return "int16";
            case 'H':
                return "uint16";
            case 'i':
                return "int32";
            case 'I':
                return "uint32";
            case 'q':
                return "int64";
            case 'Q':
                return "uint64";
            case 'f':
                return "float";
            case 'g':
                return "float16";
            case 'd':
                return "double";
            case 'c':
                return "int16 * 1e-2";
            case 'C':
                return "uint16 * 1e-2";
            case 'e':
                return "int32 * 1e-2";
            case 'E':
                return "uint32 * 1e-2";
            case 'L':
                return "int32 * 1e-7 (lat/lon)";
            case 'n':
                return "char[4]";
            case 'N':
                return "char[16]";
            case 'Z':
                return "char[64]";
            case 'M':
                return "uint8 (mode)";
            case 'a':
                return "int16[32]";
            default:
                return "string";
        }
    }

    private static class MessageFormat {
        final int type;
        final String name;
        final String format;
        final String[] columns;
        final Map<String, Integer> fieldsMap = new HashMap<String, Integer>();
        final Set<Integer> observedInstances = new HashSet<Integer>();
        int instanceIndex = -1;

        MessageFormat(int type, String name, String format, String[] columns) {
            this.type = type;
            this.name = name;
            this.format = format;
            this.columns = columns;
            for (int i = 0; i < columns.length; i++) {
                fieldsMap.put(columns[i], i);
            }
            Integer candidate=fieldsMap.get("Instance");
            if(candidate==null) candidate=fieldsMap.get("I");
            if(candidate!=null && candidate<format.length() && "bBhHiI".indexOf(format.charAt(candidate))>=0) instanceIndex=candidate;
        }

        char getFormatAt(int idx) {
            if (idx < 0 || idx >= format.length()) {
                return 's';
            }
            return format.charAt(idx);
        }
    }

    private static class SeekPoint {
        final long position;
        final long time;

        SeekPoint(long position, long time) {
            this.position = position;
            this.time = time;
        }
    }

    private static class ParsedLine {
        final long time;
        final LinkedHashMap<String, Object> update;

        ParsedLine(long time, LinkedHashMap<String, Object> update) {
            this.time = time;
            this.update = update;
        }
    }

    public static class LogMessageEntry {
        public final long timeUS;
        public final String level;
        public final String message;

        LogMessageEntry(long timeUS, String level, String message) {
            this.timeUS = timeUS;
            this.level = level;
            this.message = message;
        }
    }
}
