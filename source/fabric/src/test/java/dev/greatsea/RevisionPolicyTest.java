package dev.greatsea;
import org.junit.jupiter.api.Test;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;
import static org.junit.jupiter.api.Assertions.*;

class RevisionPolicyTest {
    @Test void ominousVaultBoundaryIncludesPositiveLaterRegionsAndExcludesSpokelands(){
        int boundary=GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(FarlandsTerrain.Stage.CORRUPTED);
        for(int sign:new int[]{-1,1}){
            assertFalse(ForbiddenWorldBlocks.allowsOminous(sign*(boundary-1),0));
            assertEquals(sign>0,ForbiddenWorldBlocks.allowsOminous(sign*boundary,0));
            assertFalse(ForbiddenWorldBlocks.allowsOminous(0,sign*(boundary-1)));
            assertEquals(sign>0,ForbiddenWorldBlocks.allowsOminous(0,sign*boundary));
            assertEquals(sign>0,ForbiddenWorldBlocks.allowsOminous(sign*(boundary+100000),0));
            assertEquals(sign>0,ForbiddenWorldBlocks.allowsOminous(0,sign*(boundary+100000)));
        }
        assertTrue(ForbiddenWorldBlocks.allowsOminous(20_000_000,0));
        assertTrue(ForbiddenWorldBlocks.allowsOminous(0,20_000_000));
        assertFalse(ForbiddenWorldBlocks.allowsOminous(-20_000_000,0));assertFalse(ForbiddenWorldBlocks.allowsOminous(0,-20_000_000));
        assertTrue(ForbiddenWorldBlocks.allowsOminous(boundary+16,-boundary),"Preserve the dominant positive face at a mixed corner");
        assertFalse(ForbiddenWorldBlocks.allowsOminous(boundary,-boundary-16),"The dominant negative face remains Spokelands");
    }
    @Test void trialChambersRemainInPositiveFloatingCorruptedLandsAndNeverInSpokelands(){
        var trial=RegistryKey.of(RegistryKeys.STRUCTURE,Identifier.ofVanilla("trial_chambers"));
        var village=RegistryKey.of(RegistryKeys.STRUCTURE,Identifier.ofVanilla("village_plains"));
        var monument=RegistryKey.of(RegistryKeys.STRUCTURE,Identifier.ofVanilla("monument"));
        int floating=GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(FarlandsTerrain.Stage.FLOATING)+100;
        int corrupted=GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(FarlandsTerrain.Stage.CORRUPTED)+100;
        assertTrue(FarlandsStructures.accepts(village,floating,0));
        assertTrue(FarlandsStructures.accepts(trial,floating,0));
        assertTrue(FarlandsStructures.accepts(trial,corrupted,0));
        assertFalse(FarlandsStructures.trialAllowed(0,0));
        for(var stage:FarlandsTerrain.Stage.values())for(int face=0;face<4;face++){
            int r=GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(stage)+100;
            int x=face<2?(face==0?r:-r):0,z=face<2?0:(face==2?r:-r);
            boolean positive=face==0||face==2;
            assertEquals(positive&&(stage==FarlandsTerrain.Stage.FLOATING||stage==FarlandsTerrain.Stage.CORRUPTED),FarlandsStructures.trialAllowed(x,z));
            if(!positive){assertTrue(SpokelandsTerrain.contains(x,z));assertFalse(FarlandsStructures.accepts(trial,x,z));}
        }
        assertFalse(FarlandsStructures.accepts(village,corrupted,0));
    }
    @Test void corruptedMissingChunksExpandSlightlyWithoutChangingOtherRegions(){
        long seed=4200439302357479658L;int missing=0,n=0;
        int base=GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(FarlandsTerrain.Stage.CORRUPTED);
        for(int x=base+16;x<base+16000;x+=32)for(int z=-1024;z<1024;z+=32){n++;if(FarlandsTerrain.missingChunk(seed,x,z))missing++;}
        assertTrue(missing/(double)n>.27 && missing/(double)n<.36,"gap fraction "+missing/(double)n);
        assertFalse(FarlandsTerrain.missingChunk(seed,GreatSea.FARLANDS.entryBoundary+100,0));
    }
}

