package dev.pumpkin.evosim;

import dev.pumpkin.evosim.config.SimConfig;
import org.bukkit.plugin.java.JavaPlugin;

public final class EvoSimPlugin extends JavaPlugin {

    private SimConfig simConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSimConfig();
        getLogger().info("EvoSim enabled: " + simConfig.arena().size() + "x"
                + simConfig.arena().size() + " arena, "
                + simConfig.population().initial() + " starting creatures.");
    }

    /** Re-reads config.yml and rebuilds the validated {@link SimConfig}. */
    public void reloadSimConfig() {
        reloadConfig();
        simConfig = SimConfig.load(getConfig(), getLogger());
    }

    public SimConfig simConfig() {
        return simConfig;
    }
}
