package dev.greatsea;
import io.papermc.paper.plugin.bootstrap.*;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
public final class PaperBootstrap implements PluginBootstrap {
    @Override public void bootstrap(BootstrapContext context){
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY,event->{
            try{event.registrar().discoverPack(getClass().getResource("/provided-data").toURI(),"world-definitions");}
            catch(Exception e){throw new IllegalStateException("Cannot load embedded Unstable Terrain world definitions",e);}
        });
    }
}
