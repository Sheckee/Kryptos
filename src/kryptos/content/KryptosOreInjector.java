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
 * Cell chance is scaled by a biome density multiplier sampled from the
 * cell's dominant floor tile: bare rock/ice/volcanic ground gets denser
 * Kryptos veins, soft/organic ground (moss, sand) gets sparser ones. This
 * gives per-sector variety in how much Kryptos shows up without ever
 * touching the underlying vanilla ore distribution, which is what actually
 * provides Copper/Lead/Titanium/Thorium/Scrap variety across sectors. A
 * hard cap keeps any biome from ever approaching full conversion.
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
    // chance of being converted, which keeps Kryptos Ore as clustered
    // patches (like a real ore vein) instead of scattering single tiles
    // all over the map.
    private static final int CELL_SIZE = 10;
    private static final float BASE_CLUSTER_CHANCE = 0.18f;
    private static final float MAX_CLUSTER_CHANCE = 0.6f;

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
     * Samples the floor of the cell's center tile and returns a density
     * multiplier for that biome. Bare rock/ice/volcanic ground gets a
     * denser Kryptos vein chance; soft/organic ground (moss, sand) gets a
     * sparser one. Anything unrecognized falls back to a neutral 1.0x so
     * this degrades gracefully on maps/mods with unfamiliar floor sets.
     */
    private static float biomeDensity(int cx, int cy) {
        int sampleX = Math.min(cx * CELL_SIZE + CELL_SIZE / 2, world.width() - 1);
        int sampleY = Math.min(cy * CELL_SIZE + CELL_SIZE / 2, world.height() - 1);

        Tile sample = world.tile(sampleX, sampleY);
        if (sample == null || sample.floor() == null) {
            return 1f;
        }

        String floorName = sample.floor().name;

        if (containsAny(floorName, "ice", "snow", "salt")) {
            return 1.6f; // frozen ground: rich veins
        }
        if (containsAny(floorName, "hotrock", "magma", "char", "slag", "scorch", "ash")) {
            return 1.8f; // volcanic/scorched ground: richest veins
        }
        if (containsAny(floorName, "stone", "rock", "shale", "dacite", "carbon", "ferric", "beryl", "crystal", "yellow", "regolith")) {
            return 1.3f; // bare rocky ground: above-average
        }
        if (containsAny(floorName, "moss", "spore", "tar")) {
            return 0.5f; // organic/vegetated ground: sparse
        }
        if (containsAny(floorName, "sand", "dune", "mud")) {
            return 0.8f; // soft ground: below-average
        }

        return 1f;
    }

    private static boolean containsAny(String haystack, String... needles) {
        String lower = haystack.toLowerCase();
        for (String needle : needles) {
            if (lower.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Deterministic per-cell roll (hash-based, no external RNG dependency)
     * so the same map always produces the same Kryptos patches instead of
     * shifting every retry sweep or reload. The roll threshold is scaled
     * by the cell's biome density (capped) so richer biomes get a higher
     * effective chance without needing a second RNG stream.
     */
    private static boolean isKryptosCell(int cx, int cy) {
        long seed = (long) world.width() * 341873128712L + (long) world.height() * 132897987541L
            + (long) cx * 668265263L + (long) cy * 374761393L;

        // MurmurHash3-style finalizer mix -> well distributed pseudo-random bits.
        seed = (seed ^ (seed >>> 33)) * 0xff51afd7ed558ccdL;
        seed = (seed ^ (seed >>> 33)) * 0xc4ceb9fe1a85ec53L;
        seed = seed ^ (seed >>> 33);

        float roll = (seed & 0xFFFFFF) / (float) 0xFFFFFF;
        float chance = Math.min(BASE_CLUSTER_CHANCE * biomeDensity(cx, cy), MAX_CLUSTER_CHANCE);
        return roll < chance;
    }
}
