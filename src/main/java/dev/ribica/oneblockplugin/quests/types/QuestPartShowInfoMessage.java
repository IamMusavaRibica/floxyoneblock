package dev.ribica.oneblockplugin.quests.types;

import com.google.gson.JsonObject;
import dev.ribica.oneblockplugin.OneBlockPlugin;
import dev.ribica.oneblockplugin.playerdata.User;
import dev.ribica.oneblockplugin.quests.Quest;
import dev.ribica.oneblockplugin.quests.QuestPart;
import dev.ribica.oneblockplugin.quests.QuestsManager;
import lombok.NonNull;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.translation.GlobalTranslator;
import net.minecraft.world.item.EnderpearlItem;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import javax.annotation.Nullable;


public class QuestPartShowInfoMessage extends QuestPart {
    private final OneBlockPlugin plugin = OneBlockPlugin.getInstance();
    private final BukkitScheduler scheduler = Bukkit.getScheduler();
    private BukkitTask task;
    private boolean firstMessageShown = false;

    private final String MESSAGE_KEY;
    private final boolean APPLY_SLOWDOWN;
    private final boolean REQUIRE_CLICK;
    private final Component FULL_MESSAGE;
    private final Component BUTTON_OK;


    private QuestPartShowInfoMessage(Quest quest, String m, boolean as, boolean rc) {
        super(quest);
        this.MESSAGE_KEY = m;
        this.APPLY_SLOWDOWN = as;
        this.REQUIRE_CLICK = rc;
        this.FULL_MESSAGE = Component.newline()
                .append(Component.translatable(MESSAGE_KEY))
                .appendNewline();
        // We create just one instance to prevent the possibility of multiple clicks when multiple copies
        // of the message are sent to the player and they may click each one of them.
        this.BUTTON_OK = Component.text(" ".repeat(50))
                .append(
                        Component.text("[CONTINUE]", TextColor.color(0x00ff00), TextDecoration.BOLD)
                                .clickEvent(ClickEvent.callback(this::callback))
                )
                .appendNewline();
    }

    private void callback(Audience player) {
        OneBlockPlugin.getInstance().getLogger().info("Callback for quest " + getQuest().getMetadata().id() + " part, clicked by " + player);
        finish0();
    }

    public void startSpammingMessage() {
        User u = getQuest().getUser();
        Player p = u.getPlayer();

        if (APPLY_SLOWDOWN) {
            u.addTemporaryEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20*60*60, 7, false, false, false));
            p.setGameMode(GameMode.ADVENTURE);
        }

        task = scheduler.runTaskTimer(plugin, () -> {
            p.sendMessage(GlobalTranslator.render(FULL_MESSAGE, p.locale()));

            if (!firstMessageShown) {
                scheduler.runTaskLater(plugin, () -> p.sendMessage(BUTTON_OK), 5 * 20L);
                firstMessageShown = true;
            } else {
                p.sendMessage(BUTTON_OK);
            }
        }, 0L, 30 * 20L);
    }

    @Override
    public void onStart() {
        Player p = getQuest().getUser().getPlayer();

        if (!REQUIRE_CLICK) {  // complete immediately
            p.sendMessage(GlobalTranslator.render(FULL_MESSAGE, p.locale()));
            finish0();
            return;
        }

        startSpammingMessage();
    }
    @Override
    public void onResume() {
        // because REQUIRE_CLICK = false means the message is shown and the part is completed immediately
        // so the quest part can never be serialized without being completed
        OneBlockPlugin.getInstance().getLogger().warning("RESUMING QUEST PART SHOW INFO MESSAGE: " + getQuest().getMetadata().id());
        assert REQUIRE_CLICK;

        startSpammingMessage();
    }

    @Override
    public void onComplete() {
        User u = getQuest().getUser();
        Player p = u.getPlayer();

        if (APPLY_SLOWDOWN) {
            p.setGameMode(GameMode.SURVIVAL);
            u.removeTemporaryEffect(PotionEffectType.SLOWNESS);
        }
        if (task != null) {  // cancel this task in case REQUIRE_CLICK == true
            task.cancel();
            task = null;
        }
    }


    public static QuestPartShowInfoMessage deserialize(
            @NonNull Quest quest,
            @NonNull QuestsManager.QuestPartMetadata metadata,
            @NonNull Document data
    ) {
        JsonObject params = metadata.params();
        return new QuestPartShowInfoMessage(
                quest,
                params.get("key").getAsString(),
                params.get("apply_slowdown").getAsBoolean(),
                params.get("require_click").getAsBoolean()
        );
    }

    @Override
    public void serializeInto(Document document) {
        // no need to serialize anything for this quest part type
    }
}
