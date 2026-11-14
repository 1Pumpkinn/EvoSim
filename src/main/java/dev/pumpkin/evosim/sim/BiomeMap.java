package dev.pumpkin.evosim.sim;

import dev.pumpkin.evosim.config.SimConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Which biome covers each block of the arena. Every generation is different: the number of
 * regions is randomised around the configured count, seed points are scattered freely, each
 * region gets its own random weight (so some biomes sprawl while others stay small), the
 * biomes themselves are picked with random per-map odds, and the borders are bent by layered
 * noise. The result is stored on a one-block grid so lookups are instant.
 */
public final class BiomeMap {

    /** Border warp strength as a fraction of the arena size. */
    private static final double WARP = 0.05;

    /** Every map gets at least this many distinct biomes (if there are enough regions). */
    private static final int MIN_DISTINCT = 3;

    private final int size;
    private final Biome[] grid;
    private final double maxFoodFactor;

    private BiomeMap(int size, Biome[] grid) {
        this.size = size;
        this.grid = grid;
        double max = 0;
        for (Biome b : grid) max = Math.max(max, b.foodFactor());
        this.maxFoodFactor = max;
    }

    /** One biome everywhere. */
    public static BiomeMap uniform(int size, Biome biome) {
        Biome[] grid = new Biome[size * size];
        java.util.Arrays.fill(grid, biome);
        return new BiomeMap(size, grid);
    }

    /** Scatters regions over the arena, or a plain grassland when biomes are turned off. */
    public static BiomeMap generate(SimConfig config, RandomGenerator rng) {
        int size = config.arena().size();
        if (!config.biomes().enabled()) return uniform(size, Biome.GRASSLAND);

        int base = Math.max(1, config.biomes().regions());
        double regionVariance = config.biomes().regionVariance();
        double factor = 1.0 + (rng.nextDouble() * 2.0 - 1.0) * regionVariance;
        int regions = Math.max(1, (int) Math.round(base * factor));

        double[] seedX = new double[regions];
        double[] seedZ = new double[regions];
        double[] weight = new double[regions];
        double spread = config.biomes().sizeVariance() * 1.2;
        for (int s = 0; s < regions; s++) {
            seedX[s] = rng.nextDouble() * size;
            seedZ[s] = rng.nextDouble() * size;
            weight[s] = Math.exp((rng.nextDouble() * 2.0 - 1.0) * spread);
        }

        Biome[] regionBiome = assignBiomes(regions, rng);

        long noiseSeed = rng.nextLong();
        double warp = size * WARP;
        double bigScale = size / 5.0;
        double smallScale = size / 14.0;

        Biome[] grid = new Biome[size * size];
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                double px = x + 0.5;
                double pz = z + 0.5;
                double wx = px + warp * (noise(px / bigScale, pz / bigScale, noiseSeed)
                        + 0.4 * noise(px / smallScale, pz / smallScale, noiseSeed + 1));
                double wz = pz + warp * (noise(px / bigScale, pz / bigScale, noiseSeed + 2)
                        + 0.4 * noise(px / smallScale, pz / smallScale, noiseSeed + 3));
                int best = 0;
                double bestD = Double.MAX_VALUE;
                for (int s = 0; s < regions; s++) {
                    double dx = seedX[s] - wx;
                    double dz = seedZ[s] - wz;
                    double d = (dx * dx + dz * dz) / (weight[s] * weight[s]);
                    if (d < bestD) {
                        bestD = d;
                        best = s;
                    }
                }
                grid[x * size + z] = regionBiome[best];
            }
        }
        return new BiomeMap(size, grid);
    }

    /**
     * Picks a biome for each region. The first few regions get distinct biomes so a map is
     * never a single flat colour; the rest are drawn with random per-map odds, so one map can
     * be desert-heavy and the next mostly forest and swamp.
     */
    private static Biome[] assignBiomes(int regions, RandomGenerator rng) {
        List<Biome> shuffled = new ArrayList<>(List.of(Biome.values()));
        Collections.shuffle(shuffled, new java.util.Random(rng.nextLong()));

        double[] odds = new double[Biome.values().length];
        double total = 0;
        for (int i = 0; i < odds.length; i++) {
            double r = rng.nextDouble();
            odds[i] = 0.05 + r * r;
            total += odds[i];
        }

        Biome[] result = new Biome[regions];
        int distinct = Math.min(Math.min(MIN_DISTINCT, regions), shuffled.size());
        for (int s = 0; s < regions; s++) {
            if (s < distinct) {
                result[s] = shuffled.get(s);
                continue;
            }
            double roll = rng.nextDouble() * total;
            Biome pick = Biome.values()[odds.length - 1];
            for (int i = 0; i < odds.length; i++) {
                roll -= odds[i];
                if (roll <= 0) {
                    pick = Biome.values()[i];
                    break;
                }
            }
            result[s] = pick;
        }

        List<Biome> order = new ArrayList<>(List.of(result));
        Collections.shuffle(order, new java.util.Random(rng.nextLong()));
        return order.toArray(new Biome[0]);
    }

    /** Smooth value noise in -1..1. */
    private static double noise(double x, double z, long seed) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double fx = smooth(x - x0);
        double fz = smooth(z - z0);
        double a = lattice(x0, z0, seed);
        double b = lattice(x0 + 1, z0, seed);
        double c = lattice(x0, z0 + 1, seed);
        double d = lattice(x0 + 1, z0 + 1, seed);
        double top = a + (b - a) * fx;
        double bottom = c + (d - c) * fx;
        return top + (bottom - top) * fz;
    }

    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lattice(int x, int z, long seed) {
        long h = seed + x * 0x9E3779B97F4A7C15L + z * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 11) * (2.0 / (1L << 53)) - 1.0;
    }

    /** The biome at arena-local coordinates (clamped to the arena). */
    public Biome at(double x, double z) {
        int ix = Math.max(0, Math.min(size - 1, (int) Math.floor(x)));
        int iz = Math.max(0, Math.min(size - 1, (int) Math.floor(z)));
        return grid[ix * size + iz];
    }

    /** The largest food factor of any biome present; used to pick food spots fairly. */
    public double maxFoodFactor() { return maxFoodFactor; }
}
