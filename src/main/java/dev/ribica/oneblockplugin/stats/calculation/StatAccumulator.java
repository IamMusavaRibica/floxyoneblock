package dev.ribica.oneblockplugin.stats.calculation;

import dev.ribica.oneblockplugin.stats.Stat;

import java.util.EnumMap;
import java.util.Map;

public class StatAccumulator {
    private final Map<Stat, StatBucket> map = new EnumMap<>(Stat.class);

    private StatBucket b(Stat s) {
        return map.computeIfAbsent(s, k -> new StatBucket());
    }

    public void add(Stat stat, double v) {
        b(stat).addFlat += v;
    }

    public void addBaseMultiplier(Stat stat, double v) {
        b(stat).addMultBase += v;
    }

    public void multiplyTotal(Stat stat, double v) {
        b(stat).multTotal *= v;
    }

    public Map<Stat, Double> computeAll() {
        Map<Stat, Double> result = new EnumMap<>(Stat.class);
        for (Stat s : Stat.values()) {
            StatBucket bucket = map.get(s);
            double computed = bucket == null ? s.getBase() : bucket.compute(s.getBase());
            result.put(s, s.clamp(computed));
        }
        return result;
    }
}
