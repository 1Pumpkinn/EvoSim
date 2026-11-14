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

    private Creature mate;
    private double matingRemaining;

    public Creature(long id, Genome genome, int generation,
                    double x, double z, double heading, double energy) {
        this.id = id;
        this.genome = genome;
        this.generation = generation;
        this.x = x;
        this.z = z;
        this.heading = heading;
        this.energy = energy;
    }

    public long id() { return id; }

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

    public void endMating() {
        this.mate = null;
        this.matingRemaining = 0;
    }

    public boolean isDead(double maxAgeSeconds) {
        return energy <= 0 || age >= maxAgeSeconds;
    }

    /** Energy drained per simulated second, from the trait cost model. */
    public double costPerSecond(SimConfig.Energy cost) {
        double speed = genome.speed();
        double size = genome.size();
        return cost.baseCost()
                + cost.speedCost() * speed * speed
                + cost.sizeCost() * size * size * size
                + cost.visionCost() * genome.vision();
    }
}
