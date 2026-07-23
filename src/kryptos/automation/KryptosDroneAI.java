package kryptos.automation;

import mindustry.entities.units.AIController;
import mindustry.entities.units.BuildPlan;
import mindustry.world.Build;
import mindustry.world.blocks.ConstructBlock.ConstructBuild;

/**
 * Controller for the Kryptos helper drone (see {@link KryptosBuilderUnits}).
 *
 * Stock Mindustry builder units run {@code BuilderAI}, which -- whenever it
 * has nothing queued -- helps itself to the team's shared block-plan queue
 * and even copies whatever build plan the nearest other builder unit is
 * working on. That means the drone could end up constructing things nobody
 * told SmartDrill/AutoConveyor to build.
 *
 * This controller strips all of that out: the drone does ONLY what's
 * explicitly queued on it via {@code unit.addBuild(...)} by KryptosSmartDrill
 * or KryptosAutoConveyor. It never scavenges the shared plan queue and never
 * follows/mimics another builder.
 *
 * Idle behavior (nothing queued right now): rather than freezing wherever
 * its last task happened to end -- which looks broken, floating motionless
 * over some random ore tile -- it flies back and hovers near the core.
 * That's the "logical" resting spot: staged, out of the way, and ready to
 * be dispatched the moment the next scan cycle queues something.
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
