package dev.greatsea;

/** Opt-in checks against a disposable native Fabric world. */
final class SpokelandsVerification {
    static void run(net.minecraft.server.MinecraftServer server){
        var report=new java.util.ArrayList<String>();long began=System.nanoTime();
        try{
            SpokelandsVerifier.verify(server.getOverworld(),report);
            SpokelandsCommandVerifier.verify(server.getOverworld(),report);
            if(Boolean.getBoolean("unstableterrain.spokelandsPreview"))SpokelandsPerspective.render(server.getOverworld(),report);
            report.add("PASS Fabric Spokelands integration in "+(System.nanoTime()-began)/1e9+" seconds");
        }catch(Throwable error){report.add("FAIL "+error);GreatSea.LOGGER.error("Spokelands integration verification",error);}
        try{java.nio.file.Files.write(java.nio.file.Path.of("spokelands-verification.txt"),report);}catch(Exception error){throw new RuntimeException(error);}
        GreatSea.LOGGER.info(String.join("\n",report));server.stop(false);
    }
}
