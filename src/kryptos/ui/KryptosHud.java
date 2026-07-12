package kryptos.ui;

import arc.Core;
import arc.func.Boolp;
import arc.scene.actions.Actions;
import arc.scene.ui.ImageButton;
import arc.scene.ui.Tooltip;
import arc.scene.ui.layout.Table;
import arc.util.Interp;
import mindustry.ui.Styles;

import static mindustry.Vars.ui;

/**
 * Kryptos HUD toggle menu.
 *
 * Ports the "icon expand/collapse" widget design (originally prototyped as an
 * HTML/CSS mockup) into Mindustry's real UI system — the game has no HTML
 * renderer, so this rebuilds the same interaction with Arc's scene2d Table/
 * ImageButton + Actions for the expand animation.
 *
 * Behavior:
 *  - A round icon button sits in the corner of the HUD.
 *  - Clicking it slides out a pill-shaped panel of feature toggle buttons
 *    (mirrors the original: Autoplay, Health Bars, Pathfinding, Range
 *    Display, Team Resources), plus a small close button.
 *  - Feature buttons are checkable (stay highlighted while active).
 *
 * NOTE: the five toggles below are wired to local boolean flags as
 * placeholders. Swap the `state.X = !state.X` lines for whatever this mod's
 * real settings/renderer flags end up being once those systems exist
 * (e.g. Core.settings.getBool(...), or fields on a KryptosState class).
 */
public class KryptosHud {
    private static final float ICON_SIZE = 52f;
    private static final float BTN_SIZE = 40f;

    // placeholder toggle state - replace with real settings hooks later
    public static boolean autoplay = false;
    public static boolean healthBars = false;
    public static boolean pathfinding = false;
    public static boolean rangeDisplay = false;
    public static boolean teamResources = false;

    private static Table panel;
    private static boolean open = false;

    /** Call once, e.g. from KryptosMod on ClientLoadEvent. */
    public static void build() {
        Table root = new Table();
        root.setFillParent(true);
        root.bottom().left().pad(16f);

        // the pill panel that slides out - starts collapsed/invisible
        panel = new Table(Styles.black6);
        panel.visible(false);
        panel.pack();

        addToggle(panel, "kryptos-icon-autoplay", "Autoplay", () -> autoplay, b -> autoplay = b);
        addToggle(panel, "kryptos-icon-health", "Health Bars", () -> healthBars, b -> healthBars = b);
        addToggle(panel, "kryptos-icon-path", "Pathfinding", () -> pathfinding, b -> pathfinding = b);
        addToggle(panel, "kryptos-icon-range", "Range Display", () -> rangeDisplay, b -> rangeDisplay = b);
        addToggle(panel, "kryptos-icon-team", "Team Resources", () -> teamResources, b -> teamResources = b);

        // main round toggle icon - uses the K-monogram badge as its face
        ImageButton icon = new ImageButton(Core.atlas.drawable("kryptos-icon"), Styles.emptyi);
        icon.resizeImage(ICON_SIZE);
        icon.clicked(KryptosHud::toggle);

        root.table(t -> {
            t.add(panel).padRight(8f);
            t.add(icon).size(ICON_SIZE + 12f);
        });

        ui.hudGroup.addChild(root);
    }

    private static void addToggle(Table into, String iconName, String tooltipText, Boolp getter, arc.func.Boolc setter) {
        ImageButton btn = new ImageButton(Core.atlas.drawable(iconName), Styles.emptyi);
        btn.resizeImage(BTN_SIZE * 0.5f);
        btn.update(() -> btn.setChecked(getter.get()));
        btn.clicked(() -> setter.get(!getter.get()));
        btn.addListener(new Tooltip<>(t -> t.background(Styles.black6).add(tooltipText).pad(4f)));
        into.add(btn).size(BTN_SIZE).pad(4f);
    }

    public static void toggle() {
        if (open) close(); else open();
    }

    public static void open() {
        if (open) return;
        open = true;
        panel.visible(true);
        panel.clearActions();
        panel.actions(Actions.alpha(0f), Actions.parallel(
            Actions.alpha(1f, 0.32f, Interp.pow3Out)
        ));
    }

    public static void close() {
        if (!open) return;
        open = false;
        panel.clearActions();
        panel.actions(Actions.alpha(0f, 0.24f, Interp.pow3In), Actions.visible(false));
    }
}
