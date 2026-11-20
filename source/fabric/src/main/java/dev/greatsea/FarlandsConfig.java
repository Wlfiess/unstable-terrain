package dev.greatsea;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

/** Distances are adjustable defaults; the reference PDF specifies order, not widths. */
public final class FarlandsConfig {
    public boolean enabled=true;
    public int entryBoundary=12_550_821;
    public int fireworkDisableDepth=10_000;
    public int[] stageWidths={12000,10000,12000,10000,12000,20976,1024,12000,14000,14000,14000,18000,1024,18000,36000,2500,48000};
    public int[] spokelandsWidths={12000,18000,28000,18000,18000,24000,22000,18000,24000};
    public void validate() {
        if(stageWidths.length==15) {
            int[] migrated=new int[16];
            System.arraycopy(stageWidths,0,migrated,0,5);
            migrated[5]=stageWidths[3];
            System.arraycopy(stageWidths,5,migrated,6,10);
            if(migrated[14]==48000)migrated[14]=2500;
            stageWidths=migrated;
        }
        if(stageWidths.length==16) {
            int[] migrated=new int[17];
            System.arraycopy(stageWidths,0,migrated,0,12);migrated[12]=1024;
            System.arraycopy(stageWidths,12,migrated,13,4);stageWidths=migrated;
        }
        if(stageWidths.length==17&&stageWidths[5]==10000&&stageWidths[6]==12000){stageWidths[5]=20976;stageWidths[6]=1024;}
        if (entryBoundary<1024 || entryBoundary>29_000_000 || fireworkDisableDepth<0 || stageWidths.length!=17)
            throw new IllegalArgumentException("Invalid Farlands entry, firework threshold or stage count");
        long total=0;
        for (int width:stageWidths) {
            if (width<256) throw new IllegalArgumentException("Farlands stages must be at least 256 blocks wide");
            total+=width;
        }
        if (total>28_000_000) throw new IllegalArgumentException("Farlands stages exceed the world border");
        if(spokelandsWidths==null || spokelandsWidths.length!=9)throw new IllegalArgumentException("Spokelands requires nine region widths");
        long spokeTotal=0;for(int width:spokelandsWidths){if(width<256)throw new IllegalArgumentException("Spokelands regions must be at least 256 blocks wide");spokeTotal+=width;}
        if(spokeTotal>28_000_000)throw new IllegalArgumentException("Spokelands regions exceed the world border");
    }
    public static FarlandsConfig load() {
        Path path=FabricLoader.getInstance().getConfigDir().resolve("unstableterrain-farlands.json");
        Gson gson=new GsonBuilder().setPrettyPrinting().create();
        try {
            FarlandsConfig config=Files.exists(path) ? gson.fromJson(Files.readString(path),FarlandsConfig.class) : new FarlandsConfig();
            int oldCount=config.stageWidths.length;
            config.validate();
            if (!Files.exists(path) || oldCount!=config.stageWidths.length) { Files.createDirectories(path.getParent());Files.writeString(path,gson.toJson(config)); }
            return config;
        } catch (IOException e) { throw new IllegalStateException("Cannot read/write "+path,e); }
    }
}
