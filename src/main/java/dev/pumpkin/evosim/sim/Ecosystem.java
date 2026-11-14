package dev.pumpkin.evosim.sim;

import dev.pumpkin.evosim.config.SimConfig;
import dev.pumpkin.evosim.genetics.Genome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * The pure simulation: food spawning, foraging, metabolism, reproduction
 * with mutation, and death. It knows nothing about Minecraft; changes are
 * reported through a {@link Listener}, which makes it testable headless.
 */
public final class Ecosystem {

    /** Receives every change so a renderer can mirror the ecosystem. */
    public interface Listener {
        void creatureBorn(Creature creature);

        void creatureDied(Creature creature);

        void foodSpawned(Food food);

        void foodEaten(Food food);
    }

    /** Heading change per sqrt(second) while wandering without a target. */
    private static final double WANDER_TURN = 1.6;
    /** Extra reach beyond the body radius when grabbing food. */
    private static final double EAT_MARGIN = 0.4;

    private final SimConfig config;
    private final Listener listener;
    private final RandomGenerator rng;
    private final double size;

    private final List<Creature> creatures = new ArrayList<>();
    private final List<Food> foods = new ArrayList<>();

    private long nextCreatureId = 1;
    private long nextFoodId = 1;
    private double foodAccumulator;
    private long births;
    private long deaths;
    private double elapsed;

    public Ecosystem(SimConfig config, Listener listener, RandomGenerator rng) {
        this.config = config;
        this.listener = listener;
        this.rng = rng;
        this.size = config.arena().size();
    }

    public List<Creature> creatures() { return Collections.unmodifiableList(creatures); }

    public List<Food> foods() { return Collections.unmodifiableList(foods); }

    public boolean isExtinct() { return creatures.isEmpty(); }

    /** Adds up to {@code count} founder creatures, respecting the population cap. Returns how many. */
    public int spawnFounders(int count) {
        SimConfig.Population pop = config.population();
        int n = Math.max(0, Math.min(count, pop.max() - creatures.size()));
        for (int i = 0; i < n; i++) {
            Genome genome = Genome.founder(config.evolution().initialSpread(), rng);
            Creature c = new Creature(nextCreatureId++, genome, 0,
                    1.0 + rng.nextDouble() * (size - 2.0),
                    1.0 + rng.nextDouble() * (size - 2.0),
                    rng.nextDouble() * 2.0 * Math.PI,
                    pop.startEnergy());
            creatures.add(c);
            listener.creatureBorn(c);
        }
        return n;
    }

    /** Forgets everything without firing events; the caller clears its own views. */
    public void clear() {
        creatures.clear();
        foods.clear();
        nextCreatureId = 1;
        nextFoodId = 1;
        foodAccumulator = 0;
        births = 0;
        deaths = 0;
        elapsed = 0;
    }

    /** Advances the world by {@code h} simulated seconds (keep h small, e.g. 0.1). */
    public void step(double h) {
        spawnFood(h);
        steerAndMove(h);
        eat();
        metabolize(h);
        reproduce();
        cull();
        elapsed += h;
    }

    private void spawnFood(double h) {
        SimConfig.Food cfg = config.food();
        foodAccumulator += cfg.spawnPerSecond() * h;
        while (foodAccumulator >= 1.0) {
            foodAccumulator -= 1.0;
            if (foods.size() >= cfg.max()) {
                foodAccumulator = 0;
                break;
            }
            Food f = new Food(nextFoodId++,
                    0.5 + rng.nextDouble() * (size - 1.0),
                    0.5 + rng.nextDouble() * (size - 1.0),
                    cfg.energy());
            foods.add(f);
            listener.foodSpawned(f);
        }
    }

    private void steerAndMove(double h) {
        for (Creature c : creatures) {
            double heading = c.heading();
            Food target = nearestFood(c);
            if (target != null) {
                heading = Math.atan2(target.z() - c.z(), target.x() - c.x());
            } else {
                heading += rng.nextGaussian() * WANDER_TURN * Math.sqrt(h);
            }

            double distance = c.genome().speed() * h;
            double nx = c.x() + Math.cos(heading) * distance;
            double nz = c.z() + Math.sin(heading) * distance;

            double r = c.radius();
            double max = size - r;
            if (nx < r) {
                nx = r;
                heading = Math.PI - heading;
            } else if (nx > max) {
                nx = max;
                heading = Math.PI - heading;
            }
            if (nz < r) {
                nz = r;
                heading = -heading;
            } else if (nz > max) {
                nz = max;
                heading = -heading;
            }

            c.moveTo(nx, nz);
            c.setHeading(heading);
        }
    }

    private Food nearestFood(Creature c) {
        double vision = c.genome().vision();
        double best = vision * vision;
        Food nearest = null;
        for (Food f : foods) {
            double dx = f.x() - c.x();
            double dz = f.z() - c.z();
            double d2 = dx * dx + dz * dz;
            if (d2 <= best) {
                best = d2;
                nearest = f;
            }
        }
        return nearest;
    }

    /** Bigger creatures get first pick, so size wins contested food. */
    private void eat() {
        if (foods.isEmpty()) return;

        List<Creature> order = new ArrayList<>(creatures);
        order.sort(Comparator.comparingDouble((Creature c) -> c.genome().size()).reversed());

        Set<Food> eaten = new HashSet<>();
        for (Creature c : order) {
            double reach = c.radius() + EAT_MARGIN;
            double reach2 = reach * reach;
            for (Food f : foods) {
                if (eaten.contains(f)) continue;
                double dx = f.x() - c.x();
                double dz = f.z() - c.z();
                if (dx * dx + dz * dz <= reach2) {
                    c.addEnergy(f.energy());
                    eaten.add(f);
                    break;
                }
            }
        }

        if (!eaten.isEmpty()) {
            foods.removeIf(eaten::contains);
            eaten.forEach(listener::foodEaten);
        }
    }

    private void metabolize(double h) {
        for (Creature c : creatures) {
            c.addEnergy(-c.costPerSecond(config.energy()) * h);
            c.advanceAge(h);
        }
    }

    private void reproduce() {
        SimConfig.Population pop = config.population();
        SimConfig.Evolution evo = config.evolution();
        int room = pop.max() - creatures.size();
        if (room <= 0) return;

        List<Creature> born = new ArrayList<>();
        for (Creature parent : creatures) {
            if (born.size() >= room) break;
            if (parent.energy() < pop.reproductionThreshold()) continue;

            Genome genome = parent.genome().mutate(rng, evo.mutationRate(), evo.mutationStrength());
            parent.addEnergy(-pop.reproductionCost());

            double angle = rng.nextDouble() * 2.0 * Math.PI;
            double cx = clamp(parent.x() + Math.cos(angle) * 0.6, 0.5, size - 0.5);
            double cz = clamp(parent.z() + Math.sin(angle) * 0.6, 0.5, size - 0.5);
            born.add(new Creature(nextCreatureId++, genome, parent.generation() + 1,
                    cx, cz, angle, pop.reproductionCost()));
        }

        for (Creature child : born) {
            creatures.add(child);
            births++;
            listener.creatureBorn(child);
        }
    }

    private void cull() {
        double maxAge = config.population().maxAgeSeconds();
        List<Creature> dead = new ArrayList<>();
        for (Creature c : creatures) {
            if (c.isDead(maxAge)) dead.add(c);
        }
        if (dead.isEmpty()) return;
        creatures.removeAll(dead);
        for (Creature c : dead) {
            deaths++;
            listener.creatureDied(c);
        }
    }

    public Stats stats() {
        int n = creatures.size();
        double speed = 0;
        double bodySize = 0;
        double vision = 0;
        int maxGeneration = 0;
        for (Creature c : creatures) {
            speed += c.genome().speed();
            bodySize += c.genome().size();
            vision += c.genome().vision();
            maxGeneration = Math.max(maxGeneration, c.generation());
        }

        double diversity = 0;
        if (n >= 2) {
            int pairs = Math.min(100, n * (n - 1) / 2);
            for (int k = 0; k < pairs; k++) {
                int i = rng.nextInt(n);
                int j = rng.nextInt(n - 1);
                if (j >= i) j++;
                diversity += creatures.get(i).genome().distanceTo(creatures.get(j).genome());
            }
            diversity /= pairs;
        }

        return new Stats(n, foods.size(), maxGeneration,
                n == 0 ? 0 : speed / n, n == 0 ? 0 : bodySize / n, n == 0 ? 0 : vision / n,
                diversity, births, deaths, elapsed);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
