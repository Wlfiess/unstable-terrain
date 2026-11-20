package dev.greatsea;

/** Explicit, opt-in checks in a disposable local server. Never runs in normal use. */
final class PaperRelief1390Verifier {
    static void run(PaperPlugin plugin) {
        var report=new java.util.ArrayList<String>();
        try {
            var world=((org.bukkit.craftbukkit.CraftWorld)org.bukkit.Bukkit.getWorlds().stream().filter(w->w.getEnvironment()==org.bukkit.World.Environment.NORMAL).findFirst().orElseThrow()).getHandle();
            long seed=world.getSeed();int border=GreatSea.FARLANDS.entryBoundary;
            for(int kind=0;kind<3;kind++) {
                int center=kind==0?SpokelandsTerrain.start(SpokelandsTerrain.Region.FLOWER_HILLS)+9000:kind==1?SpokelandsTerrain.start(SpokelandsTerrain.Region.SAND_LANDS)+9000:FarlandsTerrain.start(FarlandsTerrain.Stage.SAND_DUNES)+7000;
                int checks=0,flowers=0;var pos=new net.minecraft.core.BlockPos.MutableBlockPos();
                for(int d=-16;d<16;d++)for(int t=-16;t<16;t++) {
                    int x=kind==2?border+center+d:450+t,z=kind==2?450+t:-border-center-d;
                    int ground=kind==2?FarlandsRelief.ridge(seed,center+d,450+t,true):SpokelandsTerrain.ground(seed,x,z);
                    for(int y=ground-3;y<=ground;y++) {
                        var expected=FarlandsGeneration.state(seed,x,y,z);var actual=world.getBlockState(pos.set(x,y,z));
                        if(!actual.equals(expected))throw new AssertionError("Relief surface differs at "+pos+": "+actual+" expected "+expected);
                        checks++;
                    }
                    var above=world.getBlockState(pos.set(x,ground+1,z));
                    if(above.is(net.minecraft.world.level.block.Blocks.OXEYE_DAISY)||above.is(net.minecraft.world.level.block.Blocks.ALLIUM)||above.is(net.minecraft.world.level.block.Blocks.DANDELION))flowers++;
                }
                if(kind==0&&flowers<800)throw new AssertionError("Flower meadow decoration missing: "+flowers);
                report.add("PASS native "+new String[]{"Flower Hills","Red Sand Lands","Desert Dunes"}[kind]+": "+checks+" surface voxels, flowers="+flowers);
            }
            var commands=new StringBuilder();PaperCommandVerifier.verify(world,commands);report.add(commands.toString());
        } catch(Throwable error) {
            report.add("FAIL "+error);plugin.getLogger().log(java.util.logging.Level.SEVERE,"Relief verification failed",error);
        }
        try {java.nio.file.Files.write(java.nio.file.Path.of("paper-relief-1390-verification.txt"),report);}catch(Exception error){throw new RuntimeException(error);}
        plugin.getLogger().info(String.join("\n",report));org.bukkit.Bukkit.shutdown();
    }
}
