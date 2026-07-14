package kryptos;

import arc.Core;
import arc.Events;
import arc.util.Log;
import kryptos.content.KryptosBlocks;
import kryptos.content.KryptosItems;
import kryptos.content.KryptosOreInjector;
import kryptos.ui.KryptosHealthBar;
import kryptos.ui.KryptosHud;
import kryptos.ui.KryptosPathIndicator;
import kryptos.ui.KryptosRangeDisplay;
import kryptos.ui.KryptosTheme;
import kryptos.ui.KryptosTimeControl;
import kryptos.ui.KryptosUpdateChecker;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.mod.Mod;
import mindustry.mod.Mods.LoadedMod;

public class KryptosMod extends Mod {

    private static final String SETTING_LAST_VERSION = "kryptos-last-seen-version";
    private static final float UPDATE_TOAST_DURATION = 5f;

    public KryptosMod() {
        Log.info("Loading Kryptos Mod...");

        // Initialize systems after the game finishes loading.
        Events.on(ClientLoadEvent.class, e -> {

            // UI
            KryptosTheme.apply();
            KryptosHud.build();
            KryptosPathIndicator.init();
            KryptosHealthBar.init();
            KryptosRangeDisplay.init();
            KryptosTimeControl.init();

            // World systems
            KryptosOreInjector.init();

            // Version checker
            KryptosUpdateChecker.check();
            announceIfUpdated();

            Log.info("Kryptos initialization complete.");
        });
    }

    @Override
    public void loadContent() {
        Log.info("Loading Kryptos content...");

        KryptosItems.load();
        KryptosBlocks.load();

        Log.info("Kryptos content loaded.");
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
}
