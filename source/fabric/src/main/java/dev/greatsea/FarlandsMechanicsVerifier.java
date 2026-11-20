package dev.greatsea;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import java.util.List;
import java.util.UUID;

/** Opt-in checks of real inventory serialization and item/entity hooks in the same Overworld. */
public final class FarlandsMechanicsVerifier {
    private FarlandsMechanicsVerifier() {}
    private static void check(boolean condition,String message) { if (!condition) throw new AssertionError(message); }
    private static final class OfflinePlayer extends ServerPlayerEntity {
        private ItemCooldownManager verificationCooldowns;
        private int closedMenus;
        private int portalEntries;
        OfflinePlayer(MinecraftServer server,ServerWorld world) {
            super(server,world,new GameProfile(UUID.randomUUID(),"FarlandsTest"),SyncedClientOptions.createDefault());
        }
        @Override public void sendAbilitiesUpdate() {} // The verifier has no network connection.
        @Override public void tryUsePortal(net.minecraft.block.Portal portal,net.minecraft.util.math.BlockPos pos) { portalEntries++; }
        @Override public void closeHandledScreen() { closedMenus++;currentScreenHandler=playerScreenHandler; }
        @Override public ItemCooldownManager getItemCooldownManager() {
            if (verificationCooldowns==null) verificationCooldowns=new ItemCooldownManager();
            return verificationCooldowns;
        }
    }
    public static void verify(MinecraftServer server,List<String> report) {
        var world=server.getOverworld();
        check(FarlandsWorld.supported(world),"Farlands mechanics verification requires an enabled Unstable Overworld");
        double boundary=GreatSea.FARLANDS.entryBoundary;
        var player=new OfflinePlayer(server,world);
        var portal=net.minecraft.block.Blocks.NETHER_PORTAL.getDefaultState();
        for(int[] face:new int[][]{{(int)boundary+32,48},{-(int)boundary-32,48},{48,(int)boundary+32},{48,-(int)boundary-32}}) {
            var at=new net.minecraft.util.math.BlockPos(face[0],200,face[1]);player.setPosition(at.getX()+.5,200,at.getZ()+.5);
            int before=player.portalEntries;
            portal.onEntityCollision(world,at,player,null,false);
            check(player.portalEntries==before,"Already-lit Farlands portal accepted collision at "+at);
            check(((net.minecraft.block.NetherPortalBlock)portal.getBlock()).createTeleportTarget(world,player,at)==null,"Farlands portal produced a teleport target");
            boolean enabled=GreatSea.FARLANDS.enabled;
            try {GreatSea.FARLANDS.enabled=false;portal.onEntityCollision(world,at,player,null,false);}
            finally {GreatSea.FARLANDS.enabled=enabled;}
            check(player.portalEntries==before+1,"Disabled Farlands intercepted normal portal behavior");
        }
        player.setPosition(0,100,0);int normalEntries=player.portalEntries;
        portal.onEntityCollision(world,new net.minecraft.util.math.BlockPos(0,100,0),player,null,false);
        check(player.portalEntries==normalEntries+1,"Mainland portal entry was blocked");
        report.add("Already-lit Nether portals: real portal block collision and target creation rejected on all four Farlands faces; disabled-region and mainland collision controls pass.");
        player.setPosition(0,100,0);
        var normal=player.getEnderChestInventory();
        normal.setStack(0,new ItemStack(Items.DIAMOND,13));
        var far=((FarlandsData)(Object)player).greatsea$farlandsEnderChest();
        check(far.isEmpty() && far!=normal,"First Farlands chest was not empty and separate");
        player.setPosition(boundary+8,100,0);
        check(player.getEntityWorld()==world && player.getEnderChestInventory()==far,"Farlands inventory did not switch inside the same Overworld");
        far.setStack(0,new ItemStack(Items.EMERALD,7));
        far.setStack(26,new ItemStack(Items.GOLDEN_APPLE,3));
        var coordinateOrigin=new FarlandsCoordinates.Origin((long)boundary,-37);
        ((FarlandsCoordinateData)(Object)player).greatsea$coordinateOrigin(coordinateOrigin);
        player.currentScreenHandler=GenericContainerScreenHandler.createGeneric9x3(5,player.getInventory(),normal);
        check(FarlandsMechanics.closeStaleEnderMenu(player) && player.closedMenus==1 && player.currentScreenHandler==player.playerScreenHandler,
                "A normal-region ender chest menu remained open after entering Farlands");
        player.currentScreenHandler=GenericContainerScreenHandler.createGeneric9x3(6,player.getInventory(),far);
        check(!FarlandsMechanics.closeStaleEnderMenu(player) && !FarlandsMechanics.closeRegionMenu(player,true,true) && player.closedMenus==1,"An unchanged Farlands context closed a valid menu");
        player.setPosition(0,100,0);
        check(FarlandsMechanics.closeStaleEnderMenu(player) && player.closedMenus==2 && normal.getStack(0).getCount()==13 && far.getStack(0).getCount()==7,
                "Returning to the normal region did not close the old menu while preserving both inventories");
        player.setPosition(boundary+8,100,0);
        check(player.getStackReference(net.minecraft.entity.player.PlayerEntity.ENDER_SLOT_OFFSET).get().isOf(Items.EMERALD),"Farlands ender-slot commands target the normal inventory");
        var save=NbtWriteView.create(ErrorReporter.EMPTY,server.getRegistryManager());player.writeData(save);
        var loaded=new OfflinePlayer(server,world);
        loaded.readData(NbtReadView.create(ErrorReporter.EMPTY,server.getRegistryManager(),save.getNbt()));
        check(coordinateOrigin.equals(((FarlandsCoordinateData)(Object)loaded).greatsea$coordinateOrigin()),"Farlands display origin did not survive actual player NBT save/reload");
        check(loaded.getEnderChestInventory().getStack(0).isOf(Items.EMERALD) && loaded.getEnderChestInventory().getStack(0).getCount()==7 &&
                loaded.getEnderChestInventory().getStack(26).getCount()==3,"Farlands chest did not survive save/reload while inside Farlands");
        loaded.setPosition(0,100,0);
        check(loaded.getEnderChestInventory().getStack(0).isOf(Items.DIAMOND) && loaded.getEnderChestInventory().getStack(0).getCount()==13,
                "Normal EnderItems was overwritten by a Farlands save");
        var secondSave=NbtWriteView.create(ErrorReporter.EMPTY,server.getRegistryManager());loaded.writeData(secondSave);
        var reloaded=new OfflinePlayer(server,world);reloaded.readData(NbtReadView.create(ErrorReporter.EMPTY,server.getRegistryManager(),secondSave.getNbt()));
        var respawn=new OfflinePlayer(server,world);respawn.copyFrom(reloaded,false);
        check(coordinateOrigin.equals(((FarlandsCoordinateData)(Object)respawn).greatsea$coordinateOrigin()),"Farlands display origin did not survive actual respawn copying");
        check(respawn.getEnderChestInventory().getStack(0).isOf(Items.DIAMOND),"Normal chest did not survive death/respawn copying");
        respawn.setPosition(boundary+8,100,0);
        check(respawn.getEnderChestInventory().getStack(0).isOf(Items.EMERALD) && respawn.getEnderChestInventory().getStack(26).getCount()==3,
                "Farlands chest did not survive normal-region save and death/respawn copying");
        for (double[] point:new double[][]{{boundary+8,0},{-boundary-8,0},{0,boundary+8},{0,-boundary-8}}) {
            player.setPosition(point[0],100,point[1]);
            check(player.getEnderChestInventory()==far,"Farlands chest failed on a boundary face");
        }
        player.setPosition(boundary-1,100,0);
        check(player.getEnderChestInventory()==normal && normal.getStack(0).getCount()==13,"Normal chest did not return after leaving Farlands");
        var nether=server.getWorld(World.NETHER);
        if (nether!=null) {
            player.setServerWorld(nether);player.setPosition(boundary+100000,100,0);
            check(!FarlandsWorld.activePlayer(player) && player.getEnderChestInventory()==normal && !FarlandsMechanics.fireworksDisabled(player),"Farlands mechanics affected the Nether");
            player.setServerWorld(world);
        }
        report.add("Farlands chest persistence: normal diamonds and Farlands emeralds/apples remain separate through saves inside/outside, reload and actual death-copy hook; old menus close in both crossing directions; all four faces and Nether controls pass in one Overworld.");
        report.add("Farlands coordinate origin: actual player NBT save/reload and respawn-copy hook preserve the display offset; physical coordinates and Overworld identity remain unchanged.");

        double deep=boundary+GreatSea.FARLANDS.fireworkDisableDepth+8;
        player.setPosition(deep,world.getTopYInclusive()-1,0);player.startGliding();
        check(FarlandsMechanics.fireworksDisabled(player),"Deep Farlands did not disable rocket boosting");
        var rockets=new ItemStack(Items.FIREWORK_ROCKET,2);player.setStackInHand(Hand.MAIN_HAND,rockets);
        check(rockets.use(world,player,Hand.MAIN_HAND)==ActionResult.FAIL && rockets.getCount()==2,"Disabled rocket use consumed a rocket or remained usable");
        var velocity=new Vec3d(.04,-.03,.08);player.setVelocity(velocity);
        var active=new FireworkRocketEntity(world,new ItemStack(Items.FIREWORK_ROCKET),player);active.tick();
        check(player.getVelocity().equals(velocity),"An already-active rocket accelerated the player in deep Farlands");
        var windCharges=new ItemStack(Items.WIND_CHARGE,2);player.setStackInHand(Hand.MAIN_HAND,windCharges);
        int expectedWindCharges=player.getAbilities().creativeMode ? 2 : 1;
        check(windCharges.use(world,player,Hand.MAIN_HAND).isAccepted() && windCharges.getCount()==expectedWindCharges,"Wind charges were disabled with fireworks");
        player.setPosition(boundary-1,world.getTopYInclusive()-1,0);
        check(!FarlandsMechanics.fireworksDisabled(player),"Rocket boosting remained disabled after leaving Farlands");
        if (GreatSea.FARLANDS.fireworkDisableDepth>0) {
            player.setPosition(boundary+GreatSea.FARLANDS.fireworkDisableDepth-.5,world.getTopYInclusive()-1,0);
            check(!FarlandsMechanics.fireworksDisabled(player),"Rocket boost was disabled before the configured depth");
        }
        report.add("Farlands flight controls: real gliding rocket use rejected without consumption, active-rocket velocity unchanged, real wind-charge use accepted; shallow/mainland scope restored.");
    }
}
