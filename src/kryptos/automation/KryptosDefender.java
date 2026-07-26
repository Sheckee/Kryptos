package kryptos.automation;

import arc.Events;
import arc.util.Log;
import arc.util.Time;
import kryptos.content.KryptosUnits;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Team;
import mindustry.gen.Unit;
import mindustry.type.UnitType;

/**
 * Manages Defender units based on the number of Defense Zones.
 *
 * Each Defense Zone (represented by a protected core) gets exactly one Defender.
 * - 1 zone = 1 Defender
 * - 2 zones = 2 Defenders
 * - 3 zones = 3 Defenders
 * etc.
 *
 * Defenders are spawned near each core and assigned to defend it.
 */
public final class KryptosDefender {

    private static final float SPAWN_CHECK_INTERVAL = 60f * 10f;
    private static final float RESPAWN_DELAY = 60f * 5f;
    private static final float SPAWN_JITTER = 15f;

    private static float lastCheckTime = -SPAWN_CHECK_INTERVAL;

    private KryptosDefender() {}

    public static void init() {
        Events.on(WorldLoadEvent.class, e -> reset());
        Events.run(Trigger.update, KryptosDefender::update);
    }

    public static void requestSpawn() {
        lastCheckTime = -SPAWN_CHECK_INTERVAL;
        checkAndSpawn();
    }

    private static void reset() {
        lastCheckTime = -SPAWN_CHECK_INTERVAL;
        killAllDefenders();
    }

    private static void update() {
        if (!Vars.state.isGame()) return;
        if (!KryptosAutomationPanel.autoDefender) return;

        lastCheckTime += Time.delta;
        if (lastCheckTime < SPAWN_CHECK_INTERVAL) return;
        lastCheckTime = 0f;

        checkAndSpawn();
    }

    private static void checkAndSpawn() {
        Team team = Vars.player.team();
        int needed = countDefenseZones(team);
        int current = countDefenders(team);

        if (current >= needed) return;

        int toSpawn = needed - current;
        for (int i = 0; i < toSpawn; i++) {
            spawnDefender(team);
        }

        Log.info("[Kryptos] Defender: needed=@ current=@ spawning=@", needed, current, toSpawn);
    }

    private static int countDefenseZones(Team team) {
        // Defense zones are determined by team cores.
        // In most cases there is 1 core = 1 defense zone.
        // In campaign or sector maps, each core is a separate zone.
        if (team.core() != null) return 1;
        return 0;
    }

    private static int countDefenders(Team team) {
        int count = 0;
        for (Unit u : Groups.unit) {
            if (u.team == team && u.type == KryptosUnits.defender) {
                count++;
            }
        }
        return count;
    }

    private static void spawnDefender(Team team) {
        Building core = team.core();
        if (core == null) {
            Log.warn("[Kryptos] Defender: cannot spawn, no core found for team.", new Object[0]);
            return;
        }

        Unit defender = KryptosUnits.defender.create(team);
        defender.set(core.x + Mathf.random(-SPAWN_JITTER, SPAWN_JITTER), core.y + Mathf.random(-SPAWN_JITTER, SPAWN_JITTER));
        defender.rotation = 90f;
        defender.controller(new KryptosDefenderAI());
        defender.add();

        Log.info("[Kryptos] Defender spawned near core at @,@.", core.x, core.y);
    }

    private static void killAllDefenders() {
        Groups.unit.each(u -> {
            if (u.type == KryptosUnits.defender) {
                u.kill();
            }
        });
    }
}