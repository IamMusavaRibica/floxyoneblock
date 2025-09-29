package dev.ribica.oneblockplugin.items.recipes;

import dev.ribica.oneblockplugin.items.ItemRegistry;
import org.bukkit.inventory.CraftingInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class QuickRecipeBook {
    private final List<QuickRecipe> recipes = new ArrayList<>();

    public void add(QuickRecipe r) {
        recipes.add(r);
    }

    public Optional<QuickRecipe> firstMatch(CraftingInventory inv, ItemRegistry reg, boolean strictAmounts) {
        for (QuickRecipe r : recipes) {
            if (r.matches(inv, reg, strictAmounts)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }
}
