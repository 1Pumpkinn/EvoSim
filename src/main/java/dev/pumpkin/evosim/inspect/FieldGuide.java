package dev.pumpkin.evosim.inspect;

import dev.pumpkin.evosim.EvoSimPlugin;
import dev.pumpkin.evosim.config.SimConfig;
import dev.pumpkin.evosim.sim.Creature;
import dev.pumpkin.evosim.sim.Simulation;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The EvoSim Field Guide: a custom book item. Right-click a sheep with it to
 * open a book listing that creature's stats. Get one with {@code /evo book}.
 */
public final class FieldGuide implements Listener {

    private final EvoSimPlugin plugin;
    private final NamespacedKey key;

    public FieldGuide(EvoSimPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "field_guide");
    }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("EvoSim Field Guide", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Right-click a sheep to read its stats.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isGuide(ItemStack item) {
        if (item == null || item.getType() != Material.BOOK || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!isGuide(player.getInventory().getItemInMainHand())) return;

        Optional<Simulation> sim = plugin.simulation();
        if (sim.isEmpty()) return;
        Optional<Creature> creature = sim.get().creatureFor(event.getRightClicked());
        if (creature.isEmpty()) return;

        event.setCancelled(true);
        player.openBook(book(creature.get(), sim.get().config()));
    }

    private static Book book(Creature c, SimConfig config) {
        SimConfig.Needs needs = config.needs();
        double energyMax = config.population().reproductionThreshold();

        Component identity = Component.text()
                .append(Component.text("Sheep #" + c.id(), NamedTextColor.DARK_BLUE, TextDecoration.BOLD))
                .appendNewline().appendNewline()
                .append(line("Sex", c.isFemale() ? "Ewe" : "Ram"))
                .append(line("Pairs with", c.isSameSex() ? "Same sex" : "Opposite sex"))
                .append(line("Generation", String.valueOf(c.generation())))
                .append(line("Age", fmt(c.age(), 0) + " s / " + fmt(config.population().maxAgeSeconds(), 0) + " s"))
                .append(line("Adult", c.isAdult(config.population().maturitySeconds()) ? "Yes" : "No"))
                .append(line("Mating", c.isMating() ? "Yes" : "No"))
                .build();

        Component traits = Component.text()
                .append(Component.text("Genes", NamedTextColor.DARK_BLUE, TextDecoration.BOLD))
                .appendNewline().appendNewline()
                .append(line("Speed", fmt(c.genome().speed(), 2) + " blocks/s"))
                .append(line("Size", fmt(c.genome().size(), 2)))
                .append(line("Vision", fmt(c.genome().vision(), 1) + " blocks"))
                .append(line("Hue", fmt(c.genome().hue(), 2)))
                .appendNewline()
                .append(line("Upkeep", fmt(c.costPerSecond(config.energy()), 2) + " energy/s"))
                .build();

        String state = c.mood() < needs.depressionBelow() ? "Depressed"
                : c.hydration() < 25 ? "Thirsty"
                : c.energy() < energyMax * 0.35 ? "Hungry" : "Fine";
        Component wellbeing = Component.text()
                .append(Component.text("Needs", NamedTextColor.DARK_BLUE, TextDecoration.BOLD))
                .appendNewline().appendNewline()
                .append(line("Energy", fmt(c.energy(), 0) + " / " + fmt(energyMax, 0)))
                .append(line("Water", fmt(c.hydration(), 0) + " / 100"))
                .append(line("Mood", fmt(c.mood(), 0) + " / 100"))
                .appendNewline()
                .append(line("State", state))
                .append(line("Depressed below", fmt(needs.depressionBelow(), 0)))
                .build();

        return Book.book(Component.text("Sheep #" + c.id()), Component.text("EvoSim"),
                List.of(identity, traits, wellbeing));
    }

    private static Component line(String label, String value) {
        return Component.text()
                .append(Component.text(label + ": ", NamedTextColor.DARK_GRAY))
                .append(Component.text(value, NamedTextColor.BLACK))
                .appendNewline()
                .build();
    }

    private static String fmt(double v, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", v);
    }
}