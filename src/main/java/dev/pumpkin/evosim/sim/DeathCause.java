package dev.pumpkin.evosim.sim;

/** What killed a creature. Dehydration drains energy, so it is told apart from plain starvation. */
public enum DeathCause {
    OLD_AGE("Old age"),
    STARVATION("Starvation"),
    DEHYDRATION("Dehydration");

    private final String label;

    DeathCause(String label) { this.label = label; }

    public String label() { return label; }
}
