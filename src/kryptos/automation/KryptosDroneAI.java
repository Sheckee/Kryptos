package kryptos.automation;

import mindustry.entities.units.AIController;
import mindustry.entities.units.BuildPlan;
import mindustry.world.Build;
import mindustry.world.blocks.ConstructBlock.ConstructBuild;

/**
 * Controller for the Kryptos helper drone (see {@link KryptosBuilderUnits}).
 *
 * Strips all scavenging behavior: the drone does ONLY what's
 * explicitly queued on it via {@code unit.addBuild(...)} by
 * KryptosDefenseBuilder or KryptosSmartDrill. It never scavenges
 * the shared plan queue and never follows/mimics another builder.
 *
 * Idle behavior: rather than freezing wherever its last task
 * finished, it flies back and hovers near the core.
 */
public class KryptosDroneAI extends AIController {

    private static final float BUILD_RADIUS = 1500f;
    private static final float IDLE_HOVER_RANGE = 70f;

    @Override
    public void updateMovement() {
        unit.updateBuilding = true;

        BuildPlan req = unit.buildPlan();
        if (req == null) {
            // Nothing to build right now -- head back to the core and hover
            // there instead of just sitting dead in the air wherever the
            // last plan finished.
            var core = unit.closestCore();
            if (core != null && !unit.within(core, IDLE_HOVER_RANGE)) {
                moveTo(core, IDLE_HOVER_RANGE);
            }
            return;
        }

        boolean valid =
            (req.tile() != null && req.tile().build instanceof ConstructBuild cons && cons.current == req.block) ||
            (req.breaking
                ? Build.validBreak(unit.team(), req.x, req.y)
                : Build.validPlace(req.block, unit.team(), req.x, req.y, req.rotation));

        if (!valid) {
            unit.plans.removeFirst();
            return;
        }

        float range = Math.min(unit.type.buildRange - unit.type.hitSize * 2f, BUILD_RADIUS);
        moveTo(req.tile(), range, 20f);
    }

    @Override
    public boolean shouldShoot() {
        return false;
    }
}
