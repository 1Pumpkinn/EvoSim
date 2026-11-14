package dev.pumpkin.evosim.sim;

import dev.pumpkin.evosim.config.SimConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Which biome covers each block of the arena. Regions grow around scattered seed points
 * (nearest seed wins, with the borders wobbled a little), and the result is stored on a
 * one-block grid so lookups are instant.
 */
public final class BiomeMap {

    /** How far the region borders wobble, in blocks. */
    private static final double WOBBLE = 3.0;

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

        int regions = Math.max(1, config.biomes().regions());
        int cells = (int) Math.ceil(Math.sqrt(regions));
        double cell = size / (double) cells;

        // One seed per grid cell, jittered, so the regions spread evenly.
        List<double[]> seeds = new ArrayList<>();
        for (int i = 0; i < cells; i++) {
            for (int j = 0; j < cells; j++) {
                seeds.add(new double[] {(i + 0.15 + rng.nextDouble() * 0.7) * cell,
                        (j + 0.15 + rng.nextDouble() * 0.7) * cell});
            }
        }
        Collections.shuffle(seeds, new java.util.Random(rng.nextLong()));
        seeds = seeds.subList(0, Math.min(regions, seeds.size()));

        List<Biome> biomes = new ArrayList<>(List.of(Biome.values()));
        Collections.shuffle(biomes, new java.util.Random(rng.nextLong()));

        double phaseX = rng.nextDouble() * Math.PI * 2;
        double phaseZ = rng.nextDouble() * Math.PI * 2;
        Biome[] grid = new Biome[size * size];
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                double wx = x + 0.5 + WOBBLE * Math.sin((z + 0.5) * 0.21 + phaseX);
                double wz = z + 0.5 + WOBBLE * Math.sin((x + 0.5) * 0.17 + phaseZ);
                int best = 0;
                double bestD = Double.MAX_VALUE;
                for (int s = 0; s < seeds.size(); s++) {
                    double dx = seeds.get(s)[0] - wx;
                    double dz = seeds.get(s)[1] - wz;
                    double d = dx * dx + dz * dz;
                    if (d < bestD) {
                        bestD = d;
                        best = s;
                    }
                }
                grid[x * size + z] = biomes.get(best % biomes.size());
            }
        }
        return new BiomeMap(size, grid);
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
