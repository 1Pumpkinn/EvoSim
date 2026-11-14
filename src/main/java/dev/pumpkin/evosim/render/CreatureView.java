package dev.pumpkin.evosim.render;

import dev.pumpkin.evosim.arena.Arena;
import dev.pumpkin.evosim.genetics.Genome;
import dev.pumpkin.evosim.sim.Creature;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Sheep;

/**
 * Renders one creature as a real sheep. Its AI is off, so the simulation
 * drives it by teleporting every step; the client turns that movement into
 * the normal walking animation. Wool colour shows a gene, the body scale is
 * the size gene (newborns grow up), and mating pairs hop and shed hearts.
 */
public final class CreatureView {

    /** Newborns start at this fraction of their adult size. */
    private static final double BABY_SCALE = 0.4;

    private final Arena arena;
    private final Creature creature;
    private final double maturitySeconds;
    private final Sheep sheep;

    private double appliedScale = -1;
    private int updates;

    public CreatureView(Arena arena, Creature creature, ColorMode mode, double maturitySeconds) {
        this.arena = arena;
        this.creature = creature;
        this.maturitySeconds = maturitySeconds;

        Genome genome = creature.genome();
        this.sheep = arena.world().spawn(location(), Sheep.class, s -> {
            s.setAI(false);
            s.setGravity(false);
            s.setSilent(true);
            s.setInvulnerable(true);
            s.setCollidable(false);
            s.setCanPickupItems(false);
            s.setRemoveWhenFarAway(false);
            s.setPersistent(false);
            s.setColor(mode.dye(genome));
            s.addScoreboardTag(Arena.ENTITY_TAG);
        });
        applyScale();
    }

    /** Moves the mob to the creature's simulated position and plays mating effects. */
    public void update() {
        if (!sheep.isValid()) return;
        applyScale();
        sheep.teleport(location());

        if (creature.isMating() && updates++ % 4 == 0) {
            arena.world().spawnParticle(Particle.HEART, location().add(0, sheep.getHeight() + 0.2, 0),
                    1, 0.25, 0.1, 0.25, 0);
        }
    }

    /** A burst of hearts, played when this creature is born. */
    public void playBirthEffect() {
        if (!sheep.isValid()) return;
        arena.world().spawnParticle(Particle.HEART, location().add(0, sheep.getHeight() + 0.3, 0),
                8, 0.4, 0.3, 0.4, 0);
    }

    public void recolor(ColorMode mode) {
        sheep.setColor(mode.dye(creature.genome()));
    }

    public void remove() {
        sheep.remove();
    }

    private Location location() {
        Location loc = arena.toLocation(creature.x(), creature.z());
        // Minecraft yaw 0 faces +z; the simulation heading 0 faces +x.
        loc.setYaw((float) (Math.toDegrees(creature.heading()) - 90.0));
        if (creature.isMating()) {
            loc.add(0, 0.25 * Math.abs(Math.sin(creature.age() * 9.0)), 0); // happy hop
        }
        return loc;
    }

    /** Grows from baby to adult size, scaled by the size gene. */
    private void applyScale() {
        double growth = Math.min(1.0, BABY_SCALE + (1.0 - BABY_SCALE) * creature.age() / maturitySeconds);
        double scale = creature.genome().size() * growth;
        if (Math.abs(scale - appliedScale) > 0.005) {
            AttributeInstance attribute = sheep.getAttribute(Attribute.SCALE);
            if (attribute != null) {
                attribute.setBaseValue(scale);
            }
            appliedScale = scale;
        }
    }
}
