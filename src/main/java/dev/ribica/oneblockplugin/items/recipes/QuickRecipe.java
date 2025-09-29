package dev.ribica.oneblockplugin.items.recipes;

import dev.ribica.oneblockplugin.items.ItemRegistry;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.key.Key;
import org.apache.maven.model.Build;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class QuickRecipe {
    private final Key id;
    private final String[] rows;
    private final Map<Character, Ingredient> map;
    private final Supplier<ItemStack> resultFactory;
    private final BiConsumer<Player, ItemStack> onCraft;


    public static Builder shaped(@NonNull String id, String r1, String r2, String r3) {
        return new Builder(Key.key("quickrecipes", id), new String[]{r1, r2, r3});
    }

    public ItemStack createResult() {
        return resultFactory.get();
    }

    public void callOnCraft(Player p, ItemStack out) {
        onCraft.accept(p, out);
    }

    public boolean matches(CraftingInventory inv, ItemRegistry reg, boolean strictAmounts) {
        ItemStack[] m = inv.getMatrix();
        for (int i = 0; i < 9; i++) {
            char key = rows[i/3].charAt(i%3);
            var s = m[i];
            if (key == ' ') {
                if (s != null && s.getType() != Material.AIR)
                    return false;
                continue;
            }
            var ingredient = map.get(key);
            if (ingredient == null || !ingredient.matchesType(s, reg))
                return false;
            if (strictAmounts && s.getAmount() < ingredient.minAmount())
                return false;
        }
        return true;
    }

    public void chargeExtra(CraftingInventory inv) {
        ItemStack[] m = inv.getMatrix();
        for (int i = 0; i < 9; i++) {
            char key = rows[i / 3].charAt(i % 3);
            if (key == ' ')
                continue;
            Ingredient ing = map.get(key);
            int extra = ing.minAmount() - 1;   // vanilla will charge the remaining 1
            if (extra > 0) {
                ItemStack s = m[i];
                if (s != null)
                    s.setAmount(s.getAmount() - extra);
            }
        }
        inv.setMatrix(m);
    }


    public record Ingredient(Material mat, String customId, int minAmount) {
        public static Ingredient material(Material mat) { return new Ingredient(mat, null, 1); }
        public static Ingredient material(Material mat, int minAmount) { return new Ingredient(mat, null, Math.max(1, minAmount)); }
        public static Ingredient custom(String customId) { return new Ingredient(null, customId, 1); }
        public static Ingredient custom(String customId, int minAmount) { return new Ingredient(null, customId, Math.max(1, minAmount)); }

        boolean matchesType(ItemStack s, ItemRegistry reg) {
            if (s == null || s.getType() == Material.AIR) return false;
            if (mat != null) return s.getType() == mat;
            if (customId != null)
                return reg.fromItemStack(s).map(it -> customId.equals(it.getId())).orElse(false);
            return false;
        }
    }

    public static final class Builder {
        private final Key id;
        private final String[] rows;
        private final Map<Character, Ingredient> map = new HashMap<>();
        private Supplier<ItemStack> resultFactory;
        private BiConsumer<Player, ItemStack> onCraft = (p, it) -> {};

        private Builder(Key id, String[] rows) { this.id = id; this.rows = rows; }
        public Builder ingredient(char key, Ingredient ingredient) { this.map.put(key, ingredient); return this; }
        public Builder ingredient(char key, Material mat) { this.map.put(key, Ingredient.material(mat)); return this; }
        public Builder ingredient(char key, Material mat, int min) { this.map.put(key, Ingredient.material(mat, min)); return this; }
        public Builder result(Supplier<ItemStack> factory) { this.resultFactory = factory; return this; }
        public Builder onCraft(BiConsumer<Player, ItemStack> cb) { this.onCraft = cb; return this; }

        public QuickRecipe build() {
            Objects.requireNonNull(resultFactory, "result(...) is required");
            return new QuickRecipe(id, rows, map, resultFactory, onCraft);
        }

    }
}
