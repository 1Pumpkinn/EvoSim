package dev.pumpkin.evosim.sim;

/** A stationary piece of food at arena-local coordinates. Rare food is worth much more. */
public record Food(long id, double x, double z, double energy, boolean rare) {}
