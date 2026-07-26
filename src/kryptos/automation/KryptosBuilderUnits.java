package kryptos.automation;

import arc.math.Mathf;
import kryptos.content.KryptosUnits;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.type.UnitType;

/**
 * Spawns and hands out the shared Kryptos builder drones for
 * {@link KryptosDefenseBuilder} and {@link KryptosSmartDrill}.
 * Each module owns exactly one drone: spawned the moment its toggle
 * is switched on, reused for as long as it's alive, and quietly
 * replaced if it dies. The two modules use different {@link UnitType}s
 * (see {@link KryptosUnits}) so their drones are visually distinguishable
 * in-game.
 *
 * Automation only -- the drone is always locked to {@link KryptosDroneAI}
 * and is never player-controllable (see {@code playerControllable = false}
 * on {@link KryptosUnits#defenseBuilder} and {@link KryptosUnits#smartDrillBuilder}).
 */
public final class KryptosBuilderUnits {

    private static final float SPAWN_JITTER = 12f;

    private KryptosBuilderUnits() {}

    /**
     * Returns {@code current} if it's still alive, on the right team, and
     * still the right type, otherwise spawns a fresh drone of {@code type}
     * near the core and returns that instead. Returns null only if there's
     * no core to spawn next to.
     */
    public static Unit getOrSpawn(Unit current, Team team, UnitType type) {
        if (current != null && current.isValid() && current.team == team && current.type == type) {
            if (!(current.controller() instanceof KryptosDroneAI)) {
                current.controller(new KryptosDroneAI());
            }
            return current;
        }

        Building core = team.core();
        if (core == null) return null;

        Unit unit = type.create(team);
        unit.set(core.x + Mathf.range(SPAWN_JITTER), core.y + Mathf.range(SPAWN_JITTER));
        unit.rotation = 90f;
        unit.controller(new KryptosDroneAI());
        unit.add();
        return unit;
    }

    /**
     * Kills every existing drone of either type on load, no exceptions.
     */
    public static void killAll() {
        Groups.unit.each(u -> {
            if (u.type == KryptosUnits.defender || u.type == KryptosUnits.smartDrillBuilder) u.kill();
        });
    }
}
