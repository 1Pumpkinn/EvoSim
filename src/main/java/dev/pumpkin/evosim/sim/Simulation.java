package dev.pumpkin.evosim.sim;

import dev.pumpkin.evosim.arena.Arena;
import dev.pumpkin.evosim.config.SimConfig;
import dev.pumpkin.evosim.render.ColorMode;
import dev.pumpkin.evosim.render.CreatureView;
import dev.pumpkin.evosim.render.FoodView;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;

/**
 * Bukkit glue around {@link Ecosystem}: runs it from the scheduler, keeps a
 * display-entity view for every creature and food item, and exposes the
 * controls commands need (start, stop, reset, speed, colour mode).
 */
public final class Simulation implements Ecosystem.Listener {

    /** Largest simulated time slice per ecosystem step, in seconds. */
    private static final double MAX_SUBSTEP = 0.1;

    private final Plugin plugin;
    private final SimConfig config;
    private final Arena arena;
    private final Ecosystem ecosystem;

    private final Map<Creature, CreatureView> creatureViews = new HashMap<>();
    private final Map<Food, FoodView> foodViews = new HashMap<>();

    private BukkitTask task;
    private ColorMode colorMode = ColorMode.LINEAGE;
    private double timeScale;
    private Runnable extinctionListener;

    public Simulation(Plugin plugin, SimConfig config, Arena arena) {
        this.plugin = plugin;
        this.config = config;
        this.arena = arena;
        this.timeScale = config.simulation().timeScale();
        this.ecosystem = new Ecosystem(config, this, new SplittableRandom());
    }

    public Arena arena() { return arena; }

    public boolean isRunning() { return task != null; }

    public ColorMode colorMode() { return colorMode; }

    public double timeScale() { return timeScale; }

    public Stats stats() { return ecosystem.stats(); }

    /** The creature drawn by this entity, if it is one of ours. */
    public Optional<Creature> creatureFor(Entity entity) {
        for (Map.Entry<Creature, CreatureView> e : creatureViews.entrySet()) {
            if (e.getValue().owns(entity)) return Optional.of(e.getKey());
        }
        return Optional.empty();
    }

    public SimConfig config() { return config; }

    /** Called once when the last creature dies; the simulation stops itself first. */
    public void onExtinction(Runnable listener) { this.extinctionListener = listener; }

    public void setTimeScale(double scale) {
        this.timeScale = Math.max(0.1, Math.min(20.0, scale));
    }

    public void setColorMode(ColorMode mode) {
        this.colorMode = mode;
        creatureViews.values().forEach(v -> v.recolor(mode));
    }

    /** Starts (or resumes) the simulation, spawning founders on the first start. */
    public void start() {
        if (task != null) return;
        if (ecosystem.creatures().isEmpty()) {
            ecosystem.spawnFounders(config.population().initial());
        }
        arena.keepLoaded(plugin);
        int interval = config.simulation().tickInterval();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    /** Pauses the simulation; creatures stay where they are. */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Wipes the ecosystem and starts over with fresh founders. */
    public void reset() {
        boolean wasRunning = isRunning();
        stop();
        clearViews();
        ecosystem.clear();
        arena.purgeEntities();
        ecosystem.spawnFounders(config.population().initial());
        if (wasRunning) start();
    }

    /** Adds founder creatures now; returns how many fit under the population cap. */
    public int spawnCreatures(int count) {
        return ecosystem.spawnFounders(count);
    }

    /** Testing aid: pairs two creatures right away, whatever their sex or preference. */
    public boolean forceBreed(Creature a, Creature b) {
        return ecosystem.forceMate(a, b);
    }

    /** Stops, removes every entity and releases the arena's chunks. Blocks stay. */
    public void shutdown() {
        stop();
        clearViews();
        ecosystem.clear();
        arena.purgeEntities();
        arena.releaseChunks(plugin);
    }

    private void tick() {
        double dt = config.simulation().tickInterval() / 20.0 * timeScale;
        int steps = Math.max(1, (int) Math.ceil(dt / MAX_SUBSTEP));
        double h = dt / steps;
        for (int i = 0; i < steps && !ecosystem.isExtinct(); i++) {
            ecosystem.step(h);
        }

        creatureViews.values().forEach(CreatureView::update);

        if (ecosystem.isExtinct()) {
            stop();
            if (extinctionListener != null) extinctionListener.run();
        }
    }

    private void clearViews() {
        creatureViews.values().forEach(CreatureView::remove);
        creatureViews.clear();
        foodViews.values().forEach(FoodView::remove);
        foodViews.clear();
    }

    // --- Ecosystem.Listener: mirror every change into display entities ---

    @Override
    public void creatureBorn(Creature creature) {
        creatureViews.put(creature,
                new CreatureView(arena, creature, colorMode, config));
    }

    @Override
    public void offspringBorn(Creature child, Creature parentA, Creature parentB) {
        CreatureView view = creatureViews.get(child);
        if (view != null) view.playBirthEffect();
    }

    @Override
    public void overbred(Creature creature) {
        CreatureView view = creatureViews.get(creature);
        if (view != null) view.playOverbredEffect();
    }

    @Override
    public void creatureDied(Creature creature) {
        CreatureView view = creatureViews.remove(creature);
        if (view == null) return;
        long ticks = Math.round(config.animation().corpseSeconds() * 20.0);
        if (ticks <= 0) {
            view.remove();
            return;
        }
        view.playDeath();
        plugin.getServer().getScheduler().runTaskLater(plugin, view::removeCorpse, ticks);
    }

    @Override
    public void foodSpawned(Food food) {
        foodViews.put(food, new FoodView(arena, food));
    }

    @Override
    public void foodEaten(Food food) {
        FoodView view = foodViews.remove(food);
        if (view != null) view.remove();
    }
}
