package dev.greatsea;

/** Opt-in terrain 1.26 verification against a fresh standalone Paper world. */
final class PaperTerrain126Verifier {
    static void run(PaperPlugin plugin){
        var report=new java.util.ArrayList<String>();long began=System.nanoTime();
        try{
            var world=((org.bukkit.craftbukkit.CraftWorld)org.bukkit.Bukkit.getWorlds().stream().filter(w->w.getEnvironment()==org.bukkit.World.Environment.NORMAL).findFirst().orElseThrow()).getHandle();
            Terrain126Verifier.verify(world,report);
            report.add("PASS Paper terrain 1.26 integration in "+(System.nanoTime()-began)/1e9+" seconds");
        }catch(Throwable error){report.add("FAIL "+error);plugin.getLogger().log(java.util.logging.Level.SEVERE,"Paper terrain 1.26 verification failed",error);}
        org.bukkit.Bukkit.getScheduler().runTask(plugin,()->{
            for(var level:org.bukkit.Bukkit.getWorlds())for(var entity:level.getEntities())if(((org.bukkit.craftbukkit.entity.CraftEntity)entity).getHandle() instanceof net.minecraft.world.entity.Mob)
                report.add("FAIL terrain 1.26 spawned a mob: "+entity.getType());
            try{java.nio.file.Files.write(java.nio.file.Path.of("paper-terrain-126-verification.txt"),report);}catch(Exception error){throw new RuntimeException(error);}
            plugin.getLogger().info(String.join("\n",report));org.bukkit.Bukkit.shutdown();
        });
    }
}

