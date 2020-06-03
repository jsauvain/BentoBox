package world.bentobox.bentobox.managers.island;

import org.bukkit.Location;
import org.bukkit.World;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Determines the locations for new islands
 *
 * @author tastybento, leonardochaia
 * @since 1.8.0
 */
public interface NewIslandLocationStrategy {
    void getNextLocation(World world, Consumer<Location> postAction) throws IOException;
}
