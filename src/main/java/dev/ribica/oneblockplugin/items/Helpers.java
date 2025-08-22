package dev.ribica.oneblockplugin.items;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import org.bukkit.craftbukkit.inventory.CraftItemStack;

import java.util.function.Consumer;

public class Helpers {
    public static CustomData getNmsCustomDataComponent(net.minecraft.world.item.ItemStack nmsItemStack) {
        var customData = nmsItemStack.get(DataComponents.CUSTOM_DATA);
        return customData != null ? customData : CustomData.of(new CompoundTag());
    }

    public static CustomData getNmsCustomDataComponent(org.bukkit.inventory.ItemStack itemStack) {
        return getNmsCustomDataComponent(toNmsItemStack(itemStack));
    }

    public static void modifyCustomData(org.bukkit.inventory.ItemStack itemStack, Consumer<CompoundTag> consumer) {
        var nmsItemStack = toNmsItemStack(itemStack);
        var customData = getNmsCustomDataComponent(nmsItemStack);
        nmsItemStack.set(DataComponents.CUSTOM_DATA, customData.update(consumer));
    }

    public static net.minecraft.world.item.ItemStack toNmsItemStack(org.bukkit.inventory.ItemStack itemStack) {
        return ((CraftItemStack) itemStack).handle;
    }
}
