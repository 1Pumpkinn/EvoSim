package dev.pumpkin.evosim.inspect;

import dev.pumpkin.evosim.EvoSimPlugin;
import dev.pumpkin.evosim.config.SimConfig;
import dev.pumpkin.evosim.genetics.Genome;
import dev.pumpkin.evosim.genetics.Genome.Trait;
import dev.pumpkin.evosim.genetics.Mutation;
import dev.pumpkin.evosim.sim.Biome;
import dev.pumpkin.evosim.sim.BiomeMap;
import dev.pumpkin.evosim.sim.Creature;
import dev.pumpkin.evosim.sim.Simulation;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The EvoSim Field Guide: a custom book item. Right-click a sheep with it to
 * open a book listing that creature's stats. Get one with {@code /evo book}.
 *
 * <p>Page 1 is a contents page with clickable links. Hover over any label, value or bar
 * for a text box that explains it.
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
        player.openBook(book(creature.get(), sim.get().config(), sim.get().biomes()));
    }

    private static final TextColor LABEL = NamedTextColor.DARK_GRAY;
    private static final TextColor VALUE = NamedTextColor.BLACK;
    private static final TextColor HEADING = NamedTextColor.DARK_BLUE;
    private static final TextColor GOOD = NamedTextColor.DARK_GREEN;
    private static final TextColor BAD = NamedTextColor.DARK_RED;

    /** Gene marks per page; a line can wrap, so keep this low. */
    private static final int MARKS_PER_PAGE = 5;
    private static final int PAGE_OVERVIEW = 1;
    private static final int PAGE_BODY_GENES = 2;
    private static final int PAGE_SURVIVAL_GENES = 3;
    private static final int PAGE_SPECIALS = 4;
    private static final int PAGE_MARKS = 5;

    private static Book book(Creature c, SimConfig config, BiomeMap biomes) {
        Genome g = c.genome();
        List<Genome.Mark> marks = g.marks();
        int markPages = Math.max(1, (marks.size() + MARKS_PER_PAGE - 1) / MARKS_PER_PAGE);
        int needsPage = PAGE_MARKS + markPages;

        List<Component> pages = new ArrayList<>();
        pages.add(overviewPage(c, config, marks.size() + g.specials().size(), needsPage));
        pages.add(genePage("Body genes", g, config, c, Trait.SPEED, Trait.SIZE, Trait.VISION, Trait.LONGEVITY,
                Trait.METABOLISM, Trait.HUE));
        pages.add(genePage("Survival genes", g, config, null, Trait.THIRST_RATE, Trait.GRIEF_SENSITIVITY,
                Trait.REST_TIME, Trait.COLD_RESISTANCE, Trait.HEAT_TOLERANCE, Trait.SURE_FOOT, Trait.DIGESTION,
                Trait.MUTABILITY));
        pages.add(specialsPage(g, config));
        pages.addAll(markPages(g, marks, markPages));
        pages.add(needsPage(c, config, biomes));

        return Book.book(Component.text("Sheep #" + c.id()), Component.text("EvoSim"), pages);
    }

    // --- Pages ---

    private static Component overviewPage(Creature c, SimConfig config, int mutationCount, int needsPage) {
        SimConfig.Population pop = config.population();
        double maxAge = pop.maxAgeSeconds() * c.genome().longevity();

        Component sex = c.isFemale()
                ? Glossary.hover("Ewe", VALUE, Glossary.tip("Ewe", NamedTextColor.GOLD,
                        "Female. After mating she rests for a fixed time before she looks for a mate again."))
                : Glossary.hover("Ram", VALUE, Glossary.tip("Ram", NamedTextColor.GOLD,
                        "Male. After mating his rest time depends on his rest-time gene and mutations."));
        Component generation = Glossary.hover("Gen " + c.generation(), VALUE, Glossary.tip("Generation",
                NamedTextColor.GOLD,
                "Founders are generation 0. A child is one generation after its older parent."));
        Component pairs = Glossary.hover("Pairs: ", LABEL, Glossary.tip("Orientation", NamedTextColor.GOLD,
                "Which sex it pairs with. Same-sex pairs bond and show hearts like any pair, but no child is born."));
        Component age = Glossary.hover("Age: ", LABEL, Glossary.tip("Age", NamedTextColor.GOLD,
                "Simulated seconds lived, out of its lifespan: the base maximum age scaled by its lifespan gene "
                        + "and mutations. At the end it dies of old age."));
        Component adult = Glossary.hover("Adult: ", LABEL, Glossary.tip("Adult", NamedTextColor.GOLD,
                "Newborns must grow up for " + fmt(pop.maturitySeconds(), 0) + " s before they can mate."));

        return Component.text()
                .append(Component.text("Sheep #" + c.id(), HEADING, TextDecoration.BOLD))
                .appendNewline().appendNewline()
                .append(sex).append(Component.text(" | ", LABEL)).append(generation)
                .appendNewline()
                .append(pairs).append(Component.text(c.isSameSex() ? "Same sex" : "Other sex", VALUE))
                .appendNewline()
                .append(age).append(Component.text(fmt(c.age(), 0) + "/" + fmt(maxAge, 0) + " s", VALUE))
                .appendNewline()
                .append(adult).append(Component.text(c.isAdult(pop.maturitySeconds()) ? "Yes" : "No", VALUE))
                .appendNewline().appendNewline()
                .append(Component.text("Contents", HEADING, TextDecoration.BOLD))
                .appendNewline()
                .append(link("> Genes", PAGE_BODY_GENES, "Body and survival genes"))
                .appendNewline()
                .append(link("> Special mutations (" + c.genome().specials().size() + ")", PAGE_SPECIALS,
                        "Procedurally generated mutations"))
                .appendNewline()
                .append(link("> Gene marks", PAGE_MARKS, "Genes near an extreme of their range"))
                .appendNewline()
                .append(link("> Needs", needsPage, "Energy, water, mood and surroundings"))
                .build();
    }

    /** A page of gene lines. Pass the creature to add the live energy upkeep line. */
    private static Component genePage(String title, Genome g, SimConfig config, Creature upkeepFor, Trait... traits) {
        TextComponent.Builder page = Component.text()
                .append(Component.text(title, HEADING, TextDecoration.BOLD))
                .appendNewline().appendNewline();
        for (Trait t : traits) {
            page.append(traitLine(g, t)).appendNewline();
        }
        if (upkeepFor != null) {
            page.appendNewline().append(Glossary.hover("Upkeep: ", LABEL, Glossary.tip("Energy upkeep",
                            NamedTextColor.GOLD,
                            "Energy burned per second before biome effects: a base cost plus speed squared, "
                                    + "size cubed and vision, all times the upkeep multiplier.")))
                    .append(Component.text(fmt(upkeepFor.costPerSecond(config.energy()), 2) + "/s", VALUE))
                    .appendNewline();
        }
        return page.append(contentsLink()).build();
    }

    private static Component traitLine(Genome g, Trait t) {
        Glossary.TraitInfo info = Glossary.info(t);
        double base = g.get(t);
        double shown = effective(g, t);
        Genome.Mark mark = markFor(g, t);

        Component tip = Glossary.tip(info.fullName(), NamedTextColor.GOLD, info.description());
        tip = Glossary.withLine(tip, "This sheep: " + format(t, shown), NamedTextColor.WHITE);
        if (Math.abs(shown - base) > 0.005) {
            tip = Glossary.withLine(tip, "Gene alone: " + format(t, base) + " (mutations and perks change it)",
                    NamedTextColor.GRAY);
        }
        tip = Glossary.withLine(tip, "Range: " + format(t, t.min()) + " to " + format(t, t.max()),
                NamedTextColor.DARK_GRAY);
        if (mark != null) {
            tip = Glossary.withLine(tip, "Mutation: " + mark.label(), NamedTextColor.LIGHT_PURPLE);
        }

        TextColor barColor = mark != null ? NamedTextColor.DARK_PURPLE : NamedTextColor.DARK_AQUA;
        return Component.text()
                .append(Glossary.hover(info.shortName() + " ", LABEL, tip))
                .append(Glossary.hover(format(t, shown), VALUE, tip))
                .append(Component.text(" "))
                .append(bar(t.normalize(base), 8, barColor).hoverEvent(HoverEvent.showText(tip)))
                .build();
    }

    private static Component specialsPage(Genome g, SimConfig config) {
        TextComponent.Builder page = Component.text()
                .append(Component.text("Special mutations", HEADING, TextDecoration.BOLD))
                .appendNewline();
        List<Mutation> specials = g.specials();
        if (specials.isEmpty()) {
            page.appendNewline().append(Component.text(
                    "None. A newborn rarely gains a brand new mutation with a random benefit and often a "
                            + "random drawback. It can be passed on to children.", NamedTextColor.GRAY)).appendNewline();
        }
        double inherit = config.evolution().specialInheritance();
        for (Mutation m : specials) {
            TextColor color = rarityColor(m.rarity());
            Component tip = Glossary.tip(m.name(), color,
                    m.rarity().displayName() + " special mutation. Each parent passes it on to a child with a "
                            + fmt(inherit * 100, 0) + "% chance, so it can spread or die out.");
            for (Mutation.Effect e : m.effects()) {
                tip = Glossary.withLine(tip, e.percent() + " " + e.stat().displayName() + ": "
                        + e.stat().description(), e.beneficial() ? NamedTextColor.GREEN : NamedTextColor.RED);
            }
            page.append(Glossary.hover(m.name(), color, tip).decoration(TextDecoration.BOLD, true)).appendNewline();
            for (Mutation.Effect e : m.effects()) {
                page.append(Glossary.hover(" " + e.percent() + " " + e.stat().displayName(),
                        e.beneficial() ? GOOD : BAD, Glossary.tip(e.stat().displayName(), NamedTextColor.GOLD,
                                e.stat().description()))).appendNewline();
            }
        }
        return page.append(contentsLink()).build();
    }

    private static List<Component> markPages(Genome g, List<Genome.Mark> marks, int pageCount) {
        List<Component> pages = new ArrayList<>();
        for (int p = 0; p < pageCount; p++) {
            String title = pageCount == 1 ? "Gene marks" : "Gene marks " + (p + 1) + "/" + pageCount;
            TextComponent.Builder page = Component.text()
                    .append(Component.text(title, HEADING, TextDecoration.BOLD))
                    .appendNewline();
            if (marks.isEmpty()) {
                page.appendNewline().append(Component.text(
                        "None. A gene earns a mark, such as Swift or Stoic, once it sits in the outer quarter "
                                + "of its range.", NamedTextColor.GRAY)).appendNewline();
            } else {
                page.append(Component.text("Hover for details.", NamedTextColor.GRAY)).appendNewline();
            }
            int from = p * MARKS_PER_PAGE;
            int to = Math.min(marks.size(), from + MARKS_PER_PAGE);
            for (int i = from; i < to; i++) {
                Genome.Mark mark = marks.get(i);
                Trait t = mark.trait();
                Glossary.TraitInfo info = Glossary.info(t);
                Component tip = Glossary.tip(mark.label(), NamedTextColor.LIGHT_PURPLE,
                        info.fullName() + " is in the " + (mark.high() ? "top" : "bottom")
                                + " quarter of its range.", info.description());
                tip = Glossary.withLine(tip, info.fullName() + ": " + format(t, effective(g, t)),
                        NamedTextColor.WHITE);
                page.append(Glossary.hover(mark.label(), NamedTextColor.DARK_PURPLE, tip))
                        .append(Component.text(" (" + info.shortName() + ")", NamedTextColor.GRAY))
                        .appendNewline();
            }
            pages.add(page.append(contentsLink()).build());
        }
        return pages;
    }

    private static Component needsPage(Creature c, SimConfig config, BiomeMap biomes) {
        SimConfig.Needs needs = config.needs();
        Genome g = c.genome();
        double energyMax = config.population().reproductionThreshold();
        Biome biome = biomes.at(c.x(), c.z());

        String state;
        String stateTip;
        if (c.isMating()) {
            state = "Mating";
            stateTip = "Paired up under floating hearts, waiting for the child to be born.";
        } else if (c.isResting()) {
            state = "Resting (" + fmt(c.restRemaining(), 0) + " s)";
            stateTip = "Recovering after mating. It looks for no mate until the rest is over.";
        } else if (c.mood() < needs.depressionBelow()) {
            state = "Depressed";
            stateTip = "Mood is below " + fmt(needs.depressionBelow(), 0)
                    + ". Depressed sheep walk slower and will not mate.";
        } else if (c.hydration() < 25) {
            state = "Thirsty";
            stateTip = "Water is below 25. It heads for a pond it can see or remembers.";
        } else if (c.energy() < energyMax * 0.35) {
            state = "Hungry";
            stateTip = "Energy is below 35% of the amount needed to reproduce. It looks for food.";
        } else {
            state = "Fine";
            stateTip = "Nothing is wrong right now.";
        }

        Component biomeTip = Glossary.tip(biome.displayName(), NamedTextColor.GOLD,
                "Food growth x" + fmt(biome.foodFactor(), 2),
                "Thirst x" + fmt(biome.thirstFactor(), 2),
                "Upkeep x" + fmt(biome.upkeepFactor(), 2),
                "Walking x" + fmt(biome.speedFactor(), 2));
        biomeTip = Glossary.withLine(biomeTip, "For this sheep, with its genes:", NamedTextColor.WHITE);
        biomeTip = Glossary.withLine(biomeTip, "Thirst x" + fmt(g.desertThirst(biome.thirstFactor()), 2)
                + ", upkeep x" + fmt(g.coldUpkeep(biome.upkeepFactor()), 2)
                + ", walking x" + fmt(g.terrainSpeed(biome.speedFactor()), 2), NamedTextColor.GRAY);

        double upkeep = c.costPerSecond(config.energy()) * g.coldUpkeep(biome.upkeepFactor());

        return Component.text()
                .append(Component.text("Needs", HEADING, TextDecoration.BOLD))
                .appendNewline().appendNewline()
                .append(meter("Energy ", fmt(c.energy(), 0) + "/" + fmt(energyMax, 0), c.energy() / energyMax,
                        NamedTextColor.GOLD, Glossary.tip("Energy", NamedTextColor.GOLD,
                                "Spent on living and moving, gained by eating. At 0 the sheep starves. "
                                        + "Mating needs enough energy to pay for a child.")))
                .appendNewline()
                .append(meter("Water ", fmt(c.hydration(), 0) + "/100", c.hydration() / 100.0,
                        NamedTextColor.DARK_AQUA, Glossary.tip("Water", NamedTextColor.GOLD,
                                "Drains over time, refilled by standing in a pond. At 0 the sheep loses energy "
                                        + "until it dies of dehydration.")))
                .appendNewline()
                .append(meter("Mood ", fmt(c.mood(), 0) + "/100", c.mood() / 100.0,
                        c.mood() >= 60 ? GOOD : c.mood() >= needs.depressionBelow() ? NamedTextColor.GOLD : BAD,
                        Glossary.tip("Mood", NamedTextColor.GOLD,
                                "Falls when hungry, thirsty, alone or when others die nearby. Eating and mating "
                                        + "lift it. Below " + fmt(needs.depressionBelow(), 0) + " the sheep is depressed.")))
                .appendNewline().appendNewline()
                .append(Glossary.hover("State: ", LABEL, Glossary.tip("State", NamedTextColor.GOLD, stateTip)))
                .append(Component.text(state, VALUE))
                .appendNewline()
                .append(Glossary.hover("Doing: ", LABEL, Glossary.tip("Activity", NamedTextColor.GOLD,
                        "What its steering is aiming for right now.")))
                .append(Component.text(c.activity().label(), VALUE))
                .appendNewline()
                .append(Glossary.hover("Biome: ", LABEL, biomeTip))
                .append(Glossary.hover(biome.displayName(), VALUE, biomeTip))
                .appendNewline()
                .append(Glossary.hover("Upkeep: ", LABEL, Glossary.tip("Upkeep here", NamedTextColor.GOLD,
                        "Energy burned per second in this biome, including the cold of the tundra.")))
                .append(Component.text(fmt(upkeep, 2) + "/s", VALUE))
                .appendNewline()
                .append(contentsLink())
                .build();
    }

    // --- Pieces ---

    private static Genome.Mark markFor(Genome g, Trait t) {
        for (Genome.Mark m : g.marks()) {
            if (m.trait() == t) return m;
        }
        return null;
    }

    /** The value a gene actually has in play, with special mutations and perk costs applied. */
    private static double effective(Genome g, Trait t) {
        return switch (t) {
            case SPEED -> g.speed();
            case VISION -> g.vision();
            case LONGEVITY -> g.longevity();
            case METABOLISM -> g.metabolism();
            case THIRST_RATE -> g.thirstRate();
            case GRIEF_SENSITIVITY -> g.griefSensitivity();
            case REST_TIME -> g.restTime();
            default -> g.get(t);
        };
    }

    private static String format(Trait t, double v) {
        return switch (t) {
            case SPEED -> fmt(v, 1) + "/s";
            case SIZE, HUE -> fmt(v, 2);
            case VISION -> fmt(v, 1);
            default -> "x" + fmt(v, 2);
        };
    }

    private static TextColor rarityColor(Mutation.Rarity rarity) {
        return switch (rarity) {
            case MINOR -> NamedTextColor.DARK_AQUA;
            case NOTABLE -> NamedTextColor.DARK_PURPLE;
            case MAJOR -> NamedTextColor.GOLD;
        };
    }

    /** A labelled value and bar on one line that all show the same tooltip. */
    private static Component meter(String label, String value, double ratio, TextColor color, Component tip) {
        return Component.text()
                .append(Glossary.hover(label, LABEL, tip))
                .append(Glossary.hover(value, VALUE, tip))
                .append(Component.text(" "))
                .append(bar(ratio, 8, color).hoverEvent(HoverEvent.showText(tip)))
                .build();
    }

    private static Component bar(double ratio, int total, TextColor color) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, ratio)) * total);
        return Component.text()
                .append(Component.text("|".repeat(filled), color))
                .append(Component.text("|".repeat(total - filled), NamedTextColor.GRAY))
                .build();
    }

    private static Component link(String text, int page, String hint) {
        return Component.text(text, HEADING)
                .hoverEvent(HoverEvent.showText(Component.text(hint + " (page " + page + ")", NamedTextColor.GRAY)))
                .clickEvent(ClickEvent.changePage(page));
    }

    private static Component contentsLink() {
        return Component.text().appendNewline()
                .append(Component.text("<< Contents", NamedTextColor.DARK_GRAY)
                        .hoverEvent(HoverEvent.showText(Component.text("Back to page 1", NamedTextColor.GRAY)))
                        .clickEvent(ClickEvent.changePage(PAGE_OVERVIEW)))
                .build();
    }

    private static String fmt(double v, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", v);
    }
}
