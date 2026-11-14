package dev.pumpkin.evosim.sim;

import dev.pumpkin.evosim.config.SimConfig;
import dev.pumpkin.evosim.genetics.Genome;
import dev.pumpkin.evosim.genetics.Genome.Trait;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * Adaptive pressure: the population slowly gets better at surviving whatever has been killing it.
 *
 * <p>Every death is remembered for a while (older deaths fade with a half-life). The share of recent
 * deaths blamed on each {@link Pressure} decides how strongly newborns are nudged towards the genes
 * that counter it. Natural selection still does its own work on top of this: the nudge only gives it
 * better material.
 */
public final class Adaptation {

    /** A gene a pressure pushes on, and which way (+1 raises it, -1 lowers it). */
    public record Response(Trait trait, int direction) {}

    /** Something that kills sheep, and the genes that help against it. */
    public enum Pressure {
        STARVATION("Starvation", "efficiency, digestion and vision",
                new Response(Trait.METABOLISM, -1), new Response(Trait.DIGESTION, +1),
                new Response(Trait.VISION, +1)),
        DEHYDRATION("Dehydration", "thirst resistance and vision",
                new Response(Trait.THIRST_RATE, -1), new Response(Trait.VISION, +1)),
        COLD("Starving in the tundra", "cold resistance and efficiency",
                new Response(Trait.COLD_RESISTANCE, +1), new Response(Trait.METABOLISM, -1)),
        HEAT("Dying of thirst in the desert", "heat tolerance and thirst resistance",
                new Response(Trait.HEAT_TOLERANCE, +1), new Response(Trait.THIRST_RATE, -1)),
        ROUGH_GROUND("Starving in forest or swamp", "sure-footedness and speed",
                new Response(Trait.SURE_FOOT, +1), new Response(Trait.SPEED, +1));

        private final String label;
        private final String adaptsTo;
        private final List<Response> responses;

        Pressure(String label, String adaptsTo, Response... responses) {
            this.label = label;
            this.adaptsTo = adaptsTo;
            this.responses = List.of(responses);
        }

        public String label() { return label; }

        /** What newborns get better at, in words. */
        public String adaptsTo() { return adaptsTo; }

        public List<Response> responses() { return responses; }
    }

    /** Pressures below this level are ignored, so a handful of deaths does not cause drift. */
    private static final double MIN_LEVEL = 0.03;

    private final SimConfig.Adaptation cfg;
    private final Map<Pressure, Double> scores = new EnumMap<>(Pressure.class);
    private double totalScore;
    private long applied;

    public Adaptation(SimConfig.Adaptation cfg) {
        this.cfg = cfg;
    }

    public boolean enabled() { return cfg.enabled(); }

    /** Remembers a death. Old age counts towards the total but is no pressure: it is the good way to go. */
    public void recordDeath(DeathCause cause, Biome biome) {
        if (!cfg.enabled()) return;
        totalScore += 1.0;
        switch (cause) {
            case STARVATION -> {
                add(Pressure.STARVATION);
                if (biome == Biome.TUNDRA) add(Pressure.COLD);
                if (biome == Biome.FOREST || biome == Biome.SWAMP) add(Pressure.ROUGH_GROUND);
            }
            case DEHYDRATION -> {
                add(Pressure.DEHYDRATION);
                if (biome == Biome.DESERT) add(Pressure.HEAT);
            }
            case OLD_AGE -> { }
        }
    }

    private void add(Pressure p) {
        scores.merge(p, 1.0, Double::sum);
    }

    /** Fades remembered deaths: after the half-life only half of their weight is left. */
    public void decay(double h) {
        if (!cfg.enabled() || totalScore <= 0) return;
        double k = Math.pow(0.5, h / cfg.halfLifeSeconds());
        totalScore *= k;
        scores.replaceAll((p, v) -> v * k);
    }

    /**
     * How strong a pressure is, 0..1: the share of recent deaths it accounts for, scaled down while
     * there are few recent deaths to judge from.
     */
    public double level(Pressure p) {
        if (!cfg.enabled() || totalScore <= 0) return 0;
        double share = scores.getOrDefault(p, 0.0) / totalScore;
        double volume = Math.min(1.0, totalScore / cfg.deathsForFull());
        return Math.min(1.0, share * volume);
    }

    /** Nudges a newborn's genes towards what the recent deaths call for. */
    public Genome adapt(Genome genome, RandomGenerator rng) {
        if (!cfg.enabled()) return genome;
        Genome g = genome;
        for (Pressure p : Pressure.values()) {
            double level = level(p);
            if (level < MIN_LEVEL || rng.nextDouble() >= level * cfg.chance()) continue;
            Response r = p.responses().get(rng.nextInt(p.responses().size()));
            g = g.nudged(r.trait(), r.direction() * cfg.strength() * (0.5 + rng.nextDouble()));
            applied++;
        }
        return g;
    }

    /** How many gene nudges have been given to newborns so far. */
    public long applied() { return applied; }

    public void clear() {
        scores.clear();
        totalScore = 0;
        applied = 0;
    }
}
