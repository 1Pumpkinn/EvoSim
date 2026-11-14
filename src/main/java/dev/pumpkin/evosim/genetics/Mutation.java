package dev.pumpkin.evosim.genetics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.random.RandomGenerator;

/**
 * A procedurally generated, heritable mutation. Every mutation is rolled from scratch: it gets one
 * benefit and, usually, one drawback, each on a random stat with a random size, plus a generated
 * name. Two mutations never have to be alike, so evolution has new material to work with every run.
 *
 * <p>A mutation is identified by its {@code id}; children inherit the very same mutation.
 */
public final class Mutation {

    /** A stat that a mutation can nudge, and how it should be read. */
    public enum Stat {
        SPEED("Speed", false, "How fast it walks.",
                "Fleet", "Swift-blooded", "Quick"),
        VISION("Vision", false, "How far it notices food, water, mates and other sheep.",
                "Keen", "Sharp-eyed", "Farsighted"),
        LIFESPAN("Lifespan", false, "How long it lives before dying of old age.",
                "Enduring", "Ageless", "Hale"),
        UPKEEP("Energy upkeep", true, "Energy burned every second just to stay alive.",
                "Thrifty", "Frugal", "Lean"),
        THIRST("Thirst", true, "How fast hydration drains.",
                "Dry", "Camel-kin", "Cistern"),
        FOOD("Food energy", false, "Energy gained from each piece of food it eats.",
                "Gorging", "Nourished", "Hearty"),
        GRIEF("Grief", true, "Mood lost when other sheep die nearby.",
                "Placid", "Numb", "Steadfast"),
        REST("Ram rest time", true, "How long a ram rests after mating before he looks for a mate again.",
                "Restless", "Tireless", "Spry"),
        COLD("Cold penalty", true, "Extra energy burned in the tundra.",
                "Frost-lined", "Rime", "Snowbound"),
        HEAT("Desert thirst", true, "Extra thirst suffered in the desert.",
                "Sunbaked", "Dune", "Mirage"),
        TERRAIN("Slow-ground penalty", true, "How much forest and swamp slow its walking.",
                "Mudfoot", "Bogwalker", "Briar"),
        BIRTH_COST("Birth cost", true, "Energy each parent pays to have a child.",
                "Fertile", "Broody", "Fecund");

        private final String displayName;
        private final boolean lowerIsBetter;
        private final String description;
        private final String[] adjectives;

        Stat(String displayName, boolean lowerIsBetter, String description, String... adjectives) {
            this.displayName = displayName;
            this.lowerIsBetter = lowerIsBetter;
            this.description = description;
            this.adjectives = adjectives;
        }

        public String displayName() { return displayName; }

        /** True for costs and penalties, where a factor below 1 is the good direction. */
        public boolean lowerIsBetter() { return lowerIsBetter; }

        public String description() { return description; }

        String adjective(RandomGenerator rng) { return adjectives[rng.nextInt(adjectives.length)]; }
    }

    /** One change a mutation makes: the stat is multiplied by {@code factor}. */
    public record Effect(Stat stat, double factor) {

        /** True when this effect helps the sheep. */
        public boolean beneficial() {
            return stat.lowerIsBetter() ? factor < 1.0 : factor > 1.0;
        }

        /** Signed percent change, such as "+25%" or "-15%". */
        public String percent() {
            return String.format(Locale.ROOT, "%+.0f%%", (factor - 1.0) * 100.0);
        }
    }

    /** How strong a mutation's benefit is. */
    public enum Rarity {
        MINOR("Minor"), NOTABLE("Notable"), MAJOR("Major");

        private final String displayName;

        Rarity(String displayName) { this.displayName = displayName; }

        public String displayName() { return displayName; }
    }

    private static final String[] NOUNS = {
            "Blood", "Marrow", "Wool", "Hoof", "Gut", "Sinew", "Spark", "Strand", "Lung", "Heart", "Bone", "Fleece",
            "Gland", "Pelt", "Nerve", "Fang"
    };

    /** Chance that a generated mutation also carries a drawback. */
    private static final double DRAWBACK_CHANCE = 0.65;

    private final long id;
    private final String name;
    private final Rarity rarity;
    private final List<Effect> effects;

    private Mutation(long id, String name, Rarity rarity, List<Effect> effects) {
        this.id = id;
        this.name = name;
        this.rarity = rarity;
        this.effects = List.copyOf(effects);
    }

    /** Rolls a brand new mutation: a benefit on one stat and, usually, a drawback on another. */
    public static Mutation generate(RandomGenerator rng) {
        Stat[] stats = Stat.values();
        Stat boonStat = stats[rng.nextInt(stats.length)];
        double boon = 0.10 + 0.25 * rng.nextDouble();

        List<Effect> effects = new ArrayList<>(2);
        effects.add(new Effect(boonStat, boonStat.lowerIsBetter() ? 1.0 - boon : 1.0 + boon));

        if (rng.nextDouble() < DRAWBACK_CHANCE) {
            Stat badStat;
            do {
                badStat = stats[rng.nextInt(stats.length)];
            } while (badStat == boonStat);
            double cost = 0.08 + 0.17 * rng.nextDouble();
            effects.add(new Effect(badStat, badStat.lowerIsBetter() ? 1.0 + cost : 1.0 - cost));
        }

        Rarity rarity = boon < 0.18 ? Rarity.MINOR : boon < 0.27 ? Rarity.NOTABLE : Rarity.MAJOR;
        String name = boonStat.adjective(rng) + " " + NOUNS[rng.nextInt(NOUNS.length)];
        return new Mutation(rng.nextLong(), name, rarity, effects);
    }

    public String name() { return name; }

    public Rarity rarity() { return rarity; }

    public List<Effect> effects() { return effects; }

    /** Combined factor this mutation applies to {@code stat}; 1 if it does not touch it. */
    public double factor(Stat stat) {
        double f = 1.0;
        for (Effect e : effects) {
            if (e.stat() == stat) f *= e.factor();
        }
        return f;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Mutation m && m.id == id;
    }

    @Override
    public int hashCode() { return Long.hashCode(id); }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(name).append(" [");
        for (int i = 0; i < effects.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(effects.get(i).percent()).append(' ').append(effects.get(i).stat().displayName());
        }
        return sb.append(']').toString();
    }
}
