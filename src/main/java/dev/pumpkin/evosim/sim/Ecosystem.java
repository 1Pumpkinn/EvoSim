package dev.pumpkin.evosim.sim;

import dev.pumpkin.evosim.config.SimConfig;
import dev.pumpkin.evosim.genetics.Genome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * The pure simulation: food and water, foraging, mate seeking, sexual
 * reproduction with crossover and mutation, metabolism, mood and death. It knows
 * nothing about Minecraft; changes are reported through a {@link Listener},
 * which makes it testable headless.
 */
public final class Ecosystem {

    /** Receives every change so a renderer can mirror the ecosystem. */
    public interface Listener {
        void creatureBorn(Creature creature);

        void creatureDied(Creature creature);

        void foodSpawned(Food food);

        void foodEaten(Food food);

        /** Fired right after {@link #creatureBorn} for a child of two parents. */
        default void offspringBorn(Creature child, Creature parentA, Creature parentB) {}
    }

    /** Heading change per sqrt(second) while wandering without a target. */
    private static final double WANDER_TURN = 1.6;
    /** Extra reach beyond the body radius when grabbing food. */
    private static final double EAT_MARGIN = 0.4;
    /** Extra distance beyond touching at which two mates pair up. */
    private static final double MATE_MARGIN = 0.8;
    /** A creature in a pond keeps drinking until it reaches this hydration. */
    private static final double DRINK_UNTIL = 95;
    /** Mood gained when a pair finishes mating, when eating, and for each parent at a birth. */
    private static final double MATING_MOOD = 15;
    private static final double EATING_MOOD = 4;
    private static final int FOOD_PLACEMENT_TRIES = 8;
    /** Rare food looks this many times closer than it is when a creature picks what to walk to. */
    private static final double RARE_FOOD_PULL = 2.0;
    /** Extra mood from a rare meal, as a multiple of a normal one. */
    private static final double RARE_MOOD_FACTOR = 4.0;

    /** Fastest a creature can swing its heading, in radians per second. */
    private static final double TURN_RATE = 5.0;
    /** Within this distance of its target a creature heads straight for it. */
    private static final double CLOSE_RANGE = 2.0;
    /** Walls start pushing creatures away from this far out, and how hard. */
    private static final double WALL_MARGIN = 3.0;
    private static final double WALL_PUSH = 1.5;
    /** Creatures nudge apart when closer than their radii plus this gap, and how hard. */
    private static final double PERSONAL_GAP = 0.4;
    private static final double SEPARATION_PUSH = 0.9;
    /** Creatures within this many blocks of a death grieve. */
    private static final double GRIEF_RADIUS = 8;
    /** Below this share of the reproduction threshold a creature is hungry enough to grab food on its way to water. */
    private static final double HUNGRY_FRACTION = 0.6;
    /** Extra walking, in blocks, a hungry creature accepts to pick up food on the way to water. */
    private static final double DETOUR_THIRSTY = 4.0;
    private static final double DETOUR_PARCHED = 1.5;
    /** A food it already chose stays chosen while its detour is within this multiple of the allowance. */
    private static final double DETOUR_STICKY = 1.5;
    /** Heading change per sqrt(second) while searching for water: straighter than aimless wandering. */
    private static final double SEARCH_TURN = 0.5;

    private final SimConfig config;
    private final Listener listener;
    private final RandomGenerator rng;
    private final double size;

    private final List<Creature> creatures = new ArrayList<>();
    private final List<Food> foods = new ArrayList<>();
    private final List<Pond> ponds;
    private final BiomeMap biomes;
    /** Lower-id partner of every pair made by {@link #forceMate}: these always have a child. */
    private final Set<Creature> forcedPairs = new HashSet<>();

    private final EnumMap<DeathCause, Integer> deathCounts = new EnumMap<>(DeathCause.class);
    private final EnumMap<DeathCause, Double> deathAgeSums = new EnumMap<>(DeathCause.class);

    private long nextCreatureId = 1;
    private long nextFoodId = 1;
    private double foodAccumulator;
    private int rareFoods;
    private final Adaptation adaptation;
    private long births;
    private long deaths;
    private double elapsed;

    public Ecosystem(SimConfig config, Listener listener, RandomGenerator rng, BiomeMap biomes) {
        this.config = config;
        this.biomes = biomes;
        this.listener = listener;
        this.rng = rng;
        this.size = config.arena().size();
        this.ponds = Pond.layout(config);
        this.adaptation = new Adaptation(config.adaptation());
    }

    public List<Creature> creatures() { return Collections.unmodifiableList(creatures); }

    public List<Food> foods() { return Collections.unmodifiableList(foods); }

    public BiomeMap biomes() { return biomes; }

    /** What recent deaths are pushing the population to adapt to. */
    public Adaptation adaptation() { return adaptation; }

    public List<Pond> ponds() { return Collections.unmodifiableList(ponds); }

    public boolean isExtinct() { return creatures.isEmpty(); }

    /** Adds up to {@code count} adult founders, respecting the population cap. Returns how many. */
    public int spawnFounders(int count) {
        SimConfig.Population pop = config.population();
        int n = Math.max(0, Math.min(count, pop.max() - creatures.size()));
        for (int i = 0; i < n; i++) {
            Genome genome = Genome.founder(config.evolution().initialSpread(), rng);
            Creature c = new Creature(nextCreatureId++, genome, 0,
                    1.0 + rng.nextDouble() * (size - 2.0),
                    1.0 + rng.nextDouble() * (size - 2.0),
                    rng.nextDouble() * 2.0 * Math.PI,
                    pop.startEnergy(),
                    rng.nextBoolean(), rng.nextDouble() < config.needs().sameSexChance(), 0, 0);
            c.advanceAge(pop.maturitySeconds()); // founders start as adults
            creatures.add(c);
            listener.creatureBorn(c);
        }
        return n;
    }

    /** Forgets everything without firing events; the caller clears its own views. */
    public void clear() {
        creatures.clear();
        foods.clear();
        forcedPairs.clear();
        deathCounts.clear();
        deathAgeSums.clear();
        nextCreatureId = 1;
        nextFoodId = 1;
        foodAccumulator = 0;
        rareFoods = 0;
        adaptation.clear();
        births = 0;
        deaths = 0;
        elapsed = 0;
    }

    /** Advances the world by {@code h} simulated seconds (keep h small, e.g. 0.1). */
    public void step(double h) {
        adaptation.decay(h);
        spawnFood(h);
        steerAndMove(h);
        pairUp();
        progressMating(h);
        eat();
        drink(h);
        metabolize(h);
        updateMood(h);
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
            // Rare food can appear anywhere, so it is relatively commoner in the sparse biomes.
            boolean rare = rareFoods < cfg.rareMax() && rng.nextDouble() < cfg.rareChance();
            // Normal food grows in proportion to each biome's food factor (rejection sampling).
            double fx = 0;
            double fz = 0;
            boolean placed = false;
            if (rare) {
                fx = 0.5 + rng.nextDouble() * (size - 1.0);
                fz = 0.5 + rng.nextDouble() * (size - 1.0);
                placed = true;
            }
            for (int tries = 0; tries < FOOD_PLACEMENT_TRIES && !placed; tries++) {
                fx = 0.5 + rng.nextDouble() * (size - 1.0);
                fz = 0.5 + rng.nextDouble() * (size - 1.0);
                placed = rng.nextDouble() * biomes.maxFoodFactor() <= biomes.at(fx, fz).foodFactor();
            }
            if (!placed) continue;
            Food f = new Food(nextFoodId++, fx, fz, rare ? cfg.energy() * cfg.rareEnergyMultiplier() : cfg.energy(), rare);
            if (rare) rareFoods++;
            foods.add(f);
            listener.foodSpawned(f);
        }
    }

    /** Adult, well-fed and not already paired: looking for a mate. */
    private boolean isReady(Creature c) {
        return !c.isMating()
                && c.isAdult(config.population().maturitySeconds())
                && c.energy() >= config.population().reproductionThreshold()
                && !c.isResting()
                && !isDepressed(c);
    }

    private boolean isDepressed(Creature c) {
        return c.mood() < config.needs().depressionBelow();
    }

    /**
     * Opposite sexes pair up, or the same sex for creatures that pair with their own.
     * Parents, children and siblings never pair.
     */
    private static boolean compatible(Creature a, Creature b) {
        if (a.isCloseRelativeOf(b)) return false;
        if (a.isSameSex() != b.isSameSex()) return false;
        return a.isSameSex() ? a.isFemale() == b.isFemale() : a.isFemale() != b.isFemale();
    }

    private boolean isInPond(Creature c) {
        for (Pond p : ponds) {
            if (p.contains(c.x(), c.z())) return true;
        }
        return false;
    }

    /**
     * A creature starts drinking when it stands in a pond while thirsty, and keeps drinking
     * until it is full. One that is not thirsty just walks through (and still gets a sip).
     */
    private void updateDrinking(Creature c) {
        boolean inPond = isInPond(c);
        if (c.isDrinking()) {
            if (!inPond || c.hydration() >= DRINK_UNTIL) c.setDrinking(false);
        } else if (inPond && c.hydration() < config.needs().seekBelow()) {
            c.setDrinking(true);
        }
    }

    /** Nearest pond whose edge is within the creature's vision. */
    private Pond nearestPond(Creature c) {
        double vision = c.genome().vision();
        double best = vision;
        Pond nearest = null;
        for (Pond p : ponds) {
            double d = p.edgeDistance(c.x(), c.z());
            if (d <= best) {
                best = d;
                nearest = p;
            }
        }
        return nearest;
    }

    /** Remembers every pond whose edge is within the creature's vision. */
    private void noteVisiblePonds(Creature c) {
        double vision = c.genome().vision();
        for (Pond p : ponds) {
            if (p.edgeDistance(c.x(), c.z()) <= vision) c.rememberPond(p);
        }
    }

    /** Water to walk to: the nearest pond in sight, else the nearest one it remembers; null if it knows none. */
    private Pond chooseWater(Creature c) {
        Pond visible = nearestPond(c);
        if (visible != null) return visible;
        double best = Double.MAX_VALUE;
        Pond nearest = null;
        for (Pond p : c.knownPonds()) {
            double d = p.edgeDistance(c.x(), c.z());
            if (d < best) {
                best = d;
                nearest = p;
            }
        }
        return nearest;
    }

    /** Extra walking needed to reach the pond's edge via this food instead of going straight. */
    private static double detour(Creature c, Food f, Pond pond) {
        double direct = Math.max(0, Math.hypot(pond.x() - c.x(), pond.z() - c.z()) - pond.radius());
        double toFood = Math.hypot(f.x() - c.x(), f.z() - c.z());
        double foodToPond = Math.max(0, Math.hypot(pond.x() - f.x(), pond.z() - f.z()) - pond.radius());
        return toFood + foodToPond - direct;
    }

    /**
     * Food in sight that lies close to the walk to the pond, for a hungry creature: the one that
     * costs the least extra walking, within an allowance that is smaller when it is parched. It
     * keeps the same food until eaten so it does not dither. Null when nothing is worth a detour.
     */
    private Food foodOnTheWay(Creature c, Pond pond, boolean parched) {
        double budget = parched ? DETOUR_PARCHED : DETOUR_THIRSTY;
        double vision = c.genome().vision();
        double v2 = vision * vision;
        Food current = c.targetFood();
        if (current != null && foods.contains(current)) {
            double dx = current.x() - c.x();
            double dz = current.z() - c.z();
            if (dx * dx + dz * dz <= v2 * 1.44 && detour(c, current, pond) <= budget * DETOUR_STICKY) {
                return current;
            }
        }
        double bestDetour = budget;
        Food best = null;
        for (Food f : foods) {
            double dx = f.x() - c.x();
            double dz = f.z() - c.z();
            if (dx * dx + dz * dz > v2) continue;
            double extra = detour(c, f, pond);
            if (extra <= bestDetour) {
                bestDetour = extra;
                best = f;
            }
        }
        return best;
    }

    private void steerAndMove(double h) {
        SimConfig.Needs needs = config.needs();
        for (Creature c : creatures) {
            if (c.isMating()) continue; // paired creatures stay put
            updateDrinking(c);
            if (c.isDrinking()) { // drinking creatures stay put
                c.setActivity(Creature.Activity.DRINKING);
                continue;
            }

            noteVisiblePonds(c);

            // 1. Pick a target. Parched: water. Otherwise a mate, then water if thirsty (in sight or
            //    remembered, and while hungry it grabs food on the way), else food. A thirsty creature
            //    that is not hungry and knows no water searches for it instead of chasing food.
            boolean thirsty = c.hydration() < needs.seekBelow();
            boolean parched = c.hydration() < needs.seekBelow() * 0.5;
            boolean hungry = c.energy() < config.population().reproductionThreshold() * HUNGRY_FRACTION;
            Pond pond = thirsty ? chooseWater(c) : null;
            Creature mate = isReady(c) ? nearestMate(c) : null;
            double tx = 0;
            double tz = 0;
            double stopShort = 0; // distance to keep from the target
            boolean hasTarget = false;
            boolean searching = false;
            if (pond != null && (parched || mate == null)) {
                Food snack = hungry ? foodOnTheWay(c, pond, parched) : null;
                c.setTargetFood(snack);
                if (snack != null) {
                    tx = snack.x();
                    tz = snack.z();
                    c.setActivity(Creature.Activity.FOOD_ON_THE_WAY);
                } else {
                    tx = pond.x();
                    tz = pond.z();
                    c.setActivity(Creature.Activity.TO_WATER);
                }
                hasTarget = true;
            } else if (mate != null) {
                tx = mate.x();
                tz = mate.z();
                stopShort = c.radius() + mate.radius();
                hasTarget = true;
                c.setActivity(Creature.Activity.TO_MATE);
            } else if (thirsty && !hungry) {
                searching = true;
                c.setActivity(Creature.Activity.SEARCHING_WATER);
            } else {
                Food food = chooseFood(c);
                if (food != null) {
                    tx = food.x();
                    tz = food.z();
                    hasTarget = true;
                    c.setActivity(Creature.Activity.TO_FOOD);
                } else {
                    c.setActivity(thirsty ? Creature.Activity.SEARCHING_WATER : Creature.Activity.WANDERING);
                    searching = thirsty;
                }
            }

            // 2. Work out the direction to go.
            double dist = hasTarget ? Math.hypot(tx - c.x(), tz - c.z()) : 0;
            boolean close = hasTarget && dist < CLOSE_RANGE;
            double vx;
            double vz;
            if (hasTarget) {
                vx = dist > 1e-9 ? (tx - c.x()) / dist : Math.cos(c.heading());
                vz = dist > 1e-9 ? (tz - c.z()) / dist : Math.sin(c.heading());
            } else {
                double turn = searching ? SEARCH_TURN : WANDER_TURN;
                double wander = c.heading() + rng.nextGaussian() * turn * Math.sqrt(h);
                vx = Math.cos(wander);
                vz = Math.sin(wander);
            }
            if (!close) {
                // Steer away from walls early instead of bouncing off them...
                double margin = WALL_MARGIN + c.radius();
                if (c.x() < margin) vx += WALL_PUSH * (margin - c.x()) / margin;
                else if (c.x() > size - margin) vx -= WALL_PUSH * (c.x() - (size - margin)) / margin;
                if (c.z() < margin) vz += WALL_PUSH * (margin - c.z()) / margin;
                else if (c.z() > size - margin) vz -= WALL_PUSH * (c.z() - (size - margin)) / margin;
                // ...and drift apart from creatures that are in the way (not from the mate being sought).
                for (Creature other : creatures) {
                    if (other == c || other == mate) continue;
                    double ox = c.x() - other.x();
                    double oz = c.z() - other.z();
                    double personal = c.radius() + other.radius() + PERSONAL_GAP;
                    double d2 = ox * ox + oz * oz;
                    if (d2 >= personal * personal || d2 < 1e-12) continue;
                    double d = Math.sqrt(d2);
                    double push = SEPARATION_PUSH * (personal - d) / personal;
                    vx += ox / d * push;
                    vz += oz / d * push;
                }
            }

            // 3. Turn towards it at a limited rate (instantly when close), then walk.
            double desired = Math.atan2(vz, vx);
            double heading = c.heading();
            if (close) {
                heading = desired;
            } else {
                double delta = wrapAngle(desired - heading);
                double maxTurn = TURN_RATE * h;
                heading += Math.max(-maxTurn, Math.min(maxTurn, delta));
            }

            double step = c.genome().speed() * h
                    * (isDepressed(c) ? needs.depressedSpeedFactor() : 1.0)
                    * c.genome().terrainSpeed(biomes.at(c.x(), c.z()).speedFactor());
            if (hasTarget) {
                step = Math.min(step, Math.max(0, dist - stopShort)); // never overshoot the target
            }

            double r = c.radius();
            double nx = clamp(c.x() + Math.cos(heading) * step, r, size - r);
            double nz = clamp(c.z() + Math.sin(heading) * step, r, size - r);
            c.moveTo(nx, nz);
            c.setHeading(heading);
        }
    }

    private static double wrapAngle(double a) {
        return Math.atan2(Math.sin(a), Math.cos(a));
    }

    /**
     * Keeps heading for the same food until it is eaten or out of sight (so a creature does not
     * dither between two equally near pieces); otherwise picks the cheapest one in view, where
     * food on slow ground (forest, swamp) counts as further away.
     */
    private Food chooseFood(Creature c) {
        double vision = c.genome().vision();
        Food current = c.targetFood();
        if (current != null) {
            double dx = current.x() - c.x();
            double dz = current.z() - c.z();
            if (dx * dx + dz * dz <= vision * vision * 1.44 && foods.contains(current)) {
                return current;
            }
        }
        double v2 = vision * vision;
        double bestCost = Double.MAX_VALUE;
        Food best = null;
        for (Food f : foods) {
            double dx = f.x() - c.x();
            double dz = f.z() - c.z();
            double d2 = dx * dx + dz * dz;
            if (d2 > v2) continue;
            double cost = Math.sqrt(d2) / biomes.at(f.x(), f.z()).speedFactor();
            if (f.rare()) cost /= RARE_FOOD_PULL; // worth a longer walk
            if (cost < bestCost) {
                bestCost = cost;
                best = f;
            }
        }
        c.setTargetFood(best);
        return best;
    }

    private Creature nearestMate(Creature c) {
        double vision = c.genome().vision();
        double best = vision * vision;
        Creature nearest = null;
        for (Creature other : creatures) {
            if (other == c || !isReady(other) || !compatible(c, other)) continue;
            double dx = other.x() - c.x();
            double dz = other.z() - c.z();
            double d2 = dx * dx + dz * dz;
            if (d2 <= best) {
                best = d2;
                nearest = other;
            }
        }
        return nearest;
    }

    /** Ready creatures that end up close to each other become a mating pair. */
    private void pairUp() {
        double seconds = config.population().matingSeconds();
        for (Creature a : creatures) {
            if (!isReady(a)) continue;
            for (Creature b : creatures) {
                if (b == a || !isReady(b) || !compatible(a, b)) continue;
                double reach = a.radius() + b.radius() + MATE_MARGIN;
                double dx = b.x() - a.x();
                double dz = b.z() - a.z();
                if (dx * dx + dz * dz <= reach * reach) {
                    a.beginMating(b, seconds);
                    b.beginMating(a, seconds);
                    a.setHeading(Math.atan2(dz, dx));
                    b.setHeading(Math.atan2(-dz, -dx));
                    break;
                }
            }
        }
    }

    /**
     * Testing aid: pairs two creatures right now, whatever their sex, pairing preference,
     * age, energy or kinship. The second one is moved up to the first, and the pair ends
     * with a child like any other. Returns false if either is missing or already paired.
     */
    public boolean forceMate(Creature a, Creature b) {
        if (a == b || a.isMating() || b.isMating()
                || !creatures.contains(a) || !creatures.contains(b)) {
            return false;
        }
        double reach = a.radius() + b.radius() + MATE_MARGIN;
        double dx = b.x() - a.x();
        double dz = b.z() - a.z();
        double dist = Math.hypot(dx, dz);
        if (dist > reach) {
            double ux = dist > 1e-6 ? dx / dist : 1.0;
            double uz = dist > 1e-6 ? dz / dist : 0.0;
            b.moveTo(clamp(a.x() + ux * reach, b.radius(), size - b.radius()),
                    clamp(a.z() + uz * reach, b.radius(), size - b.radius()));
            dx = b.x() - a.x();
            dz = b.z() - a.z();
        }
        // Make sure the child can be afforded, so the test always plays through.
        double share = config.population().reproductionCost() / 2.0;
        a.addEnergy(Math.max(0, share * a.genome().birthCostMultiplier() - a.energy()));
        b.addEnergy(Math.max(0, share * b.genome().birthCostMultiplier() - b.energy()));

        double seconds = config.population().matingSeconds();
        a.beginMating(b, seconds);
        b.beginMating(a, seconds);
        a.setHeading(Math.atan2(dz, dx));
        b.setHeading(Math.atan2(-dz, -dx));
        forcedPairs.add(a.id() < b.id() ? a : b);
        return true;
    }

    private void progressMating(double h) {
        List<Creature> finished = new ArrayList<>();
        for (Creature c : creatures) {
            if (!c.isMating()) continue;
            c.advanceMating(h);
            // Handle each pair once, from the lower id.
            if (c.matingRemaining() <= 0 && c.id() < c.mate().id()) {
                finished.add(c);
            }
        }
        for (Creature a : finished) {
            Creature b = a.mate();
            a.endMating();
            b.endMating();
            a.addMood(MATING_MOOD);
            b.addMood(MATING_MOOD);
            restAfterMating(a);
            restAfterMating(b);
            boolean forced = forcedPairs.remove(a);
            if (forced || !a.isSameSex()) { // same-sex pairs bond but have no child
                giveBirth(a, b);
            }
        }
    }

    /** Everyone rests after mating and looks for no mate until it ends; a ram's rest follows his rest-time gene. */
    private void restAfterMating(Creature c) {
        SimConfig.Needs needs = config.needs();
        double seconds = c.isFemale() ? needs.femaleRestSeconds() : needs.maleRestSeconds() * c.genome().restTime();
        c.beginRest(seconds);
    }

    private void giveBirth(Creature a, Creature b) {
        SimConfig.Population pop = config.population();
        SimConfig.Evolution evo = config.evolution();
        double share = pop.reproductionCost() / 2.0;
        double shareA = share * a.genome().birthCostMultiplier();
        double shareB = share * b.genome().birthCostMultiplier();
        if (creatures.size() >= pop.max() || a.energy() < shareA || b.energy() < shareB) {
            return; // no room or a parent ran low on energy while paired
        }

        a.addEnergy(-shareA);
        b.addEnergy(-shareB);
        Genome genome = Genome.crossover(a.genome(), b.genome(), rng, evo.specialInheritance())
                .mutate(rng, evo.mutationRate(), evo.mutationStrength(), evo.specialMutationChance());
        genome = adaptation.adapt(genome, rng);
        Creature child = new Creature(nextCreatureId++, genome,
                Math.max(a.generation(), b.generation()) + 1,
                clamp((a.x() + b.x()) / 2.0, 0.5, size - 0.5),
                clamp((a.z() + b.z()) / 2.0, 0.5, size - 0.5),
                rng.nextDouble() * 2.0 * Math.PI,
                pop.reproductionCost(),
                rng.nextBoolean(), rng.nextDouble() < config.needs().sameSexChance(),
                a.id(), b.id());
        creatures.add(child);
        births++;
        listener.creatureBorn(child);
        listener.offspringBorn(child, a, b);
    }

    /** Bigger creatures get first pick, so size wins contested food. */
    private void eat() {
        if (foods.isEmpty()) return;

        List<Creature> order = new ArrayList<>(creatures);
        order.sort(Comparator.comparingDouble((Creature c) -> c.genome().size()).reversed());

        Set<Food> eaten = new HashSet<>();
        for (Creature c : order) {
            if (c.isMating()) continue;
            double reach = c.radius() + EAT_MARGIN;
            double reach2 = reach * reach;
            for (Food f : foods) {
                if (eaten.contains(f)) continue;
                double dx = f.x() - c.x();
                double dz = f.z() - c.z();
                if (dx * dx + dz * dz <= reach2) {
                    c.addEnergy(f.energy() * c.genome().foodMultiplier());
                    c.addMood(f.rare() ? EATING_MOOD * RARE_MOOD_FACTOR : EATING_MOOD);
                    if (f.rare()) c.addHydration(config.food().rareHydration());
                    eaten.add(f);
                    break;
                }
            }
        }

        if (!eaten.isEmpty()) {
            for (Food f : eaten) {
                if (f.rare()) rareFoods--;
            }
            foods.removeIf(eaten::contains);
            eaten.forEach(listener::foodEaten);
        }
    }

    private void drink(double h) {
        double rate = config.needs().drinkRate();
        for (Creature c : creatures) {
            if (!c.isMating() && isInPond(c)) {
                c.addHydration(rate * h);
            }
        }
    }

    private void metabolize(double h) {
        SimConfig.Needs needs = config.needs();
        for (Creature c : creatures) {
            Biome biome = biomes.at(c.x(), c.z());
            c.addEnergy(-c.costPerSecond(config.energy()) * c.genome().coldUpkeep(biome.upkeepFactor()) * h);
            c.addHydration(-needs.thirstDrain() * c.genome().thirstRate() * c.genome().desertThirst(biome.thirstFactor()) * h);
            c.tickRest(h);
            if (c.hydration() <= 0) {
                c.addEnergy(-needs.dehydrationDamage() * h); // dehydration
            }
            c.advanceAge(h);
        }
    }

    /** Mood falls while hungry, thirsty or alone, and recovers when all is well. */
    private void updateMood(double h) {
        SimConfig.Needs needs = config.needs();
        double hungry = config.population().reproductionThreshold() * 0.35;
        for (Creature c : creatures) {
            boolean unmet = c.energy() < hungry || c.hydration() < 25;
            boolean alone = isAlone(c);
            double delta;
            if (unmet || alone) {
                delta = -needs.moodDrain() * ((unmet ? 1.5 : 0) + (alone ? 1.0 : 0));
            } else {
                delta = needs.moodRecovery();
            }
            c.addMood(delta * h);
        }
    }

    /** Nobody else within the creature's vision. */
    private boolean isAlone(Creature c) {
        double v2 = c.genome().vision() * c.genome().vision();
        for (Creature other : creatures) {
            if (other == c) continue;
            double dx = other.x() - c.x();
            double dz = other.z() - c.z();
            if (dx * dx + dz * dz <= v2) return false;
        }
        return true;
    }

    private void cull() {
        double maxAge = config.population().maxAgeSeconds();
        List<Creature> dead = new ArrayList<>();
        for (Creature c : creatures) {
            if (c.isDead(maxAge)) dead.add(c);
        }
        if (dead.isEmpty()) return;
        creatures.removeAll(dead);
        double grief = config.needs().griefMoodLoss();
        for (Creature c : dead) {
            for (Creature other : creatures) {
                double dx = other.x() - c.x();
                double dz = other.z() - c.z();
                if (dx * dx + dz * dz <= GRIEF_RADIUS * GRIEF_RADIUS) {
                    other.addMood(-grief * other.genome().griefSensitivity());
                }
            }
            Creature partner = c.mate();
            if (partner != null && partner.mate() == c) {
                partner.endMating(); // widowed mid-mating
                partner.addMood(-grief * partner.genome().griefSensitivity());
                forcedPairs.remove(partner);
            }
            forcedPairs.remove(c);
            c.endMating();
            DeathCause cause = causeOfDeath(c, maxAge);
            adaptation.recordDeath(cause, biomes.at(c.x(), c.z()));
            deathCounts.merge(cause, 1, Integer::sum);
            deathAgeSums.merge(cause, c.age(), Double::sum);
            deaths++;
            listener.creatureDied(c);
        }
    }

    private static DeathCause causeOfDeath(Creature c, double maxAge) {
        if (c.age() >= maxAge * c.genome().longevity()) return DeathCause.OLD_AGE;
        return c.hydration() <= 0 ? DeathCause.DEHYDRATION : DeathCause.STARVATION;
    }

    /** Deaths so far by cause, most common first; causes that have not happened are left out. */
    public List<DeathTally> deathTallies() {
        List<DeathTally> tallies = new ArrayList<>();
        for (var e : deathCounts.entrySet()) {
            tallies.add(new DeathTally(e.getKey(), e.getValue(), deathAgeSums.get(e.getKey()) / e.getValue()));
        }
        tallies.sort(Comparator.comparingInt(DeathTally::count).reversed());
        return tallies;
    }

    public Stats stats() {
        int n = creatures.size();
        double speed = 0;
        double bodySize = 0;
        double vision = 0;
        double hydration = 0;
        double mood = 0;
        int depressed = 0;
        int sameSex = 0;
        int maxGeneration = 0;
        for (Creature c : creatures) {
            hydration += c.hydration();
            mood += c.mood();
            if (isDepressed(c)) depressed++;
            if (c.isSameSex()) sameSex++;
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
                diversity, births, deaths, elapsed,
                n == 0 ? 0 : hydration / n, n == 0 ? 0 : mood / n, depressed, sameSex);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
