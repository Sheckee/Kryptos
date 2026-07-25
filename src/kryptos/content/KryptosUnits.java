package kryptos.content;

import mindustry.ai.UnitCommand;
import mindustry.type.UnitType;

/**
 * Content-side unit definitions for Kryptos. Two "builder drone" types --
 * {@link #defenseBuilder} for {@link kryptos.automation.KryptosDefenseBuilder} and
 * {@link #smartDrillBuilder} for {@link kryptos.automation.KryptosSmartDrill}
 * -- each module spawns (and reuses) its own instead of forcing the player's
 * own unit to fly out and build things.
 *
 * They're functionally identical, split into two types only so the two
 * modules' drones are visually distinguishable in-game.
 */
public class KryptosUnits {
    public static UnitType defenseBuilder;
    public static UnitType smartDrillBuilder;

    public static void load() {
        defenseBuilder = new UnitType("kryptos-defense-builder") {{
            applyBuilderDroneStats(this);
        }};

        // Sprite: sprites/units/kryptos-smartdrill.png
        smartDrillBuilder = new UnitType("kryptos-smartdrill") {{
            applyBuilderDroneStats(this);
        }};
    }

    private static void applyBuilderDroneStats(UnitType type) {
        // rebuildCommand -> BuilderAI: the drone will fly to whatever
        // BuildPlan is queued on it (via unit.addBuild(...)) and
        // construct it on its own, no player control needed.
        type.defaultCommand = UnitCommand.rebuildCommand;

        type.flying = true;
        type.lowAltitude = true;
        type.isEnemy = false;
        type.controlSelectGlobal = false;
        // Automation only -- the drone can never be selected or
        // commanded by the player via the RTS Command panel, so it can
        // only ever do what KryptosDefenseBuilder/KryptosSmartDrill
        // explicitly queue on it (see KryptosDroneAI).
        type.playerControllable = false;

        type.hitSize = 9f;
        type.health = 160f;

        type.speed = 2.1f;
        type.accel = 0.1f;
        type.drag = 0.06f;
        type.rotateSpeed = 12f;

        type.engineOffset = 5.5f;
        type.engineSize = 1.6f;

        // Utility drone: no weapons, just building.
        type.buildSpeed = 5.5f;
        type.buildRange = 120f;
    }
}
