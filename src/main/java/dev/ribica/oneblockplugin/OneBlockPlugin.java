package dev.ribica.oneblockplugin;

import co.aikar.commands.InvalidCommandArgument;
import co.aikar.commands.PaperCommandManager;
import dev.ribica.oneblockplugin.challenges.ChallengeConfigManager;
import dev.ribica.oneblockplugin.challenges.ChallengesEventListener;
import dev.ribica.oneblockplugin.islands.*;
import dev.ribica.oneblockplugin.items.ItemRegistry;
import dev.ribica.oneblockplugin.items.ItemsEventListener;
import dev.ribica.oneblockplugin.items.impl.FancyGold;
import dev.ribica.oneblockplugin.oneblock.OneBlockListener;
import dev.ribica.oneblockplugin.oneblock.StageManager;
import dev.ribica.oneblockplugin.playerdata.*;
import dev.ribica.oneblockplugin.quests.QuestsManager;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationStore;
import net.kyori.adventure.util.UTF8ResourceBundleControl;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import javax.annotation.Nullable;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;


public class OneBlockPlugin extends JavaPlugin {
    private static @Getter OneBlockPlugin instance;
    private Logger logger;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private @Getter long currentTick = 0;
    private @Getter UserManager userManager;
    private @Getter MongoStorageProvider storageProvider;

    private @Getter IslandAllocator2 islandAllocator2;

    private @Getter IslandRegionManager islandRegionManager;
    private @Getter ChallengeConfigManager challengeConfigManager;
    private @Getter StageManager stageManager;
    private @Getter QuestsManager questsManager;

    private @Getter @Setter boolean stopping = false;


    private @Getter FakeBeaconRenderer fakeBeaconRenderer;
    private @Getter KlyBeaconRenderer klyBeaconRenderer;
    private @Getter ItemRegistry itemRegistry;


    @Override
    public void onEnable() {
        instance = this;
        logger = getLogger();

        loadTranslations();

        fakeBeaconRenderer = new FakeBeaconRenderer(this);
        fakeBeaconRenderer.start();
        klyBeaconRenderer = new KlyBeaconRenderer(this);
        klyBeaconRenderer.start();

        // Initialize challenge configuration first
        challengeConfigManager = new ChallengeConfigManager(this);

        // Initialize stage manager
        stageManager = new StageManager(this);

        questsManager = new QuestsManager(this);
        questsManager.loadQuests();

        getServer().getScheduler().runTaskTimer(this, () -> currentTick++, 0L, 1L);
        registerAikarCommands();

        World islandsWorld = getServer().getWorld("world");
        if (islandsWorld == null) {
            logger.severe("World 'world' not found!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        userManager = new UserManager(this);
        storageProvider = new MongoStorageProvider(this);
        storageProvider.startAutoSaving();

        islandAllocator2 = new IslandAllocator2(this);

        islandRegionManager = new IslandRegionManager(this, islandsWorld);


        // register items
        itemRegistry = new ItemRegistry(this);
        new ItemsEventListener(this);
        itemRegistry.registerItem(new FancyGold());


        getServer().getPluginManager().registerEvents(new OneBlockListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new Fix_weird_commands(this), this);
        getServer().getPluginManager().registerEvents(new ChallengesEventListener(this), this);

        islandsWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        islandsWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        islandsWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        islandsWorld.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        islandsWorld.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        islandsWorld.setGameRule(GameRule.KEEP_INVENTORY, true);
        islandsWorld.setGameRule(GameRule.TNT_EXPLODES, false);
        islandsWorld.setGameRule(GameRule.MOB_GRIEFING, false);

    }

    @Override
    public void onDisable() {
        logger.info("Kicking all players and saving data synchronously");
        Component kickMessage = Component.text("Server is restarting or shutting down");

        // Save all player data synchronously BEFORE kicking players
        // This ensures we control the data saving process and don't rely on async tasks from PlayerQuitEvent
        for (Player player : getServer().getOnlinePlayers()) {
            if (userManager.hasUser(player.getUniqueId())) {
                User user = getUser(player);
                try {
                    // Save directly on the main thread - blocking until complete
                    storageProvider.saveUser(user);
                    logger.info("Saved data for player " + player.getName() + " during shutdown");
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error saving user data for " + user.getUuid() + " during shutdown", e);
                }
            }
        }

        // Now kick all players after their data has been saved
        for (Player player : getServer().getOnlinePlayers()) {
            player.kick(kickMessage);
        }


        logger.info("Finished saving player data");
        logger.info("Started saving islands");
        Islands.unloadAll();
        logger.info("Finished saving islands");

        // Unload all island worlds created by IslandAllocator2
        logger.info("Unloading all island worlds");
        islandAllocator2.unloadAllWorlds();
        logger.info("Finished unloading island worlds");

        storageProvider.shutdown();
    }

    public void registerAikarCommands() {
        PaperCommandManager manager = new PaperCommandManager(this);

        manager.getCommandContexts().registerIssuerAwareContext(User.class, ctx -> {
            if (ctx.getSender() instanceof Player player) {
                return getUser(player);
            }
            throw new InvalidCommandArgument("Samo igrači mogu ovu komandu!", false);
        });
        manager.getCommandContexts().registerIssuerOnlyContext(Island.class, ctx -> {
            if (ctx.getSender() instanceof Player player) {
                User user = getUser(player);
                Island island = user.getActiveIsland();
                if (island == null) {
                    logger.severe("Player " + player.getName() + " doesn't have an active profile???");
                    throw new InvalidCommandArgument("Nemaš ostrvo (ovo je bug)", false);
                }
                return island;
            }
            throw new InvalidCommandArgument("Samo igrači mogu ovu komandu!", false);
        });

        // Add context resolver for Island.Member
        manager.getCommandContexts().registerIssuerAwareContext(IslandMember.class, ctx -> {
            if (ctx.getSender() instanceof Player player) {
                User user = getUser(player);
                Island island = user.getActiveIsland();

                // Check if player is actually the owner of this island
                if (!island.getOwner().getId().getUuid().equals(player.getUniqueId())) {
                    throw new InvalidCommandArgument("Moraš biti vlasnik ostrva za ovu komandu!", false);
                }

                // Check if player provided a member name
                String memberName = ctx.popFirstArg();
                if (memberName == null) {
                    throw new InvalidCommandArgument("Moraš navesti člana ostrva!", false);
                }

                // Find the member by name
                for (IslandMember member : island.members.getCurrentMembers()) {
                    if (member.getId().getName().equalsIgnoreCase(memberName)) {
                        return member;
                    }
                }

                throw new InvalidCommandArgument("Igrač " + memberName + " nije član tvog ostrva!", false);
            }
            throw new InvalidCommandArgument("Samo igrači mogu ovu komandu!", false);
        });

        // Add completion handler for @coopmembers
        manager.getCommandCompletions().registerCompletion("coopmembers", ctx -> {
            if (ctx.getSender() instanceof Player player) {
                User user = getUser(player);
                Island island = user.getActiveIsland();

                // Check if player is actually the owner of this island
                if (!island.getOwner().getId().getUuid().equals(player.getUniqueId())) {
                    // Return a help message if player is not on their own island
                    return Collections.singletonList("< Moraš biti na svom ostrvu >");
                }

                // Return list of member names
                return island.members.getCurrentMembers().stream()
                    .map(member -> member.getId().getName())
                    .toList();
            }
            return Collections.emptyList();
        });

        manager.registerCommand(new MiniCommands(this));
        manager.registerCommand(new IslandCommands(this));
    }

    private void loadTranslations() {
        // Use a simpler key structure that matches your usage
        final TranslationStore.StringBased<MessageFormat> store = TranslationStore.messageFormat(Key.key("floxyoneblock", "translations"));

        // Register English as default/fallback
        store.registerAll(Locale.ENGLISH, ResourceBundle.getBundle("translationfiles/quests", Locale.ENGLISH, UTF8ResourceBundleControl.get()), false);

        // Register Croatian
        store.registerAll(Locale.of("hr", "HR"), ResourceBundle.getBundle("translationfiles/quests", Locale.of("hr", "HR"), UTF8ResourceBundleControl.get()), false);

        // Register Serbian locales to match your file naming
        // sr_CS - Serbian Latin (corresponds to quests_sr_CS.properties)
        Locale serbianLatin = Locale.of("sr", "CS");
        store.registerAll(serbianLatin, ResourceBundle.getBundle("translationfiles/quests", serbianLatin, UTF8ResourceBundleControl.get()), false);

        // sr_RS - Serbian  ali ovaj unused
//        Locale serbianRS = Locale.of("sr", "RS");
//        store.registerAll(serbianRS, ResourceBundle.getBundle("translationfiles/quests", serbianRS, UTF8ResourceBundleControl.get()), false);

        // sr_SP - Serbian Cyrillic (corresponds to quests_sr_SP.properties)
        Locale serbianCyrillic = Locale.of("sr", "SP");
        store.registerAll(serbianCyrillic, ResourceBundle.getBundle("translationfiles/quests", serbianCyrillic, UTF8ResourceBundleControl.get()), false);

        getLogger().info("Registered locales: en, hr_HR, sr_CS, sr_RS, sr_SP");

        // Set English as default fallback
        store.defaultLocale(Locale.ENGLISH);

        GlobalTranslator.translator().addSource(store);

        // Log some debug info
        getLogger().info("Translation store registered with key: floxyoneblock:translations");
    }

    public User getUser(Player player) {
        return userManager.getUser(player.getUniqueId());
    }

    public @Nullable User getUserIfAvailable(UUID uuid) {
        return userManager.hasUser(uuid) ? userManager.getUser(uuid) : null;
    }

    public BukkitTask runTask(Runnable runnable) {
        return getServer().getScheduler().runTask(this, runnable);
    }

    public BukkitTask runTaskAsync(Runnable runnable) {
        return getServer().getScheduler().runTaskAsynchronously(this, runnable);
    }

    public Component deserializeMiniMessage(String s, boolean disallowDefaultItalic) {
        Component c = mm.deserialize(s);
        if (disallowDefaultItalic) {  // for using this in item display names and lore
            c = c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        }
        return c;
    }

    public String serializeMiniMessage(Component c) {
        return mm.serialize(c);
    }
}
