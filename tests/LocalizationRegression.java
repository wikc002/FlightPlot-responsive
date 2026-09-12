import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeSet;

/** Compares every legacy localization entry with the compact data-driven implementation. */
public class LocalizationRegression {
    private static final String FIELD_DIGEST="7EA01C2CC03D1CFA67CC1E3EF7F5E8884A8FB1DE40BEE714FB544A89060F106C";
    private static final String PARAMETER_DIGEST="AA11B32C7D3ABD8449FE96D246B652EFA4A3568E4E881883104FD44D40BAC229";
    @SuppressWarnings("unchecked")
    private static Map<String,String> map(Class<?> type, String name) throws Exception {
        Field field=type.getDeclaredField(name); field.setAccessible(true); return (Map<String,String>)field.get(null);
    }

    private static void compare(URLClassLoader before, URLClassLoader after, String className,
                                String firstMap, String secondMap, boolean fieldNames) throws Exception {
        Class<?> oldType=Class.forName(className,true,before), newType=Class.forName(className,true,after);
        Method oldTranslate=oldType.getMethod("toZhCn",String.class), newTranslate=newType.getMethod("toZhCn",String.class);
        TreeSet<String> first=new TreeSet<String>(map(oldType,firstMap).keySet());
        TreeSet<String> second=new TreeSet<String>(map(oldType,secondMap).keySet());
        int checks=0;
        for(String key:first) {
            String input=fieldNames ? key+".Value" : key+"_VALUE";
            Object expected=oldTranslate.invoke(null,input), actual=newTranslate.invoke(null,input);
            if(!expected.equals(actual)) throw new AssertionError(input+": "+expected+" != "+actual);
            checks++;
        }
        for(String key:second) {
            String input=fieldNames ? "ATT."+key : "TEST_"+key;
            Object expected=oldTranslate.invoke(null,input), actual=newTranslate.invoke(null,input);
            if(!expected.equals(actual)) throw new AssertionError(input+": "+expected+" != "+actual);
            checks++;
        }
        if(map(newType,firstMap).size()!=first.size() || map(newType,secondMap).size()!=second.size())
            throw new AssertionError(className+" map size changed");
        System.out.println("PASS "+className+" entries="+checks);
    }

    private static String digest(Class<?> type, String firstMap, String secondMap, boolean fieldNames) throws Exception {
        Method translate=type.getMethod("toZhCn",String.class);
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        TreeSet<String> first=new TreeSet<String>(map(type,firstMap).keySet());
        TreeSet<String> second=new TreeSet<String>(map(type,secondMap).keySet());
        for(String key:first) update(digest,key,translate.invoke(null,fieldNames?key+".Value":key+"_VALUE"));
        for(String key:second) update(digest,key,translate.invoke(null,fieldNames?"ATT."+key:"TEST_"+key));
        StringBuilder value=new StringBuilder();
        for(byte b:digest.digest()) value.append(String.format("%02X",b&0xff));
        return value.toString();
    }

    private static void update(MessageDigest digest, String key, Object value) {
        digest.update((key+"\t"+value+"\n").getBytes(StandardCharsets.UTF_8));
    }

    public static void main(String[] args) throws Exception {
        if(args.length==0) {
            String fields=digest(Class.forName("me.drton.flightplot.FieldNameLocalizer"),"moduleMap","tokenMap",true);
            String parameters=digest(Class.forName("me.drton.flightplot.ParameterNameLocalizer"),"prefixMap","tokenMap",false);
            if(!FIELD_DIGEST.equals(fields) || !PARAMETER_DIGEST.equals(parameters))
                throw new AssertionError("localization data or behavior changed");
            System.out.println("PASS localization compatibility digests");
            return;
        }
        if(args.length!=2) throw new IllegalArgumentException("[before.jar after.jar]");
        try(URLClassLoader before=new URLClassLoader(new URL[]{new File(args[0]).toURI().toURL()},null);
            URLClassLoader after=new URLClassLoader(new URL[]{new File(args[1]).toURI().toURL()},null)) {
            compare(before,after,"me.drton.flightplot.FieldNameLocalizer","moduleMap","tokenMap",true);
            compare(before,after,"me.drton.flightplot.ParameterNameLocalizer","prefixMap","tokenMap",false);
        }
    }
}
