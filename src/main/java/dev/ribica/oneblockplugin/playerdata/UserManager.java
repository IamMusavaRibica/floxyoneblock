package dev.ribica.oneblockplugin.playerdata;

import dev.ribica.oneblockplugin.OneBlockPlugin;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class UserManager {
    private final OneBlockPlugin plugin;
    private final @Getter Map<UUID, User> userMap = new ConcurrentHashMap<>();

    public void addUser(User user) {
        userMap.put(user.getUuid(), user);
    }

    public @NotNull User getUser(UUID uuid) {
        User user = userMap.get(uuid);
        if (user != null)
            return user;
        plugin.getLogger().warning("creating new user from call to getUser for uuid=" + uuid);
        plugin.getLogger().warning("THIS MAY OR MAY NOT WORK, BUT IT'S A BUG AND IT MUST BE FIXED!");
        return createNewUser(uuid);
    }

    public boolean hasUser(UUID uuid) {
        return userMap.containsKey(uuid);
    }

    public void removeUser(UUID uuid) {
        userMap.remove(uuid);
    }

    protected User createNewUser(UUID uuid) {
        // Create a temporary User, don't add it to userMap. The storage provider will take this User,
        // feed it with appropriate data (player stats) and then call #addUser
        return new User(plugin, uuid);
    }

    public Set<User> getOnlineUsers() {
        Set<User> set = new HashSet<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (userMap.containsKey(uuid)) {
                set.add(userMap.get(uuid));
            } else {
                plugin.getLogger().warning("UserManager#getOnlineUsers: no user found for online player $1 ($2)"
                        .replace("$1", uuid.toString()).replace("$2", player.getName()));
            }
        }
        return set;
    }
}
