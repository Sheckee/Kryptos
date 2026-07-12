package kryptos;

import arc.Events;
import arc.util.Log;
import kryptos.content.KryptosBlocks;
import kryptos.content.KryptosItems;
import kryptos.ui.KryptosHud;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.mod.Mod;

public class KryptosMod extends Mod {

    public KryptosMod() {
        Log.info("Kryptos mod loaded.");
        Events.on(ClientLoadEvent.class, e -> KryptosHud.build());
    }

    @Override
    public void loadContent() {
        KryptosItems.load();
        KryptosBlocks.load();
    }
}
