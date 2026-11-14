package dev.pumpkin.evosim.sim;

/** Point-in-time summary of the ecosystem. */
public record Stats(
        int population,
        int food,
        int maxGeneration,
        double meanSpeed,
        double meanSize,
        double meanVision,
        /** Mean pairwise genetic distance (0..1) over a random sample. */
        double diversity,
        long births,
        long deaths,
        double elapsedSeconds
) {}
