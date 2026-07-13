package kryptos.ui;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import mindustry.Vars;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;

import static mindustry.Vars.state;

/**
 * Draws HP (and shield) bars above damaged units and buildings.
 */
public class KryptosHealthBar {
    private static final float BAR_SCALE = 1f;

    private static TextureRegion barRegion;

    public static void init() {
        Events.run(Trigger.draw, KryptosHealthBar::draw);
    }

    private static void draw() {
        if (!KryptosHud.healthBars || !state.isGame() || Vars.ui.hudfrag == null || !Vars.ui.hudfrag.shown) {
            return;
        }

        if (barRegion == null) {
            barRegion = Core.atlas.white();
        }

        float cx = Core.camera.position.x;
        float cy = Core.camera.position.y;
        float cw = Core.camera.width;
        float ch = Core.camera.height;

        Draw.z(Layer.shields + 5f);

        Groups.unit.intersect(cx - cw / 2f, cy - ch / 2f, cw, ch, KryptosHealthBar::drawUnit);

        Vars.indexer.eachBlock(null, cx, cy, Math.max(cw, ch), b -> true, KryptosHealthBar::drawBuilding);

        Draw.reset();
    }

    private static void drawUnit(Unit unit) {
        if (!unit.isValid()) {
            return;
        }
        boolean damaged = unit.health < unit.maxHealth;
        boolean shielded = unit.shield > 0;
        if (!damaged && !shielded) {
            return;
        }

        float x = unit.x;
        float y = unit.y + (unit.hitSize * 0.8f + 3f) * BAR_SCALE;
        float w = unit.hitSize * 2.5f;
        drawBar(x, y, w, unit.health, unit.maxHealth, unit.team.color);

        if (unit.shield > 0) {
            drawShieldPips(x, y, w, unit.shield, Math.max(unit.maxHealth, 1f), unit.team.color);
        }
    }

    private static void drawBuilding(Building build) {
        if (!build.isValid()) {
            return;
        }
        if (build.health >= build.maxHealth) {
            return;
        }

        float x = build.x;
        float y = build.y + build.block.size * Vars.tilesize * 0.5f + 3f;
        float w = build.block.size * Vars.tilesize * 0.9f;
        drawBar(x, y, w, build.health, build.maxHealth, build.team.color);
    }

    private static void drawBar(float x, float y, float w, float health, float maxHealth, Color teamColor) {
        float h = 2f * BAR_SCALE;
        if (Float.isNaN(maxHealth) || maxHealth <= 0f) {
            maxHealth = 1f;
        }
        float pct = Math.max(0f, Math.min(1f, health / maxHealth));

        Draw.color(Color.black, 0.6f);
        Draw.rect(barRegion, x, y, w + 2f, h + 2f);

        if (pct > 0) {
            float left = x - w / 2f;
            float filledW = w * pct;
            Draw.color(teamColor, 0.85f);
            Draw.rect(barRegion, left + filledW / 2f, y, filledW, h);
        }
        Draw.reset();
    }

    private static void drawShieldPips(float x, float startY, float w, float shield, float maxHealth,
            Color teamColor) {
        float h = 2f * BAR_SCALE;
        float shieldValue = Math.min(shield / maxHealth, 20f);
        float y = startY;

        while (shieldValue > 0) {
            y += h * 1.8f;
            Draw.color(Color.black, 0.6f);
            Draw.rect(barRegion, x, y, w + 2f, h + 2f);

            float pct = Math.min(shieldValue, 1f);
            float left = x - w / 2f;
            float filledW = w * pct;
            Draw.color(Pal.shield, 0.6f);
            Draw.rect(barRegion, left + filledW / 2f, y, filledW, h);

            shieldValue -= 1;
        }
        Draw.reset();
    }

}
