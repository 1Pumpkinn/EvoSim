package dev.pumpkin.evosim;

import dev.pumpkin.evosim.arena.Arena;
import dev.pumpkin.evosim.command.EvoCommand;
import dev.pumpkin.evosim.config.SimConfig;
import dev.pumpkin.evosim.hud.Hud;
import dev.pumpkin.evosim.sim.Simulation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.Optional;

public final class EvoSimPlugin extends JavaPlugin {

    private SimConfig simConfig;
    private Simulation simulation;
    private Hud hud;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSimConfig();

        hud = new Hud(this);
        getServer().getPluginManager().registerEvents(hud, this);
        hud.start();

        PluginCommand command = Objects.requireNonNull(getCommand("evo"), "evo command missing from plugin.yml");
        EvoCommand executor = new EvoCommand(this, hud);
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        getLogger().info("EvoSim enabled. Use /evo create to build an arena.");
    }

    @Override
    public void onDisable() {
        if (hud != null) {
            hud.stop();
        }
        if (simulation != null) {
            simulation.shutdown();
            simulation = null;
        }
    }

    /** Re-reads config.yml and rebuilds the validated {@link SimConfig}. */
    public void reloadSimConfig() {
        reloadConfig();
        simConfig = SimConfig.load(getConfig(), getLogger());
    }

    public SimConfig simConfig() {
        return simConfig;
    }

    /** The current simulation, if an arena has been created. */
    public Optional<Simulation> simulation() {
        return Optional.ofNullable(simulation);
    }

    /**
     * Builds a fresh arena at {@code origin} (its minimum corner; the floor is
     * the block below) and creates a paused simulation in it. Any previous
     * simulation is shut down first. Throws IllegalArgumentException if the
     * arena does not fit the world's height limits.
     */
    public Simulation createSimulation(Location origin) {
        Arena arena = new Arena(origin, simConfig.arena());
        if (simulation != null) {
            simulation.shutdown();
        }
        arena.purgeEntities();
        arena.build();
        arena.keepLoaded(this);
        simulation = new Simulation(this, simConfig, arena);
        simulation.onExtinction(() -> getServer().broadcast(
                Component.text("[EvoSim] The population went extinct. Use /evo reset to try again.",
                        NamedTextColor.RED)));
        return simulation;
    }

    /** Stops the simulation and removes the arena's blocks and entities. */
    public void removeSimulation() {
        if (simulation == null) return;
        Simulation old = simulation;
        simulation = null;
        old.stop();
        old.arena().demolish();
        old.shutdown();
    }
}
