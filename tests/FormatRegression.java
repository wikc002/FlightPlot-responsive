import me.drton.jmavlib.log.px4.*;
import java.io.*;
import java.nio.*;
import java.util.*;

public class FormatRegression {
    public static void main(String[] args) throws Exception {
        String path="verification-0.5.8/formats.bin";
        try(DataOutputStream out=new DataOutputStream(new FileOutputStream(path))) {
            ReaderRegression.fmt(out,1,15,"TEST","IfI","TimeMS,Value,Unsigned");
            ReaderRegression.record(out,1,ReaderRegression.bytes(12).putInt(123).putFloat(4.5f).putInt(-1));
            ReaderRegression.fmt(out,2,44,"FMTU","QBNN","TimeUS,FmtType,UnitIds,MultIds");
            ReaderRegression.fmt(out,3,16,"SENS","QBf","TimeUS,Id,Value");
            ByteBuffer meta=ReaderRegression.bytes(41);meta.putLong(124000).put((byte)3);meta.put("s#-".getBytes("ISO-8859-1"));meta.position(25);
            ReaderRegression.record(out,2,meta);
            ReaderRegression.record(out,3,ReaderRegression.bytes(13).putLong(124000).put((byte)12).putFloat(9.25f));
            ReaderRegression.fmt(out,4,18,"GPS","QBIH","TimeUS,Status,GMS,GWk");
            ReaderRegression.record(out,4,ReaderRegression.bytes(15).putLong(125000).put((byte)3).putInt(123456).putShort((short)2400));
            ReaderRegression.record(out,1,ReaderRegression.bytes(12).putInt(126).putFloat(7.5f).putInt(1));
            ReaderRegression.record(out,3,ReaderRegression.bytes(13).putLong(126000).put((byte)12).putFloat(8.25f));
        }
        PX4LogReader r=new PX4LogReader(path);
        try {
            ReaderRegression.check(r.getFields().containsKey("SENS[12].Value"),"FMTU instance indicator");
            ReaderRegression.check(r.getUTCTimeReferenceMicroseconds()==(315964800L+2400L*604800-18)*1000000+123456000-125000,"GPS week and millis");
            Map<String,Object> data=new HashMap<>();ReaderRegression.check(r.readUpdate(data)==123000,"TimeMS conversion");
            ReaderRegression.check(((Number)data.get("TEST.Unsigned")).longValue()==4294967295L,"uint32");
            r.seek(126000);data.clear();r.readUpdate(data);
            ReaderRegression.check(data.containsKey("TEST.Value") && data.containsKey("SENS[12].Value"),"same timestamp grouping and last group");
        } finally {r.close();}
        try(DataOutputStream out=new DataOutputStream(new FileOutputStream(path+".px4"))) {
            ReaderRegression.fmt(out,1,11,"TIME","Q","StartTime");
            ReaderRegression.fmt(out,2,11,"ATT","ff","Roll,Pitch");
            ReaderRegression.record(out,1,ReaderRegression.bytes(8).putLong(100));
            ReaderRegression.record(out,2,ReaderRegression.bytes(8).putFloat(10).putFloat(20));
            ReaderRegression.record(out,1,ReaderRegression.bytes(8).putLong(200));
            ReaderRegression.record(out,2,ReaderRegression.bytes(8).putFloat(30).putFloat(40));
        }
        r=new PX4LogReader(path+".px4");
        try {Map<String,Object> data=new HashMap<>();ReaderRegression.check("PX4".equals(r.getFormat()),"PX4 detection");
            ReaderRegression.check(r.readUpdate(data)==100,"PX4 frame time");data.clear();ReaderRegression.check(r.readUpdate(data)==200 && ((Number)data.get("ATT.Roll")).intValue()==30,"PX4 last frame");
        } finally {r.close();}
        System.out.println("PASS FMTU, TimeMS, uint32, GPS UTC, same-time grouping, legacy PX4 final frame");
    }
}
