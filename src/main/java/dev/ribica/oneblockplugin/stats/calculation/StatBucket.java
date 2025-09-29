package dev.ribica.oneblockplugin.stats.calculation;

public class StatBucket {
    public double addFlat = 0;
    public double addMultBase = 0;
    public double multTotal = 1;

    public double compute(double base) {
        return (base + addFlat) * (1 + addMultBase) * multTotal;
    }
}
