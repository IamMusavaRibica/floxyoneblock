package dev.ribica.oneblockplugin.challenges.types;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.ribica.oneblockplugin.challenges.ChallengeConfig;
import dev.ribica.oneblockplugin.challenges.ChallengeConfigManager;
import dev.ribica.oneblockplugin.challenges.IslandChallenge;
import dev.ribica.oneblockplugin.islands.Island;
import dev.ribica.oneblockplugin.playerdata.User;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bson.Document;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CraftSingleItemsChallenge extends IslandChallenge {
    private final Map<Material, Boolean> items;
    private final List<Material> requiredItems;

    public CraftSingleItemsChallenge(int id, Document doc, Island island, List<Material> itemsOrder, Map<Material, Boolean> items) {
        super(id, doc, island);
        this.requiredItems = itemsOrder;
        this.items = items;
        checkCompleted();
    }

    public Document serialize() {
        Document doc = super.serialize();
        if (isCompleted())
            return doc;
        doc.append("c", items.keySet().stream().filter(items::get).map(Material::name).toList());
        return doc;
    }

    @SuppressWarnings("unused")  // accessed via reflection
    public static CraftSingleItemsChallenge deserialize(int id, ChallengeConfig config, Document doc, Island island) {
        JsonArray arr = config.getParameters().getAsJsonArray("items");
        List<Material> requiredItems = new ArrayList<>();
        for (JsonElement el : arr) {
            requiredItems.add(Material.valueOf(el.getAsString()));
        }

        List<Material> craftedItems = doc.getList("c", String.class, new ArrayList<>()).stream().map(Material::valueOf).toList();
        Map<Material, Boolean> itemsMap = new HashMap<>();

        for (Material mat : craftedItems) {
            if (requiredItems.contains(mat))
                itemsMap.put(mat, true);
        }
        for (Material mat : requiredItems) {
            if (!itemsMap.containsKey(mat))
                itemsMap.put(mat, false);
        }

        return new CraftSingleItemsChallenge(id, doc, island, requiredItems, itemsMap);
    }

    public void progress(User user, CraftItemEvent event) {
        Material crafted = event.getRecipe().getResult().getType();
        Player player = user.getPlayer();
        if (items.containsKey(crafted) && !items.get(crafted)) {
            items.put(crafted, true);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_HIT, 1.0f, 1.0f);
            checkCompleted();
        }
    }

    public void checkCompleted() {
        if (items.values().stream().allMatch(Boolean::booleanValue)) {
            complete();
        }
    }

    public boolean hasAnyProgress() {
        return items.values().stream().anyMatch(Boolean::booleanValue);
    }

    @Override
    public List<Component> getProgressInfo(ItemStack is) {
        List<Component> list = new ArrayList<>();

        for (Material mat : requiredItems) {
            assert items.containsKey(mat);
            boolean completed = items.get(mat);
            if (completed) {
                list.add(Component.text("  ✔ " + mat, NamedTextColor.GREEN).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            } else {
                list.add(Component.text("  ✘ " + mat, TextColor.color(0xffdc17)).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            }
        }
        return list;
    }
}
