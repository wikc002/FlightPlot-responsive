package me.drton.jmavlib.log.px4;
import java.io.IOException;
import me.drton.jmavlib.log.FormatErrorException;
/** Compatibility name; all BIN consumers share the indexed implementation. */
public final class PX4LogReaderOptimized extends PX4LogReader {
    public PX4LogReaderOptimized(String file) throws IOException, FormatErrorException { super(file); }
}
