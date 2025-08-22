package dev.ribica.oneblockplugin.items;

import dev.ribica.oneblockplugin.OneBlockPlugin;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
}
