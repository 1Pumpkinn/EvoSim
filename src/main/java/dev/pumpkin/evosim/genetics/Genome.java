package dev.pumpkin.evosim.genetics;

import java.util.Locale;
import java.util.random.RandomGenerator;

/**
 * Immutable set of heritable traits. Every trait is stored in its real unit
 * and always kept inside the trait's [min, max] range.
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

    private static final Trait[] TRAITS = Trait.values();

    private final double[] values;

    private Genome(double[] values) {
        this.values = values;
    }

    /**
     * Creates a founder genome. Non-wrapping traits start near the middle of
     * their range, scattered by {@code spread} (0 = identical, 1 = full range);
     * the hue is fully random so founders form distinct lineages.
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
        return new Genome(v);
    }

    /**
     * Mixes two parents: each functional trait is inherited whole from one parent or the
     * other, while the hue is blended, so red and blue parents have a purple child.
     */
    public static Genome crossover(Genome a, Genome b, RandomGenerator rng) {
        double[] v = new double[TRAITS.length];
        for (Trait t : TRAITS) {
            if (t == Trait.HUE) {
                v[t.ordinal()] = blendHue(a.hue(), b.hue());
            } else {
                v[t.ordinal()] = rng.nextBoolean() ? a.get(t) : b.get(t);
            }
        }
        return new Genome(v);
    }

    /** Midpoint of two hues on the colour wheel, taking the shorter way around. */
    private static double blendHue(double a, double b) {
        double delta = b - a;
        if (delta > 0.5) delta -= 1.0;
        if (delta < -0.5) delta += 1.0;
        return Trait.HUE.constrain(a + delta / 2.0);
    }

    /**
     * Returns a mutated copy. Each trait mutates with probability {@code rate}
     * by a Gaussian step whose std. deviation is {@code strength} of the range.
     */
    public Genome mutate(RandomGenerator rng, double rate, double strength) {
        double[] v = values.clone();
        for (Trait t : TRAITS) {
            if (rng.nextDouble() < rate) {
                double delta = rng.nextGaussian() * strength * t.range() * t.mutationScale;
                v[t.ordinal()] = t.constrain(v[t.ordinal()] + delta);
            }
        }
        return new Genome(v);
    }

    public double get(Trait trait) { return values[trait.ordinal()]; }

    public double speed() { return get(Trait.SPEED); }

    public double size() { return get(Trait.SIZE); }

    public double vision() { return get(Trait.VISION); }

    public double hue() { return get(Trait.HUE); }

    public double longevity() { return get(Trait.LONGEVITY); }

    public double metabolism() { return get(Trait.METABOLISM); }

    public double thirstRate() { return get(Trait.THIRST_RATE); }

    public double griefSensitivity() { return get(Trait.GRIEF_SENSITIVITY); }

    public double restTime() { return get(Trait.REST_TIME); }

    /** A trait counts as a mutation once it sits in the outer quarter of its range. */
    private static final double MUTATION_EDGE = 0.25;

    /** Names of the traits that sit near an extreme of their range, e.g. "Swift", "Stoic". */
    public java.util.List<String> mutations() {
        java.util.List<String> labels = new java.util.ArrayList<>();
        for (Trait t : TRAITS) {
            if (t.lowLabel() == null) continue;
            double n = t.normalize(get(t));
            if (n <= MUTATION_EDGE) labels.add(t.lowLabel());
            else if (n >= 1.0 - MUTATION_EDGE) labels.add(t.highLabel());
        }
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
        return String.format(Locale.ROOT, "Genome[speed=%.2f, size=%.2f, vision=%.1f, hue=%.2f]",
                speed(), size(), vision(), hue());
    }
}
