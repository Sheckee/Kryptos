package kryptos.ui;

import arc.Core;
import arc.graphics.Color;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.ui.dialogs.BaseDialog;

public class KryptosUpdateDialog extends BaseDialog {
    private static final String REPO = "Ains-Code/Kryptos";

    public KryptosUpdateDialog(String currentVer, String latestVer, String changelog, String releaseUrl) {
        super("Kryptos Update Available");
        name = "kryptosUpdateDialog";

        Table table = new Table();
        table.defaults().left();

        table.add("[#" + Color.crimson + "]" + currentVer + "[white] -> [#" + Color.valueOf("8ff5ff") + "]"
                + latestVer)
                .wrap()
                .width(480f)
                .padBottom(16f)
                .row();

        table.image().height(4f).color(Color.gray).fillX().pad(10f).row();

        Table changelogTable = new Table();
        changelogTable.top().left();
        changelogTable.add(changelog == null || changelog.isEmpty() ? "No release notes provided." : changelog)
                .growX().wrap().width(460f).left();

        ScrollPane pane = new ScrollPane(changelogTable);
        table.add(pane).size(480f, 360f).scrollX(false).row();

        cont.add(table);

        buttons.button("Later", this::hide).size(110f, 50f);
        buttons.button("Update", () -> {
            try {
                hide();
                Vars.ui.mods.show();
                Vars.ui.mods.githubImportMod(REPO, true);
                Vars.ui.mods.toFront();
                Timer.schedule(() -> Vars.ui.loadfrag.toFront(), 0.2f);
            } catch (Exception e) {
                Log.err(e);
                Vars.ui.showException(e);
            }
        }).size(110f, 50f);
    }
}
