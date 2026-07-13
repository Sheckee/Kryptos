package kryptos.content;

import arc.Events;
import arc.math.Rand;
import mindustry.content.Blocks;
import mindustry.content.Planets;
import mindustry.game.EventType.SectorLaunchEvent;
import mindustry.gen.Building;
import mindustry.world.Tile;

import static mindustry.Vars.state;
import static mindustry.Vars.tilesize;
import static mindustry.Vars.world;

/**
 * Vanilla campaign generators (Serpulo/Erekir) hardcode their own ore lists,
 * so a modded ore like voidsteel never appears there even with
 * {@code oreDefault = true}. This sprinkles a sparse, spawn-safe scatter of
 * voidsteel into a sector's terrain right after it's freshly generated
 * (never on a sector that's already been visited/saved before).
 */
public class KryptosOreInjector {
    private static final float CHANCE = 0.03f;
    private static final float MIN_SPAWN_DIST = 45f * tilesize;

    public static void init() {
        Events.on(SectorLaunchEvent.class, e -> {
            if (e.sector == null || e.sector.planet == null) {
                return;
            }
            if (e.sector.planet != Planets.serpulo && e.sector.planet != Planets.erekir) {
                return;
            }
            inject(e.sector.id);
        });
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
                    if (dx * dx + dy * dy < MIN_SPAWN_DIST * MIN_SPAWN_DIST) {
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
