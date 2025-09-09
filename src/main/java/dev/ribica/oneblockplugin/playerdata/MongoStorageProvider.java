package dev.ribica.oneblockplugin.playerdata;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import dev.ribica.oneblockplugin.OneBlockPlugin;
import dev.ribica.oneblockplugin.islands.Island;
import dev.ribica.oneblockplugin.islands.IslandAllocator2;
import dev.ribica.oneblockplugin.islands.IslandMember;
import dev.ribica.oneblockplugin.util.TimeUtils;
import dev.ribica.oneblockplugin.util.UUIDNamePair;
import dev.ribica.oneblockplugin.util.WorldUtils;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bson.Document;
import org.bson.UuidRepresentation;
import org.bson.conversions.Bson;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.mongodb.client.model.Filters.eq;

@Slf4j
public class MongoStorageProvider {
    private final OneBlockPlugin plugin;
    private final Logger logger;
    private final UserManager userManager;

    private final MongoClient mongoClient;
    private final MongoDatabase database;
    private final MongoCollection<Document> playersCollection;
    private BukkitTask autoSaveTask;


    private final MongoCollection<Document> islandDataCollection;
    private final MongoCollection<Document> islandMembersCollection;


    public MongoStorageProvider(OneBlockPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.userManager = plugin.getUserManager();

        // Read connection string from config, fallback to default
        String connectionString = plugin.getConfig().getString("mongodb-connection-string", "mongodb://localhost:27017");
        logger.info("Connecting to MongoDB with connection string: " + connectionString);
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(connectionString))
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .build();
        this.mongoClient = MongoClients.create(settings);
        this.database = mongoClient.getDatabase("oneblock");
        this.playersCollection = database.getCollection("players");

        this.islandDataCollection = database.getCollection("island_data");
        this.islandMembersCollection = database.getCollection("island_members");

        logger.info("MongoDB connection established successfully");
    }

    public void startAutoSaving() {
        logger.warning("auto save started");
        this.autoSaveTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long start = TimeUtils.ms();
            logger.info("Auto save started");
            for (User user : userManager.getOnlineUsers()) {
                try {
                    saveUser(user);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error running auto-save on user data:", e);
                }
            }
            logger.info("Auto save user data finished in " + TimeUtils.msSince(start) + " ms");
            start = TimeUtils.ms();
            Map<UUID, Island> islands = new HashMap<>();
            Islands.getCachedIslands(islands);
            for (Island island : islands.values()) {
                try {
                    Islands.saveIsland(island.getUuid());
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error running auto-save on island " + island.getUuid() + ":", e);
                }
            }
            logger.info("Auto save island data finished in " + TimeUtils.msSince(start) + " ms");
        }, 400, 4000);
    }

    protected void saveIsland(@NonNull Island island) {
        // No longer save schematics - world files handle persistence automatically
        // Only save island metadata (members, stats, etc.)

        UUID islandUuid = island.getUuid();
        Date lastUpdated = Date.from(Instant.now());

        // Save members data
        List<Document> memberDocs = island.members.serialize();
        for (var memberDoc : memberDocs) {
            memberDoc.append("island_uuid", islandUuid);
            memberDoc.append("last_updated", lastUpdated);
            islandMembersCollection.updateOne(
                    new Document("island_uuid", islandUuid)
                            .append("uuid", memberDoc.get("uuid")),
                    new Document("$set", memberDoc),
                    new UpdateOptions().upsert(true)
            );
        }

        // Save island data (metadata only)
        List<Bson> dataUpdates = new ArrayList<>();
        dataUpdates.add(Updates.set("uuid", islandUuid));
        dataUpdates.add(Updates.set("owner", island.getOwner().serialize()));
        dataUpdates.add(Updates.set("last_updated", lastUpdated));

        // Set a custom name for this island if it has one
        if (island.hasCustomName())
            dataUpdates.add(Updates.set("name", plugin.serializeMiniMessage(island.getName())));

        dataUpdates.add(Updates.set("stage", island.getCurrentStageId()));
        dataUpdates.add(Updates.set("stage_progress", island.getBlocksMinedSinceLastStage()));
        dataUpdates.add(Updates.set("stage_progress", island.getStageCycles()));

        UpdateResult dataResult = islandDataCollection.updateOne(
                eq("uuid", islandUuid),
                Updates.combine(dataUpdates),
                new UpdateOptions().upsert(true)
        );

        logger.info("Saved island metadata for " + islandUuid + " (world files saved automatically)");
    }

    public void removeMemberFromUserDataProfiles(@NonNull UUIDNamePair exMemberUuid, @NonNull UUID islandUuid) {
        UUID userUuid = exMemberUuid.getUuid();
        // No longer need to update profiles in players collection since we query from island_members
        // The member removal is already handled by IslandMembers.removeMember() which sets permissions to 0

        if (userManager.hasUser(userUuid)) {
            // Player is online, check if they need to switch islands
            User user = userManager.getUser(userUuid);
            if (user.getActiveIsland().getUuid().equals(islandUuid)) {
                // Switch to their owned island
                plugin.runTask(() -> {
                    user.switchActiveIsland(user.getOwnedIslandUuid()).whenComplete((newIsland, error) -> {
                        if (error != null) {
                            plugin.getLogger().severe("Failed to switch user to owned island after being kicked: " + error.getMessage());
                        }
                    });
                });
            }
        } else {
            plugin.runTaskAsync(() -> {
                // Player is offline, update their database document
                Document playerDoc = playersCollection.find(eq("uuid", userUuid))
                        .projection(Projections.include("data.selected_profile", "data.owned_profile"))
                        .first();
                if (playerDoc != null) {
                    Document dataDoc = playerDoc.get("data", Document.class);
                    UUID selectedProfile = dataDoc.get("selected_profile", UUID.class);
                    UUID ownedProfile = dataDoc.get("owned_profile", UUID.class);

                    // If data.selected_profile == the island they were kicked from, set it to data.owned_profile
                    if (islandUuid.equals(selectedProfile)) {
                        playersCollection.updateOne(
                            eq("uuid", userUuid),
                            Updates.set("data.selected_profile", ownedProfile)
                        );
                    }
                }
            });
        }
    }

    public void saveUser(@NonNull User user) {
        // THIS IS FOR SAVING USER DATA! DO NOT SAVE ISLANDS HERE!!!
        // 'public' because it's called in Plugin#onDisable
        long start = TimeUtils.ms();

        // Convert user's mined blocks to a document using material name as key directly
        Document minedBlocksDoc = new Document();
        user.getMinedBlocks().forEach((material, count) -> {
            minedBlocksDoc.append(material.name(), count);
        });

        playersCollection.updateOne(
                eq("uuid", user.getUuid()),
                Updates.combine(
                        Updates.set("name", UUIDNamePair.of(user.getUuid()).getName()),
                        Updates.set("last_updated", Date.from(Instant.now())),
                        Updates.set("data.selected_profile", user.getActiveIsland().getUuid()),
                        Updates.set("data.collections", minedBlocksDoc),
                        Updates.set("data.quests", user.quests.serialize()),
                        Updates.set("data.skills", user.skills.serialize()),
                        Updates.set("settings.challenge_bar", user.isWantsToSeeChallengeBar())
                )
        );

//        logger.info("Saving data for " + user.getPlayer().getName() + " took " + TimeUtils.msSince(start) + " ms");
    }

    public @NotNull User loadUser(@NonNull UUID uuid) {
        /* THIS IS CALLED ASYNCHRONOUSLY FROM AsyncPlayerPreLoginEvent
         * THE "PLAYER" OBJECT IS NOT YET AVAILABLE
         * DO NOT INTERACT WITH BUKKIT API! USE THE SCHEDULER */
        User user = userManager.createNewUser(uuid);

        Document doc = playersCollection.find(eq("uuid", uuid)).first();
        if (doc == null) {
            doc = registerNewUser(user);  // If this user joined for the first time
        }

        UUIDNamePair userId = UUIDNamePair.of(doc);

        Document data = doc.get("data", Document.class);
        // Get profiles from island_members collection instead of players collection
        List<UUID> profiles = getUserProfiles(uuid);

        // Step 1: check if the user has profiles.
        //   If not, create a fresh island for them
        //   If yes, load their selected (active) island from the database
        UUID ownedProfileUuid = data.get("owned_profile", UUID.class);
        if (profiles.isEmpty()) {
            Island island = Islands.generateNewIsland(userId);
            prepareAndLoadIsland(island, true); // true = new island

            user.setActiveIsland(island);
            ownedProfileUuid = island.getUuid();
            playersCollection.updateOne(
                    eq("uuid", user.getUuid()),
                    Updates.set("data.owned_profile", island.getUuid())  // this is just for convenience
            );
        }
        else {
            // Load existing profiles for this user
            UUID activeIslandUuid = data.get("selected_profile", UUID.class);
            if (activeIslandUuid == null) {
                throw new RuntimeException("corrupted user data: selected_profile is null for user " + uuid);
            }
            try {
                Island active = Islands.loadIsland(activeIslandUuid).get();
                user.setActiveIsland(active);
            } catch (Exception e) {
                logger.severe("Failed to load owned island for user " + uuid + ": " + e.getMessage());
                throw new RuntimeException("Failed to load owned island", e);
            }
        }

        Document questsDoc = data.get("quests", Document.class);
        user.loadQuests(questsDoc);

        Document skillsDoc = data.get("skills", Document.class);
        user.loadSkills(skillsDoc);

        Document minedBlocksDoc = data.get("collections", Document.class);
        parseMinedBlocks(minedBlocksDoc, user.getMinedBlocks());

        user.setJoinedAt(doc.getDate("joined_at"));
        user.getProfiles().addAll(profiles);
        user.setOwnedIslandUuid(ownedProfileUuid);


        Document settingsDoc = doc.get("settings", Document.class);
        if (settingsDoc == null)
            settingsDoc = new Document();

        user.setWantsToSeeChallengeBar(settingsDoc.getBoolean("challenge_bar", true));

        user.ensureLoaded();
        logger.info("Loaded user " + uuid );
//        userManager.addUser(user);  do this in PlayerJoinEvent to ensure player actually joined
        return user;
    }

    private void parseMinedBlocks(Document minedBlocksDoc, Map<Material, Integer> map) {
        if (minedBlocksDoc != null) {
            for (String key : minedBlocksDoc.keySet()) {
                if (key.equals("name") || key.equals("uuid"))
                    continue;
                try {
                    Material material = Material.valueOf(key);
                    int count = minedBlocksDoc.getInteger(key, 0);
                    if (count > 0) {
                        map.put(material, count);
                    }
                } catch (IllegalArgumentException e) {
                    // Skip invalid material names
                    logger.warning("Invalid material for mined blocks: " + key);
                }
            }
        }
    }

    public void prepareAndLoadIsland(Island island, boolean isNewIsland) {
        IslandAllocator2 allocator = plugin.getIslandAllocator2();
        if (allocator.getSlot(island) != -1) {
            // if the island is already loaded somewhere
            logger.warning("Tried to load island " + island.getUuid() + " but it is already allocated!!");
            return;
        }
        Location origin = allocator.allocate(island);
        // Register WorldGuard region for the island
        plugin.getIslandRegionManager().registerRegion(island);

        // For new islands: only create the initial one block
        // For existing islands: world files will automatically load all blocks
        if (isNewIsland) {
            plugin.getComponentLogger().info(Component.text(
                    "Creating new island in world: " + origin.getWorld().getName() + " - placing initial sponge block",
                    NamedTextColor.LIGHT_PURPLE
            ));
            WorldUtils.setBlockData(origin, Material.SPONGE.createBlockData());
        } else {
            plugin.getComponentLogger().info(Component.text(
                    "Loading existing island in world: " + origin.getWorld().getName() + " - blocks will load from world files automatically",
                    NamedTextColor.LIGHT_PURPLE
            ));
            // No need to do anything - the world loading system automatically restores all placed blocks
        }
    }

    public Island loadIslandFromDatabase(@NonNull UUID islandUuid) {
        Document islandDataDoc = islandDataCollection.find(eq("uuid", islandUuid)).first();
        if (islandDataDoc == null) {
            throw new RuntimeException("Island data doc not found: " + islandUuid);
        }

        IslandMember owner = IslandMember.deserialize(islandDataDoc.get("owner", Document.class), islandUuid);
        String _name = islandDataDoc.getString("name");
        Component islandName = _name != null ? plugin.deserializeMiniMessage(_name, false) : null;

        Island island = new Island(islandUuid, owner, islandName);
        island.setCurrentStageId(islandDataDoc.getInteger("stage", 0));
        island.setBlocksMinedSinceLastStage(islandDataDoc.getInteger("stage_progress", 0));
        island.setStageCycles(islandDataDoc.getInteger("stage_cycles", 0));

        List<Document> memberDocs = islandMembersCollection.find(eq("island_uuid", islandUuid)).into(new ArrayList<>());
        island.members.deserialize(memberDocs);

        return island;
    }

    private Document registerNewUser(User user) {
        UUID userUuid = user.getUuid();

        // will generate a temporary name because we don't have their real username yet
        UUIDNamePair userId = UUIDNamePair.of(userUuid);

        // No longer create profiles field - profiles are now queried from island_members collection
        Document doc = new Document()
                            .append("data", new Document())
                            .append("joined_at", Date.from(Instant.now()));
        userId.serialize(doc);

        playersCollection.insertOne(doc);
        return doc;
    }

    public List<UUID> getUserProfiles(@NonNull UUID userUuid) {
        List<UUID> profiles = new ArrayList<>();

        // Query island_members collection for all islands where this user is a member
        List<Document> memberDocs = islandMembersCollection.find(eq("uuid", userUuid)).into(new ArrayList<>());

        for (var d : memberDocs) {
            var islandUuid = d.get("island_uuid", UUID.class);
            int permissions = d.getInteger("permissions", 0);

            if (permissions > 0 && islandUuid != null) {
                profiles.add(islandUuid);
            }
        }

        // Also check if the user owns any islands (from island_data collection)
        List<Document> ownedIslands = islandDataCollection.find(eq("owner.uuid", userUuid)).into(new ArrayList<>());
        for (var d : ownedIslands) {
            var islandUuid = d.get("uuid", UUID.class);
            if (islandUuid != null && !profiles.contains(islandUuid)) {
                profiles.add(islandUuid);
            }
        }

        return profiles;
    }

    public void shutdown() {
        if (autoSaveTask != null)
            autoSaveTask.cancel();

        if (mongoClient != null) {
            try {
                mongoClient.close();
                logger.info("MongoDB connection closed successfully");
            } catch (Exception e) {
                logger.warning("Error closing MongoDB connection: " + e.getMessage());
            }
        }
    }
}

