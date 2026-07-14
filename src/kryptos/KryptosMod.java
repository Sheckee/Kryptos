package kryptos;

import arc.Core;
import arc.Events;
import arc.util.Log;
import kryptos.content.KryptosBlocks;
import kryptos.content.KryptosItems;
import kryptos.ui.KryptosHealthBar;
import kryptos.ui.KryptosHud;
import kryptos.ui.KryptosPathIndicator;
import kryptos.ui.KryptosRangeDisplay;
import kryptos.ui.KryptosTheme;
import kryptos.ui.KryptosTimeControl;
import kryptos.ui.KryptosUpdateChecker;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.mod.Mod;
import mindustry.mod.Mods.LoadedMod;
import mindustry.world.Tile;

public class KryptosMod extends Mod {

    private static final String SETTING_LAST_VERSION = "kryptos-last-seen-version";
    private static final float UPDATE_TOAST_DURATION = 5f;

    public KryptosMod() {
        Log.info("Kryptos mod loaded.");

        Events.on(ClientLoadEvent.class, e -> {
            KryptosTheme.apply();
            KryptosHud.build();
            KryptosPathIndicator.init();
            KryptosHealthBar.init();
            KryptosRangeDisplay.init();
            KryptosTimeControl.init();
            KryptosUpdateChecker.check();
            announceIfUpdated();
        });

        // Repair old saves containing ore-kryptos
        Events.on(WorldLoadEvent.class, e -> {
            repairOldKryptosOre();
        });
    }

    @Override
    public void loadContent() {
        KryptosItems.load();
        KryptosBlocks.load();
    }

    private void announceIfUpdated() {
        LoadedMod self = Vars.mods.getMod(KryptosMod.class);

        if (self == null || self.meta.version == null) {
            return;
        }

        String currentVersion = self.meta.version;
        String lastSeenVersion = Core.settings.getString(SETTING_LAST_VERSION, null);

        if (lastSeenVersion != null && !lastSeenVersion.equals(currentVersion)) {
            Vars.ui.showInfoToast(
                    "[accent]Kryptos[] updated to v" + currentVersion + "!",
                    UPDATE_TOAST_DURATION
            );
        }

        Core.settings.put(SETTING_LAST_VERSION, currentVersion);
    }

    /**
     * Converts old Kryptos ore to Copper so old saves remain playable.
     */
    private void repairOldKryptosOre() {
        if (Vars.world == null) return;

        int repaired = 0;

        for (Tile tile : Vars.world.tiles) {
            if (tile == null) continue;

            if (tile.overlay() == KryptosBlocks.oreCustom) {
                tile.setOverlay(Blocks.oreCopper);
                repaired++;
            }
        }

        if (repaired > 0) {
            Log.info("Repaired @ old Kryptos ore tiles.", repaired);
        }
    }
}
