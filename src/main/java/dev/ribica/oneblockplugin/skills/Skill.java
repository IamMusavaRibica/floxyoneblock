package dev.ribica.oneblockplugin.skills;

import dev.ribica.oneblockplugin.util.Bisect;
import lombok.Getter;
import org.bukkit.boss.BarColor;
import org.enginehub.linbus.stream.token.LinToken;

import java.util.HashMap;
import java.util.Map;

public enum Skill {
    MINING(0, "mining", BarColor.PURPLE),
    FARMING(1, "farming", BarColor.GREEN),
    FISHING(2, "fishing", BarColor.BLUE);

    public static final int MAX_LEVEL = 100;
    private final static Map<Integer, Skill> skillMap = new HashMap<>();
    private final long[] totalXpForLevel = new long[MAX_LEVEL + 1];
    private final @Getter int id;
    private final @Getter String name;
    private final @Getter BarColor barColor;

    static {
        for (Skill skill : Skill.values()) { skillMap.put(skill.getId(), skill); }
    }

    public static Skill fromId(int id) {
        return skillMap.get(id);
    }

    Skill(int id, String strId, BarColor barColor) {
        this.id = id;
        this.name = strId;
        this.barColor = barColor;
        this.totalXpForLevel[0] = 0;
        this.totalXpForLevel[1] = 100;
        for (int i = 2; i <= MAX_LEVEL; i++) {
            // 5x^2 + 50x
            this.totalXpForLevel[i] = this.totalXpForLevel[i-1] + (long) (5*i*i + 50*i);
        }
    }

    public long getXpRequiredForLevel(int level) {
        return this.totalXpForLevel[level];
    }

    public int getLevel(long xp) {
        if (xp < 100)
            return 0;
        // minus 1 because indexes start at zero
        // arr = this.totalXpForLevel
        // user with xp is level x iff arr[x] <= xp < arr[x+1]
        return Math.min(Bisect.bisectRight(totalXpForLevel, xp) - 1, MAX_LEVEL);
    }
}
