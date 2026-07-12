package kryptos;

import arc.Events;
import arc.util.Log;
import kryptos.ui.KryptosHud;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.mod.Mod;

/**
 * Kryptos — sci-fi/tech themed content mod.
 * See STYLE.md at the project root for the visual/design bible.
 *
 * This class is the mod's entry point. Content (items, blocks, units)
 * can be registered here in loadContent(), either by loading JSON/HJSON
 * definitions from the content/ folder or by defining content directly
 * in Java for anything needing custom logic.
 */
public class KryptosMod extends Mod {

    public KryptosMod() {
        Log.info("Kryptos mod loaded.");

        // HUD must be built after the client UI is fully initialized
        Events.on(ClientLoadEvent.class, e -> KryptosHud.build());
    }

    @Override
    public void loadContent() {
        // Content registration goes here, e.g.:
        // KryptosItems.load();
        // KryptosBlocks.load();
        // KryptosUnits.load();
    }
}
