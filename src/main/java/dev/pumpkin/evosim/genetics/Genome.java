package dev.pumpkin.evosim.genetics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.random.RandomGenerator;

/**
 * Immutable set of heritable traits. Every trait is stored in its real unit
 * and always kept inside the trait's [min, max] range. On top of the genes a
 * genome can carry a few procedurally generated {@link Mutation}s.
 */
public final class Genome {

    public enum Trait {
        /** Movement speed in blocks per second. */
        SPEED(1.0, 6.0, false, 1.0, "Sluggish", "Swift"),
        /** Body scale; 1.0 is one block wide. */
        SIZE(0.3, 1.5, false, 1.0, "Tiny", "Giant"),
        /** Radius in blocks within which food and rivals are noticed. */
        VISION(3.0, 20.0, false, 1.0, "Short-sighted", "Eagle-eyed"),
        /** Lifespan multiplier. */
        LONGEVITY(0.6, 1.4, false, 1.0, "Short-lived", "Long-lived"),
        /** Energy upkeep multiplier; low is efficient. */
        METABOLISM(0.7, 1.3, false, 1.0, "Efficient", "Gluttonous"),
        /** Thirst multiplier: how fast hydration drains; low is hardy. */
        THIRST_RATE(0.6, 1.4, false, 1.0, "Camel-like", "Parched"),
        /** Multiplier on the mood lost when others die nearby; low is stoic. */
        GRIEF_SENSITIVITY(0.5, 1.5, false, 1.0, "Stoic", "Sensitive"),
        /** Multiplier on a ram's rest period after mating; low recovers fast. */
        REST_TIME(0.5, 1.5, false, 1.0, "Quick recovery", "Slow recovery"),
        /** Resistance to the tundra's extra energy drain; high shrugs the cold off. */
        COLD_RESISTANCE(0.5, 1.5, false, 1.0, "Thin-blooded", "Frostproof"),
        /** Resistance to the desert's extra thirst; high copes with the heat. */
        HEAT_TOLERANCE(0.5, 1.5, false, 1.0, "Heat-sensitive", "Sun-hardened"),
        /** How well it walks through forest and swamp; high is barely slowed. */
        SURE_FOOT(0.5, 1.5, false, 1.0, "Clumsy", "Sure-footed"),
        /** Multiplier on the energy gained from food; costs extra upkeep. */
        DIGESTION(0.7, 1.3, false, 1.0, "Poor digestion", "Rich digestion"),
        /** Multiplier on this sheep's own mutation rate and chance of a new special mutation. */
        MUTABILITY(0.4, 1.6, false, 1.0, "Stable", "Volatile"),
        /** Neutral colour marker that makes lineages visible. Wraps around. */
        HUE(0.0, 1.0, true, 0.3, null, null);

        private final double min;
        private final double max;
        private final boolean wraps;
        private final double mutationScale;
        private final String lowLabel;
        private final String highLabel;

        Trait(double min, double max, boolean wraps, double mutationScale, String lowLabel, String highLabel) {
            this.min = min;
            this.max = max;
            this.wraps = wraps;
            this.mutationScale = mutationScale;
            this.lowLabel = lowLabel;
            this.highLabel = highLabel;
        }

        /** Name shown when this trait sits near the bottom of its range; null if it has none. */
        public String lowLabel() { return lowLabel; }

        /** Name shown when this trait sits near the top of its range; null if it has none. */
        public String highLabel() { return highLabel; }

        public double min() { return min; }

        public double max() { return max; }

        public double range() { return max - min; }

        public double mid() { return (min + max) / 2.0; }

        /** Maps a real value to 0..1 within this trait's range. */
        public double normalize(double value) { return (value - min) / (max - min); }

        double constrain(double value) {
            if (wraps) {
                double r = range();
                double x = (value - min) % r;
                if (x < 0) x += r;
                return min + x;
            }
            return Math.max(min, Math.min(max, value));
        }
    }

    /** A gene sitting in the outer quarter of its range, with the name it earns there. */
    public record Mark(Trait trait, boolean high, String label) {}

    private static final Trait[] TRAITS = Trait.values();

    /** Most special mutations one sheep can carry; a newer one pushes an old one out. */
    public static final int MAX_SPECIALS = 3;

    /** Chance that a founder starts with one special mutation. */
    private static final double FOUNDER_SPECIAL_CHANCE = 0.10;

    private final double[] values;
    private final List<Mutation> specials;

    private Genome(double[] values, List<Mutation> specials) {
        this.values = values;
        this.specials = List.copyOf(specials);
    }

    /**
     * Creates a founder genome. Non-wrapping traits start near the middle of
     * their range, scattered by {@code spread} (0 = identical, 1 = full range);
     * the hue is fully random so founders form distinct lineages. A few founders
     * start out with a special mutation.
     */
    public static Genome founder(double spread, RandomGenerator rng) {
        double[] v = new double[TRAITS.length];
        for (Trait t : TRAITS) {
            if (t == Trait.HUE) {
                v[t.ordinal()] = rng.nextDouble();
            } else {
                double offset = (rng.nextDouble() * 2.0 - 1.0) * spread * t.range() / 2.0;
                v[t.ordinal()] = t.constrain(t.mid() + offset);
            }
        }
        List<Mutation> specials = new ArrayList<>();
        if (rng.nextDouble() < FOUNDER_SPECIAL_CHANCE) specials.add(Mutation.generate(rng));
        return new Genome(v, specials);
    }

    /** Crossover where each special mutation of a parent is passed on with a 50% chance. */
    public static Genome crossover(Genome a, Genome b, RandomGenerator rng) {
        return crossover(a, b, rng, 0.5);
    }

    /**
     * Mixes two parents: each functional trait is inherited whole from one parent or the
     * other, while the hue is blended, so red and blue parents have a purple child. Each
     * special mutation of either parent is passed on with probability {@code inheritance}.
     */
    public static Genome crossover(Genome a, Genome b, RandomGenerator rng, double inheritance) {
        double[] v = new double[TRAITS.length];
        for (Trait t : TRAITS) {
            if (t == Trait.HUE) {
                v[t.ordinal()] = blendHue(a.hue(), b.hue());
            } else {
                v[t.ordinal()] = rng.nextBoolean() ? a.get(t) : b.get(t);
            }
        }
        List<Mutation> inherited = new ArrayList<>();
        inherit(inherited, a.specials, rng, inheritance);
        inherit(inherited, b.specials, rng, inheritance);
        while (inherited.size() > MAX_SPECIALS) inherited.remove(rng.nextInt(inherited.size()));
        return new Genome(v, inherited);
    }

    private static void inherit(List<Mutation> into, List<Mutation> from, RandomGenerator rng, double chance) {
        for (Mutation m : from) {
            if (rng.nextDouble() < chance && !into.contains(m)) into.add(m);
        }
    }

    /** Midpoint of two hues on the colour wheel, taking the shorter way around. */
    private static double blendHue(double a, double b) {
        double delta = b - a;
        if (delta > 0.5) delta -= 1.0;
        if (delta < -0.5) delta += 1.0;
        return Trait.HUE.constrain(a + delta / 2.0);
    }

    /** Mutated copy without any chance of a new special mutation. */
    public Genome mutate(RandomGenerator rng, double rate, double strength) {
        return mutate(rng, rate, strength, 0.0);
    }

    /**
     * Returns a mutated copy. Each trait mutates with probability {@code rate}
     * by a Gaussian step whose std. deviation is {@code strength} of the range.
     * With probability {@code specialChance} the copy also gains a freshly generated
     * {@link Mutation}. Both chances are scaled by this genome's mutability gene.
     */
    public Genome mutate(RandomGenerator rng, double rate, double strength, double specialChance) {
        double volatility = mutability();
        double effectiveRate = Math.min(1.0, rate * volatility);
        double[] v = values.clone();
        for (Trait t : TRAITS) {
            if (rng.nextDouble() < effectiveRate) {
                double delta = rng.nextGaussian() * strength * t.range() * t.mutationScale;
                v[t.ordinal()] = t.constrain(v[t.ordinal()] + delta);
            }
        }

        List<Mutation> kept = specials;
        if (rng.nextDouble() < Math.min(1.0, specialChance * volatility)) {
            kept = new ArrayList<>(specials);
            while (kept.size() >= MAX_SPECIALS) kept.remove(rng.nextInt(kept.size())); // make room for the new one
            kept.add(Mutation.generate(rng));
        }
        return new Genome(v, kept);
    }

    /** Copy with one gene moved by {@code fraction} of its range (negative lowers it), kept inside its range. */
    public Genome nudged(Trait trait, double fraction) {
        double[] v = values.clone();
        v[trait.ordinal()] = trait.constrain(v[trait.ordinal()] + fraction * trait.range());
        return new Genome(v, specials);
    }

    public double get(Trait trait) { return values[trait.ordinal()]; }

    /** Product of every special mutation's factor on {@code stat}. */
    private double mod(Mutation.Stat stat) {
        double f = 1.0;
        for (Mutation m : specials) f *= m.factor(stat);
        return f;
    }

    // --- Effective stats: the gene combined with any special mutations and perk costs ---

    public double speed() { return get(Trait.SPEED) * mod(Mutation.Stat.SPEED); }

    public double size() { return get(Trait.SIZE); }

    public double vision() { return get(Trait.VISION) * mod(Mutation.Stat.VISION); }

    public double hue() { return get(Trait.HUE); }

    public double longevity() { return get(Trait.LONGEVITY) * mod(Mutation.Stat.LIFESPAN); }

    /** Upkeep multiplier: the gene, special mutations, and the price of resistances and rich digestion. */
    public double metabolism() {
        return get(Trait.METABOLISM) * mod(Mutation.Stat.UPKEEP) * perkUpkeep();
    }

    public double thirstRate() { return get(Trait.THIRST_RATE) * mod(Mutation.Stat.THIRST); }

    public double griefSensitivity() { return get(Trait.GRIEF_SENSITIVITY) * mod(Mutation.Stat.GRIEF); }

    public double restTime() { return get(Trait.REST_TIME) * mod(Mutation.Stat.REST); }

    /** Multiplier on the energy gained from each piece of food. */
    public double foodMultiplier() { return get(Trait.DIGESTION) * mod(Mutation.Stat.FOOD); }

    /** Multiplier on the energy a parent pays to have a child. */
    public double birthCostMultiplier() { return mod(Mutation.Stat.BIRTH_COST); }

    /** Multiplier on this sheep's own chances of mutating when it is born. */
    public double mutability() { return get(Trait.MUTABILITY); }

    /**
     * Small upkeep price for the survival perks. Each resistance costs up to 8% of upkeep at the
     * top of its range (and saves as much at the bottom); rich digestion costs 0.8x its bonus.
     */
    private double perkUpkeep() {
        double resist = (Trait.COLD_RESISTANCE.normalize(get(Trait.COLD_RESISTANCE)) - 0.5)
                + (Trait.HEAT_TOLERANCE.normalize(get(Trait.HEAT_TOLERANCE)) - 0.5)
                + (Trait.SURE_FOOT.normalize(get(Trait.SURE_FOOT)) - 0.5);
        return (1.0 + 0.16 * resist) * (1.0 + 0.8 * (get(Trait.DIGESTION) - 1.0));
    }

    // --- Biome effects ---

    /** Upkeep multiplier in a biome: only a factor above 1 (the cold) is reduced by cold resistance. */
    public double coldUpkeep(double biomeFactor) {
        if (biomeFactor <= 1.0) return biomeFactor;
        return 1.0 + (biomeFactor - 1.0) * mod(Mutation.Stat.COLD) / get(Trait.COLD_RESISTANCE);
    }

    /** Thirst multiplier in a biome: only a factor above 1 (the desert) is reduced by heat tolerance. */
    public double desertThirst(double biomeFactor) {
        if (biomeFactor <= 1.0) return biomeFactor;
        return 1.0 + (biomeFactor - 1.0) * mod(Mutation.Stat.HEAT) / get(Trait.HEAT_TOLERANCE);
    }

    /** Walking speed multiplier in a biome: only a factor below 1 (forest, swamp) is softened by sure-footedness. */
    public double terrainSpeed(double biomeFactor) {
        if (biomeFactor >= 1.0) return biomeFactor;
        double penalty = (1.0 - biomeFactor) * mod(Mutation.Stat.TERRAIN) / get(Trait.SURE_FOOT);
        return 1.0 - Math.max(0.0, Math.min(0.9, penalty));
    }

    // --- Mutation labels ---

    /** A trait counts as a mutation once it sits in the outer quarter of its range. */
    private static final double MUTATION_EDGE = 0.25;

    /** Genes sitting near an extreme of their range, with the label each earns. */
    public List<Mark> marks() {
        List<Mark> marks = new ArrayList<>();
        for (Trait t : TRAITS) {
            if (t.lowLabel() == null) continue;
            double n = t.normalize(get(t));
            if (n <= MUTATION_EDGE) marks.add(new Mark(t, false, t.lowLabel()));
            else if (n >= 1.0 - MUTATION_EDGE) marks.add(new Mark(t, true, t.highLabel()));
        }
        return marks;
    }

    /** The procedurally generated mutations this sheep carries. */
    public List<Mutation> specials() { return specials; }

    /** Names of gene marks followed by special mutations, e.g. "Swift", "Stoic", "Fleet Marrow". */
    public List<String> mutations() {
        List<String> labels = new ArrayList<>();
        for (Mark m : marks()) labels.add(m.label());
        for (Mutation m : specials) labels.add(m.name());
        return labels;
    }

    /**
     * Normalised distance (0..1) between two genomes over the functional
     * traits, ignoring the neutral hue. Useful as a diversity measure.
     */
    public double distanceTo(Genome other) {
        double sum = 0;
        int n = 0;
        for (Trait t : TRAITS) {
            if (t.wraps) continue;
            double d = t.normalize(get(t)) - t.normalize(other.get(t));
            sum += d * d;
            n++;
        }
        return Math.sqrt(sum / n);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "Genome[speed=%.2f, size=%.2f, vision=%.1f, hue=%.2f, specials=%s]",
                speed(), size(), vision(), hue(), specials);
    }
}
