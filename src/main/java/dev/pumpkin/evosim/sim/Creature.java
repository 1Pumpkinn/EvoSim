package dev.pumpkin.evosim.sim;

import dev.pumpkin.evosim.config.SimConfig;
import dev.pumpkin.evosim.genetics.Genome;

/**
 * Simulation state of one creature. Positions are arena-local blocks
 * (0..size). This class has no Bukkit dependency; rendering lives in
 * {@code CreatureView}.
 */
public final class Creature {

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
    private double lastMatedAt = Double.NEGATIVE_INFINITY;
    private double restRemaining;

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

    /** Simulation time of the last finished mating, or negative infinity if it never mated. */
    public double lastMatedAt() { return lastMatedAt; }

    public void markMated(double simTime) { this.lastMatedAt = simTime; }

    /** Seconds left of the rest a ram takes after mating; 0 when ready again. */
    public double restRemaining() { return restRemaining; }

    public boolean isResting() { return restRemaining > 0; }

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
