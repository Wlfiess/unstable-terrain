package dev.greatsea;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

public final class SeaConfig {
    public int configVersion = 12;
    public boolean enabled = true;
    public int innerBoundary = 250_000;
    public int outerBoundary = 3_000_000;
    /** All widths are horizontal blocks, measured inside the sea ring. */
    public int beachWidth = 550;
    public int coastWaviness = 48;
    public int underwaterSlopeWidth = 500;
    public int seaLevel = 200;
    public int beachPeakY = 210;
    public int mainlandJoinY = 30;
    public int mainlandPreserveTopY = 70;
    public int minFloorY = -30;
    public int maxFloorY = -10;
    /** Stalk height above each local ocean bed, rather than an absolute kelp ceiling. */
    public int kelpMaxHeight = 18;
    public int coldKelpMaxHeight = 31;
    public int coldOceanInset = 20_000;
    public int palmSpacing = 194;
    public double palmChance = .9284936814413263;
    public int deadBushSpacing = 28;
    public double deadBushChance = .72;

    public void validate() {
        if (innerBoundary < 1024 || outerBoundary > 29_000_000 ||
                beachWidth < 128 || underwaterSlopeWidth < 128 || coastWaviness<0 || coastWaviness>beachWidth/3 ||
                outerBoundary - innerBoundary <= 2L * (beachWidth + underwaterSlopeWidth) ||
                minFloorY < -48 || maxFloorY < minFloorY || maxFloorY >= seaLevel - 32 ||
                seaLevel < 80 || seaLevel > 256 || beachPeakY<seaLevel || beachPeakY>260 || mainlandJoinY<0 || mainlandJoinY>seaLevel ||
                mainlandPreserveTopY<mainlandJoinY || mainlandPreserveTopY>seaLevel || palmChance<0 || palmChance>1 ||
                deadBushChance<0 || deadBushChance>1 ||
                kelpMaxHeight<2 || kelpMaxHeight>64 || coldKelpMaxHeight<2 || coldKelpMaxHeight>64 ||
                coldOceanInset<beachWidth || palmSpacing < 40 || deadBushSpacing < 8) {
            throw new IllegalArgumentException("Invalid Great Sea configuration; check boundaries, widths, heights and spacing.");
        }
    }
    public static SeaConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("greatsea.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            SeaConfig config;
            if (Files.exists(path)) {
                String content=Files.readString(path);
                JsonObject json=JsonParser.parseString(content).getAsJsonObject();
                config = parse(content);
                if (!json.has("configVersion") || json.get("configVersion").getAsInt()<12)
                    Files.writeString(path,gson.toJson(config));
            } else {
                config = new SeaConfig();
                Files.createDirectories(path.getParent());
                Files.writeString(path, gson.toJson(config));
            }
            config.validate();
            return config;
        } catch (IOException e) { throw new IllegalStateException("Cannot read/write " + path, e); }
    }
    /** Upgrade the previous release's defaults so replacing the JAR actually changes generation. */
    static SeaConfig parse(String content) {
        JsonObject json=JsonParser.parseString(content).getAsJsonObject();
        SeaConfig c=new Gson().fromJson(json,SeaConfig.class);
        int version=json.has("configVersion") ? json.get("configVersion").getAsInt() : 0;
        if (version<3) {
            if (c.beachWidth==2048) c.beachWidth=550;
            if (c.underwaterSlopeWidth==2048 || c.underwaterSlopeWidth==550) c.underwaterSlopeWidth=500;
            if (c.palmSpacing==96) c.palmSpacing=224;
            if (c.minFloorY==0 && c.maxFloorY==30) { c.minFloorY=-30; c.maxFloorY=-10; }
        }
        if (version<4 && c.underwaterSlopeWidth==875) c.underwaterSlopeWidth=500;
        if (version<8) {
            if (c.coldOceanInset==1_000) c.coldOceanInset=20_000;
            if (c.kelpMaxHeight==24) c.kelpMaxHeight=18;
            if (c.coldKelpMaxHeight==32) c.coldKelpMaxHeight=26;
            if (Math.abs(c.palmChance-.7475)<1e-12) c.palmChance=.859625;
        }
        if (version<9 && c.coldKelpMaxHeight==26) c.coldKelpMaxHeight=31;
        if (version<10) {
            if (c.palmSpacing==224) c.palmSpacing=204;
            if (Math.abs(c.palmChance-.859625)<1e-12) c.palmChance=.8555680963010203;
        }
        if (version<11) {
            if (c.palmSpacing==204) c.palmSpacing=194;
            if (Math.abs(c.palmChance-.8555680963010203)<1e-12) c.palmChance=.9284936814413263;
            if (Math.abs(c.deadBushChance-.6)<1e-12) c.deadBushChance=.72;
        }
        c.configVersion=12;
        c.validate();
        return c;
    }
}
