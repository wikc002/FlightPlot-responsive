package me.drton.flightplot;

import java.util.Comparator;

/** Numeric runs compare as numbers, without integer-size limits. */
public final class NaturalFieldOrder implements Comparator<String> {
    public static final NaturalFieldOrder INSTANCE = new NaturalFieldOrder();
    private NaturalFieldOrder() {}

    public int compare(String a, String b) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char x = a.charAt(i), y = b.charAt(j);
            if (digit(x) && digit(y)) {
                int ae = i, be = j;
                while (ae < a.length() && digit(a.charAt(ae))) ae++;
                while (be < b.length() && digit(b.charAt(be))) be++;
                int as = i, bs = j;
                while (as < ae - 1 && a.charAt(as) == '0') as++;
                while (bs < be - 1 && b.charAt(bs) == '0') bs++;
                int diff = (ae - as) - (be - bs);
                if (diff != 0) return diff;
                for (int k = 0; k < ae - as; k++) {
                    diff = a.charAt(as + k) - b.charAt(bs + k);
                    if (diff != 0) return diff;
                }
                i = ae; j = be;
            } else {
                int diff = Character.toLowerCase(x) - Character.toLowerCase(y);
                if (diff != 0) return diff;
                i++; j++;
            }
        }
        int diff = (a.length() - i) - (b.length() - j);
        return diff != 0 ? diff : a.compareTo(b);
    }

    private static boolean digit(char c) { return c >= '0' && c <= '9'; }
}
