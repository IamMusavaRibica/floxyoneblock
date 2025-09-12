package dev.ribica.oneblockplugin.items;

import dev.ribica.oneblockplugin.OneBlockPlugin;
import lombok.RequiredArgsConstructor;

import java.util.*;

@RequiredArgsConstructor
public class ItemRegistry {
    private final OneBlockPlugin plugin;
    private final Map<String, RawItem> itemMap = new HashMap<>();

    public void registerItem(RawItem item) {
        var itemId = item.getId().toLowerCase();
        if (itemMap.containsKey(itemId)) {
            plugin.getLogger().warning("Item with ID '" + itemId + "' is already registered, overwriting it!");
        }
        itemMap.put(itemId, item);
    }

    // Add this method to ItemRegistry.java
    public Collection<RawItem> getAllItems() {
        return Collections.unmodifiableCollection(itemMap.values());
    }

    public RawItem byId(String id) {
        return itemMap.get(id.toLowerCase());
    }

    public Optional<RawItem> fromItemStack(org.bukkit.inventory.ItemStack itemStack) {
        if (itemStack == null)
            return Optional.empty();

        try {
            @SuppressWarnings("deprecation")
            var tag = Helpers.getNmsCustomDataComponent(itemStack).getUnsafe().get("id");
            if (!(tag instanceof net.minecraft.nbt.StringTag(String value)))
                return Optional.empty();

            return Optional.ofNullable(byId(value));
        } catch (Exception e) {
            // Return null if any exception occurs during the conversion process
            plugin.getLogger().warning("Error converting ItemStack to RawItem: " + e.getMessage() + " ItemStack: " + itemStack);
            return Optional.empty();
        }
    }
}
