package dev.pumpkin.evosim.sim;

/**
 * A kind of terrain. The factors scale how the arena treats a creature standing in it:
 * food growth, thirst, energy upkeep and walking speed.
 */
public enum Biome {
    GRASSLAND("Grassland", 1.0, 1.0, 1.0, 1.0),
    /** Plenty of food, but undergrowth slows walking. */
    FOREST("Forest", 1.6, 1.0, 1.0, 0.85),
    /** Little food and fierce thirst. */
    DESERT("Desert", 0.4, 1.8, 1.0, 1.0),
    /** Sparse food, and the cold burns extra energy. */
    TUNDRA("Tundra", 0.6, 0.7, 1.4, 1.0),
    /** Wet, so little thirst, but mud slows walking. */
    SWAMP("Swamp", 1.0, 0.5, 1.0, 0.7);

    private final String displayName;
    private final double foodFactor;
    private final double thirstFactor;
    private final double upkeepFactor;
    private final double speedFactor;

    Biome(String displayName, double foodFactor, double thirstFactor, double upkeepFactor, double speedFactor) {
        this.displayName = displayName;
        this.foodFactor = foodFactor;
        this.thirstFactor = thirstFactor;
        this.upkeepFactor = upkeepFactor;
        this.speedFactor = speedFactor;
    }

    public String displayName() { return displayName; }

    /** Relative amount of food that grows here. */
    public double foodFactor() { return foodFactor; }

    /** Multiplier on how fast hydration drains. */
    public double thirstFactor() { return thirstFactor; }

    /** Multiplier on energy upkeep. */
    public double upkeepFactor() { return upkeepFactor; }

    /** Multiplier on walking speed. */
    public double speedFactor() { return speedFactor; }
}
