package dev.greatsea;

import java.util.Locale;
import java.util.regex.Pattern;

/** Pure formatting helper: Y, chunk files and every non-position diagnostic stay native. */
public final class FarlandsCoordinateText {
    private static final String NUMBER="([+-]?\\d+(?:\\.\\d+)?)";
    private static final Pattern XYZ=Pattern.compile("^XYZ: "+NUMBER+" / "+NUMBER+" / "+NUMBER+"$");
    private static final Pattern BLOCK=Pattern.compile("^Block: ([+-]?\\d+) ([+-]?\\d+) ([+-]?\\d+)$");
    private FarlandsCoordinateText() {}
    public static String relative(String line,long originX,long originZ) {
        var xyz=XYZ.matcher(line);
        if(xyz.matches())return String.format(Locale.ROOT,"XYZ: %.3f / %s / %.3f",
                Double.parseDouble(xyz.group(1))-originX,xyz.group(2),Double.parseDouble(xyz.group(3))-originZ);
        var block=BLOCK.matcher(line);
        if(block.matches())return "Block: "+(Long.parseLong(block.group(1))-originX)+" "+block.group(2)+" "+
                (Long.parseLong(block.group(3))-originZ);
        return line;
    }
}
