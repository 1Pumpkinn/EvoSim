package dev.pumpkin.evosim.sim;

import dev.pumpkin.evosim.config.SimConfig;

import java.util.ArrayList;
import java.util.List;

/** A circular pond in arena-local coordinates. Creatures standing in it drink. */
public record Pond(double x, double z, double radius) {

    public boolean contains(double px, double pz) {
        double dx = px - x;
        double dz = pz - z;
        return dx * dx + dz * dz <= radius * radius;
    }

    /** Distance from a point to the pond's edge; negative inside. */
    public double edgeDistance(double px, double pz) {
        return Math.hypot(px - x, pz - z) - radius;
    }

    /** The fixed pond layout for an arena: one in the middle, or a ring around it. */
    public static List<Pond> layout(SimConfig config) {
        int count = config.needs().pondCount();
        double size = config.arena().size();
        double radius = Math.min(config.needs().pondRadius(), size / 6.0);
        List<Pond> ponds = new ArrayList<>();
        if (count == 1) {
            ponds.add(new Pond(size / 2.0, size / 2.0, radius));
            return ponds;
        }
        double ring = size * 0.3;
        for (int i = 0; i < count; i++) {
            double angle = 2.0 * Math.PI * i / count + Math.PI / 4.0;
            ponds.add(new Pond(size / 2.0 + ring * Math.cos(angle),
                    size / 2.0 + ring * Math.sin(angle), radius));
        }
        return ponds;
    }
}
