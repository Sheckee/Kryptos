package kryptos.content;

import arc.Events;
import arc.util.Timer;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.OreBlock;

import static mindustry.Vars.world;

/**
 * Sprinkles the customized Kryptos ore into the loaded map by converting a
 * minority of the existing ore patches (copper, lead, titanium, scrap,
 * thorium -- whatever the generator placed). Runs on every map load,
 * campaign sector or custom/skirmish map alike, so the ore is always
 * visible in-game regardless of how the map was started.
 *
 * IMPORTANT: this must NOT convert every ore tile it finds, or Kryptos Ore
 * ends up being the only mineral on the map, crowding out copper/lead/
 * titanium/thorium/scrap entirely. Instead, the map is divided into fixed
 * cells and each cell independently (and deterministically, based on map
 * size + cell coordinates) rolls a small chance of becoming a Kryptos
 * patch. Only ore tiles inside a "chosen" cell get converted; every other
 * cell's ore is left completely untouched.
 *
 * Some maps (particularly larger/procedural ones) haven't finished
 * placing every ore tile the instant WorldLoadEvent fires, so a single
 * sweep can miss patches. To catch those, the sweep repeats a few times
 * over the first couple seconds after load.
 */
public class KryptosOreInjector {
    private static final int RETRY_SWEEPS = 4;
    private static final float SWEEP_INTERVAL_SECONDS = 0.5f;

    // Tiles are grouped into CELL_SIZE x CELL_SIZE cells. Each cell has a
    // CLUSTER_CHANCE probability of being converted, which keeps Kryptos Ore
    // as clustered patches (like a real ore vein) instead of scattering
    // single tiles all over the map.
    private static final int CELL_SIZE = 10;
    private static final float CLUSTER_CHANCE = 0.18f;

    public static void init() {
        Events.on(WorldLoadEvent.class, e -> {
            convertAllOre();
            for (int i = 1; i <= RETRY_SWEEPS; i++) {
                Timer.schedule(KryptosOreInjector::convertAllOre, SWEEP_INTERVAL_SECONDS * i);
            }
        });
    }

    private static void convertAllOre() {
        OreBlock ore = KryptosBlocks.oreCustom;
        if (ore == null) {
            return;
        }

        int cellsX = (world.width() / CELL_SIZE) + 1;
        int cellsY = (world.height() / CELL_SIZE) + 1;

        for (int cx = 0; cx < cellsX; cx++) {
            for (int cy = 0; cy < cellsY; cy++) {
                if (!isKryptosCell(cx, cy)) {
                    continue;
                }

                int startX = cx * CELL_SIZE;
                int startY = cy * CELL_SIZE;
                int endX = Math.min(startX + CELL_SIZE, world.width());
                int endY = Math.min(startY + CELL_SIZE, world.height());

                for (int x = startX; x < endX; x++) {
                    for (int y = startY; y < endY; y++) {
                        Tile tile = world.tile(x, y);
                        if (tile == null) {
                            continue;
                        }

                        if (tile.overlay() instanceof OreBlock && tile.overlay() != ore) {
                            tile.setOverlay(ore);
                        }
                    }
                }
            }
        }
    }

    /**
     * Deterministic per-cell roll (hash-based, no external RNG dependency)
     * so the same map always produces the same Kryptos patches instead of
     * shifting every retry sweep or reload.
     */
    private static boolean isKryptosCell(int cx, int cy) {
        long seed = (long) world.width() * 341873128712L + (long) world.height() * 132897987541L
            + (long) cx * 668265263L + (long) cy * 374761393L;

        // MurmurHash3-style finalizer mix -> well distributed pseudo-random bits.
        seed = (seed ^ (seed >>> 33)) * 0xff51afd7ed558ccdL;
        seed = (seed ^ (seed >>> 33)) * 0xc4ceb9fe1a85ec53L;
        seed = seed ^ (seed >>> 33);

        float roll = (seed & 0xFFFFFF) / (float) 0xFFFFFF;
        return roll < CLUSTER_CHANCE;
    }
}
