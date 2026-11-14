package dev.pumpkin.evosim.sim;

/** A stationary piece of food at arena-local coordinates. */
public record Food(long id, double x, double z, double energy) {}
