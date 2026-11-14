package dev.pumpkin.evosim.arena;

import dev.pumpkin.evosim.config.SimConfig;
import dev.pumpkin.evosim.sim.Pond;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;

import java.util.List;
import java.util.Objects;

/**
 * A flat, walled square arena. Simulation coordinates are local: (0, 0) is
 * the interior's minimum corner and (size, size) the opposite one.
 */
public final class Arena {

    /** Scoreboard tag put on every entity EvoSim spawns, for cleanup. */
    public static final String ENTITY_TAG = "evosim";

    private final World world;
    private final int originX;
    private final int floorY;
    private final int originZ;
    private final int size;
    private final int wallHeight;
    private final Material floorBlock;
    private final Material wallBlock;

    /**
     * @param origin interior minimum corner; the floor is the block below it,
     *               so creatures stand at {@code origin}'s height
     */
    public Arena(Location origin, SimConfig.Arena config) {
        this.world = Objects.requireNonNull(origin.getWorld(), "origin has no world");
        this.originX = origin.getBlockX();
        this.originZ = origin.getBlockZ();
        this.floorY = origin.getBlockY() - 1;
        this.size = config.size();
        this.wallHeight = config.wallHeight();
        this.floorBlock = config.floorBlock();
        this.wallBlock = config.wallBlock();

        if (floorY < world.getMinHeight() || topY() >= world.getMaxHeight()) {
            throw new IllegalArgumentException("Arena does not fit between the world's height limits.");
        }
    }

    public World world() { return world; }

    public int size() { return size; }

    public int floorY() { return floorY; }

    /** Highest block layer the arena clears (walls plus headroom). */
    private int topY() { return floorY + wallHeight + 2; }

    public boolean contains(double x, double z) {
        return x >= 0 && x < size && z >= 0 && z < size;
    }

    /** World location for arena-local coordinates, standing on the floor. */
    public Location toLocation(double x, double z) {
        return new Location(world, originX + x, floorY + 1, originZ + z);
    }

    public Location centerLocation() {
        return toLocation(size / 2.0, size / 2.0);
    }

    /** Places floor, walls and ponds, and clears the air space above the floor. */
    public void build(List<Pond> ponds) {
        int minX = originX - 1;
        int maxX = originX + size;
        int minZ = originZ - 1;
        int maxZ = originZ + size;
        int top = topY();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                boolean edge = x == minX || x == maxX || z == minZ || z == maxZ;
                world.getBlockAt(x, floorY, z).setType(edge ? wallBlock : floorBlock, false);
                for (int y = floorY + 1; y <= top; y++) {
                    Material m = edge && y <= floorY + wallHeight ? wallBlock : Material.AIR;
                    world.getBlockAt(x, y, z).setType(m, false);
                }
            }
        }
        buildPonds(ponds);
    }

    /** Fills each pond's footprint with water in the floor layer. */
    private void buildPonds(List<Pond> ponds) {
        for (Pond pond : ponds) {
            int x0 = (int) Math.floor(pond.x() - pond.radius());
            int x1 = (int) Math.ceil(pond.x() + pond.radius());
            int z0 = (int) Math.floor(pond.z() - pond.radius());
            int z1 = (int) Math.ceil(pond.z() + pond.radius());
            for (int lx = Math.max(0, x0); lx <= Math.min(size - 1, x1); lx++) {
                for (int lz = Math.max(0, z0); lz <= Math.min(size - 1, z1); lz++) {
                    if (!pond.contains(lx + 0.5, lz + 0.5)) continue;
                    var below = world.getBlockAt(originX + lx, floorY - 1, originZ + lz);
                    if (!below.getType().isSolid()) below.setType(floorBlock, false);
                    world.getBlockAt(originX + lx, floorY, originZ + lz).setType(Material.WATER, false);
                }
            }
        }
    }

    /** Removes every block of the arena (floor, walls) and all EvoSim entities in it. */
    public void demolish() {
        purgeEntities();
        for (int x = originX - 1; x <= originX + size; x++) {
            for (int z = originZ - 1; z <= originZ + size; z++) {
                for (int y = floorY; y <= topY(); y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    /** Removes leftover EvoSim display entities, e.g. after a crash. */
    public void purgeEntities() {
        BoundingBox box = new BoundingBox(
                originX - 1, floorY, originZ - 1,
                originX + size + 1, topY() + 1, originZ + size + 1);
        world.getNearbyEntities(box, e -> e.getScoreboardTags().contains(ENTITY_TAG))
                .forEach(e -> e.remove());
    }

    /** Keeps the arena's chunks loaded so the simulation never stalls. */
    public void keepLoaded(Plugin plugin) {
        for (int cx = (originX - 1) >> 4; cx <= (originX + size) >> 4; cx++) {
            for (int cz = (originZ - 1) >> 4; cz <= (originZ + size) >> 4; cz++) {
                world.addPluginChunkTicket(cx, cz, plugin);
            }
        }
    }

    public void releaseChunks(Plugin plugin) {
        for (int cx = (originX - 1) >> 4; cx <= (originX + size) >> 4; cx++) {
            for (int cz = (originZ - 1) >> 4; cz <= (originZ + size) >> 4; cz++) {
                world.removePluginChunkTicket(cx, cz, plugin);
            }
        }
    }
}
