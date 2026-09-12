package me.drton.jmavlib.log.px4;

import me.drton.jmavlib.log.BinaryLogReader;
import me.drton.jmavlib.log.FormatErrorException;
import java.io.*;
import java.util.*;

/** Streaming DataFlash/PX4 reader. Metadata is complete before publication to Swing. */
public class PX4LogReader extends BinaryLogReader {
    private final Plan[] plans = new Plan[256];
    private final Map<String,String> fields = new TreeMap<>();
    private final Map<String,Object> version = new HashMap<>(), parameters = new HashMap<>();
    private final List<Exception> errors = new ArrayList<>();
    private final List<PX4LogMessage> messages = new ArrayList<>();
    private final List<Long> messageTimes = new ArrayList<>();
    private final List<Checkpoint> index = new ArrayList<>();
    private static final Set<String> HIDDEN = new HashSet<>(Arrays.asList("FMT", "FMTU", "UNIT", "MULT", "PARM", "TIME", "VER"));
    private Set<String> neededFields, neededTypes;
    private long start = -1, end = -1, updates, time, seekFloor, recordPosition;
    private long utc = -1;
    private boolean px4, schemaChanged;
    private Plan pendingPlan;

    private static final class Checkpoint {
        final long position, maxTime, previousTime;
        Checkpoint(long p, long max, long previous) { position=p; maxTime=max; previousTime=previous; }
    }
    private static final class Plan {
        final PX4LogMessageDescription desc;
        final int[] offsets;
        final int timestamp;
        int instance;
        final Map<Integer,String[]> keys = new HashMap<>();
        Plan(PX4LogMessageDescription d) {
            desc=d; offsets=new int[d.format.length()];
            int offset=0;
            for(int i=0;i<offsets.length;i++) { offsets[i]=offset; offset+=width(d.format.charAt(i)); }
            if(d.length < 3 || offset != d.length-3) throw new IllegalArgumentException("Invalid FMT length: " + d.name);
            timestamp = d.fieldsMap.containsKey("TimeUS") ? d.fieldsMap.get("TimeUS") :
                d.fieldsMap.containsKey("TimeMS") ? d.fieldsMap.get("TimeMS") : "TIME".equals(d.name) ? 0 : -1;
            instance = d.fieldsMap.containsKey("Instance") ? d.fieldsMap.get("Instance") :
                d.fieldsMap.containsKey("I") ? d.fieldsMap.get("I") : -1;
            // PID.I is an integral term, not a sensor instance number.
            if (instance >= 0 && "bBhHiI".indexOf(d.format.charAt(instance)) < 0) instance = -1;
        }
        String[] keys(int instanceId) {
            String[] result=keys.get(instanceId);
            if(result==null) {
                result=new String[desc.fields.length];
                String prefix=desc.name+(instance>=0 ? "["+instanceId+"]" : "")+".";
                for(int i=0;i<result.length;i++) result[i]=prefix+desc.fields[i];
                keys.put(instanceId,result);
            }
            return result;
        }
    }
    public PX4LogReader(String path) throws IOException, FormatErrorException {
        super(path);
        try { scan(); seek(0); }
        catch(IOException | RuntimeException e) { close(); throw e; }
    }
    private static int width(char c) {
        switch(c) {
            case 'b': case 'B': case 'M': return 1;
            case 'h': case 'H': case 'c': case 'C': case 'g': return 2;
            case 'i': case 'I': case 'L': case 'e': case 'E': case 'f': case 'n': return 4;
            case 'q': case 'Q': case 'd': return 8;
            case 'N': return 16;
            case 'Z': case 'a': return 64;
            default: throw new IllegalArgumentException("Unsupported DataFlash format: "+c);
        }
    }
    private Object value(Plan p, int field, int base) {
        buffer.position(base+p.offsets[field]);
        switch(p.desc.format.charAt(field)) {
            case 'b': return (int)buffer.get();
            case 'B': case 'M': return buffer.get()&255;
            case 'h': return (int)buffer.getShort();
            case 'H': return buffer.getShort()&65535;
            case 'i': return buffer.getInt();
            case 'I': return buffer.getInt()&0xffffffffL;
            case 'q': case 'Q': return buffer.getLong();
            case 'f': return buffer.getFloat();
            case 'd': return buffer.getDouble();
            case 'c': return buffer.getShort()*0.01;
            case 'C': return (buffer.getShort()&65535)*0.01;
            case 'e': return buffer.getInt()*0.01;
            case 'E': return (buffer.getInt()&0xffffffffL)*0.01;
            case 'L': return buffer.getInt()*1e-7;
            case 'g': return halfToFloat(buffer.getShort()&0xffff);
            case 'a': short[] a=new short[32]; for(int i=0;i<32;i++) a[i]=buffer.getShort(); return a;
            default:
                byte[] bytes=new byte[width(p.desc.format.charAt(field))]; buffer.get(bytes);
                int n=0; while(n<bytes.length && bytes[n]!=0) n++;
                return new String(bytes,0,n,java.nio.charset.StandardCharsets.ISO_8859_1);
        }
    }
    /** IEEE-754 binary16 used by recent ArduPilot DataFlash schemas. */
    static float halfToFloat(int bits) {
        int sign=(bits&0x8000)<<16, exponent=(bits>>>10)&0x1f, fraction=bits&0x3ff;
        int result;
        if(exponent==0) {
            if(fraction==0) result=sign;
            else {
                exponent=1;
                while((fraction&0x400)==0) { fraction<<=1; exponent--; }
                fraction&=0x3ff;
                result=sign|((exponent+112)<<23)|(fraction<<13);
            }
        } else if(exponent==31) result=sign|0x7f800000|(fraction<<13);
        else result=sign|((exponent+112)<<23)|(fraction<<13);
        return Float.intBitsToFloat(result);
    }
    private void catalogFields(Plan p,int instanceId) {
        if(HIDDEN.contains(p.desc.name)) return;
        String[] names=p.keys(instanceId);
        for(int i=0;i<names.length;i++) {
            char type=p.desc.format.charAt(i);
            if(type=='a') for(int j=0;j<32;j++) fields.put(names[i]+"["+j+"]","h");
            else fields.put(names[i],String.valueOf(type));
        }
    }
    private void removeCatalog(String messageName) {
        Iterator<String> iterator=fields.keySet().iterator();
        while(iterator.hasNext()) {
            String key=iterator.next();
            if(key.startsWith(messageName+".") || key.startsWith(messageName+"[")) iterator.remove();
        }
    }
    private boolean putField(Map<String,Object> out,String key,Object value) {
        if(value instanceof short[]) {
            boolean any=false; short[] values=(short[])value;
            for(int j=0;j<values.length;j++) {
                String arrayKey=key+"["+j+"]";
                if(neededFields==null || neededFields.contains(arrayKey)) { out.put(arrayKey,(int)values[j]); any=true; }
            }
            return any;
        }
        if(neededFields==null || neededFields.contains(key)) { out.put(key,value); return true; }
        return false;
    }
    private long timestamp(Plan p, int base) {
        if(p.timestamp<0) return time;
        buffer.position(base+p.offsets[p.timestamp]);
        char format=p.desc.format.charAt(p.timestamp);
        long t;
        if(format=='Q' || format=='q') t=buffer.getLong();
        else if(format=='I') t=buffer.getInt()&0xffffffffL;
        else { Object v=value(p,p.timestamp,base); if(!(v instanceof Number)) return time; t=((Number)v).longValue(); }
        return "TimeMS".equals(p.desc.fields[p.timestamp]) ? t*1000 : t;
    }
    private int instance(Plan p,int base) {
        if(p.instance<0) return -1;
        Object v=value(p,p.instance,base);
        return v instanceof Number ? ((Number)v).intValue() : -1;
    }
    private void error(long pos,String message) {
        if(errors.size()<100) errors.add(new FormatErrorException(pos,message));
    }
    /** Returns a complete payload, even when the record crosses a refill boundary. */
    private Plan next() throws IOException {
        while(true) {
            if(Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Log operation cancelled");
            fillBuffer(3);
            int b=buffer.position();
            if((buffer.get(b)&255)!=0xa3 || (buffer.get(b+1)&255)!=0x95) { buffer.position(b+1); continue; }
            recordPosition=position();
            int type=buffer.get(b+2)&255;
            buffer.position(b+3);
            if(type==128) {
                fillBuffer(86);
                int base=buffer.position();
                try {
                    PX4LogMessageDescription d=new PX4LogMessageDescription(buffer);
                    if(d.type!=128) {
                        Plan old=plans[d.type];
                        if(old==null || !old.desc.format.equals(d.format) || !Arrays.equals(old.desc.fields,d.fields) || !old.desc.name.equals(d.name)) {
                            if(old!=null) schemaChanged=true;
                            plans[d.type]=new Plan(d);
                        }
                    }
                    if("TIME".equals(d.name)) px4=true;
                } catch(RuntimeException e) { error(recordPosition,e.getMessage()); }
                buffer.position(base+86);
                continue;
            }
            Plan p=plans[type];
            if(p==null) { error(recordPosition,"Unknown message type: "+type); continue; }
            try { fillBuffer(p.desc.length-3); }
            catch(EOFException e) { error(recordPosition,"Truncated "+p.desc.name+" record"); throw e; }
            if ("FMTU".equals(p.desc.name)) {
                int base=buffer.position();
                PX4LogMessage meta=p.desc.parseMessage(buffer);
                Object typeId=meta.get("FmtType"), unitIds=meta.get("UnitIds");
                if(typeId instanceof Number && unitIds instanceof String) {
                    int id=((Number)typeId).intValue();
                    Plan target=id>=0 && id<plans.length ? plans[id] : null;
                    int marker=((String)unitIds).indexOf('#');
                    int instanceIndex=target!=null && marker>=0 && marker<target.offsets.length &&
                            "bBhHiI".indexOf(target.desc.format.charAt(marker))>=0 ? marker : -1;
                    if(target!=null && target.instance!=instanceIndex) {
                        removeCatalog(target.desc.name);
                        target.instance=instanceIndex; target.keys.clear();
                    }
                }
                buffer.position(base);
            }
            return p;
        }
    }
    private void scan() throws IOException {
        long nextCheckpoint=0;
        while(true) {
            Plan p;
            try { p=next(); } catch(EOFException e) { break; }
            int base=buffer.position(), limit=base+p.desc.length-3;
            long previous=time;
            time=timestamp(p,base);
            if(recordPosition>=nextCheckpoint) {
                index.add(new Checkpoint(recordPosition,end,previous));
                nextCheckpoint=recordPosition+1024*1024;
            }
            if(p.timestamp>=0 && time>=0) { if(start<0) start=time; end=Math.max(end,time); }
            updates++;
            int inst=instance(p,base);
            if(!p.keys.containsKey(inst)) {
                p.keys(inst);
                catalogFields(p,inst);
            }
            String name=p.desc.name;
            if("PARM".equals(name) || "VER".equals(name) || "GPS".equals(name) && utc<0 ||
                    ("MSG".equals(name) || "ERR".equals(name) || "EV".equals(name)) && messages.size()<5000) {
                buffer.position(base);
                PX4LogMessage msg=p.desc.parseMessage(buffer);
                if("PARM".equals(name)) {
                    Object key=msg.get("Name"); if(key!=null) parameters.put(key.toString(),msg.get("Value"));
                } else if("VER".equals(name)) {
                    version.put("FW",msg.get("FwGit")); version.put("HW",msg.get("Arch"));
                } else if("GPS".equals(name)) {
                    Object t=msg.get("GPSTime"), fix=msg.get("Fix");
                    if(t instanceof Number && fix instanceof Number && ((Number)fix).intValue()>=3) utc=((Number)t).longValue()-time;
                    Object week=msg.get("GWk"), millis=msg.get("GMS"), status=msg.get("Status");
                    if(week instanceof Number && millis instanceof Number && status instanceof Number &&
                            ((Number)week).intValue()>0 && ((Number)status).intValue()>=3) {
                        // Matches ArduPilot/pymavlink's GPS week conversion (post-2017 logs).
                        utc=(315964800L+((Number)week).longValue()*604800L-18)*1000000L+
                            ((Number)millis).longValue()*1000L-time;
                    }
                } else {
                    messages.add(msg); messageTimes.add(time);
                    Object text=msg.get("Message");
                    if(text instanceof String && ((String)text).startsWith("Ardu")) version.put("FW",text);
                }
            }
            buffer.position(limit);
        }
        if(fields.isEmpty()) throw new IOException("No supported DataFlash records found");
        if(start<0) { start=0; end=0; }
    }
    public List<PX4LogMessage> getLogMessages() { return Collections.unmodifiableList(messages); }
    public long getLogMessageTime(int i) { return messageTimes.get(i); }
    @Override public String getFormat() { return px4 ? "PX4" : "APM"; }
    @Override public String getSystemName() { return getFormat(); }
    @Override public long getStartMicroseconds() { return start; }
    @Override public long getSizeMicroseconds() { return Math.max(0,end-start); }
    @Override public long getSizeUpdates() { return updates; }
    @Override public long getUTCTimeReferenceMicroseconds() { return utc; }
    @Override public Map<String,Object> getVersion() { return version; }
    @Override public Map<String,Object> getParameters() { return parameters; }
    @Override public Map<String,String> getFields() { return fields; }
    @Override public List<Exception> getErrors() { return errors; }
    @Override public void clearErrors() { errors.clear(); }
    public void setNeededFields(Set<String> names) {
        neededFields=names==null || names.isEmpty() ? null : new HashSet<>(names);
        neededTypes=null;
        if(neededFields!=null) {
            neededTypes=new HashSet<>();
            for(String s:names) { int dot=s.indexOf('.'), bracket=s.indexOf('['); int stop=dot<0?s.length():dot;
                if(bracket>=0) stop=Math.min(stop,bracket); neededTypes.add(s.substring(0,stop)); }
        }
    }
    @Override public boolean seek(long target) throws IOException {
        pendingPlan=null; time=0; seekFloor=target;
        long pos=0;
        if(target>0 && !schemaChanged) {
            int low=0,high=index.size()-1,chosen=-1;
            while(low<=high) { int mid=(low+high)>>>1; if(index.get(mid).maxTime<target) {chosen=mid;low=mid+1;} else high=mid-1; }
            if(chosen>=0) { Checkpoint c=index.get(chosen); pos=c.position; time=c.previousTime; }
        }
        position(pos);
        if(target==0) return true;
        try {
            while(true) {
                Plan p=next(); int base=buffer.position(); long previous=time;
                time=timestamp(p,base);
                if(time>=target) { position(recordPosition); time=previous; return true; }
                buffer.position(base+p.desc.length-3);
            }
        } catch(EOFException e) { return false; }
    }
    public PX4LogMessage readMessage() throws IOException, FormatErrorException {
        Plan p=takeNext(); int base=buffer.position(); time=timestamp(p,base); buffer.position(base);
        return p.desc.parseMessage(buffer);
    }
    private Plan takeNext() throws IOException {
        if(pendingPlan==null) return next();
        Plan p=pendingPlan; pendingPlan=null; return p;
    }
    private void apply(Map<String,Object> out,PX4LogMessage msg) {
        Plan p=plans[msg.description.type];
        if(neededTypes!=null && !neededTypes.contains(p.desc.name)) return;
        int inst=p.instance<0?-1:((Number)msg.get(p.instance)).intValue();
        String[] keys=p.keys(inst);
        for(int i=0;i<keys.length;i++) putField(out,keys[i],msg.get(i));
    }
    @Override public long readUpdate(Map<String,Object> out) throws IOException, FormatErrorException {
        if(px4) {
            long t=time; boolean any=false;
            while(true) {
                PX4LogMessage msg;
                try { msg=readMessage(); } catch(EOFException e) { if(any) return t; throw e; }
                if("TIME".equals(msg.description.name)) { if(any) return t; t=time; }
                else { apply(out,msg); any=true; }
            }
        }
        long updateTime=0;
        boolean applied=false;
        while(true) {
            Plan p;
            try { p=takeNext(); } catch(EOFException eof) { if(applied) return updateTime; throw eof; }
            int base=buffer.position(), limit=base+p.desc.length-3;
            time=timestamp(p,base);
            if(time<seekFloor || HIDDEN.contains(p.desc.name) || neededTypes!=null && !neededTypes.contains(p.desc.name)) { buffer.position(limit); continue; }
            if(applied && time>updateTime) {
                buffer.position(base); pendingPlan=p; return updateTime;
            }
            String[] keys=p.keys(instance(p,base));
            boolean any=false;
            for(int i=0;i<keys.length;i++) any|=putField(out,keys[i],value(p,i,base));
            buffer.position(limit);
            if(any && !applied) { updateTime=time; applied=true; }
        }
    }
}
