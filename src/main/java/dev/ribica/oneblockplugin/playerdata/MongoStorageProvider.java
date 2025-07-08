package dev.ribica.oneblockplugin.playerdata;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import com.sk89q.worldedit.regions.Region;
import dev.ribica.oneblockplugin.OneBlockPlugin;
import dev.ribica.oneblockplugin.islands.Island;
import dev.ribica.oneblockplugin.islands.IslandAllocator;
import dev.ribica.oneblockplugin.islands.IslandMember;
import dev.ribica.oneblockplugin.util.TimeUtils;
import dev.ribica.oneblockplugin.util.UUIDNamePair;
import dev.ribica.oneblockplugin.util.WorldUtils;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.Document;
import org.bson.UuidRepresentation;
import org.bson.conversions.Bson;
import org.bson.types.Binary;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
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

    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> playersCollection;
    private MongoCollection<Document> profilesCollection;
    private BukkitTask autoSaveTask;


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
        this.profilesCollection = database.getCollection("profiles");

        if (this.playersCollection == null) {
            logger.warning("MongoDB collections are not initialized properly. Please check your MongoDB connection.");
            plugin.getServer().getPluginManager().disablePlugin(plugin);
        } else {
            logger.info("MongoDB connection established successfully");
        }
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
        }, 400, 400);
    }


    protected void saveIsland(@NonNull Island island) {
        Island.Serialized is = island.serialize();
        if (is == null) {
            logger.warning("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
            logger.warning("@ Skipping saving island " + island.getUuid());
            logger.warning("@ by player " + island.getOwner().getId().getName());
            logger.warning("@ ");
            logger.warning("@ If this happens often, the above player might be abusing an exploit!");
            logger.warning("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
            throw new RuntimeException("Failed to serialize island!");
        } else {
            List<Bson> chx = new ArrayList<>();

            chx.add(Updates.set("schematic_compression_type", is.getCompressionType()));
            chx.add(Updates.set("schematic", is.getRawData()));
            chx.add(Updates.set("last_updated", Date.from(Instant.now())));

            // Save members and owner
            List<Document> memberDocs = island.getAllMembers().stream()
                    .map(IslandMember::serialize)
                    .toList();
            chx.add(Updates.set("members", memberDocs));
            chx.add(Updates.set("owner", island.getOwner().serialize()));

            // Set a custom name for this island if it has one
            if (island.hasCustomName())
                chx.add(Updates.set("name", plugin.serializeMiniMessage(island.getName())));

            // Update this island in the database
            UpdateResult res = profilesCollection.updateOne(eq("uuid", island.getUuid()), Updates.combine(chx));
            assert res.getMatchedCount() == 1 && res.getModifiedCount() == 1;
        }
    }

    public void removeMemberFromUserDataProfiles(@NonNull UUIDNamePair exMemberUuid, @NonNull UUID islandUuid) {
        UUID userUuid = exMemberUuid.getUuid();
        if (userManager.hasUser(userUuid)) {
            // Player is online, update their in-memory data
            if (!userManager.getUser(userUuid).getProfiles().remove(islandUuid)) {
                logger.severe("Tried to remove island " + islandUuid + " from user " + exMemberUuid.getName() + " but it was not in their profiles!");
            }
        } else {
            plugin.runTaskAsync(() -> {
                // Player is offline, update their database document
                Document playerDoc = playersCollection.find(eq("uuid", userUuid))
                        .projection(Projections.include("data.selected_profile", "data.owned_profile"))
                        .first();
                assert playerDoc != null;

                Document dataDoc = playerDoc.get("data", Document.class);
                UUID selectedProfile = dataDoc.get("selected_profile", UUID.class);
                UUID ownedProfile = dataDoc.get("owned_profile", UUID.class);

                List<Bson> chx = new ArrayList<>();
                chx.add(Updates.pull("data.profiles", islandUuid));
                // if data.selected_profile == the island they were kicked from, we set it to data.owned_profile
                if (islandUuid.equals(selectedProfile)) {
                    chx.add(Updates.set("data.selected_profile", ownedProfile));
                }

                UpdateResult result = playersCollection.updateOne(eq("uuid", userUuid), Updates.combine(chx));
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
                        Updates.set("data.profiles", user.getProfiles()),
                        Updates.set("data.collections", minedBlocksDoc)
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
        List<UUID> profiles = data.getList("profiles", UUID.class);

        // Step 1: check if the user has profiles.
        //   If not, create a fresh island for them
        //   If yes, load their selected (active) island from the database
        UUID ownedProfileUuid = data.get("owned_profile", UUID.class);
        if (profiles.isEmpty()) {
            Island island = Islands.generateNewIsland(userId);
            prepareAndPasteIsland(new Island.Serialized(island, new byte[0], "new_island"));

            user.setActiveIsland(island);
            profiles.add(island.getUuid());
            ownedProfileUuid = island.getUuid();
            profilesCollection.insertOne(new Document()
                    .append("uuid", island.getUuid())
                    .append("owner", island.getOwner().serialize())
                    .append("last_updated", Date.from(Instant.now()))
            );
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

        Document minedBlocksDoc = data.get("collections", Document.class);
        parseMinedBlocks(minedBlocksDoc, user.getMinedBlocks());

        user.setJoinedAt(doc.getDate("joined_at"));
        user.getProfiles().addAll(profiles);
        user.setOwnedIslandUuid(ownedProfileUuid);

        user.ensureLoaded();
        logger.info("Loaded user " + uuid );
        userManager.addUser(user);
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

    public void prepareAndPasteIsland(Island.Serialized is) {
        Island island = is.getIsland();
        IslandAllocator allocator = plugin.getIslandAllocator();
        if (allocator.getSlot(island) != -1) {
            // if the island is already loaded somewhere
            logger.warning("Tried to paste island " + island.getUuid() + " but it is already allocated!!");
            return;
        }
        Location origin = allocator.allocate(island);
        plugin.getIslandRegionManager().registerRegion(island);

        // This is important, clear the area, because pasting a schematic will not paste air blocks from schematic!
        Region region = island.getBoundary();
        plugin.getComponentLogger().info(Component.text(
                "before-paste filling air at slot: " + allocator.getSlot(island) + " region: " + region.getMinimumPoint() + " to " + region.getMaximumPoint(),
                NamedTextColor.LIGHT_PURPLE
        ));
        WorldUtils.fillAir(origin.getWorld(), region);

        try {
            if (is.getCompressionType().equals("new_island")) {
                // a brand-new island, we don't have a schematic for it yet, put sponge in the origin
                WorldUtils.setBlockData(origin, Material.SPONGE.createBlockData());
            } else {
                WorldUtils.pasteSchematic(is.decompress(), origin);
            }
        } catch (IOException e) {
            logger.severe("Failed to paste island schematic for island " + island.getUuid() + ": " + e.getMessage());
            throw new RuntimeException("Failed to paste island schematic", e);
        }
    }

    public Pair<Island, Document> loadIslandMetadata(@NonNull UUID islandUuid, boolean onlyBasicData) {
        var req = profilesCollection.find(eq("uuid", islandUuid));
        if (onlyBasicData) {
            req = req.projection(Projections.fields(
                    Projections.exclude("schematic", "schematic_compression_type")
            ));
        }
        Document islandDoc = req.first();
        if (islandDoc == null)
            throw new RuntimeException("Island not found: " + islandUuid);

        // load the owner
        IslandMember owner = IslandMember.deserialize(islandDoc.get("owner", Document.class), islandUuid);
        UUIDNamePair ownerUuid = owner.getId();

        // get custom name if present
        String _name = islandDoc.getString("name");
        Component islandName = _name != null ? plugin.deserializeMiniMessage(_name, false) : null;

        // Create the island
        Island island = new Island(islandUuid, owner, islandName);

        // Register members using deserialize
        islandDoc.getList("members", Document.class, new ArrayList<>()).forEach(memberDoc -> {
            IslandMember member = IslandMember.deserialize(memberDoc, islandUuid);
            island.putMember(member);  // permissions == 0 means they are a past member!
        });

        // Recalculate the island's challenge progress from all members (including owner)
        island.recalculateChallengeProgress();

        return Pair.of(island, islandDoc);
    }

    protected Island.Serialized loadIslandFromDatabase(@NonNull UUID islandUuid) {
        var pair = loadIslandMetadata(islandUuid, false);
        Island island = pair.getLeft();
        Document islandDoc = pair.getRight();

        String compressionType = islandDoc.getString("schematic_compression_type");
        if (compressionType == null) {
            return new Island.Serialized(island, new byte[0], "new_island");
        }
        byte[] schematicData = islandDoc.get("schematic", Binary.class).getData();
        return new Island.Serialized(island, schematicData, compressionType);
    }

    private Document registerNewUser(User user) {
        UUID userUuid = user.getUuid();

        // will generate a temporary name because we don't have their real username yet
        UUIDNamePair userId = UUIDNamePair.of(userUuid);

        Document data = new Document("profiles", new ArrayList<UUID>());
        Document doc = new Document()
                .append("joined_at", Date.from(Instant.now()))
                .append("data", data);
        userId.serialize(doc);

        playersCollection.insertOne(doc);
        return doc;
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


