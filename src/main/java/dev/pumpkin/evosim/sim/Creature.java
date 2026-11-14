package dev.pumpkin.evosim.sim;

import dev.pumpkin.evosim.config.SimConfig;
import dev.pumpkin.evosim.genetics.Genome;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Simulation state of one creature. Positions are arena-local blocks
 * (0..size). This class has no Bukkit dependency; rendering lives in
 * {@code CreatureView}.
 */
public final class Creature {

    /** What the creature is doing right now, as decided by the steering code. */
    public enum Activity {
        WANDERING("Wandering"),
        TO_FOOD("Heading for food"),
        TO_WATER("Heading for water"),
        FOOD_ON_THE_WAY("Grabbing food on the way to water"),
        SEARCHING_WATER("Searching for water"),
        TO_MATE("Looking for a mate"),
        DRINKING("Drinking"),
        MATING("Mating");

        private final String label;

        Activity(String label) { this.label = label; }

        public String label() { return label; }
    }

    private final long id;
    private final Genome genome;
    private final int generation;

    private double x;
    private double z;
    private double heading;
    private double energy;
    private double age;
    private final boolean female;
    private final boolean sameSex;
    private final long parentAId;
    private final long parentBId;
    private double hydration = 100;
    private double mood = 100;

    private Creature mate;
    private double matingRemaining;
    private double restRemaining;
    private Food targetFood;
    private boolean drinking;
    private Activity activity = Activity.WANDERING;
    /** Ponds this creature has seen, so it can walk back to water that is out of sight. */
    private final Set<Pond> knownPonds = new LinkedHashSet<>();

    public Creature(long id, Genome genome, int generation,
                    double x, double z, double heading, double energy,
                    boolean female, boolean sameSex, long parentAId, long parentBId) {
        this.id = id;
        this.genome = genome;
        this.generation = generation;
        this.x = x;
        this.z = z;
        this.heading = heading;
        this.energy = energy;
        this.female = female;
        this.sameSex = sameSex;
        this.parentAId = parentAId;
        this.parentBId = parentBId;
    }

    public long id() { return id; }

    /** Ewe (true) or ram (false). */
    public boolean isFemale() { return female; }

    /** True for parent and child, and for siblings (one or both parents shared). Founders have no parents. */
    public boolean isCloseRelativeOf(Creature other) {
        if (other == this) return true;
        if (parentAId == other.id || parentBId == other.id) return true;
        if (other.parentAId == id || other.parentBId == id) return true;
        return sharesParent(parentAId, other) || sharesParent(parentBId, other);
    }

    private static boolean sharesParent(long parentId, Creature other) {
        return parentId != 0 && (parentId == other.parentAId || parentId == other.parentBId);
    }

    /** Pairs with its own sex instead of the other one. */
    public boolean isSameSex() { return sameSex; }

    /** Water level, 0..100. */
    public double hydration() { return hydration; }

    public void addHydration(double delta) { hydration = Math.max(0, Math.min(100, hydration + delta)); }

    /** Wellbeing, 0..100; low values mean depression. */
    public double mood() { return mood; }

    public void addMood(double delta) { mood = Math.max(0, Math.min(100, mood + delta)); }

    public Genome genome() { return genome; }

    /** 0 for founders, parent's generation + 1 for children. */
    public int generation() { return generation; }

    public double x() { return x; }

    public double z() { return z; }

    /** Facing angle in radians; 0 points along +x, pi/2 along +z. */
    public double heading() { return heading; }

    public double energy() { return energy; }

    /** Simulated seconds lived. */
    public double age() { return age; }

    /** Body radius in blocks. */
    public double radius() { return genome.size() / 2.0; }

    public void moveTo(double x, double z) {
        this.x = x;
        this.z = z;
    }

    public void setHeading(double heading) { this.heading = heading; }

    public void addEnergy(double delta) { this.energy += delta; }

    public void advanceAge(double seconds) { this.age += seconds; }

    public boolean isAdult(double maturitySeconds) { return age >= maturitySeconds; }

    /** True while paired up with a partner, waiting for the child to be born. */
    public boolean isMating() { return mate != null; }

    public Creature mate() { return mate; }

    public double matingRemaining() { return matingRemaining; }

    public void beginMating(Creature partner, double seconds) {
        this.mate = partner;
        this.matingRemaining = seconds;
    }

    public void advanceMating(double seconds) { this.matingRemaining -= seconds; }

    /** Seconds left of the rest a creature takes after mating; 0 when ready again. */
    public double restRemaining() { return restRemaining; }

    public boolean isResting() { return restRemaining > 0; }

    /** The food this creature is heading for; kept until eaten or out of sight so it does not dither. */
    public Food targetFood() { return targetFood; }

    public void setTargetFood(Food food) { this.targetFood = food; }

    /** Standing in a pond and drinking until full. */
    public boolean isDrinking() { return drinking; }

    public void setDrinking(boolean drinking) { this.drinking = drinking; }

    /** What it is doing: mating wins over whatever steering last decided. */
    public Activity activity() { return isMating() ? Activity.MATING : activity; }

    public void setActivity(Activity activity) { this.activity = activity; }

    /** Ponds it has seen so far. */
    public Set<Pond> knownPonds() { return Collections.unmodifiableSet(knownPonds); }

    public void rememberPond(Pond pond) { knownPonds.add(pond); }

    public void beginRest(double seconds) { this.restRemaining = Math.max(restRemaining, seconds); }

    public void tickRest(double seconds) { this.restRemaining = Math.max(0, restRemaining - seconds); }

    public void endMating() {
        this.mate = null;
        this.matingRemaining = 0;
    }

    public boolean isDead(double maxAgeSeconds) {
        return energy <= 0 || age >= maxAgeSeconds * genome.longevity();
    }

    /** Energy drained per simulated second, from the trait cost model. */
    public double costPerSecond(SimConfig.Energy cost) {
        double speed = genome.speed();
        double size = genome.size();
        return (cost.baseCost()
                + cost.speedCost() * speed * speed
                + cost.sizeCost() * size * size * size
                + cost.visionCost() * genome.vision()) * genome.metabolism();
    }
}
