package world.bentobox.bentobox.managers.island;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.util.Util;

import java.util.*;
import java.util.function.Consumer;

/**
 * The default strategy for generating locations for island
 *
 * @author tastybento, leonardochaia
 * @since 1.8.0
 */
public class DefaultNewIslandLocationStrategy implements NewIslandLocationStrategy {

    /**
     * The amount times to tolerate island check returning blocks without kwnon
     * island.
     */
    protected static final Integer MAX_UNOWNED_ISLANDS = 20;

    protected enum Result {
        ISLAND_FOUND, BLOCKS_IN_AREA, FREE
    }

    protected BentoBox plugin = BentoBox.getInstance();

    @Override
    public void getNextLocation(World world, Consumer<Location> postAction) {
        Location last = plugin.getIslands().getLast(world);
        if (last == null) {
            last = new Location(world,
                    (double) plugin.getIWM().getIslandXOffset(world) + plugin.getIWM().getIslandStartX(world),
                    plugin.getIWM().getIslandHeight(world),
                    (double) plugin.getIWM().getIslandZOffset(world) + plugin.getIWM().getIslandStartZ(world));
        }
        // Find a free spot
        Map<Result, Integer> result = new EnumMap<>(Result.class);
        findSpotForNewIsland(last, postAction, result);
    }


    private void findSpotForNewIsland(Location location, Consumer<Location> postAction, Map<Result, Integer> counts) {
        isIsland(location, result -> {
            if (!result.equals(Result.FREE) && counts.getOrDefault(Result.BLOCKS_IN_AREA, 0) < MAX_UNOWNED_ISLANDS) {
                nextGridLocation(location);
                counts.put(result, counts.getOrDefault(result, 0) + 1);
                findSpotForNewIsland(location, postAction, counts);
                return;
            }

            if (!result.equals(Result.FREE)) {
                // We could not find a free spot within the limit required. It's likely this
                // world is not empty
                plugin.logError("Could not find a free spot for islands! Is this world empty?");
                plugin.logError("Blocks around center locations: " + counts.getOrDefault(Result.BLOCKS_IN_AREA, 0) + " max "
                        + MAX_UNOWNED_ISLANDS);
                plugin.logError("Known islands: " + counts.getOrDefault(Result.ISLAND_FOUND, 0) + " max unlimited.");
                postAction.accept(null);
            }
            plugin.getIslands().setLast(location);
            postAction.accept(location);
        });
    }

    /**
     * Checks if there is an island or blocks at this location
     *
     * @param location - the location
     */
    protected void isIsland(Location location, Consumer<Result> postAction) {
        // Quick check
        if (plugin.getIslands().getIslandAt(location).isPresent()) {
            postAction.accept(Result.ISLAND_FOUND);
        }

        World world = location.getWorld();

        // Check 4 corners
        int dist = plugin.getIWM().getIslandDistance(location.getWorld());
        Set<Location> locs = new HashSet<>();
        locs.add(location);

        locs.add(new Location(world, location.getX() - dist, 0, location.getZ() - dist));
        locs.add(new Location(world, location.getX() - dist, 0, location.getZ() + dist - 1));
        locs.add(new Location(world, location.getX() + dist - 1, 0, location.getZ() - dist));
        locs.add(new Location(world, location.getX() + dist - 1, 0, location.getZ() + dist - 1));

        boolean generated = false;
        for (Location l : locs) {
            if (plugin.getIslands().getIslandAt(l).isPresent() || plugin.getIslandDeletionManager().inDeletion(l)) {
                postAction.accept(Result.ISLAND_FOUND);
                return;
            }
            if (Util.isChunkGenerated(l)) generated = true;
        }
        // If chunk has not been generated yet, then it's not occupied
        if (!generated) {
            postAction.accept(Result.FREE);
            return;
        }

        Util.getChunkAtAsync(location).thenAccept(chunk -> {
            Block block = chunk.getBlock(location.getBlockX(), location.getBlockY(), location.getBlockZ());
            // Block check
            boolean hasBlocksInArea = Arrays.stream(BlockFace.values())
                    .anyMatch(bf ->
                            !block.getRelative(bf).isEmpty() &&
                                    !block.getRelative(bf).getType().equals(Material.WATER)
                    );
            if (!plugin.getIWM().isUseOwnGenerator(world) && hasBlocksInArea) {
                // Block found
                plugin.getIslands().createIsland(location);
                postAction.accept(Result.BLOCKS_IN_AREA);
                return;
            }
            postAction.accept(Result.FREE);
        });
    }

    /**
     * Finds the next free island spot based off the last known island Uses
     * island_distance setting from the config file Builds up in a grid fashion
     *
     * @param lastIsland - last island location
     * @return Location of next free island
     */
    private Location nextGridLocation(final Location lastIsland) {
        int x = lastIsland.getBlockX();
        int z = lastIsland.getBlockZ();
        int d = plugin.getIWM().getIslandDistance(lastIsland.getWorld()) * 2;
        if (x < z) {
            if (-1 * x < z) {
                lastIsland.setX(lastIsland.getX() + d);
                return lastIsland;
            }
            lastIsland.setZ(lastIsland.getZ() + d);
            return lastIsland;
        }
        if (x > z) {
            if (-1 * x >= z) {
                lastIsland.setX(lastIsland.getX() - d);
                return lastIsland;
            }
            lastIsland.setZ(lastIsland.getZ() - d);
            return lastIsland;
        }
        if (x <= 0) {
            lastIsland.setZ(lastIsland.getZ() + d);
            return lastIsland;
        }
        lastIsland.setZ(lastIsland.getZ() - d);
        return lastIsland;
    }
}
