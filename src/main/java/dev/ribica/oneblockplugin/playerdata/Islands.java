package dev.ribica.oneblockplugin.playerdata;

import dev.ribica.oneblockplugin.OneBlockPlugin;
import dev.ribica.oneblockplugin.islands.Island;
import dev.ribica.oneblockplugin.islands.IslandMember;
import dev.ribica.oneblockplugin.util.TimeUtils;
import dev.ribica.oneblockplugin.util.UUIDNamePair;
import io.papermc.paper.datapack.DiscoveredDatapack;
import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Islands {
    /*
    Utility class for managing loading, saving and unloading islands.
    Do not make these methods synchronized, we use the per-uuid locks instead TODO: explain why not per island

    Thanks to these locks, only one thread is allowed to perform an operation on an island at a time. This includes
    loading, saving and unloading (removing from cachedIslands). The locks are re-entrant (by Java design), so a
    deadlock will not occur if the same thread acquires the same lock multiple times (e.g. in a nested call).

    Nonetheless, some optimizations are still introduced.
     */

    private static Islands instance;

    private final OneBlockPlugin plugin;
    private final Logger logger;
    private final ComponentLogger logger1;
    private final Component PREFIX_SAVE = Component.text("Islands#saveIsland(): ", NamedTextColor.DARK_GREEN);
    private final Component PREFIX_LOAD = Component.text("Islands#loadIsland(): ", NamedTextColor.BLUE);


    private final Map<UUID, CompletableFuture<Void>> savingIslands = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Island>> loadingIslands = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> unloadingIslands = new ConcurrentHashMap<>();
    private final Map<UUID, Island> cachedIslands = new ConcurrentHashMap<>();

    private final Map<UUID, Object> islandLocks = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> guards = new ConcurrentHashMap<>();


    private Islands(OneBlockPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.logger1 = plugin.getComponentLogger();
    }

    private static synchronized Islands getInstance() {
        if (instance == null) {
            instance = new Islands(OneBlockPlugin.getInstance());
        }
        return instance;
    }

    public static Island getIfLoaded(@NonNull UUID islandUuid) {
        return getInstance().cachedIslands.get(islandUuid);
    }

    public static CompletableFuture<Island> loadIsland(@NonNull UUID islandUuid) {
        return getInstance().loadIsland0(islandUuid);
    }

    public static CompletableFuture<Void> saveIsland(@NonNull UUID islandUuid) {
        return getInstance().saveIsland0(islandUuid, false);
    }

    public static void getCachedIslands(@NonNull Map<UUID, Island> map) {
        getInstance();
        map.putAll(instance.cachedIslands);
    }

    public static void unloadIsland(@NonNull UUID islandUuid) {
        getInstance().unloadIsland0(islandUuid, true);
    }

    public static void unloadAll() {
        getInstance();
        instance.cachedIslands.forEach((uuid, island) -> instance.unloadIsland0(uuid, false));
    }

    public static Island generateNewIsland(@NonNull UUIDNamePair ownerId) {
        // This method assumes we will paste this island immediately after creating it
        UUID islandUuid = UUID.randomUUID();  // generate a random UUID for a new island
        Island island = new Island(
                islandUuid,  // generate a random UUID for a new island
                new IslandMember(islandUuid, ownerId, 2147483646, Date.from(Instant.now())),
                null   // no custom name, so Island#getName will return a default name
        );
        getInstance().cachedIslands.put(island.getUuid(), island);
        return island;
    }

    private CompletableFuture<Island> loadIsland0(@NonNull UUID islandUuid) {
        logger1.info(PREFIX_LOAD.append(Component.text("started loading island " + islandUuid, NamedTextColor.GRAY)));
        long time0 = TimeUtils.ms();

        CompletableFuture<Island> existingFuture = loadingIslands.get(islandUuid);
        if (existingFuture != null) {
            logger1.info(PREFIX_LOAD.append(Component.text("island " + islandUuid + " is already loading, waiting for it to finish", NamedTextColor.GRAY)));
            try {
                existingFuture.join();
            } catch (CompletionException ignored) {

            }
            return existingFuture;
        }

        Object lock = islandLocks.computeIfAbsent(islandUuid, k -> new Object());
        synchronized (lock) {
            logger1.info(PREFIX_LOAD.append(Component.text("obtained lock for " + islandUuid + " after "
                    + TimeUtils.msSince(time0) + " ms", NamedTextColor.GRAY)));

            // check if the island is already loaded
            Island cached = cachedIslands.get(islandUuid);
            if (cached != null) {
                logger1.info(PREFIX_LOAD.append(Component.text("loading already cached island " + islandUuid, NamedTextColor.GRAY)));
                return CompletableFuture.completedFuture(cached);
            }
            CompletableFuture<Island> future = new CompletableFuture<>();
            loadingIslands.put(islandUuid, future);

            // The calling thread will hold the lock until loadAndPasteInternal completes.
            // But the same method will complete the future, so we return an already completed future.
            loadAndPasteInternal(islandUuid, future);
            return future;
        }
    }

    private void loadAndPasteInternal(@NonNull UUID islandUuid, @NonNull CompletableFuture<Island> future) {
        // Do not attempt to synchronize this method, it's the job of other public methods from here to ensure
        // that concurrent calls to this method will never happen, since calling this method concurrently will
        // lead to some very unexpected bugs; if we ever mess with code, it's better to fail than do something nasty
        if (guards.getOrDefault(islandUuid, false)) {
            logger.log(Level.SEVERE, "Islands#loadAndPasteInternal() called concurrently for island " + islandUuid);
            throw new RuntimeException("Island load called concurrently for island " + islandUuid + " !!!!");
        }
        guards.put(islandUuid, true);

        long start = TimeUtils.ms();
        try {
            Island.Serialized serialized = plugin.getStorageProvider().loadIslandFromDatabase(islandUuid);
            Island island = serialized.getIsland();

            logger1.info(Component.text("Islands#loadIsland(): ", NamedTextColor.DARK_BLUE).append(Component.text("loaded from database island owner: " + island.getOwner().getId().getName(), NamedTextColor.GRAY)));
            plugin.getStorageProvider().prepareAndPasteIsland(serialized);
            cachedIslands.put(islandUuid, island);
            future.complete(island);
        } catch (Exception e) {
            future.completeExceptionally(e);
        } finally {
            loadingIslands.remove(islandUuid);
        }
        logger1.info(Component.text("Island " + islandUuid + " loaded and pasted in "
                + TimeUtils.msSince(start) + " ms", NamedTextColor.YELLOW));

        guards.put(islandUuid, false);
    }

    private CompletableFuture<Void> saveIsland0(@NonNull UUID islandUuid, boolean earlyUncache) {
        logger1.info(PREFIX_SAVE.append(Component.text("started saving island " + islandUuid, NamedTextColor.GRAY)));
        long time0 = TimeUtils.ms();

        /*
        Optimization: consider the following scenario
         - Thread A starts saving an island and acquires the lock.
         - Thread B starts saving the island too

         Before Thread B acquires the lock, which would cause a double save, it will check if the island is already
         being saved, because thread A will put a completable future in the savingIslands map. If there is a future,
         thread B it will block until that future completes, to comply with saveIsland0 beind blocking
         If we didn't have this early-check, using CompletableFutures would be completely unnecessary.

         Notice the "thread A acquires the lock" part. If both threads called this at the same time, they both would
         end up waiting in front of the lock, because the "faster" one wasn't fast enough to put the future in the
         savingIslands map before the other thread checked the map. In this case the island will be saved twice,
         hopefully won't be a problem.
         */
        CompletableFuture<Void> existingFuture = savingIslands.get(islandUuid);
        if (existingFuture != null) {
            // just wait until existingFuture compeletes, regardless of how it completes
            // (normally or with exception) and then return that finished future
            try {
                existingFuture.join();
            } catch (CompletionException ignored) {

            }
            return existingFuture;
        }

        Object lock = islandLocks.computeIfAbsent(islandUuid, k -> new Object());
        synchronized (lock) {
            Island island = cachedIslands.get(islandUuid);
            if (island == null || island.isObsolete()) {
                logger1.info(PREFIX_SAVE.append(Component.text("island " + islandUuid + " is " + (island == null ? "null" : "obsolete") + ", skipping save", TextColor.color(0x888800))));
                return CompletableFuture.completedFuture(null);
            }
            if (earlyUncache) {  // this is the final save before unloading
                cachedIslands.remove(islandUuid);
            }
            logger1.info(PREFIX_SAVE.append(Component.text("obtained lock for " + islandUuid + " after " +
                    TimeUtils.msSince(time0) + " ms", NamedTextColor.WHITE)));

            CompletableFuture<Void> newSaveFuture = new CompletableFuture<>();
            savingIslands.put(islandUuid, newSaveFuture);

            try {
                plugin.getStorageProvider().saveIsland(island);

                logger1.info(PREFIX_SAVE.append(Component.text("island " + islandUuid + " save successful!" +
                        " took " + TimeUtils.msSince(time0) + " ms", NamedTextColor.WHITE)));

                // simulate a random failure
//                if (3 == (new Random()).nextInt(5))
//                    throw new RuntimeException("Simulated random failure during island save");

                newSaveFuture.complete(null);
            } catch (Exception e) {
                newSaveFuture.completeExceptionally(e);
            } finally {
                savingIslands.remove(islandUuid);
            }
            return newSaveFuture;
        }
    }

    private void unloadIsland0(@NonNull UUID islandUuid, boolean removeWorldGuardRegion) {
        // the caller should ensure that no members are online (for now)

        Object lock = islandLocks.computeIfAbsent(islandUuid, k -> new Object());
        synchronized (lock) {
            // if multiple successive calls to unloadIsland() are made, the island will have been unloaded
            // by the time other threads reach this point; Island#safeUnload() should not let this happen
            Island island = cachedIslands.get(islandUuid);
            if (island == null || island.isObsolete()) {
                logger.warning("Islands#unloadIsland(): island " + islandUuid + " is " + (island == null ? "null" : "obsolete"));
                return;
            }

            // remove island from cache early to prevent other threads from teleporting players to it before it's
            // properly cleaned up?
//            cachedIslands.remove(islandUuid);

            unloadingIslands.put(islandUuid, true);
            try {
//                logger.info("Islands#unloadIsland(): passed all the checks, started unload for: " + island.getUuid());
                saveIsland0(islandUuid, true).join();
                if (removeWorldGuardRegion)
                    plugin.getIslandRegionManager().unregisterRegion(island);  // this calls getBoundary so it must be called before allocator free
                plugin.getIslandAllocator().free(island);
                island.markObsolete();
                logger.info("Islands#unloadIsland(): island " + island.getUuid() + " unloaded successfully");
            } catch (CompletionException e) {
                logger.log(Level.SEVERE, "Islands#unloadIsland(): save failed for island " + island.getUuid() + " exception:" + e.getCause(), e.getCause());
                throw new RuntimeException("Failed to save island " + island.getUuid(), e.getCause());
            } finally {
                // always clean up the unloading state
                unloadingIslands.remove(islandUuid);
            }
        }



    }
}
