package dev.pumpkin.evosim.sim;

/** How many creatures died of one cause, and how old they were on average (simulated seconds). */
public record DeathTally(DeathCause cause, int count, double meanAgeSeconds) {}
