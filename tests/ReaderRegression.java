import me.drton.jmavlib.log.px4.*;
import me.drton.flightplot.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ReaderRegression {
    static void check(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
    static void str(DataOutputStream out,String value,int size) throws Exception {
        byte[] b=value.getBytes(StandardCharsets.ISO_8859_1); out.write(b); for(int i=b.length;i<size;i++) out.writeByte(0);
    }
    static void fmt(DataOutputStream out,int type,int length,String name,String format,String labels) throws Exception {
        out.write(new byte[]{(byte)0xa3,(byte)0x95,(byte)128}); out.writeByte(type); out.writeByte(length);
        str(out,name,4); str(out,format,16); str(out,labels,64);
    }
    static void record(DataOutputStream out,int type,ByteBuffer b) throws Exception {
        out.write(new byte[]{(byte)0xa3,(byte)0x95,(byte)type}); out.write(b.array());
    }
    static ByteBuffer bytes(int n) { return ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN); }
    static ByteBuffer fixed(ByteBuffer buffer,String value,int size) {
        byte[] data=value.getBytes(StandardCharsets.ISO_8859_1);
        buffer.put(data,0,Math.min(data.length,size));
        for(int i=Math.min(data.length,size);i<size;i++) buffer.put((byte)0);
        return buffer;
    }
    static void synthetic(String path) throws Exception {
        try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path)))) {
            fmt(out,1,19,"ATT","Qff","TimeUS,Roll,Pitch");
            fmt(out,2,15,"PID","Qf","TimeUS,I");
            for(int i=0;i<100000;i++) record(out,1,bytes(16).putLong(i*1000L).putFloat(i).putFloat(-i));
            // A late FMT and timestamp not in column zero; an instance seen only once.
            fmt(out,3,16,"SEN","BQf","I,TimeUS,Value");
            record(out,3,bytes(13).put((byte)9).putLong(100000000L).putFloat(123.5f));
            fmt(out,4,77,"NEW","Qga","TimeUS,Half,Array");
            ByteBuffer modern=bytes(74).putLong(100000000L).putShort((short)0x3e00);
            for(int j=0;j<32;j++) modern.putShort((short)(j-16));
            record(out,4,modern);
            fmt(out,114,44,"FMTU","QBNN","TimeUS,FmtType,UnitIds,MultIds");
            fmt(out,5,19,"CTRL","QII","TimeUS,I,Value");
            ByteBuffer fmtu=bytes(41).putLong(100000000L).put((byte)5);
            fixed(fmtu,"s--",16); fixed(fmtu,"F--",16); record(out,114,fmtu);
            record(out,5,bytes(16).putLong(100000000L).putInt(9).putInt(12));
            record(out,2,bytes(12).putLong(100000001L).putFloat(0.75f));
            record(out,1,bytes(16).putLong(100000002L).putFloat(777).putFloat(-777));
        }
        PX4LogReader reader=new PX4LogReader(path);
        try {
            check(reader.getStartMicroseconds()==0,"zero timestamp");
            check(reader.getFields().containsKey("SEN[9].Value"),"late instance and FMT");
            check(reader.getFields().containsKey("SEN[9].TimeUS") && reader.getFields().containsKey("SEN[9].I"),"timestamp and instance fields visible");
            check(reader.getFields().containsKey("PID.I"),"PID integral is not an instance");
            check(!reader.getFields().containsKey("SEN[0].Value"),"no fabricated instances");
            check(reader.getFields().containsKey("NEW.Half") && reader.getFields().containsKey("NEW.Array[31]"),"float16 and array fields visible");
            check(reader.getFields().containsKey("CTRL.I") && !reader.getFields().containsKey("CTRL[9].I"),"FMTU clears false instance guess");
            reader.seek(100000000L); reader.setNeededFields(new HashSet<>(Arrays.asList("NEW.Half","NEW.Array[31]")));
            Map<String,Object> modernData=new HashMap<>();
            while(!modernData.containsKey("NEW.Half")) reader.readUpdate(modernData);
            check(Math.abs(((Number)modernData.get("NEW.Half")).doubleValue()-1.5)<1e-6,"float16 decode");
            check(((Number)modernData.get("NEW.Array[31]")).intValue()==15,"array element decode");
            reader.seek(0);
            reader.setNeededFields(new HashSet<>(Arrays.asList("ATT.Roll")));
            Map<String,Object> data=new HashMap<>(); int count=0; double last=0;
            try { while(true) { data.clear(); reader.readUpdate(data); check(data.size()==1,"field projection"); last=((Number)data.get("ATT.Roll")).doubleValue(); count++; } } catch(EOFException e) {}
            check(count==100001 && last==777,"final record must not be lost");
            reader.seek(90000000L); data.clear(); check(reader.readUpdate(data)==90000000L,"seek equality");
            check(((Number)data.get("ATT.Roll")).intValue()==90000,"indexed seek value");
            reader.seek(100000002L); data.clear(); check(reader.readUpdate(data)==100000002L,"seek last record");
            check(!reader.seek(100000003L),"seek beyond EOF");
            reader.seek(0); data.clear(); check(reader.readUpdate(data)==0,"rewind after EOF");
        } finally { reader.close(); reader.close(); }
        // FMT payload straddles the 512 KiB buffer; incomplete trailing record is reported.
        try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path+".boundary")))) {
            for(int i=0;i<524280;i++) out.writeByte(0);
            fmt(out,1,15,"TEST","Qf","TimeUS,Value");
            record(out,1,bytes(12).putLong(7).putFloat(42));
            out.write(new byte[]{(byte)0xa3,(byte)0x95,1,0});
        }
        reader=new PX4LogReader(path+".boundary");
        try { Map<String,Object> data=new HashMap<>(); check(reader.readUpdate(data)==7,"boundary timestamp"); check(((Number)data.get("TEST.Value")).intValue()==42,"boundary payload"); check(!reader.getErrors().isEmpty(),"truncated tail warning"); }
        finally { reader.close(); }
        Thread.currentThread().interrupt();
        try { new PX4LogReader(path); throw new AssertionError("cancellation ignored"); }
        catch(InterruptedIOException expected) {} finally { Thread.interrupted(); }
        System.out.println("PASS synthetic: index, late FMT, instances, timestamp position/zero, filtering, EOF, refill, truncation, cancellation");
    }
    static void peaks() {
        Series s=new Series("spike",1);
        for(int i=0;i<10000;i++) s.addPoint(i*0.001,i==123?999:i==127?-999:0);
        s.finish(); double min=0,max=0,last=-1;
        for(XYPoint p:s) { min=Math.min(min,p.y);max=Math.max(max,p.y); check(p.x>=last,"chronological envelope");last=p.x; }
        check(min==-999 && max==999,"preserve both spikes"); check(s.size()<=40,"bounded plotted points"); check(last==9.999,"last bucket");
        System.out.println("PASS peak-preserving decimation points="+s.size());
        Series missing=new Series("missing",1);
        for(int i=0;i<1000000;i++) missing.addPoint(i*0.00001,i%2==0?Double.NaN:1);
        missing.finish();check(missing.size()<100,"bounded missing-data markers");
        System.out.println("PASS missing-data decimation points="+missing.size());
    }
    static void real(String path) throws Exception {
        PX4LogReader r=new PX4LogReader(path);
        try {
            Set<String> chosen=new HashSet<>(); for(String f:r.getFields().keySet()) if(f.equals("ATT.Roll") || f.equals("ATT.Pitch")) chosen.add(f);
            check(!chosen.isEmpty(),"real ATT fields");
            // Independent decode path: full message objects vs projected field values.
            long rawCount=0; double sum=0,last=0;
            try { while(true) { PX4LogMessage m=r.readMessage(); if("ATT".equals(m.description.name)) { rawCount++; last=((Number)m.get("Roll")).doubleValue();sum+=last; } } } catch(EOFException e) {}
            r.seek(0);r.setNeededFields(chosen); long selectedCount=0;double selectedSum=0,selectedLast=0;
            Map<String,Object> data=new HashMap<>();
            try { while(true) { data.clear();r.readUpdate(data);if(data.containsKey("ATT.Roll")) { selectedCount++;selectedLast=((Number)data.get("ATT.Roll")).doubleValue();selectedSum+=selectedLast; } } } catch(EOFException e) {}
            check(rawCount==selectedCount && sum==selectedSum && last==selectedLast,"full vs projected ATT data mismatch");
            System.out.println("PASS real "+new File(path).getName()+" ATT="+rawCount+" fields="+r.getFields().size()+" errors="+r.getErrors().size());
        } finally {r.close();}
    }
    public static void main(String[] args) throws Exception {
        synthetic("verification-0.5.8/synthetic.bin");peaks();for(String path:args) real(path);
    }
}
