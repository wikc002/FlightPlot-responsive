import me.drton.jmavlib.log.*;
import me.drton.jmavlib.log.px4.PX4LogReader;
import me.drton.jmavlib.log.ulog.ULogReader;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class FieldCatalogRegression {
    static void check(boolean condition,String message) { if(!condition) throw new AssertionError(message); }

    static LogReader open(String path) throws Exception {
        String lower=path.toLowerCase(Locale.ROOT);
        if(lower.endsWith(".ulg")) return new ULogReader(path);
        if(lower.endsWith(".log")) return new DataFlashTextLogReader(path);
        return new PX4LogReader(path);
    }

    static Set<String> verify(String path,int readLimit) throws Exception {
        LogReader reader=open(path);
        try {
            Set<String> catalog=new TreeSet<String>(reader.getFields().keySet());
            check(!catalog.isEmpty(),"empty dynamic catalog: "+path);
            boolean hasTimestamp=false;
            for(String name:catalog) if(name.endsWith(".timestamp") || name.endsWith(".TimeUS") || name.endsWith(".TimeMS")) {hasTimestamp=true;break;}
            check(hasTimestamp,"timestamp fields absent from catalog: "+path);
            if(reader.getSizeMicroseconds()>1) {
                long target=reader.getStartMicroseconds()+reader.getSizeMicroseconds()/2;
                check(reader.seek(target),"mid-log seek failed: "+path);
                Map<String,Object> middle=new HashMap<String,Object>();
                check(reader.readUpdate(middle)>=target,"mid-log seek returned earlier data: "+path);
            }
            reader.seek(0);
            Map<String,Object> update=new HashMap<String,Object>();
            int updates=0;
            try {
                while(updates<readLimit) {
                    update.clear(); reader.readUpdate(update); updates++;
                    for(String key:update.keySet()) check(catalog.contains(key),"decoded field missing from catalog: "+key+" in "+path);
                }
            } catch(EOFException expected) {}
            System.out.println("PASS dynamic catalog "+new File(path).getName()+" fields="+catalog.size()+" checked_updates="+updates);
            return catalog;
        } finally { reader.close(); }
    }

    static void textSchema() throws Exception {
        Path path=Paths.get("verification-0.5.8/dynamic-text.log");
        Files.createDirectories(path.getParent());
        List<String> lines=Arrays.asList(
            "FMT, 128, 89, FMT, BBnNZ, Type,Length,Name,Format,Columns",
            "FMT, 114, 44, FMTU, QBNN, TimeUS,FmtType,UnitIds,MultIds",
            "FMT, 200, 16, TEST, QBI, TimeUS,I,NewField",
            "FMT, 201, 13, NEW, Qg, TimeUS,Half",
            "FMT, 202, 19, CTRL, QII, TimeUS,I,Value",
            "FMTU, 10, 200, s#-, F---",
            "FMTU, 11, 201, s-, F-",
            "FMTU, 12, 202, s--, F--",
            "TEST, 1000, 7, 4294967295",
            "NEW, 1001, 1.5",
            "CTRL, 1002, 9, 12"
        );
        Files.write(path,lines,StandardCharsets.ISO_8859_1);
        DataFlashTextLogReader reader=new DataFlashTextLogReader(path.toString());
        try {
            Map<String,String> fields=reader.getFields();
            check(fields.containsKey("TEST[7].TimeUS"),"FMTU instance timestamp");
            check(fields.containsKey("TEST[7].I"),"FMTU instance field");
            check(fields.containsKey("TEST[7].NewField"),"new firmware field");
            check(!fields.containsKey("TEST.I"),"uninstanced duplicate");
            check(fields.containsKey("NEW.Half") && "float16".equals(fields.get("NEW.Half")),"text float16");
            check(fields.containsKey("CTRL.I") && !fields.containsKey("CTRL[9].I"),"FMTU clears false instance guess");
            reader.seek(0); Map<String,Object> update=new HashMap<String,Object>();
            reader.readUpdate(update);
            check(((Number)update.get("TEST[7].NewField")).longValue()==4294967295L,"uint32 value");
            System.out.println("PASS synthetic text FMT/FMTU, late fields, instances, uint32 and float16");
        } finally {reader.close();}
    }

    static void cancellation(String path) throws Exception {
        Thread.currentThread().interrupt();
        try {
            open(path);
            throw new AssertionError("cancelled reader unexpectedly opened: "+path);
        } catch(IOException expected) {
            check(expected instanceof java.io.InterruptedIOException || expected instanceof java.nio.channels.ClosedByInterruptException,
                    "unexpected cancellation exception: "+expected);
            System.out.println("PASS prompt open cancellation "+new File(path).getName());
        } finally { Thread.interrupted(); }
    }

    public static void main(String[] args) throws Exception {
        textSchema();
        if(args.length>0) cancellation(args[0]);
        for(String path:args) if(path.toLowerCase(Locale.ROOT).endsWith(".ulg")) {cancellation(path);break;}
        Set<Set<String>> distinct=new HashSet<Set<String>>();
        for(String path:args) {
            Set<String> current=verify(path,250000);
            distinct.add(current);
        }
        if(args.length>1) check(distinct.size()>1,"all supplied files unexpectedly have the same schema");
    }
}
