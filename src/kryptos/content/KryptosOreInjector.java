package kryptos.content;

import arc.Core;
import arc.Events;
import arc.math.Rand;
import mindustry.content.Blocks;
import mindustry.content.Planets;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Building;
import mindustry.world.Tile;

import static mindustry.Vars.state;
import static mindustry.Vars.tilesize;
import static mindustry.Vars.world;

/**
 * Vanilla campaign generators (Serpulo/Erekir) hardcode their own ore lists,
 * so a modded ore like voidsteel never appears there even with
 * {@code oreDefault = true}. This grows spawn-safe voidsteel veins (blobs of
 * connected tiles, like a real ore deposit) into a sector's terrain
 * automatically on load, no HUD toggle needed. A per-sector flag (saved in
 * settings, not the map) makes sure it only runs once each -- this also
 * retroactively covers sectors you already visited before this existed,
 * since it just checks "have I injected this sector id yet?" rather than
 * "is this a brand new sector?".
 */
public class KryptosOreInjector {
    private static final String FLAG_PREFIX = "kryptos-ore-injected-";

    /** Chance per eligible tile to become the seed of a brand new vein. */
    private static final float VEIN_SEED_CHANCE = 0.0012f;
    private static final int VEIN_MIN_SIZE = 10;
    private static final int VEIN_MAX_SIZE = 28;

    /** 4-directional neighbor offsets, used to grow a vein tile-by-tile. */
    private static final int[][] NEIGHBOR_OFFSETS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /**
     * Base spawn-safe exclusion radius in tiles, around the core. This is
     * only a starting point -- {@link #spawnExclusionDist()} shrinks it on
     * smaller sectors so the excluded zone can never swallow the whole map
     * (many early Serpulo/Erekir sectors are well under 100 tiles across,
     * and a flat 45-tile radius there leaves nothing left to seed ore on,
     * which is why voidsteel was never showing up on those maps).
     */
    private static final float MIN_SPAWN_DIST_TILES = 45f;

    public static void init() {
        Events.on(WorldLoadEvent.class, e -> {
            var sector = state.rules.sector;
            if (sector == null || sector.planet == null) {
                return;
            }
            if (sector.planet != Planets.serpulo && sector.planet != Planets.erekir) {
                return;
            }

            String flag = FLAG_PREFIX + sector.planet.name + "-" + sector.id;
            if (Core.settings.getBool(flag, false)) {
                return;
            }

            inject(sector.id);
            Core.settings.put(flag, true);
        });
    }

    /**
     * The full exclusion zone (diameter = 2x this value) must never eat the
     * whole map, or no tile ever passes the distance check and no ore ever
     * spawns. Caps the radius at 30% of the shorter map dimension so small
     * sectors still keep plenty of eligible tiles outside the safe zone.
     */
    private static float spawnExclusionDist() {
        int shorterSide = Math.min(world.width(), world.height());
        float cap = shorterSide * 0.3f * tilesize;
        return Math.min(MIN_SPAWN_DIST_TILES * tilesize, cap);
    }

    private static void inject(int sectorId) {
        if (KryptosBlocks.oreVoidsteel == null) {
            return;
        }

        Rand rand = new Rand(sectorId * 9781L + 13);

        float spawnX = -1, spawnY = -1;
        Building core = state.rules.defaultTeam.core();
        if (core != null) {
            spawnX = core.x;
            spawnY = core.y;
        }

        float minSpawnDist = spawnExclusionDist();
        boolean[] placed = new boolean[world.width() * world.height()];

        for (int x = 0; x < world.width(); x++) {
            for (int y = 0; y < world.height(); y++) {
                Tile tile = world.tile(x, y);
                if (!eligible(tile, spawnX, spawnY, minSpawnDist) || placed[x + y * world.width()]) {
                    continue;
                }
                if (rand.chance(VEIN_SEED_CHANCE)) {
                    growVein(tile, rand, spawnX, spawnY, minSpawnDist, placed);
                }
            }
        }
    }

    private static boolean eligible(Tile tile, float spawnX, float spawnY, float minSpawnDist) {
        if (tile == null || !tile.overlay().isAir() || tile.floor().isLiquid) {
            return false;
        }
        if (tile.block() != Blocks.air && tile.block().isStatic()) {
            return false;
        }
        if (spawnX >= 0) {
            float dx = tile.worldx() - spawnX;
            float dy = tile.worldy() - spawnY;
            if (dx * dx + dy * dy < minSpawnDist * minSpawnDist) {
                return false;
            }
        }
        return true;
    }

    /**
     * Grows an organic-looking blob of connected ore tiles outward from a
     * seed tile, picking a random tile from the current growing frontier
     * each step (rather than a strict flood fill) so veins come out as
     * roughly round clusters instead of straight lines or perfect diamonds.
     */
    private static void growVein(Tile seed, Rand rand, float spawnX, float spawnY, float minSpawnDist,
            boolean[] placed) {
        int targetSize = VEIN_MIN_SIZE + rand.random(VEIN_MAX_SIZE - VEIN_MIN_SIZE);
        int width = world.width();

        Tile[] frontier = new Tile[64];
        int frontierSize = 0;
        frontier[frontierSize++] = seed;

        int placedCount = 0;
        while (frontierSize > 0 && placedCount < targetSize) {
            int idx = rand.random(frontierSize - 1);
            Tile tile = frontier[idx];
            frontier[idx] = frontier[--frontierSize]; // swap-remove, order doesn't matter

            int key = tile.x + tile.y * width;
            if (placed[key] || !eligible(tile, spawnX, spawnY, minSpawnDist)) {
                continue;
            }

            tile.setOverlay(KryptosBlocks.oreVoidsteel);
            placed[key] = true;
            placedCount++;

            for (int[] offset : NEIGHBOR_OFFSETS) {
                Tile next = world.tile(tile.x + offset[0], tile.y + offset[1]);
                if (next != null && !placed[next.x + next.y * width]) {
                    if (frontierSize == frontier.length) {
                        frontier = java.util.Arrays.copyOf(frontier, frontier.length * 2);
                    }
                    frontier[frontierSize++] = next;
                }
            }
        }
    }
}
