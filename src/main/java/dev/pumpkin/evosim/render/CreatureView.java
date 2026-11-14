package dev.pumpkin.evosim.render;

import dev.pumpkin.evosim.arena.Arena;
import dev.pumpkin.evosim.config.SimConfig;
import dev.pumpkin.evosim.genetics.Genome;
import dev.pumpkin.evosim.sim.Creature;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders one creature as a real sheep. Its AI is off, so the simulation
 * drives it by teleporting every step; the client turns that movement into
 * the normal walking animation. Wool colour shows a gene, the body scale is
 * the size gene (newborns grow up), and mating pairs stand still under hearts.
 *
 * <p>While mating, the lower-id partner mounts the other. A sheep entity
 * cannot tilt its body, so with {@code animation.tilted-mount} on, that
 * partner's sheep is hidden and an {@link ItemDisplay} showing the pack's
 * {@code evosim:mounting_sheep} model is drawn in its place, tilted nose-up.
 */
public final class CreatureView {

    /**
     * Ticks a mob takes to glide to a teleported position on the client. Displays that follow a
     * sheep use the same glide, otherwise they run ahead of it and end up inside its body.
     */
    private static final int MOB_GLIDE_TICKS = 3;

    /** Newborns start at this fraction of their adult size. */
    private static final double BABY_SCALE = 0.4;

    /** Fallback (flat) pose: gap between the mates, per unit of summed body size. */
    private static final double MOUNT_GAP = 0.3;

    /** Fallback (flat) pose: how far the mounting mate is raised, per unit of the mounted mate's size. */
    private static final double MOUNT_LIFT = 0.55;

    /** Fallback (flat) pose: upward head tilt in degrees. */
    private static final float MOUNT_HEAD_PITCH = 25f;

    /** Tilted pose: nose-up angle of the mounting mate in degrees. */
    private static final float MOUNT_TILT_DEGREES = 40f;

    /** Tilted pose: hip distance behind the mounted mate's centre, per unit of its body size. */
    private static final double MOUNT_HIP_DISTANCE = 0.9;

    /** Rocking motion of the mounting mate: speed (radians per simulated second). */
    private static final double ROCK_SPEED = 15.0;

    /** Rocking motion: back-and-forth travel in blocks, per unit of the mounted mate's size. */
    private static final double ROCK_DISTANCE = 0.12;

    /** Rocking motion: extra tilt swing in degrees. */
    private static final double ROCK_TILT_DEGREES = 4.0;

    /** Model pixels from the ground to the hip pivot of the pack model (see resourcepack/). */
    private static final double MODEL_HIP_HEIGHT = 13.0 / 16.0;

    /** White particles shown around a mating pair. */
    private static final Particle.DustOptions WHITE_DUST = new Particle.DustOptions(Color.WHITE, 1.2f);

    /** Ram stick: size per unit of the sheep's scale. It hangs under the belly; longer while mounting. */
    private static final double STICK_LENGTH = 0.9;
    private static final double STICK_MOUNT_LENGTH = 1.2;
    private static final double STICK_THICKNESS = 0.12;

    /** Ram stick: height under the belly, and how far behind the sheep's centre it starts, per unit of scale. */
    private static final double STICK_BELLY_HEIGHT = 0.55;
    private static final double STICK_BACK = 0.35;

    /**
     * Ewe mark: a hollow oval-ish diamond ring on the behind. Size, distance behind the centre and height, per unit of scale.
     * The back distance sits clear of the wool (which ends about 0.61 behind the centre) so the ring stays visible while walking.
     */
    private static final double EWE_MARK_WIDTH = 0.36;
    private static final double EWE_MARK_HEIGHT = 0.5;
    private static final double EWE_MARK_DEPTH = 0.1;
    private static final double EWE_RING_THICKNESS = 0.07;
    private static final double EWE_MARK_BACK = 0.8;
    private static final double EWE_MARK_RISE = 0.9;

    /** Rams carry a wooden stick, ewes a magenta mark. */
    private static final Material RAM_STICK = Material.OAK_PLANKS;
    private static final Material EWE_MARK = Material.MAGENTA_CONCRETE;

    private static final NamespacedKey MOUNT_MODEL = new NamespacedKey("evosim", "mounting_sheep");

    private final Arena arena;
    private final Creature creature;
    private final double maturitySeconds;
    private final boolean tiltedMount;
    private final SimConfig config;
    private final Sheep sheep;
    private TextDisplay statBar;

    private DyeColor dye;
    private ItemDisplay mountDisplay;
    private BlockDisplay markDisplay;
    private final List<BlockDisplay> ringParts = new ArrayList<>();

    private double appliedScale = -1;
    private int updates;
    private int ticks;

    public CreatureView(Arena arena, Creature creature, ColorMode mode, SimConfig config) {
        this.arena = arena;
        this.creature = creature;
        this.config = config;
        this.maturitySeconds = config.population().maturitySeconds();
        this.tiltedMount = config.animation().tiltedMount();
        this.dye = mode.dye(creature.genome());

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
            s.setColor(dye);
            if (creature.isSameSex()) {
                // A sheep named "jeb_" has rainbow wool.
                s.customName(Component.text("jeb_"));
                s.setCustomNameVisible(false);
            }
            s.addScoreboardTag(Arena.ENTITY_TAG);
        });
        applyScale();

        if (config.animation().statBars()) {
            this.statBar = arena.world().spawn(statBarLocation(), TextDisplay.class, d -> {
                d.setBillboard(Display.Billboard.CENTER);
                d.setPersistent(false);
                d.setSeeThrough(false);
                d.setShadowed(true);
                d.setLineWidth(240);
                d.setAlignment(TextDisplay.TextAlignment.CENTER);
                d.setBackgroundColor(Color.fromARGB(90, 0, 0, 0));
                d.setViewRange(0.3f);
                d.setTeleportDuration(MOB_GLIDE_TICKS);
                float k = 0.45f;
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                        new Vector3f(k, k, k), new AxisAngle4f()));
                d.addScoreboardTag(Arena.ENTITY_TAG);
            });
            refreshStatBar();
        }
    }

    /** True if this view's sheep is the given entity. */
    public boolean owns(Entity entity) {
        return entity.getUniqueId().equals(sheep.getUniqueId());
    }

    public Creature creature() { return creature; }

    /** Moves the mob to the creature's simulated position and plays mating effects. */
    public void update() {
        if (!sheep.isValid()) return;
        applyScale();
        ticks++;

        if (isMountingWithModel()) {
            sheep.setInvisible(true);
            updateMountDisplay();
            if (creature.isSameSex() && ticks % 4 == 0 && mountDisplay != null) {
                mountDisplay.setItemStack(mountItem()); // cycle the rainbow tint
            }
        } else {
            removeMountDisplay();
            sheep.setInvisible(false);
        }
        sheep.teleport(location());

        updateMark();

        if (statBar != null && statBar.isValid()) {
            statBar.teleport(statBarLocation());
            if (ticks % 5 == 0) refreshStatBar();
        }

        if (creature.isMating()) {
            if (updates % 4 == 0) {
                arena.world().spawnParticle(Particle.HEART, location().add(0, sheep.getHeight() + 0.2, 0),
                        1, 0.25, 0.1, 0.25, 0);
            }
            if (updates % 2 == 0) {
                arena.world().spawnParticle(Particle.DUST, location().add(0, sheep.getHeight() * 0.5, 0),
                        3, 0.3, 0.25, 0.3, 0, WHITE_DUST);
            }
            updates++;
        }
    }

    /** A burst of hearts, played when this creature is born. */
    public void playBirthEffect() {
        if (!sheep.isValid()) return;
        arena.world().spawnParticle(Particle.HEART, location().add(0, sheep.getHeight() + 0.3, 0),
                8, 0.4, 0.3, 0.4, 0);
    }

    /** Angry particles, played when this creature was bred again too soon. */
    public void playOverbredEffect() {
        if (!sheep.isValid()) return;
        arena.world().spawnParticle(Particle.ANGRY_VILLAGER, location().add(0, sheep.getHeight() + 0.3, 0),
                4, 0.3, 0.2, 0.3, 0);
    }

    public void recolor(ColorMode mode) {
        dye = mode.dye(creature.genome());
        sheep.setColor(dye);
        if (mountDisplay != null && mountDisplay.isValid()) {
            mountDisplay.setItemStack(mountItem());
        }
    }

    /**
     * Turns the sheep into a corpse: it plays the death animation and then stays
     * lying on its side where it died. This view stops following the creature;
     * call {@link #removeCorpse()} later to clear it away.
     */
    public void playDeath() {
        if (statBar != null) {
            statBar.remove();
            statBar = null;
        }
        removeMountDisplay();
        removeMark();
        if (!sheep.isValid()) return;
        sheep.setInvisible(false);
        Location loc = sheep.getLocation();
        sheep.playEffect(EntityEffect.DEATH);
        arena.world().playSound(loc, Sound.ENTITY_SHEEP_DEATH, 1f, 1f);
        arena.world().spawnParticle(Particle.SMOKE, loc.clone().add(0, 0.4, 0), 10, 0.3, 0.2, 0.3, 0.01);
    }

    /** Clears a corpse left by {@link #playDeath()} with a small puff of smoke. */
    public void removeCorpse() {
        if (!sheep.isValid()) return;
        arena.world().spawnParticle(Particle.POOF, sheep.getLocation().add(0, 0.4, 0), 12, 0.3, 0.2, 0.3, 0.02);
        sheep.remove();
    }

    public void remove() {
        if (statBar != null) {
            statBar.remove();
            statBar = null;
        }
        removeMountDisplay();
        removeMark();
        sheep.remove();
    }

    // --- Mounting partner: tilted model ---

    /** True for the lower-id partner of a mating pair, when the tilted model is enabled. */
    private boolean isMountingWithModel() {
        Creature partner = creature.mate();
        return tiltedMount && partner != null && creature.id() < partner.id();
    }

    private void updateMountDisplay() {
        Creature partner = creature.mate();
        // Same facing as the mounted partner: its heading, turned around (see location()).
        double facing = partner.heading() + Math.PI;
        double leaderScale = partner.genome().size();
        // Rhythmic rocking: slides toward and away from the partner while the tilt swings a little.
        double rock = Math.sin(creature.age() * ROCK_SPEED);
        double dist = (MOUNT_HIP_DISTANCE - ROCK_DISTANCE * rock) * leaderScale;
        double tilt = MOUNT_TILT_DEGREES + ROCK_TILT_DEGREES * rock;
        double x = partner.x() - Math.cos(facing) * dist;
        double z = partner.z() - Math.sin(facing) * dist;

        double scale = currentScale();
        // Pivot is the hip. Lift it so the hind feet of the tilted model touch the floor.
        double lift = MODEL_HIP_HEIGHT * scale * Math.cos(Math.toRadians(tilt));

        Location loc = arena.toLocation(x, z).add(0, lift, 0);
        loc.setYaw((float) (Math.toDegrees(facing) - 90.0));
        loc.setPitch((float) -tilt); // negative = nose up

        if (mountDisplay == null || !mountDisplay.isValid()) {
            mountDisplay = arena.world().spawn(loc, ItemDisplay.class, d -> {
                d.setItemStack(mountItem());
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setPersistent(false);
                d.setTeleportDuration(1); // smooth the rocking between updates
                d.addScoreboardTag(Arena.ENTITY_TAG);
            });
        }
        float s = (float) scale;
        // Item displays draw their model turned 180 degrees about Y; turn it back so the
        // model's front (+z) matches the entity's facing and negative pitch is nose-up.
        mountDisplay.setTransformation(new Transformation(
                new Vector3f(), new AxisAngle4f((float) Math.PI, 0f, 1f, 0f),
                new Vector3f(s, s, s), new AxisAngle4f()));
        mountDisplay.teleport(loc);
    }

    private ItemStack mountItem() {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(MOUNT_MODEL);
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setColors(List.of(tint()));
        meta.setCustomModelDataComponent(cmd);
        item.setItemMeta(meta);
        return item;
    }

    /** Wool tint for the mounting model: the dye colour, or a slowly cycling rainbow. */
    private Color tint() {
        if (creature.isSameSex()) {
            float hue = (float) ((creature.age() * 0.5) % 1.0);
            return Color.fromRGB(java.awt.Color.HSBtoRGB(hue, 0.8f, 1f) & 0xFFFFFF);
        }
        return dye.getColor();
    }

    /** True for the lower-id partner of a mating pair, in either pose. */
    private boolean isMounting() {
        Creature partner = creature.mate();
        return partner != null && creature.id() < partner.id();
    }

    /**
     * Every sheep always carries a sex marker: rams a wooden stick under the belly pointing
     * forward, ewes an oval-ish mark on the behind. While mounting it follows the rocking motion.
     */
    private void updateMark() {
        double scale = currentScale();
        boolean ewe = creature.isFemale();
        Location loc;
        double facing;
        double lift;
        double back;
        if (isMounting()) {
            Creature partner = creature.mate();
            facing = partner.heading() + Math.PI;
            double leaderScale = partner.genome().size();
            double x;
            double z;
            if (tiltedMount) {
                double rock = Math.sin(creature.age() * ROCK_SPEED);
                double dist = (MOUNT_HIP_DISTANCE - ROCK_DISTANCE * rock) * leaderScale;
                x = partner.x() - Math.cos(facing) * dist;
                z = partner.z() - Math.sin(facing) * dist;
                lift = MODEL_HIP_HEIGHT * Math.cos(Math.toRadians(MOUNT_TILT_DEGREES)) + (ewe ? 0.15 : 0.0);
                back = ewe ? 0.1 : 0.0;
            } else {
                double gap = MOUNT_GAP * (creature.genome().size() + leaderScale);
                x = partner.x() - Math.cos(facing) * gap;
                z = partner.z() - Math.sin(facing) * gap;
                lift = MOUNT_LIFT * leaderScale / scale + (ewe ? EWE_MARK_RISE : STICK_BELLY_HEIGHT);
                back = ewe ? EWE_MARK_BACK : STICK_BACK;
            }
            loc = arena.toLocation(x, z);
        } else {
            loc = location();
            facing = Math.toRadians(loc.getYaw() + 90.0);
            lift = ewe ? EWE_MARK_RISE : STICK_BELLY_HEIGHT;
            back = ewe ? EWE_MARK_BACK : STICK_BACK;
        }
        loc.add(-Math.cos(facing) * back * scale, lift * scale, -Math.sin(facing) * back * scale);
        loc.setYaw((float) (Math.toDegrees(facing) - 90.0));
        loc.setPitch(0f);

        int glide = isMounting() ? 1 : MOB_GLIDE_TICKS;
        if (ewe) {
            updateRing(loc, scale, glide);
        } else {
            updateStick(loc, scale, glide);
        }
    }

    /** The ram's stick: one block display shrunk into a rod that reaches forward (+z) from the entity. */
    private void updateStick(Location loc, double scale, int glide) {
        if (markDisplay == null || !markDisplay.isValid()) {
            markDisplay = spawnMarkPart(loc, RAM_STICK);
        }
        markDisplay.setTeleportDuration(glide);
        float width = (float) (STICK_THICKNESS * scale);
        float length = (float) ((isMounting() ? STICK_MOUNT_LENGTH : STICK_LENGTH) * scale);
        markDisplay.setTransformation(new Transformation(
                new Vector3f(-width / 2f, -width / 2f, 0f), new AxisAngle4f(),
                new Vector3f(width, width, length), new AxisAngle4f()));
        markDisplay.teleport(loc);
    }

    /** The ewe's mark: four thin bars forming a hollow diamond, centred on the entity and facing backward. */
    private void updateRing(Location loc, double scale, int glide) {
        boolean spawned = ringParts.size() == 4;
        for (BlockDisplay part : ringParts) {
            if (!part.isValid()) spawned = false;
        }
        if (!spawned) {
            removeMark();
            for (int i = 0; i < 4; i++) {
                ringParts.add(spawnMarkPart(loc, EWE_MARK));
            }
        }

        float a = (float) (EWE_MARK_WIDTH * scale / 2.0);
        float b = (float) (EWE_MARK_HEIGHT * scale / 2.0);
        float thickness = (float) (EWE_RING_THICKNESS * scale);
        float depth = (float) (EWE_MARK_DEPTH * scale);
        Vector3f[] corners = {
                new Vector3f(0f, b, 0f), new Vector3f(a, 0f, 0f),
                new Vector3f(0f, -b, 0f), new Vector3f(-a, 0f, 0f)
        };
        for (int i = 0; i < 4; i++) {
            Vector3f from = corners[i];
            Vector3f to = corners[(i + 1) % 4];
            float dx = to.x - from.x;
            float dy = to.y - from.y;
            float length = (float) Math.hypot(dx, dy);
            float angle = (float) Math.atan2(dy, dx);
            // The cube spans one block from its corner: scale it into a bar, turn it along its
            // edge and shift it so its centre lands on the middle of the edge.
            Vector3f shift = new Vector3f(length / 2f, thickness / 2f, depth / 2f).rotateZ(angle).negate()
                    .add((from.x + to.x) / 2f, (from.y + to.y) / 2f, 0f);
            BlockDisplay part = ringParts.get(i);
            part.setTeleportDuration(glide);
            part.setTransformation(new Transformation(shift, new AxisAngle4f(angle, 0f, 0f, 1f),
                    new Vector3f(length, thickness, depth), new AxisAngle4f()));
            part.teleport(loc);
        }
    }

    private BlockDisplay spawnMarkPart(Location loc, Material material) {
        return arena.world().spawn(loc, BlockDisplay.class, d -> {
            d.setBlock(material.createBlockData());
            d.setBrightness(new Display.Brightness(15, 15));
            d.setPersistent(false);
            d.setTeleportDuration(MOB_GLIDE_TICKS);
            d.addScoreboardTag(Arena.ENTITY_TAG);
        });
    }

    private void removeMark() {
        if (markDisplay != null) {
            markDisplay.remove();
            markDisplay = null;
        }
        ringParts.forEach(BlockDisplay::remove);
        ringParts.clear();
    }

    private void removeMountDisplay() {
        if (mountDisplay != null) {
            mountDisplay.remove();
            mountDisplay = null;
        }
    }

    // --- Stat bar ---

    private Location statBarLocation() {
        return arena.toLocation(creature.x(), creature.z())
                .add(0, 1.5 * currentScale() + 0.45, 0);
    }

    private void refreshStatBar() {
        if (statBar == null || !statBar.isValid()) return;
        SimConfig.Needs needs = config.needs();
        double energyMax = config.population().reproductionThreshold();

        String status;
        NamedTextColor statusColor = NamedTextColor.GRAY;
        if (creature.isMating()) {
            status = "Mating";
            statusColor = NamedTextColor.LIGHT_PURPLE;
        } else if (creature.mood() < needs.depressionBelow()) {
            status = "Depressed";
            statusColor = NamedTextColor.RED;
        } else if (creature.hydration() < 25) {
            status = "Thirsty";
            statusColor = NamedTextColor.AQUA;
        } else if (creature.energy() < energyMax * 0.35) {
            status = "Hungry";
            statusColor = NamedTextColor.GOLD;
        } else {
            status = "Fine";
        }

        double mood = creature.mood();
        TextColor moodColor = mood >= 60 ? NamedTextColor.GREEN
                : mood >= needs.depressionBelow() ? NamedTextColor.YELLOW : NamedTextColor.RED;

        Component text = Component.text()
                .append(Component.text(creature.isFemale() ? "Ewe" : "Ram", NamedTextColor.WHITE))
                .append(Component.text(" | Gen " + creature.generation(), NamedTextColor.GRAY))
                .appendNewline()
                .append(bar("Energy", creature.energy() / energyMax, NamedTextColor.GOLD))
                .appendNewline()
                .append(bar("Water", creature.hydration() / 100.0, NamedTextColor.AQUA))
                .appendNewline()
                .append(bar("Mood", mood / 100.0, moodColor))
                .appendNewline()
                .append(Component.text(status, statusColor))
                .build();
        statBar.text(text);
    }

    private static Component bar(String label, double ratio, TextColor color) {
        int total = 10;
        int filled = (int) Math.round(Math.max(0, Math.min(1, ratio)) * total);
        return Component.text()
                .append(Component.text(label + " ", NamedTextColor.WHITE))
                .append(Component.text("|".repeat(filled), color))
                .append(Component.text("|".repeat(total - filled), NamedTextColor.DARK_GRAY))
                .build();
    }

    // --- Flat fallback pose and positions ---

    private Location location() {
        double x = creature.x();
        double z = creature.z();
        double heading = creature.heading();
        double lift = 0;
        float pitch = 0f;

        Creature partner = creature.mate();
        if (partner != null) {
            // Both partners stand completely still and face the same way, away
            // from where they met. The creature with the higher id is mounted.
            double lead = creature.id() > partner.id() ? creature.heading() : partner.heading();
            double facing = lead + Math.PI;
            heading = facing;
            if (creature.id() < partner.id() && !tiltedMount) {
                // Flat fallback: stand right behind it, raised, head lifted.
                double leaderSize = partner.genome().size();
                double gap = MOUNT_GAP * (creature.genome().size() + leaderSize);
                x = partner.x() - Math.cos(facing) * gap;
                z = partner.z() - Math.sin(facing) * gap;
                lift = MOUNT_LIFT * leaderSize;
                pitch = -MOUNT_HEAD_PITCH;
            }
        }

        Location loc = arena.toLocation(x, z);
        // Minecraft yaw 0 faces +z; the simulation heading 0 faces +x.
        loc.setYaw((float) (Math.toDegrees(heading) - 90.0));
        loc.setPitch(pitch);
        if (lift > 0) loc.add(0, lift, 0);
        return loc;
    }

    private double currentScale() {
        double growth = Math.min(1.0, BABY_SCALE + (1.0 - BABY_SCALE) * creature.age() / maturitySeconds);
        return creature.genome().size() * growth;
    }

    /** Grows from baby to adult size, scaled by the size gene. */
    private void applyScale() {
        double scale = currentScale();
        if (Math.abs(scale - appliedScale) > 0.005) {
            AttributeInstance attribute = sheep.getAttribute(Attribute.SCALE);
            if (attribute != null) {
                attribute.setBaseValue(scale);
            }
            appliedScale = scale;
        }
    }
}
