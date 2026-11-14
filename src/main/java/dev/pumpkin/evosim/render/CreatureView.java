package dev.pumpkin.evosim.render;

import dev.pumpkin.evosim.arena.Arena;
import dev.pumpkin.evosim.genetics.Genome;
import dev.pumpkin.evosim.sim.Creature;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Renders one creature as a glowing, coloured cube. Moving it every
 * simulation step with a matching teleport duration makes the client
 * interpolate the motion smoothly.
 */
public final class CreatureView {

    private final Arena arena;
    private final Creature creature;
    private final BlockDisplay display;

    public CreatureView(Arena arena, Creature creature, ColorMode mode, int tickInterval) {
        this.arena = arena;
        this.creature = creature;

        Genome genome = creature.genome();
        float s = (float) genome.size();
        this.display = arena.world().spawn(arena.toLocation(creature.x(), creature.z()), BlockDisplay.class, d -> {
            d.setBlock(mode.block(genome).createBlockData());
            // Shift by half the scale so the cube is centred on the entity.
            d.setTransformation(new Transformation(
                    new Vector3f(-s / 2f, 0f, -s / 2f), new Quaternionf(),
                    new Vector3f(s, s, s), new Quaternionf()));
            d.setTeleportDuration(Math.max(0, Math.min(59, tickInterval)));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setGlowing(true);
            d.setGlowColorOverride(mode.color(genome));
            d.setPersistent(false);
            d.addScoreboardTag(Arena.ENTITY_TAG);
        });
    }

    /** Moves the display to the creature's current simulation position. */
    public void update() {
        if (display.isValid()) {
            display.teleport(arena.toLocation(creature.x(), creature.z()));
        }
    }

    public void recolor(ColorMode mode) {
        Genome genome = creature.genome();
        display.setBlock(mode.block(genome).createBlockData());
        display.setGlowColorOverride(mode.color(genome));
    }

    public void remove() {
        display.remove();
    }
}
