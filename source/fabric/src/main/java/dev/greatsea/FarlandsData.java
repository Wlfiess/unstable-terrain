package dev.greatsea;

import net.minecraft.inventory.EnderChestInventory;

/** The independently saved Farlands chest never replaces the normal chest field. */
public interface FarlandsData {
    EnderChestInventory greatsea$farlandsEnderChest();
    void greatsea$copyFarlandsData(FarlandsData source);
}
