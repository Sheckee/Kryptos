package kryptos.ui;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Rect;
import arc.struct.LongMap;
import arc.util.Align;
import arc.util.Time;
import mindustry.Vars;
import mindustry.ai.Pathfinder;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Team;
import mindustry.graphics.Layer;
import mindustry.ui.Fonts;
import mindustry.world.Tile;

import java.util.Arrays;

import static mindustry.Vars.state;

/**
 * Combines the enemy spawn indicator with an accurate route preview: each
 * spawn point draws the real path its units will flowfield-walk toward the
 * core, using the same {@link Pathfinder} data the AI itself follows, so the
 * line always matches where enemies actually go.
 */
public class KryptosPathIndicator {
    private static final float MARKER_RADIUS = 7f;
    private static final float EDGE_MARGIN = 28f;
    private static final float ARROW_SIZE = 9f;
    private static final int MAX_PATH_STEPS = 1000;
    private static final float PATH_REFRESH_INTERVAL = 60f;
    private static final float PATH_OPACITY = 0.55f;

    private static final class PathCache {
        float[] data = new float[256];
        int size;
        float lastUpdateTime = -9999f;
    }

    private static final LongMap<PathCache> pathCache = new LongMap<>();
    private static final Rect viewBounds = new Rect();

    public static void init() {
        Events.run(Trigger.draw, KryptosPathIndicator::draw);
        Events.on(WorldLoadEvent.class, e -> pathCache.clear());
    }

    private static void draw() {
        if (!KryptosHud.pathfinding || !state.isGame()) {
            return;
        }
        if (!state.rules.waves || Vars.spawner.getSpawns() == null) {
            return;
        }

        float cx = Core.camera.position.x;
        float cy = Core.camera.position.y;
        float cw = Core.camera.width;
        float ch = Core.camera.height;
        Core.camera.bounds(viewBounds);

        Color color = state.rules.waveTeam.color;
        float secondsLeft = Math.max(0f, state.wavetime / 60f);
        float pulse = Mathf.absin(Time.time, 6f, 1f);
        float currentTime = Time.time;

        Draw.z(Layer.overlayUI);

        for (Tile tile : Vars.spawner.getSpawns()) {
            if (tile == null) {
                continue;
            }

            drawPathFromSpawn(tile, state.rules.waveTeam, color, currentTime);

            float x = tile.worldx();
            float y = tile.worldy();
            if (viewBounds.contains(x, y)) {
                drawOnScreenMarker(x, y, color, pulse, secondsLeft);
            } else {
                drawOffScreenArrow(cx, cy, cw, ch, x, y, color, pulse, secondsLeft);
            }
        }

        Draw.reset();
    }

    private static void drawPathFromSpawn(Tile spawnTile, Team team, Color color, float currentTime) {
        if (Vars.pathfinder == null) {
            return;
        }

        long key = ((long) spawnTile.pos() << 16) | (long) team.id;
        PathCache cache = pathCache.get(key);
        if (cache == null) {
            cache = new PathCache();
            pathCache.put(key, cache);
        }

        if ((currentTime - cache.lastUpdateTime) > PATH_REFRESH_INTERVAL) {
            recalculatePath(spawnTile, team, cache);
            cache.lastUpdateTime = currentTime;
        }

        if (cache.size < 4) {
            return;
        }

        Draw.color(color, PATH_OPACITY);
        Lines.stroke(1.5f);
        for (int i = 0; i < cache.size - 2; i += 2) {
            Lines.line(cache.data[i], cache.data[i + 1], cache.data[i + 2], cache.data[i + 3]);
        }
        Draw.reset();
    }

    /** Walks the real flowfield toward the core, exactly as spawned units will. */
    private static void recalculatePath(Tile startTile, Team team, PathCache cache) {
        int costType = Pathfinder.costGround;
        Pathfinder.Flowfield field = Vars.pathfinder.getField(team, costType, Pathfinder.fieldCore);
        if (field == null) {
            cache.size = 0;
            return;
        }

        Tile currentTile = startTile;
        int idx = 0;
        cache.data[idx++] = startTile.worldx();
        cache.data[idx++] = startTile.worldy();

        for (int i = 0; i < MAX_PATH_STEPS; i++) {
            Tile nextTile = Vars.pathfinder.getTargetTile(currentTile, field);
            if (nextTile == null || nextTile == currentTile) {
                break;
            }
            if (idx + 2 > cache.data.length) {
                cache.data = Arrays.copyOf(cache.data, cache.data.length * 2);
            }
            cache.data[idx++] = nextTile.worldx();
            cache.data[idx++] = nextTile.worldy();
            currentTile = nextTile;
        }

        cache.size = idx;
    }

    private static void drawOnScreenMarker(float x, float y, Color color, float pulse, float secondsLeft) {
        Draw.color(color, 0.9f);
        Lines.stroke(2f);
        Lines.circle(x, y, MARKER_RADIUS + pulse * 3f);
        Fill.poly(x, y, 3, MARKER_RADIUS * 0.6f, 90f);
        Draw.reset();

        if (secondsLeft > 0) {
            Fonts.outline.draw(String.format("%.0fs", secondsLeft), x, y + MARKER_RADIUS + 10f,
                    Color.white, 0.3f, false, Align.center);
        }
    }

    private static void drawOffScreenArrow(float cx, float cy, float cw, float ch, float x, float y,
            Color color, float pulse, float secondsLeft) {
        float hw = cw / 2f - EDGE_MARGIN;
        float hh = ch / 2f - EDGE_MARGIN;
        float dx = x - cx;
        float dy = y - cy;
        if (Mathf.zero(dx) && Mathf.zero(dy)) {
            return;
        }

        float scaleX = Mathf.zero(dx) ? Float.MAX_VALUE : Math.abs(hw / dx);
        float scaleY = Mathf.zero(dy) ? Float.MAX_VALUE : Math.abs(hh / dy);
        float scale = Math.min(scaleX, scaleY);

        float ex = cx + dx * scale;
        float ey = cy + dy * scale;
        float angle = Angles.angle(cx, cy, x, y);

        Draw.color(color, 0.7f + pulse * 0.3f);
        float tipX = ex + Mathf.cosDeg(angle) * ARROW_SIZE;
        float tipY = ey + Mathf.sinDeg(angle) * ARROW_SIZE;
        float baseX = ex - Mathf.cosDeg(angle) * ARROW_SIZE * 0.6f;
        float baseY = ey - Mathf.sinDeg(angle) * ARROW_SIZE * 0.6f;
        float leftX = baseX + Mathf.cosDeg(angle + 90f) * ARROW_SIZE * 0.6f;
        float leftY = baseY + Mathf.sinDeg(angle + 90f) * ARROW_SIZE * 0.6f;
        float rightX = baseX + Mathf.cosDeg(angle - 90f) * ARROW_SIZE * 0.6f;
        float rightY = baseY + Mathf.sinDeg(angle - 90f) * ARROW_SIZE * 0.6f;
        Fill.tri(tipX, tipY, leftX, leftY, rightX, rightY);
        Draw.reset();

        if (secondsLeft > 0) {
            Fonts.outline.draw(String.format("%.0fs", secondsLeft), ex, ey - ARROW_SIZE - 6f,
                    Color.white, 0.3f, false, Align.center);
        }
    }
}
