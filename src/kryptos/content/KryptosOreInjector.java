package kryptos.content;

import arc.Core;
import arc.Events;
import mindustry.content.Planets;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.OreBlock;

import static mindustry.Vars.state;
import static mindustry.Vars.world;

/**
 * Converts every existing ore tile in a sector (copper, lead, titanium,
 * scrap, thorium -- whatever the vanilla generator placed) into the
 * customized Kryptos voidsteel ore. A per-sector flag (saved in settings,
 * not the map) makes sure it only runs once each -- this also
 * retroactively covers sectors you already visited before this existed,
 * since it just checks "have I converted this sector id yet?" rather than
 * "is this a brand new sector?".
 */
public class KryptosOreInjector {
    private static final String FLAG_PREFIX = "kryptos-ore-injected-v2-";

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

            convertAllOre();
            Core.settings.put(flag, true);
        });
    }

    private static void convertAllOre() {
        if (KryptosBlocks.oreVoidsteel == null) {
            return;
        }

        for (int x = 0; x < world.width(); x++) {
            for (int y = 0; y < world.height(); y++) {
                Tile tile = world.tile(x, y);
                if (tile == null) {
                    continue;
                }

                if (tile.overlay() instanceof OreBlock && tile.overlay() != KryptosBlocks.oreVoidsteel) {
                    tile.setOverlay(KryptosBlocks.oreVoidsteel);
                }
            }
        }
    }
}
