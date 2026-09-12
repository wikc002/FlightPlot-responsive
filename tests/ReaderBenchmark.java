import me.drton.jmavlib.log.LogReader;
import java.io.*;
import java.util.*;
public class ReaderBenchmark {
    public static void main(String[] args) throws Exception {
        for (String path : args) {
            long start = System.nanoTime();
            LogReader r = (LogReader) Class.forName(System.getProperty("reader", "me.drton.jmavlib.log.px4.PX4LogReader"))
                    .getConstructor(String.class).newInstance(path);
            try {
                long begin = r.getStartMicroseconds(), duration = r.getSizeMicroseconds();
                int fields = r.getFields().size();
                long opened = System.nanoTime();
                r.seek(begin + duration * 9 / 10);
                long sought = System.nanoTime();
                Map<String,Object> data = new HashMap<>();
                int count = 0;
                try { for (; count < 1000; count++) { data.clear(); r.readUpdate(data); } } catch (EOFException e) { }
                System.out.printf(Locale.ROOT, "BENCH %s bytes=%d open_ms=%.1f seek90_ms=%.1f read1000_ms=%.1f fields=%d start=%d duration=%d records=%d errors=%d heap_MB=%d%n",
                    new File(path).getName(), new File(path).length(), (opened-start)/1e6, (sought-opened)/1e6,
                    (System.nanoTime()-sought)/1e6, fields, begin, duration, r.getSizeUpdates(), r.getErrors().size(),
                    (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1048576);
            } finally { r.close(); }
        }
    }
}
