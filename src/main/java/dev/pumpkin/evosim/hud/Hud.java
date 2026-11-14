package dev.pumpkin.evosim.hud;

import dev.pumpkin.evosim.EvoSimPlugin;
import dev.pumpkin.evosim.sim.Simulation;
import dev.pumpkin.evosim.sim.Stats;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** A boss bar with live ecosystem stats, shown to players who opt in. */
public final class Hud implements Listener {

    private final EvoSimPlugin plugin;
    private final BossBar bar = BossBar.bossBar(
            Component.empty(), 0f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
    private final Set<UUID> viewers = new HashSet<>();
    private BukkitTask task;

    public Hud(EvoSimPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task == null) {
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refresh, 20L, 20L);
        }
    }

    /** Stops updating and hides the bar from everyone. */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID id : viewers) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) player.hideBossBar(bar);
        }
        viewers.clear();
    }

    public void show(Player player) {
        if (viewers.add(player.getUniqueId())) {
            player.showBossBar(bar);
        }
        refresh();
    }

    /** Returns true if the HUD is now visible to the player. */
    public boolean toggle(Player player) {
        if (viewers.remove(player.getUniqueId())) {
            player.hideBossBar(bar);
            return false;
        }
        show(player);
        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        viewers.remove(event.getPlayer().getUniqueId());
    }

    private void refresh() {
        if (viewers.isEmpty()) return;

        Optional<Simulation> simulation = plugin.simulation();
        if (simulation.isEmpty()) {
            bar.name(Component.text("EvoSim: no arena yet (use /evo create)"));
            bar.progress(0f);
            bar.color(BossBar.Color.RED);
            return;
        }

        Simulation sim = simulation.get();
        Stats s = sim.stats();
        int max = plugin.simConfig().population().max();
        String text = String.format(Locale.ROOT,
                "%s | Pop %d | Food %d | Gen %d | Speed %.2f | Size %.2f | Vision %.1f",
                sim.isRunning() ? "Running x" + trim(sim.timeScale()) : "Paused",
                s.population(), s.food(), s.maxGeneration(),
                s.meanSpeed(), s.meanSize(), s.meanVision());
        bar.name(Component.text(text));
        bar.progress((float) Math.max(0.0, Math.min(1.0, s.population() / (double) max)));
        bar.color(sim.isRunning() ? BossBar.Color.GREEN : BossBar.Color.YELLOW);
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }
}
