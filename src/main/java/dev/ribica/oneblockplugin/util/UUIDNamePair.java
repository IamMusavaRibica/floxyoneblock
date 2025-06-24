package dev.ribica.oneblockplugin.util;

import dev.ribica.oneblockplugin.OneBlockPlugin;
import lombok.*;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/*
This class is used for associating a player's name with their UUID, which
is required for displaying their name when it would otherwise be unavailable,
for instance when we want to display all members of an island, but not all of
them are online. Objects are singletons per UUID.

The UUID may not the modified. But the name could be, because players can
change their usernames. It is always checked when #getName() is called.
 */

@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UUIDNamePair {
    private static final Map<UUID, UUIDNamePair> INSTANCES = new ConcurrentHashMap<>();

    private String name;
    @EqualsAndHashCode.Include
    private final @Getter UUID uuid;

    private UUIDNamePair(@NonNull String name, @NonNull UUID uuid) {
        this.name = name;
        this.uuid = uuid;
    }

    public String getName() {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            this.name = player.getName();
        }
        return name;
    }

    public Document serialize(Document doc) {
        doc.put("uuid", this.getUuid());
        doc.put("name", this.getName());
        return doc;
    }

    public static UUIDNamePair of(@NonNull UUID uuid, @NonNull String name) {
        // 'name' is silently ignored if there is already an instance for the UUID
        return INSTANCES.computeIfAbsent(uuid, id -> new UUIDNamePair(name, id));
    }

    public static UUIDNamePair of(@NonNull UUID uuid) {
        return of(uuid, "$" + uuid);
    }

    public static UUIDNamePair of(@NonNull Document doc) {
        UUID uuid = doc.get("uuid", UUID.class);
        String name = doc.getString("name");
        if (uuid == null) {
            throw new IllegalArgumentException("Document must contain a 'uuid' field");
        } if (name == null) {
            throw new IllegalArgumentException("Document must contain a 'name' field");
        }
        return of(uuid, name);
    }
}
