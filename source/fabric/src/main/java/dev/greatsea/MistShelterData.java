package dev.greatsea;
import com.mojang.serialization.Codec;
import java.util.*;
import java.util.stream.LongStream;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

/** Remember player-built stone roofs across restarts, without marking natural cave stone. */
public final class MistShelterData extends PersistentState {
 public static final PersistentStateType<MistShelterData> TYPE=new PersistentStateType<>("unstableterrain_mist_shelters",MistShelterData::new,Codec.LONG_STREAM.fieldOf("blocks").codec().xmap(MistShelterData::decode,MistShelterData::encode),DataFixTypes.LEVEL);
 private final Set<Long> blocks=new HashSet<>();
 private static MistShelterData decode(LongStream stream){var d=new MistShelterData();stream.forEach(d.blocks::add);return d;}
 private synchronized LongStream encode(){return LongStream.of(blocks.stream().mapToLong(Long::longValue).toArray());}
 public synchronized boolean contains(BlockPos p){return blocks.contains(p.asLong());}
 private synchronized void add(BlockPos p){if(blocks.add(p.asLong()))markDirty();}
 public static MistShelterData get(ServerWorld world){return world.getPersistentStateManager().getOrCreate(TYPE);}
 public static void placed(ServerWorld world,BlockPos pos){if(MistRegion.active(world)&&MistRegion.contains(pos.getX(),pos.getZ()))get(world).add(pos);}
}
