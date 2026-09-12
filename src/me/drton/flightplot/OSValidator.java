package me.drton.flightplot;

/**
 * User: ton Date: 22.06.13 Time: 11:40
 */
public final class OSValidator {

    private static final String OS = System.getProperty("os.name").toLowerCase();

    private OSValidator() {}

    public static boolean isMac() {
        return OS.contains("mac");
    }
}
