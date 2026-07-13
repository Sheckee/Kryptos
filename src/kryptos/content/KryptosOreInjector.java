package kryptos.content;

import arc.Events;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.OreBlock;

import static mindustry.Vars.world;

/**
 * Converts every existing ore tile on the loaded map (copper, lead,
 * titanium, scrap, thorium -- whatever the generator placed) into the
 * customized Kryptos ore. Runs on every map load, campaign sector or
 * custom/skirmish map alike, so the ore is always visible in-game
 * regardless of how the map was started.
 */
public class KryptosOreInjector {
    public static void init() {
        Events.on(WorldLoadEvent.class, e -> convertAllOre());
    }

    private static void convertAllOre() {
        if (KryptosBlocks.oreCustom == null) {
            return;
        }

        for (int x = 0; x < world.width(); x++) {
            for (int y = 0; y < world.height(); y++) {
                Tile tile = world.tile(x, y);
                if (tile == null) {
                    continue;
                }

                if (tile.overlay() instanceof OreBlock && tile.overlay() != KryptosBlocks.oreCustom) {
                    tile.setOverlay(KryptosBlocks.oreCustom);
                }
            }
        }
    }
}
