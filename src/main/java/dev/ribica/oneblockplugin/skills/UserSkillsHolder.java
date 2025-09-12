package dev.ribica.oneblockplugin.skills;

import dev.ribica.oneblockplugin.OneBlockPlugin;
import dev.ribica.oneblockplugin.playerdata.User;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class UserSkillsHolder {
    private static final Logger logger = Logger.getLogger(UserSkillsHolder.class.getName());
    private final User user;
    private final Map<Skill, Long> skillsXp;
    private final Map<Skill, Integer> skillLevels = new HashMap<>();

    public UserSkillsHolder(User user, Map<Skill, Long> skillsXp) {
        this.user = user;
        this.skillsXp = skillsXp;
        for (Skill skill : Skill.values()) {
            this.skillLevels.put(skill, skill.getLevel(this.getTotalXp(skill)));
        }
    }

    public long getTotalXp(Skill skill) {
        return this.skillsXp.getOrDefault(skill, 0L);
    }

    public int getLevel(Skill skill) {
        return this.skillLevels.computeIfAbsent(skill, s -> s.getLevel(this.getTotalXp(skill)));
    }

    public void addRawXp(Skill skill, long xpAmount) {
        int currentLevel = this.getLevel(skill);
        long skillUpToCurrentLevel = skill.getTotalXpRequiredForLevel(currentLevel);

        long xpBefore = this.getTotalXp(skill) - skillUpToCurrentLevel;
        long xpAfter = this.skillsXp.merge(skill, xpAmount, Math::addExact) - skillUpToCurrentLevel;

        long xpDelta = skill.getXpForNextLevel(currentLevel);

        OneBlockPlugin.getInstance().getBossBarManager().sendUpdate(user.getPlayer(), skill, currentLevel, xpBefore, xpAfter, xpDelta);
        checkLevelUp(skill, false);
    }

    private void checkLevelUp(Skill skill, boolean silent) {
        int oldLevel = this.getLevel(skill);
        if (oldLevel >= Skill.MAX_LEVEL)
            return;

        long currentXp = this.getTotalXp(skill);
        long nextLevelXp = skill.getTotalXpRequiredForLevel(oldLevel + 1);

        if (currentXp >= nextLevelXp) {
            this.levelUp(skill, silent);

            // TODO
            Bukkit.getScheduler().runTaskLater(OneBlockPlugin.getInstance(), () -> this.checkLevelUp(skill, silent), 10);
        }
    }

    private void levelUp(Skill skill, boolean silent) {
        int newLevel = this.getLevel(skill) + 1;
        this.skillLevels.put(skill, newLevel);

        OneBlockPlugin.getInstance().getBossBarManager().sendUpdate(
                user.getPlayer(), skill, newLevel,
                0, this.getTotalXp(skill) - skill.getTotalXpRequiredForLevel(newLevel),
                skill.getXpForNextLevel(newLevel)
        );

        user.getPlayer().sendMessage("You leveled up " + skill.getName() + " to level " + newLevel + "; silent: " + silent);
    }

    public Document serialize() {
        var doc = new Document();
        for (Skill skill : Skill.values()) {
            Long xp = skillsXp.get(skill);
            if (xp != null && xp > 0) {
                doc.append(skill.name(), xp);
            }
        }
        return doc;
    }

    public static UserSkillsHolder deserialize(Document document, User user) {
        Map<Skill, Long> skillsXpMap = new HashMap<>();
        if (document == null)
            document = new Document();
        for (Skill skill : Skill.values()) {
            Long xp = document.getLong(skill.name());
            skillsXpMap.put(skill, xp != null ? xp : 0L);
        }
        return new UserSkillsHolder(user, skillsXpMap);
    }
}
