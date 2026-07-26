package kryptos.automation;

import arc.func.Boolf;
import arc.math.Mathf;
import arc.math.geom.Position;
import arc.struct.IntSet;
import arc.struct.Seq;
import arc.util.Time;
import arc.math.geom.Vec2;
import kryptos.content.KryptosUnits;
import mindustry.Vars;
import mindustry.ai.UnitCommand;
import mindustry.content.Blocks;
import mindustry.content.Liquids;
import mindustry.entities.units.AIController;
import mindustry.entities.units.BuildPlan;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.Wall;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.blocks.distribution.Conveyor;
import mindustry.world.blocks.production.Drill;
import mindustry.world.blocks.environment.OreBlock;

public class KryptosDefenderAI extends AIController {

    private static final float REPAIR_RANGE = 80f;
    private static final float WALL_BUILD_RANGE = 140f;
    private static final float COMBAT_RANGE = 220f;
    private static final float RETREAT_HP = 0.3f;
    private static final float HEAL_RANGE = 100f;
    private static final float BUILD_CHECK_INTERVAL = 10f;
    private static final float COMBAT_CHECK_INTERVAL = 5f;

    private float buildTimer = 0f;
    private float combatTimer = 0f;
    private boolean retreating = false;
    private boolean wasInCombat = false;
    private UnitType defenderType;
    private Building assignedCore;
    private float zoneRadius;
    private IntSet plannedPositions = new IntSet();

    @Override
    public void init(Unit unit) {
        super.init(unit);
        this.unit = unit;
        defenderType = unit.type;
        unit.command = UnitCommand.rebuildCommand;
    }

    @Override
    public void updateUnit() {
        if (unit == null) return;
        if (unit.dead) return;

        Team team = unit.team;
        Building core = team.core();
        if (core == null) return;

        assignedCore = core;
        zoneRadius = core.block.size * 8f + 50f;

        combatTimer -= Time.delta;
        buildTimer -= Time.delta;

        Unit closestEnemy = findClosestEnemy(unit, core.x, core.y);
        boolean lowHp = unit.health < unit.type.health * RETREAT_HP;

        // Combat: if enemy is near, prioritize combat
        boolean hasThreat = closestEnemy != null && unit.within(closestEnemy, COMBAT_RANGE);

        if (hasThreat) {
            wasInCombat = true;
            if (lowHp) {
                retreating = true;
                retreat(unit, core.x, core.y);
            } else {
                retreating = false;
                unit.attack(closestEnemy);
            }
        } else if (lowHp) {
            retreating = true;
            retreat(unit, core.x, core.y);
        } else {
            retreating = false;
            wasInCombat = false;
        }

        // Don't build while retreating or in combat
        if (retreating || (hasThreat && lowHp == false)) {
            // We can still attack, just don't build
            if (hasThreat && !lowHp) {
                // Already attacking above
            }
            return;
        }

        // Building phase
        if (buildTimer <= 0f) {
            buildTimer = BUILD_CHECK_INTERVAL;
            doBuildPhase(unit, core, team);
        }

        // Repair phase
        doRepairPhase(unit, core);
    }

    @Override
    public void updateTargeting() {
        // Use our own combat logic, don't let the base AI pick targets
    }

    private void retreat(Unit unit, float cx, float cy) {
        float dx = cx - unit.x;
        float dy = cy - unit.y;
        float dist = Mathf.dst(dx, dy);
        if (dist > HEAL_RANGE && dist > 0.1f) {
            unit.moveAt(new Vec2(dx / dist, dy / dist).scl(unit.type.speed * 60f));
        }
    }

    private Unit findClosestEnemy(Unit unit, float cx, float cy) {
        Unit closest = null;
        float closestDist = COMBAT_RANGE;

        for (Unit other : Groups.unit) {
            if (other.team == unit.team) continue;
            if (other.dead) continue;
            if (!other.isEnemy()) continue;
            float d = Mathf.dst(other.x, other.y, cx, cy);
            if (d < closestDist) {
                closestDist = d;
                closest = other;
            }
        }
        return closest;
    }

    private void doBuildPhase(Unit unit, Building core, Team team) {
        float cx = core.x;
        float cy = core.y;

        // 1. Fix damaged walls/turrets first (highest priority)
        Building damaged = findDamagedStructure(unit, cx, cy, zoneRadius);
        if (damaged != null) {
            BuildPlan plan = new BuildPlan(damaged.tile.x, damaged.tile.y, damaged.rotation, damaged.block);
            if (plan.placeable(team)) {
                unit.addBuild(plan);
                return;
            }
        }

        // 2. Remove invalid build plans (blockers like conveyors etc)
        cleanBuildPlans(unit);

        // 3. Extend wall line outside zone
        extendWalls(unit, team, cx, cy);

        // 4. Add turrets behind walls when enough walls exist
        addTurrets(unit, team, cx, cy);
    }

    private void cleanBuildPlans(Unit unit) {
        if (unit.buildPlan() == null) return;
        BuildPlan plan = unit.buildPlan();
        Tile t = Vars.world.tile(plan.x, plan.y);
        if (t == null) {
            unit.plans.removeFirst();
            return;
        }
        Block block = t.block();
        // Don't build on conveyors, factories, drills, or allies
        if (block instanceof Conveyor || block instanceof Drill || block instanceof Turret) {
            if (t.build != null && t.build.team == unit.team) {
                unit.plans.removeFirst();
            }
        }
    }

    private void extendWalls(Unit unit, Team team, float cx, float cy) {
        // Count existing walls to determine how many more to build
        int existingWalls = 0;
        int maxWalls = 12;

        for (Building b : Groups.build) {
            if (b.team != team) continue;
            if (b.block instanceof Wall) existingWalls++;
        }

        // Build a ring of walls outside the zone when walls are missing
        int needed = Math.max(0, maxWalls - existingWalls);
        int built = 0;

        for (int angle = 0; angle < 360 && built < needed; angle += 20) {
            for (int r = (int)(zoneRadius / 8f); r < zoneRadius / 8f + 5 && built < needed; r += 3) {
                int wx = (int)(cx / 8f) + Mathf.round(Mathf.cosDeg(angle) * r);
                int wy = (int)(cy / 8f) + Mathf.round(Mathf.sinDeg(angle) * r);
                int key = Point2.pack(wx, wy);

                if (plannedPositions.contains(key)) continue;
                Tile tile = Vars.world.tile(wx, wy);
                if (tile == null) continue;
                Block bl = tile.block();
                if (bl != Blocks.air && !(bl instanceof OreBlock)) continue;
                if (tile.floor().isLiquid) continue;
                if (tile.build != null) continue;

                BuildPlan plan = new BuildPlan(wx, wy, 0, Blocks.stoneWall);
                if (plan.placeable(team)) {
                    unit.addBuild(plan);
                    plannedPositions.add(key);
                    built++;
                    break; // one per angle, keep it spread out
                }
            }
        }
    }

    private void addTurrets(Unit unit, Team team, float cx, float cy) {
        Block[] turretOptions = {Blocks.arc, Blocks.lancer, Blocks.tsunami};

        // Only add turrets if there are enough walls built
        int wallCount = 0;
        for (Building b : Groups.build) {
            if (b.team != team) continue;
            if (b.block instanceof Wall) wallCount++;
        }
        if (wallCount < 3) return; // Need at least 3 walls first

        int built = 0;
        for (Block turret : turretOptions) {
            if (!turret.unlockedNow()) continue;
            if (built >= 2) break;

            for (int angle = 45; angle < 360 && built < 2; angle += 40) {
                int r = (int)(zoneRadius / 8f) + 2;
                int tx = (int)(cx / 8f) + Mathf.round(Mathf.cosDeg(angle) * r);
                int ty = (int)(cy / 8f) + Mathf.round(Mathf.sinDeg(angle) * r);

                if (Mathf.sqrt(Mathf.pow(tx * 8f - cx, 2) + Mathf.pow(ty * 8f - cy, 2)) < zoneRadius * 0.7f) continue;

                Tile tile = Vars.world.tile(tx, ty);
                if (tile == null) continue;
                Block bl = tile.block();
                if (bl != Blocks.air && !(bl instanceof OreBlock)) continue;
                if (tile.floor().isLiquid) continue;
                if (tile.build != null) continue;

                BuildPlan plan = new BuildPlan(tx, ty, 0, turret);
                if (plan.placeable(team)) {
                    unit.addBuild(plan);
                    built++;
                }
            }
        }
    }

    private Building findDamagedStructure(Unit unit, float cx, float cy, float maxDist) {
        Building best = null;
        float bestDist = Float.MAX_VALUE;

        for (Building b : Groups.build) {
            if (b.team != unit.team) continue;
            if (b.dead) continue;
            Block block = b.block;
            if (!(block instanceof Wall) && !(block instanceof Turret)) continue;
            if (b.health >= b.maxHealth * 0.9f) continue;

            float d = Mathf.dst(b.x, b.y, cx, cy);
            if (d > maxDist) continue;
            if (d < bestDist) {
                bestDist = d;
                best = b;
            }
        }
        return best;
    }

    private void doRepairPhase(Unit unit, Building core) {
        if (unit.buildPlan() != null) return; // Already building something

        float cx = core.x;
        float cy = core.y;

        for (Building b : Groups.build) {
            if (b.team != unit.team) continue;
            if (b.dead) continue;
            if (b.health >= b.maxHealth) continue;

            float d = Mathf.dst(b.x, b.y, unit.x, unit.y);
            if (d > REPAIR_RANGE) continue;

            Block block = b.block;
            if (!(block instanceof Wall) && !(block instanceof Turret)) continue;

            BuildPlan plan = new BuildPlan(b.tile.x, b.tile.y, b.rotation, block);
            if (plan.placeable(unit.team)) {
                unit.addBuild(plan);
                return;
            }
        }
    }
}
