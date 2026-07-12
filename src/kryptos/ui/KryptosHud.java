package kryptos.ui;

import arc.Core;
import arc.func.Boolp;
import arc.math.Interp;
import arc.scene.actions.Actions;
import arc.scene.ui.ImageButton;
import arc.scene.ui.Tooltip;
import arc.scene.ui.layout.Table;
import mindustry.ui.Styles;

import static mindustry.Vars.ui;

public class KryptosHud {
    private static final float ICON_SIZE = 52f;
    private static final float BTN_SIZE = 40f;

    public static boolean autoplay = false;
    public static boolean healthBars = false;
    public static boolean pathfinding = false;
    public static boolean rangeDisplay = false;
    public static boolean teamResources = false;

    private static Table panel;
    private static boolean open = false;

    public static void build() {
        Table root = new Table();
        root.setFillParent(true);
        root.bottom().left();

        panel = new Table(Styles.black6);
        panel.visible = false;
        panel.pack();

        addToggle(panel, "kryptos-icon-autoplay", "Autoplay", () -> autoplay, b -> autoplay = b);
        addToggle(panel, "kryptos-icon-health", "Health Bars", () -> healthBars, b -> healthBars = b);
        addToggle(panel, "kryptos-icon-path", "Pathfinding", () -> pathfinding, b -> pathfinding = b);
        addToggle(panel, "kryptos-icon-range", "Range Display", () -> rangeDisplay, b -> rangeDisplay = b);
        addToggle(panel, "kryptos-icon-team", "Team Resources", () -> teamResources, b -> teamResources = b);

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
        btn.addListener(new Tooltip(t -> t.background(Styles.black6).add(tooltipText).pad(4f)));
        into.add(btn).size(BTN_SIZE).pad(4f);
    }

    public static void toggle() {
        if (open) close(); else open();
    }

    public static void open() {
        if (open) return;
        open = true;
        panel.visible = true;
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
