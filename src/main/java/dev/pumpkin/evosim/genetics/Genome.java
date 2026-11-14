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
        SPEED(1.0, 6.0, false, 1.0),
        /** Body scale; 1.0 is one block wide. */
        SIZE(0.3, 1.5, false, 1.0),
        /** Radius in blocks within which food and rivals are noticed. */
        VISION(3.0, 20.0, false, 1.0),
        /** Neutral colour marker that makes lineages visible. Wraps around. */
        HUE(0.0, 1.0, true, 0.3);

        private final double min;
        private final double max;
        private final boolean wraps;
        private final double mutationScale;

        Trait(double min, double max, boolean wraps, double mutationScale) {
            this.min = min;
            this.max = max;
            this.wraps = wraps;
            this.mutationScale = mutationScale;
        }

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
