package me.drton.flightplot;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Data-driven parameter translation with stable native parameter keys. */
public final class ParameterNameLocalizer {
    private static final Map<String,String> prefixMap = new HashMap<String,String>();
    private static final Map<String,String> tokenMap = new HashMap<String,String>();
    private static final String DATA =
            "H4sIAAAAAAAACm1Y6VLbShb+ffwuU3WN7/qzLbWNBm3TahvIG0xN1RQ/9AAGkovZfRMS9i2QmJCLTVbABvwG8xTulvQWU0ctJAvywy73OXL3Wb" +
            "/vtGaAaMyBYPFGLjXk2bG4XS/MANG9Mn79o4yLceaBaA9kYzZsnovWX2J9LmzMo4ZZhl2FsH0SbczGa8/VIfjQixqHYecYJVxL/irX2qL5HUU1" +
            "7vCaTSFcOBdLZ+HlvFifS+SJLLOjrkM0O4h29uKFY8TLsPOhMANlwhyQlxtibVmdVSacQ7DxTX46wpVmw3BwLI8WcMF0GF735f4AF9wGubIYve" +
            "wUZkAjFgS713KvFy9s/MhGP+jhk9p4jVOItlfD1t3w9gAlBtNMCkH/tdhr4tqxXOJ5ENxtBrtbKKh5PPaiszO8WUQBpJ7rtA5h516coknU09DY" +
            "8BIDSac4iNM30fxZYQYqGkQnB3Ktjb9Nbjk6LaIkPF5RgSlmirGcYixTlHKKUqb4Oaf4OVP8klP8kil+zSl+RYVjms6kRSG8Oox2MFEVRiwKcq" +
            "8nD9DTigfi9JNcaw8H+3LptDADVdcD8eIsaM1jZO5WUTTNHIi2G+FBb9g/L8yAYdVAzndF9xYXNqlD0H8uWk3RvQ2b+IBJbD0uuG9XYetOHYWy" +
            "JEVqVa2RKoWwexGeNVDiVmB4txY1dmT/VH55W5gBi1QhOJkVS7uqcCwyBXKvIU4x4pbhwbDfF0uocQkjlgdifU6+vsQ1c/SaxmHYa4tXWPGM2N" +
            "WKrYP8thxe7Ss3mFaEaPadXGtHjZ1o9hWmi2ljORlmimmlnAyTxLSfczLMD9N+yckwNUz7NSfDrDDtt5zst1j2e072eyz7Iyf7Q9n8U97on5T0" +
            "kSvKl2LemaLypph3p6j8KeYdKiqPinmXisqnYt6povLKIm4illtH4vI5CvkEfgozwEG+vhzeYsfXHZNj6oONb2JtuTADky6EzfNg7qbgg0ZMo8" +
            "wIpzqIq8/y6Fgs/FnwwTJsw+aU1YkZl8DlerT5NdrZKPjgOh4M71aDu07BB6eCJT27HrT7BR88UqcwHOyLi62CnzuGuSDPzmV/ZzjoDvuXBR8m" +
            "XJDdjeH9csEHBCiFhmiRY7kQHr8Ts4OCDxVqaxTE7ld5tF7wgdowvBuE83e4J9GNGojVJXH/HE12uMNinNvrFXwwdJOCbBxFjcOCD1PTIC+/ip" +
            "sv6EB2sA7iviOa6LHmVCD40sd69sGAoN1V8pEm8GHi4Q/pkxMPj6YSlnha8GEaQ4Mt6kOlAmJxNXrfRCcyiFMJgDTuOjXJNIj+93CAZtuU6hDt" +
            "NcL3GBl30oLw+aLo3CE5NL8XfODTLoXgU18coDsjcOmDpxGMwIOrGXr6yE8q3wlD+aDZHMLOsfKg7DgcRKsrlnCfcs0wdZAHz0Uf46oTTkFuvp" +
            "N7aGDFNKrjPAFCtMdAuNv8Hm1+jWNXNTSILjbUvpqjUxj2T4Kj+EgzJohPfdH8iDs9gkYfHJcbju1B1FiMjuMi4iaEgw0VUA+C9ks8cZyB/HwT" +
            "baKtdWLWKIjGLToZM/BL0XuPCX2Cmj5YHsjuR7VLlRI2Ap8+jEP0cUv9t05NiBqHycIx+UMn+U9g1X8Kqz4QTQOxhIUoeu8RWf3HEO+PDhtxaA" +
            "yrDEHzb7G6gCF3Jm0YXi9H26toGVUhfzDPdAxOGci5v8Xs3uPjYr3jgrg/D9a6KkFZfrjDsb8bfWVVHtV9YNSjHKKFpNcfgbwPXq0c15+4aKUl" +
            "6E1CeNcd9jGGdcogWGzKvb/TbI4kk9Yp87AM11dF6y+0hhkWtpfqC/0ZyIu+WMGaKxOPgjjsBR+3484GQy/4UCOMw/D6s1g/idEAR5LGYbCGIS" +
            "vTcVI3HAbh8crwOt6DTbgc5JsLBUge/ReI3rpobmLtEY+DGHxUWFEmJoibL+FxHBODa+OQohZzTHOkv8lk1uF5A+iU64BcWVCR5IwYNmUQdi6C" +
            "/vyTvs1DXplyAv/7nAJKzaMIesEGVrhl2AkgP8amKjFsEG8Pgt2lHzWPwwxqcxInQb65+VHItXGqTYA8acjDd0/Ax3Q0SEYUH1hN17Hm4n3C5r" +
            "cYmUnZpAgbylBiV02aNSDRNGrm6tKw3RqH8P6VeIGnjc4rPlg1k4NorKpgMsKzyFZM/oDCaYtVTI492BVXnVEZh2C3I48WUpmBEVN4PbxeirZb" +
            "WAlxFN/sBWsLqczVrSlw9ewZk06CWN8SK2+CtYVou5Wg7yj2WMYUyKurBGGrVQZy0AgHu4n/U9SD8A67TjdwFL7aD94jZ1qEVQ0bwvub8Gof02" +
            "jrIPYuo32012XU85JhHjcxR/oeqZfyjHxdZliETeM8nwzTyIm2EbPi7l/KrnFKOGYhmL+I+bpC+TSIzqJ4EbtAvAmQax8UQD/iophuvYxvdarF" +
            "0+LsehhD6MgUH/eoPFoIuzGnGQRE9yZsv0xpztENiPYP5MXJsHeaSZNZIkEbw4Hh7ZZ40Qx67ad8FoNVhlUVYpjozmMS8dy4ih+KrubC8Hopgd" +
            "URUJ2CKZWcaZhWP57BM/VjZFwYGQtGJodRamej8OAaPEOOOjXTISRjE2o+A3EwF+x+TWWu46UPpnOW63gPD47KskHMdTwj7u1UwhCc1g5Fe/mH" +
            "aFup2Vr8B7F0qOYpG0F2+1ycXD6B3/T+GLMAdzTHBLG6HnY6j6cOpiUzaYxIiIhbTdG4jTbPVCeZThWnBzFA3KVTnJEiyMUP4tPr4oNgLBGMPQ" +
            "hKiaCE+yOrr8yLXUxNxTA5ZDybXc6fchnXEtoT19dK4nF0a+l7MnVyZj7cSB+PMbhyEKu+v5CbqC6Tmg7yy9tg8UZhkjZOkNpwOI+5xfZgeHsk" +
            "nx+KbQxKxXQmQbxYlN/wqJELvZr/OLXcZASU1x9SvHwyNLgeftRcNXJRi+NAQb5ZULSbG3w8vHXJXguj6+FtS/ZaGFgPb1my18KY0olKEmGxei" +
            "wuN+TubRrUf1I2gYaMIrfOSBWirb5Y2o1popKUTXCxKF8307/iHRmSVxY+mFTHTwym+D5BEbdG7HjEfPYMwr256PpEBcxyeDbJj1dHUM/mFYi2" +
            "r+SXt+k5fJxRbzwpNAVEhp5AUTwoTBpILSdrCb07DrJ9TJRyvaWwAMk7Y9OUayxSx4/57//+B8PMJ0Hs9lSU8Y1OGpHRUdLRjbHH4IYRd3Sj9F" +
            "heSokzHsdT8ixj0Js34V2cQh08Xaxiok3DgpR9nIo3luBlvH/FKyXL0o9GTBTgbS2VpVBbnWZjedXYqK6U15VS3aRWwbCqwWV03FW3woeKSZ8n" +
            "mjaWV2XnEE0r5XXZOUTTEPwzlTxaEJ2dZMe8amxUV8rrSqlON8hYjpMwgLpBSjlhSVXiWFKKyk980nJ4KSfEJ/GolMoeTdZ1w4Ow/WfYXsRziv" +
            "gy7eEdiA/6WLaO7WAGTs7Rh08KIfSH36o3XIdxCD521dg78jrPh6pXwdkhYeXmeZqTOmUsoQ81JSW3SXfSSoZRdRM7WI52/xStZuH/VHMbg/8U" +
            "AAA=";

    static { load(DATA, prefixMap, tokenMap); }

    private static void load(String data, Map<String,String> first, Map<String,String> second) {
        try {
            InputStream raw = new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(data)));
            BufferedReader reader = new BufferedReader(new InputStreamReader(raw, StandardCharsets.UTF_8));
            for (String line; (line = reader.readLine()) != null;) {
                String[] parts = line.split("\\t", 3);
                (parts[0].charAt(0) == 't' ? second : first).put(parts[1], parts[2]);
            }
            reader.close();
        } catch (IOException impossible) { throw new ExceptionInInitializerError(impossible); }
    }

    private ParameterNameLocalizer() {}

    public static String toZhCn(String paramName) {
        if (paramName == null || paramName.length() == 0) return "";
        String[] parts = paramName.split("_");
        if (parts.length == 0) return paramName;
        StringBuilder result = new StringBuilder();
        String prefix = normalizePrefix(parts[0]);
        result.append(prefixMap.containsKey(prefix) ? prefixMap.get(prefix) : parts[0]);
        for (int i=1;i<parts.length;i++) {
            String token = parts[i].toUpperCase(Locale.ROOT);
            result.append('.').append(tokenMap.containsKey(token) ? tokenMap.get(token) : parts[i]);
        }
        return result.toString();
    }

    private static String normalizePrefix(String token) {
        String upper = token.toUpperCase(Locale.ROOT);
        return upper.length() > 3 && Character.isDigit(upper.charAt(upper.length()-1))
                ? upper.substring(0, upper.length()-1) : upper;
    }
}
