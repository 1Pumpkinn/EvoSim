package dev.pumpkin.evosim.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.logging.Logger;

/** Immutable, validated snapshot of config.yml. */
public record SimConfig(
        Simulation simulation,
        Arena arena,
        Population population,
        Food food,
        Energy energy,
        Evolution evolution,
        Needs needs,
        Animation animation
) {

    public record Simulation(int tickInterval, double timeScale) {}

    public record Arena(int size, int wallHeight, Material floorBlock, Material wallBlock) {}

    public record Population(int initial, int max, double startEnergy,
                             double reproductionThreshold, double reproductionCost,
                             double maxAgeSeconds, double maturitySeconds, double matingSeconds) {}

    public record Food(double spawnPerSecond, int max, double energy) {}

    public record Energy(double baseCost, double speedCost, double sizeCost, double visionCost) {}

    public record Evolution(double mutationRate, double mutationStrength, double initialSpread) {}

    /**
     * Thirst, wellbeing and orientation. Hydration and mood run 0..100.
     * pondCount/pondRadius: water the creatures drink from; thirstDrain: hydration lost per
     * second; drinkRate: hydration gained per second in a pond; seekBelow: hydration under which
     * creatures look for water; dehydrationDamage: energy lost per second at zero hydration;
     * depressionBelow: mood under which a creature is depressed (slower, no mating);
     * moodDrain/moodRecovery: mood change per second; griefMoodLoss: mood lost by every creature
     * near a death (double for a widowed partner); sameSexChance: share of creatures that
     * pair with their own sex; overbreedingWindow: a creature that mates again within this many
     * seconds of its last mating is overbred; overbreedingMoodLoss: mood it loses each time.
     */
    public record Needs(int pondCount, double pondRadius, double thirstDrain, double drinkRate,
                        double seekBelow, double dehydrationDamage, double depressionBelow,
                        double depressedSpeedFactor, double moodDrain, double moodRecovery,
                        double griefMoodLoss, double sameSexChance,
                        double overbreedingWindow, double overbreedingMoodLoss) {}

    /**
     * tiltedMount: tilted model for the mounting mate (needs the resource pack); statBars: floating
     * stat bars; corpseSeconds: how long a dead sheep lies on its side before it disappears.
     */
    public record Animation(boolean tiltedMount, boolean statBars, double corpseSeconds) {}

    public static SimConfig load(FileConfiguration cfg, Logger log) {
        Simulation simulation = new Simulation(
                clamp(cfg.getInt("simulation.tick-interval", 1), 1, 20),
                clamp(cfg.getDouble("simulation.time-scale", 1.0), 0.1, 20.0));

        Arena arena = new Arena(
                clamp(cfg.getInt("arena.size", 64), 16, 256),
                clamp(cfg.getInt("arena.wall-height", 3), 1, 16),
                block(cfg, "arena.floor-block", Material.GRASS_BLOCK, log),
                block(cfg, "arena.wall-block", Material.DEEPSLATE_BRICKS, log));

        double threshold = clamp(cfg.getDouble("population.reproduction-threshold", 120), 1, 100_000);
        double cost = clamp(cfg.getDouble("population.reproduction-cost", 50), 1, 100_000);
        if (cost > threshold) {
            log.warning("population.reproduction-cost is higher than reproduction-threshold; lowering it.");
            cost = threshold;
        }
        Population population = new Population(
                clamp(cfg.getInt("population.initial", 30), 1, 1000),
                clamp(cfg.getInt("population.max", 150), 1, 2000),
                clamp(cfg.getDouble("population.start-energy", 60), 1, 100_000),
                threshold,
                cost,
                clamp(cfg.getDouble("population.max-age-seconds", 300), 10, 100_000),
                clamp(cfg.getDouble("population.maturity-seconds", 15), 1, 1000),
                clamp(cfg.getDouble("population.mating-seconds", 2.5), 0.5, 60));

        Food food = new Food(
                clamp(cfg.getDouble("food.spawn-per-second", 12.0), 0, 1000),
                clamp(cfg.getInt("food.max", 150), 1, 5000),
                clamp(cfg.getDouble("food.energy", 30), 1, 100_000));

        Energy energy = new Energy(
                clamp(cfg.getDouble("energy.base-cost", 0.6), 0, 1000),
                clamp(cfg.getDouble("energy.speed-cost", 0.2), 0, 1000),
                clamp(cfg.getDouble("energy.size-cost", 1.5), 0, 1000),
                clamp(cfg.getDouble("energy.vision-cost", 0.04), 0, 1000));

        Evolution evolution = new Evolution(
                clamp(cfg.getDouble("evolution.mutation-rate", 0.25), 0, 1),
                clamp(cfg.getDouble("evolution.mutation-strength", 0.08), 0, 1),
                clamp(cfg.getDouble("evolution.initial-spread", 0.3), 0, 1));

        Needs needs = new Needs(
                clamp(cfg.getInt("water.ponds", 3), 0, 12),
                clamp(cfg.getDouble("water.pond-radius", 3.5), 1, 20),
                clamp(cfg.getDouble("water.thirst-per-second", 1.2), 0, 100),
                clamp(cfg.getDouble("water.drink-per-second", 25), 0.1, 1000),
                clamp(cfg.getDouble("water.seek-below", 50), 1, 100),
                clamp(cfg.getDouble("water.dehydration-damage", 2.0), 0, 1000),
                clamp(cfg.getDouble("wellbeing.depression-below", 25), 0, 100),
                clamp(cfg.getDouble("wellbeing.depressed-speed-factor", 0.5), 0.05, 1),
                clamp(cfg.getDouble("wellbeing.mood-drain-per-second", 1.0), 0, 100),
                clamp(cfg.getDouble("wellbeing.mood-recovery-per-second", 0.6), 0, 100),
                clamp(cfg.getDouble("wellbeing.grief-mood-loss", 18), 0, 100),
                clamp(cfg.getDouble("orientation.same-sex-chance", 0.1), 0, 1),
                clamp(cfg.getDouble("wellbeing.overbreeding-window-seconds", 30), 0, 10_000),
                clamp(cfg.getDouble("wellbeing.overbreeding-mood-loss", 35), 0, 100));

        Animation animation = new Animation(
                cfg.getBoolean("animation.tilted-mount", true),
                cfg.getBoolean("animation.stat-bars", true),
                clamp(cfg.getDouble("animation.corpse-seconds", 4.0), 0, 60));

        return new SimConfig(simulation, arena, population, food, energy, evolution, needs, animation);
    }

    private static Material block(FileConfiguration cfg, String path, Material fallback, Logger log) {
        String name = cfg.getString(path, fallback.name());
        Material material = Material.matchMaterial(name);
        if (material == null || !material.isBlock()) {
            log.warning("Invalid block '" + name + "' at " + path + "; using " + fallback + ".");
            return fallback;
        }
        return material;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
