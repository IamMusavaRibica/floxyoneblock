package dev.ribica.oneblockplugin.challenges;

import dev.ribica.oneblockplugin.OneBlockPlugin;
import dev.ribica.oneblockplugin.islands.Island;
import dev.ribica.oneblockplugin.util.StringUtils;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.*;
import org.bson.Document;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

@Getter
public abstract class IslandChallenge {
    private final OneBlockPlugin plugin = OneBlockPlugin.getInstance();
    private final int id;
    private final Island island;
    private final ChallengeConfig config;
    private boolean completed;
    private boolean claimed;

    protected IslandChallenge(int id, Document doc, Island island) {
        this.id = id;
        this.island = island;
        this.completed = doc.getBoolean("completed", false);
        this.claimed = doc.getBoolean("claimed", false);
        this.config = plugin.getChallengeConfigManager().getChallengeById(id);
        if (this.config == null) {
            throw new IllegalArgumentException("No configuration found for challenge ID: " + id);
        }
    }





    public static IslandChallenge fromDocument(Island island, Document doc) {
        int id = doc.getInteger("id");
        ChallengeConfig config = OneBlockPlugin.getInstance().getChallengeConfigManager().getChallengeById(id);
        if (config == null) {
            throw new IllegalArgumentException("No configuration found for challenge ID: " + id);
        }

        try {
            return (IslandChallenge) config.getChallengeType().getClazz()
                    .getMethod("deserialize", int.class, ChallengeConfig.class, Document.class, Island.class)
                    .invoke(null, id, config, doc, island);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Failed to deserialize IslandChallenge with id " + id, e);
        }
    }





    public Document serialize() {
        return new Document()
                .append("id", id)
                .append("completed", completed)
                .append("claimed", claimed);
    }

    public void complete() {
        if (completed) {
            throw new IllegalStateException("Challenge is already completed!");
        }
        completed = true;
        if (/*!getConfig().isManualClaim()*/true) {
            claim(); // automatically claim if the challenge does not require manual claiming
        }
    }

    public void claim() {
        // TODO: pass here *who* claimed the challenge OR just broadcast to all island members
        if (!completed) {
            throw new IllegalStateException("Cannot claim a challenge that is not completed!");
        }
        if (!claimed) {
            claimed = true;
            plugin.getComponentLogger().info(Component.text("Claimed challenge: " + config.getName(), NamedTextColor.GREEN, TextDecoration.BOLD));
        }
        // maybe two players had the gui opened at the same time so both of them could invoke
        // this method. for the other call we just silently ignore it.
    }

    /**
     * Should we save this challenge to the database, or not to save space?
     * @return true if there is non-zero progress on this challenge
     */
    public abstract boolean hasAnyProgress();

    public abstract List<Component> getProgressInfo(ItemStack itemStack);

    public boolean shouldBeSaved() {
        return completed || hasAnyProgress();
    }

    public ItemStack getGuiItemStack() {
        ItemStack is = new ItemStack(completed ? Material.BOOK : Material.WRITABLE_BOOK);
        if (completed && !claimed) {  // claimable!!
            is.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.addAll(StringUtils.splitWorldsIntoRows(config.getDescription(), 40)
                .stream().map(row -> Component.text(row, TextColor.color(0xdddddd)).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)).toList()
        );
        lore.add(Component.empty());
        if (claimed) {
            lore.add(Component.text("DOVRŠENO!", TextColor.color(0x00ff00), TextDecoration.BOLD));
        } else {
            lore.addAll(this.getProgressInfo(is));
        }

        is.editMeta(meta -> {
            meta.displayName(Component.text(config.getName()).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            meta.lore(lore);
        });
        return is;
    }
}
