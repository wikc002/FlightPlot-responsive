import java.io.*;
import java.nio.*;
import java.util.*;
import me.drton.jmavlib.log.px4.PX4LogReader;

/** Generates a 1 GiB log with increasing timestamps; bounded-memory stress test. */
public class LargeBinBenchmark {
    public static void main(String[] args) throws Exception {
        File file=new File("verification-0.5.8/generated-1g.bin");
        if(file.exists()) throw new IOException("Refusing to overwrite existing stress-test file");
        if(file.getAbsoluteFile().getParentFile().getUsableSpace()<2L*1024*1024*1024) throw new IOException("Insufficient test disk space");
        long records=(1024L*1024*1024-89)/19;
        try {
            try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file),1048576))) {
                ReaderRegression.fmt(out,1,19,"ATT","Qff","TimeUS,Roll,Pitch");
                ByteBuffer block=ByteBuffer.allocate(19*32768).order(ByteOrder.LITTLE_ENDIAN);
                for(long i=0;i<records;i++) {
                    if(block.remaining()<19) { out.write(block.array(),0,block.position());block.clear(); }
                    block.put((byte)0xa3).put((byte)0x95).put((byte)1).putLong(i*1000).putFloat((float)(i%100)).putFloat((float)(-(i%100)));
                }
                out.write(block.array(),0,block.position());
            }
            long began=System.nanoTime(); PX4LogReader reader=new PX4LogReader(file.getPath());
            try {
                long opened=System.nanoTime();
                ReaderRegression.check(reader.getSizeUpdates()==records,"1 GiB record count");
                long target=records*9/10*1000;
                reader.setNeededFields(Collections.singleton("ATT.Roll")); reader.seek(target);
                Map<String,Object> data=new HashMap<>();
                ReaderRegression.check(reader.readUpdate(data)==target,"1 GiB seek");
                long sought=System.nanoTime();
                System.out.printf(Locale.ROOT,"PASS 1GiB bytes=%d records=%d open_ms=%.1f seek90_ms=%.1f heap_used_MB=%d heap_limit_MB=%d%n",
                    file.length(),records,(opened-began)/1e6,(sought-opened)/1e6,
                    (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1048576,Runtime.getRuntime().maxMemory()/1048576);
            } finally {reader.close();}
        } finally {if(file.exists() && !file.delete()) throw new IOException("Cannot remove generated stress-test file");}
    }
}
