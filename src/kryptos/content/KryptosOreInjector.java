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
 * {@code oreDefault = true}. This sprinkles a sparse, spawn-safe scatter of
 * voidsteel into a sector's terrain automatically on load, no HUD toggle
 * needed. A per-sector flag (saved in settings, not the map) makes sure it
 * only runs once each -- this also retroactively covers sectors you already
 * visited before this existed, since it just checks "have I injected this
 * sector id yet?" rather than "is this a brand new sector?".
 */
public class KryptosOreInjector {
    private static final String FLAG_PREFIX = "kryptos-ore-injected-";
    private static final float CHANCE = 0.03f;
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

        for (int x = 0; x < world.width(); x++) {
            for (int y = 0; y < world.height(); y++) {
                Tile tile = world.tile(x, y);
                if (tile == null || !tile.overlay().isAir() || tile.floor().isLiquid) {
                    continue;
                }
                if (tile.block() != Blocks.air && tile.block().isStatic()) {
                    continue;
                }
                if (spawnX >= 0) {
                    float dx = tile.worldx() - spawnX;
                    float dy = tile.worldy() - spawnY;
                    if (dx * dx + dy * dy < minSpawnDist * minSpawnDist) {
                        continue;
                    }
                }
                if (rand.chance(CHANCE)) {
                    tile.setOverlay(KryptosBlocks.oreVoidsteel);
                }
            }
        }
    }
}
