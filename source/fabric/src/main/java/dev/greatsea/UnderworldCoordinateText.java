package dev.greatsea;

/** Cosmetic coordinate text. The animation is independent of the physical position. */
public final class UnderworldCoordinateText {
    public static final long FRAME_NANOS=50_000_000L;
    private static final java.util.regex.Pattern FORMATTING=java.util.regex.Pattern.compile("(?i)§[0-9a-fk-or]");
    private static final char[] GLYPHS="∑∆∇∴∵≋≠≈⊕⊗⊙⋈⋄⋆⌁⌖⌗⌘ΨΩЖϞ".toCharArray();
    private UnderworldCoordinateText() {}
    public static long animationFrame(long monotonicNanos){return Math.floorDiv(monotonicNanos,FRAME_NANOS);}
    public static boolean hidden(boolean nether,int bottomY,double playerY){return nether && bottomY== -320 && playerY< -128;}
    public static String actionBar(long frame){return "X: "+symbols(frame,0,7)+"   Y: "+symbols(frame,1,7)+"   Z: "+symbols(frame,2,7);}
    public static String maskLine(String line,long frame){
        int colon=line.indexOf(':');if(colon<0)return line;
        String prefix=line.substring(0,colon+1);
        String label=prefix.indexOf('§')<0?prefix:FORMATTING.matcher(prefix).replaceAll("");
        return switch(label){
            case "XYZ:" -> prefix+" "+symbols(frame,0,7)+" / "+symbols(frame,1,7)+" / "+symbols(frame,2,7);
            case "Block:","Chunk:","Section-relative:","Chunk-relative:","Targeted Block:","Targeted Fluid:" ->
                    prefix+" "+symbols(frame,label.hashCode(),21);
            default -> line;
        };
    }
    private static String symbols(long frame,long channel,int length){
        long state=frame^(channel*0x9e3779b97f4a7c15L);var result=new StringBuilder(length);
        for(int i=0;i<length;i++){
            state+=0x9e3779b97f4a7c15L;long h=state;
            h=(h^(h>>>30))*0xbf58476d1ce4e5b9L;h=(h^(h>>>27))*0x94d049bb133111ebL;h^=h>>>31;
            result.append(GLYPHS[(int)Long.remainderUnsigned(h,GLYPHS.length)]);
        }
        return result.toString();
    }
}
