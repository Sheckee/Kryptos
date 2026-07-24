package kryptos.automation;

import arc.math.Mathf;
import kryptos.content.KryptosUnits;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Unit;

/**
 * Spawns and hands out the shared Kryptos builder drone for
 * {@link KryptosAutoConveyor} and {@link KryptosSmartDrill}. Both modules reuse
 * the same live helper when possible, so enabling both toggles does not leave
 * a second helper hovering idle with no work while the first one drains the
 * actual build queue.
 *
 * Automation only -- the drone is always locked to {@link KryptosDroneAI}
 * and is never player-controllable (see {@code playerControllable = false}
 * on {@link KryptosUnits#builder}).
 */
public final class KryptosBuilderUnits {

    private static final float SPAWN_JITTER = 12f;

    private KryptosBuilderUnits() {}

    /**
     * Returns {@code current} if it's still alive and on the right team, then
     * falls back to any other live Kryptos helper on that team before spawning
     * a fresh drone near the core. Returns null only if there's no core to
     * spawn next to.
     */
    public static Unit getOrSpawn(Unit current, Team team) {
        if (current != null && current.isValid() && current.team == team) {
            // Guards against drones that survived from before this fix (e.g.
            // loaded from an existing save) and are still stuck on the stock
            // BuilderAI or some other controller -- force our controller
            // back on instead of leaving them to their old behavior.
            if (!(current.controller() instanceof KryptosDroneAI)) {
                current.controller(new KryptosDroneAI());
            }
            return current;
        }

        Unit existing = findExisting(team);
        if (existing != null) {
            if (!(existing.controller() instanceof KryptosDroneAI)) {
                existing.controller(new KryptosDroneAI());
            }
            return existing;
        }

        Building core = team.core();
        if (core == null) return null;

        Unit unit = KryptosUnits.builder.create(team);
        unit.set(core.x + Mathf.range(SPAWN_JITTER), core.y + Mathf.range(SPAWN_JITTER));
        unit.rotation = 90f;
        // Force our own controller instead of the stock BuilderAI that
        // create() would otherwise assign -- see KryptosDroneAI for why.
        unit.controller(new KryptosDroneAI());
        unit.add();
        return unit;
    }

    private static Unit findExisting(Team team) {
        final Unit[] found = {null};
        Groups.unit.each(u -> {
            if (found[0] == null && u.type == KryptosUnits.builder && u.team == team && u.isValid()) {
                found[0] = u;
            }
        });
        return found[0];
    }

    /**
     * Kills every existing drone of our type on load, no exceptions. Without
     * this, a drone left over from a previous session/save (spawned before a
     * fix existed, or orphaned when its module's static reference was reset)
     * just sits in the world running whatever old/default behavior it had --
     * completely invisible to and unmanaged by the current code, since
     * nothing ever calls getOrSpawn() on it while its module's toggle is off.
     * That's the "there's already a drone even though I haven't turned
     * anything on" symptom. Called from both modules' reset() on
     * WorldLoadEvent, so the world always starts with zero drones and the
     * next getOrSpawn() call is guaranteed to create a clean one.
     */
    public static void killAll() {
        Groups.unit.each(u -> {
            if (u.type == KryptosUnits.builder) u.kill();
        });
    }
}
