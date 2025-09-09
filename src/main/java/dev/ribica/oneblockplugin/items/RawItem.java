package dev.ribica.oneblockplugin.items;

import io.papermc.paper.datacomponent.DataComponentTypes;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.key.Key;
import net.minecraft.nbt.StringTag;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

@RequiredArgsConstructor
public abstract class RawItem {
    private final @Getter String id;
    private final @Getter Key itemModel;
    // TODO: implement properties, example properties: Tracked (has UUID)

    @SuppressWarnings({"UnstableApiUsage"})
    public ItemStack newItemStack() {
        // We use a music disc because it's not a part of any recipes in crafting table, furnace, brewing stand etc.
        // Use `ItemType.MUSIC_DISC_BLOCKS.createItemStack();` when it gets out of experimental api in Paper
        // TODO: set item name

        var base = ItemStack.of(Material.MUSIC_DISC_CREATOR_MUSIC_BOX);
        base.unsetData(DataComponentTypes.JUKEBOX_PLAYABLE);
        base.setData(DataComponentTypes.ITEM_MODEL, itemModel);
        base.setData(DataComponentTypes.MAX_STACK_SIZE, this.getMaxStackSize());
        System.out.println("Max stack size: " + this.getMaxStackSize() + " for " + id);
        Helpers.modifyCustomData(base, tag -> tag.put("id", StringTag.valueOf(id)));

        return base;
    }

    public int getMaxStackSize() { return 1; }
    public void onInteract(PlayerInteractEvent event, Player player, ItemStack itemStack) {}
}
