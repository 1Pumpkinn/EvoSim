package dev.pumpkin.evosim.render;

import dev.pumpkin.evosim.arena.Arena;
import dev.pumpkin.evosim.sim.Food;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Renders one piece of food as a small floating berry; rare food is a glowing golden apple. */
public final class FoodView {

    private final ItemDisplay display;

    public FoodView(Arena arena, Food food) {
        Location location = arena.toLocation(food.x(), food.z()).add(0, 0.3, 0);
        this.display = arena.world().spawn(location, ItemDisplay.class, d -> {
            float scale = food.rare() ? 0.8f : 0.6f;
            d.setItemStack(new ItemStack(food.rare() ? Material.GOLDEN_APPLE : Material.SWEET_BERRIES));
            d.setBillboard(Display.Billboard.CENTER);
            d.setTransformation(new Transformation(
                    new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf()));
            if (food.rare()) {
                d.setGlowing(true);
                d.setGlowColorOverride(Color.YELLOW);
            }
            d.setPersistent(false);
            d.addScoreboardTag(Arena.ENTITY_TAG);
        });
    }

    public void remove() {
        display.remove();
    }
}
