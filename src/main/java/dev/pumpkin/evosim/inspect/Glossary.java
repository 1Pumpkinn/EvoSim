package dev.pumpkin.evosim.inspect;

import dev.pumpkin.evosim.genetics.Genome;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Plain-language explanations for everything the Field Guide shows, and the helpers that
 * turn them into hover text boxes.
 */
final class Glossary {

    private Glossary() {}

    /** Names and explanation of one gene. */
    record TraitInfo(String shortName, String fullName, String description) {}

    static TraitInfo info(Genome.Trait trait) {
        return switch (trait) {
            case SPEED -> new TraitInfo("Speed", "Speed",
                    "Walking speed in blocks per second. Fast sheep reach food and mates first, "
                            + "but upkeep grows with speed squared.");
            case SIZE -> new TraitInfo("Size", "Body size",
                    "Body scale. Big sheep get first pick of contested food, but upkeep grows with size cubed.");
            case VISION -> new TraitInfo("Vision", "Vision",
                    "How far, in blocks, it notices food, water and other sheep. Wide vision costs upkeep, "
                            + "and a sheep with nobody in sight gets lonely.");
            case LONGEVITY -> new TraitInfo("Lifespan", "Lifespan",
                    "Multiplier on how long it lives. Long-lived sheep have more time to find mates.");
            case METABOLISM -> new TraitInfo("Upkeep", "Metabolism",
                    "Multiplier on the energy burned every second. Lower is more efficient. The value shown "
                            + "includes special mutations and the small upkeep price of resistances and rich digestion.");
            case THIRST_RATE -> new TraitInfo("Thirst", "Thirst rate",
                    "Multiplier on how fast water drains. Camel-like sheep can roam far from ponds.");
            case GRIEF_SENSITIVITY -> new TraitInfo("Grief", "Grief sensitivity",
                    "Multiplier on the mood lost when sheep die nearby. Stoic sheep stay cheerful through losses.");
            case REST_TIME -> new TraitInfo("Rest", "Rest time",
                    "Multiplier on how long a ram rests after mating before he looks for a mate again. "
                            + "Quick recovery means more matings.");
            case COLD_RESISTANCE -> new TraitInfo("Cold", "Cold resistance",
                    "Divides the extra energy burned in the tundra. Frostproof sheep thrive there. "
                            + "Every resistance costs a little upkeep in all biomes.");
            case HEAT_TOLERANCE -> new TraitInfo("Heat", "Heat tolerance",
                    "Divides the extra thirst suffered in the desert. Sun-hardened sheep can cross it. "
                            + "Every resistance costs a little upkeep in all biomes.");
            case SURE_FOOT -> new TraitInfo("Footing", "Sure-footedness",
                    "Softens how much forest and swamp slow its walking. "
                            + "Every resistance costs a little upkeep in all biomes.");
            case DIGESTION -> new TraitInfo("Digest", "Digestion",
                    "Multiplier on the energy gained from each piece of food. Rich digestion also raises upkeep.");
            case MUTABILITY -> new TraitInfo("Volatility", "Mutability",
                    "Multiplier on this sheep's own chance of mutating when it is born, including new special "
                            + "mutations. Volatile lines evolve faster but drift more at random.");
            case HUE -> new TraitInfo("Hue", "Hue",
                    "A neutral colour marker. It does nothing except make lineages easy to tell apart.");
        };
    }

    /** A tooltip: a bold coloured title followed by grey lines. */
    static Component tip(String title, TextColor titleColor, String... lines) {
        TextComponent.Builder b = Component.text()
                .append(Component.text(title, titleColor, TextDecoration.BOLD));
        for (String line : lines) {
            b.appendNewline().append(Component.text(line, NamedTextColor.GRAY));
        }
        return b.build();
    }

    /** Adds a line to an existing tooltip. */
    static Component withLine(Component tip, String text, TextColor color) {
        return Component.text().append(tip).appendNewline().append(Component.text(text, color)).build();
    }

    /** Text that shows {@code tip} in a hover box. */
    static Component hover(String text, TextColor color, Component tip) {
        return Component.text(text, color).hoverEvent(HoverEvent.showText(tip));
    }
}
