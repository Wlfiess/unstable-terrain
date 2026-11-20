package dev.greatsea;

import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Native rocket tick with the same single velocity guard used by the Fabric mod.
 * The tick instructions are read from the running Paper server, never shipped as
 * Minecraft code. No agent, JVM flag, client mod or external plugin is required.
 */
public final class RocketHooks {
    private static Constructor<? extends FireworkRocketEntity> constructor;
    private static MethodHandle projectileTick;
    private static final Map<FireworkRocketEntity,Boolean> CLIENT_STATE=new WeakHashMap<>();
    public static synchronized void initialize(){
        if(constructor!=null)return;
        try{
            var lookup=MethodHandles.privateLookupIn(FireworkRocketEntity.class,MethodHandles.lookup());
            projectileTick=lookup.findSpecial(Projectile.class,"tick",MethodType.methodType(void.class),FireworkRocketEntity.class);
            var original=new ClassNode();
            try(var input=FireworkRocketEntity.class.getResourceAsStream("/net/minecraft/world/entity/projectile/FireworkRocketEntity.class")){new ClassReader(input).accept(original,0);}
            String parent=original.name,name="dev/greatsea/ControlledFirework";
            var generated=new ClassNode();generated.version=Opcodes.V21;generated.access=Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL;
            generated.name=name;generated.superName=parent;
            var init=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V",null,null);
            init.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));init.instructions.add(new VarInsnNode(Opcodes.ALOAD,1));init.instructions.add(new VarInsnNode(Opcodes.ALOAD,2));
            init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,parent,"<init>",init.desc,false));init.instructions.add(new InsnNode(Opcodes.RETURN));generated.methods.add(init);
            var privateNames=new HashSet<String>();for(var method:original.methods)if((method.access&Opcodes.ACC_PRIVATE)!=0 && (method.access&Opcodes.ACC_STATIC)==0)privateNames.add(method.name);
            for(var method:original.methods){
                if(!method.name.equals("tick") && !privateNames.contains(method.name))continue;
                for(var insn=method.instructions.getFirst();insn!=null;){
                    var next=insn.getNext();
                    if(insn instanceof MethodInsnNode call){
                        if(call.owner.equals(parent) && privateNames.contains(call.name))call.owner=name;
                        else if(call.getOpcode()==Opcodes.INVOKESPECIAL && call.name.equals("tick") && call.owner.equals("net/minecraft/world/entity/projectile/Projectile")){
                            call.setOpcode(Opcodes.INVOKESTATIC);call.owner="dev/greatsea/RocketHooks";call.name="projectileTick";call.desc="(Lnet/minecraft/world/entity/projectile/FireworkRocketEntity;)V";
                        }else if(call.name.equals("setDeltaMovement") && call.owner.equals("net/minecraft/world/entity/LivingEntity") && call.desc.equals("(Lnet/minecraft/world/phys/Vec3;)V")){
                            call.setOpcode(Opcodes.INVOKESTATIC);call.owner="dev/greatsea/RocketHooks";call.name="boost";call.desc="(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/phys/Vec3;)V";
                        }
                    }else if(insn instanceof InvokeDynamicInsnNode dynamic){
                        dynamic.desc=dynamic.desc.replace("L"+parent+";","L"+name+";");
                        for(int i=0;i<dynamic.bsmArgs.length;i++)if(dynamic.bsmArgs[i] instanceof Handle h && h.getOwner().equals(parent) && privateNames.contains(h.getName()))dynamic.bsmArgs[i]=new Handle(h.getTag(),name,h.getName(),h.getDesc(),h.isInterface());
                    }
                    if(method.name.equals("tick") && insn.getOpcode()==Opcodes.RETURN){
                        var end=new InsnList();end.add(new VarInsnNode(Opcodes.ALOAD,0));end.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/greatsea/RocketHooks","clientUpdate","(Lnet/minecraft/world/entity/projectile/FireworkRocketEntity;)V",false));method.instructions.insertBefore(insn,end);
                    }
                    insn=next;
                }
                generated.methods.add(method);
            }
            var writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS){
                @Override protected String getCommonSuperClass(String a,String b){return super.getCommonSuperClass(a.equals(name)?parent:a,b.equals(name)?parent:b);}
            };
            generated.accept(writer);var type=MethodHandles.lookup().defineClass(writer.toByteArray()).asSubclass(FireworkRocketEntity.class);
            constructor=type.getConstructor(EntityType.class,Level.class);
        }catch(Throwable error){throw new IllegalStateException("Paper native rocket adapter is incompatible",error);}
    }
    public static void projectileTick(FireworkRocketEntity rocket){try{projectileTick.invokeExact(rocket);}catch(Throwable e){throw new IllegalStateException(e);}}
    public static void boost(LivingEntity entity,Vec3 proposed){
        if(entity instanceof ServerPlayer player && PaperPlugin.instance.disabledBoost(player.getBukkitEntity()))return;
        entity.setDeltaMovement(proposed);
    }
    public static FireworkRocketEntity copy(FireworkRocketEntity source){
        initialize();
        try{
            var result=constructor.newInstance(EntityType.FIREWORK_ROCKET,source.level());
            var output=net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,source.registryAccess());
            source.saveWithoutId(output);
            result.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,source.registryAccess(),output.buildResult()));
            result.setId(source.getId());result.setUUID(source.getUUID());result.setOwner(source.getOwner());
            result.attachedToEntity=source.attachedToEntity;result.spawningEntity=source.spawningEntity;
            result.getEntityData().set(FireworkRocketEntity.DATA_ATTACHED_TO_TARGET,source.getEntityData().get(FireworkRocketEntity.DATA_ATTACHED_TO_TARGET));
            var random=Entity.class.getDeclaredField("random");random.setAccessible(true);random.set(result,random.get(source));
            return result;
        }catch(Exception error){throw new IllegalStateException("Cannot adapt attached rocket",error);}
    }
    public static void clientUpdate(FireworkRocketEntity rocket){
        if(!(rocket.attachedToEntity instanceof ServerPlayer player) || player.connection==null)return;
        boolean blocked=PaperPlugin.instance.disabledBoost(player.getBukkitEntity());
        Boolean previous=CLIENT_STATE.put(rocket,blocked);
        if(previous==null || previous!=blocked){
            var attached=blocked?OptionalInt.empty():rocket.getEntityData().get(FireworkRocketEntity.DATA_ATTACHED_TO_TARGET);
            player.connection.send(new ClientboundSetEntityDataPacket(rocket.getId(),List.of(
                SynchedEntityData.DataValue.create(FireworkRocketEntity.DATA_ATTACHED_TO_TARGET,attached),
                SynchedEntityData.DataValue.create(FireworkRocketEntity.DATA_SHOT_AT_ANGLE,blocked||rocket.isShotAtAngle()))));
        }
        if(blocked && !rocket.isRemoved())player.connection.send(ClientboundEntityPositionSyncPacket.of(rocket));
    }
}
