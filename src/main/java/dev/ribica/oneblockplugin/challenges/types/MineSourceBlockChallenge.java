package dev.ribica.oneblockplugin.challenges.types;

import dev.ribica.oneblockplugin.challenges.ChallengeConfig;
import dev.ribica.oneblockplugin.challenges.ChallengeConfigManager;
import dev.ribica.oneblockplugin.challenges.IslandChallenge;
import dev.ribica.oneblockplugin.islands.Island;
import dev.ribica.oneblockplugin.playerdata.User;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bson.Document;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class MineSourceBlockChallenge extends IslandChallenge {
    private int currentProgress;
    private final int targetProgress;

    public MineSourceBlockChallenge(int id, Document doc, Island island, int currentProgress, int targetProgress) {
        super(id, doc, island);
        this.currentProgress = currentProgress;
        this.targetProgress = targetProgress;
        if (!isCompleted())
            checkCompleted();
    }

    public Document serialize() {
        Document doc = super.serialize();
        if (isCompleted())
            return doc;
        return doc.append("p", currentProgress);
    }

    @SuppressWarnings("unused")  // accessed via reflection
    public static MineSourceBlockChallenge deserialize(int id, ChallengeConfig config, Document document, Island island) {
        int currentProgress = document.getInteger("p", 0);
        int targetProgress = config.getParameters().get("count").getAsInt();
        return new MineSourceBlockChallenge(id, document, island, currentProgress, targetProgress);
    }

    public boolean hasAnyProgress() {
        return currentProgress > 0;
    }

    public void progress(User user, BlockBreakEvent event) {
        currentProgress++;
        checkCompleted();
    }

    public void checkCompleted() {
        if (currentProgress >= targetProgress) {
            complete();
        }
    }

    @Override
    public List<Component> getProgressInfo(ItemStack itemStack) {
        List<Component> lore = new ArrayList<>();
        if (currentProgress == targetProgress) {
            // TODO: do not do this here, IslandChallenge.java should add DOVRŠENO if isClaimed()
            lore.add(Component.text("DOVRŠENO!", TextColor.color(0x00ff00), TextDecoration.BOLD));
        } else {
            double progressPercentage = (double) currentProgress / targetProgress;
            double exactGreenBars = progressPercentage * 16;
            int greenBars = (int) exactGreenBars;

            // Check if we need a light gray transition bar
            boolean needsTransitionBar = false;
            double fractionalPart = exactGreenBars - greenBars;
            if (fractionalPart >= 0.2 && fractionalPart <= 0.8 && greenBars < 16) {
                needsTransitionBar = true;
            }

            int lightGrayBars = needsTransitionBar ? 1 : 0;
            int darkGrayBars = 16 - greenBars - lightGrayBars;

            Component progressBar = Component.empty();

            // Add green bars
            if (greenBars > 0)
                progressBar = progressBar.append(Component.text("-".repeat(greenBars), TextColor.color(0x00dd00)));

            // Add light gray transition bar
            if (lightGrayBars > 0)
                progressBar = progressBar.append(Component.text("-".repeat(lightGrayBars), TextColor.color(0x888888)));

            // Add dark gray bars
            if (darkGrayBars > 0)
                progressBar = progressBar.append(Component.text("-".repeat(darkGrayBars), TextColor.color(0x555555)));

            progressBar = progressBar.append(Component.text(" " + currentProgress + "/" + targetProgress, TextColor.color(0xffff00)));
            lore.add(progressBar.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
//            itemStack.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return lore;
    }
}
