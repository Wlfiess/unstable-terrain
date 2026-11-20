package dev.greatsea;
import com.google.gson.*;
import java.nio.file.*;

/** Development-only region metadata for a coordinate-accurate top-down world overview. */
final class WorldOverviewExporter {
    static void export(long seed) throws Exception {
        var json=new JsonObject();json.addProperty("version","1.22.0");json.addProperty("seed",seed);
        json.addProperty("worldBorderHalfSize",29999984);json.addProperty("farlandsEntry",GreatSea.FARLANDS.entryBoundary);
        json.addProperty("amplifiedStart",AmplifiedTerrain.START);
        json.addProperty("amplifiedEnd",Math.min(AmplifiedTerrain.END,GreatSea.CONFIG.innerBoundary-16));
        json.addProperty("seaInner",GreatSea.CONFIG.innerBoundary);json.addProperty("seaOuter",GreatSea.CONFIG.outerBoundary);
        json.addProperty("seaCornerRadius",SeaTerrain.cornerRadius(GreatSea.CONFIG));
        json.addProperty("beachWidth",GreatSea.CONFIG.beachWidth);json.addProperty("seaLevel",GreatSea.CONFIG.seaLevel);
        json.addProperty("underworldTop",UnstableNether.UNDERWORLD_TOP);
        var stages=new JsonArray();
        for(var stage:FarlandsTerrain.Stage.values()){
            var row=new JsonObject();row.addProperty("name",stage.name());row.addProperty("start",GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(stage));
            row.addProperty("end",GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.end(stage));
            row.addProperty("width",GreatSea.FARLANDS.stageWidths[stage.ordinal()]);stages.add(row);
        }
        json.add("stages",stages);
        var grid=new JsonArray();int start=GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(FarlandsTerrain.Stage.SKYGRID);
        int width=GreatSea.FARLANDS.stageWidths[FarlandsTerrain.Stage.SKYGRID.ordinal()];
        for(int level=0;level<5;level++){
            int depth=start-GreatSea.FARLANDS.entryBoundary+(int)(width*.18*level);
            while(FarlandsTerrain.skygridSpacing(depth)==0)depth++;
            var row=new JsonObject();row.addProperty("spacing",FarlandsTerrain.skygridSpacing(depth));row.addProperty("samplePosition",GreatSea.FARLANDS.entryBoundary+depth);
            row.addProperty("retention",FarlandsTerrain.skygridRetention(depth));grid.add(row);
        }
        json.add("skygridSamples",grid);json.addProperty("skygridEntranceEmptyGap",5);json.addProperty("skygridVoidStart",start+(int)Math.ceil(width*.90));
        Files.writeString(Path.of("world-overview.json"),new GsonBuilder().setPrettyPrinting().create().toJson(json));
    }
}
