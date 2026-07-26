package kryptos.content;

import mindustry.ai.UnitCommand;
import mindustry.type.UnitType;

/**
 * Content-side unit definitions for Kryptos.
 *
 * {@link #defender} is a combat AI unit that builds defensive walls and turrets
 * outside the core's protected zone, repairs damaged structures, and fights enemies.
 *
 * {@link #smartDrillBuilder} is a builder drone for the Smart Drill module.
 */
public class KryptosUnits {
    public static UnitType defender;
    public static UnitType smartDrillBuilder;

    public static void load() {
        defender = new UnitType("kryptos-defender") {{
            range = 100f;
            hitSize = 12f;
            health = 500f;
            speed = 1.8f;
            accel = 0.15f;
            drag = 0.08f;
            rotateSpeed = 8f;
            buildSpeed = 3f;
            buildRange = 100f;
            flying = false;
            lowAltitude = false;
            engineOffset = 6f;
            engineSize = 2.0f;
            defaultCommand = UnitCommand.rebuildCommand;
        }};

        smartDrillBuilder = new UnitType("kryptos-smartdrill") {{
            defaultCommand = UnitCommand.rebuildCommand;
            flying = true;
            lowAltitude = true;
            isEnemy = false;
            controlSelectGlobal = false;
            playerControllable = false;
            hitSize = 9f;
            health = 160f;
            speed = 2.1f;
            accel = 0.1f;
            drag = 0.06f;
            rotateSpeed = 12f;
            engineOffset = 5.5f;
            engineSize = 1.6f;
            buildSpeed = 5.5f;
            buildRange = 120f;
        }};
    }
}
