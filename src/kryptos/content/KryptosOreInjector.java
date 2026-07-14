package kryptos.content;

import arc.Events;
import arc.util.Timer;
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
 *
 * Some maps (particularly larger/procedural ones) haven't finished
 * placing every ore tile the instant WorldLoadEvent fires, so a single
 * sweep can miss patches. To catch those, the sweep repeats a few times
 * over the first couple seconds after load.
 */
public class KryptosOreInjector {
    private static final int RETRY_SWEEPS = 4;
    private static final float SWEEP_INTERVAL_SECONDS = 0.5f;

    public static void init() {
        Events.on(WorldLoadEvent.class, e -> {
            convertAllOre();
            for (int i = 1; i <= RETRY_SWEEPS; i++) {
                Timer.schedule(KryptosOreInjector::convertAllOre, SWEEP_INTERVAL_SECONDS * i);
            }
        });
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
