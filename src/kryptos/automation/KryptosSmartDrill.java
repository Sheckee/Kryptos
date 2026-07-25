package kryptos.automation;

import arc.Events;
import arc.math.geom.Point2;
import arc.struct.IntSeq;
import arc.struct.IntSet;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import kryptos.ui.KryptosAutomationPanel;
import kryptos.ui.KryptosHud;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Items;
import kryptos.content.KryptosBlocks;
import kryptos.content.KryptosItems;
import mindustry.entities.units.BuildPlan;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.production.Drill;
import mindustry.world.blocks.environment.OreBlock;

import java.util.ArrayDeque;
import java.util.HashMap;

import static mindustry.Vars.world;

/**
 * Player-controlled smart drill inspired by Ains-Code/mod-mindustry.
 *
 * When toggled on via the automation panel, the player's unit
 * automatically scans nearby ore and fills connected deposits
 * with drills at maximum efficiency. The player controls the
 * unit's movement directly and drills get placed automatically
 * as the unit moves over ore deposits.
 *
 * "Fill with ore" = when activated, fills the entire connected
 * ore vein with drills, placing the best available drill tier
 * for each ore type and connecting them with item bridges.
 */
public final class KryptosSmartDrill {

    private static final float SCAN_INTERVAL_TICKS = 60f * 2f;
    private static final int MAX_DRILLS_PER_CYCLE = 8;
    private static final int FILL_ALL_MAX_TILES = 5000;

    private static final int[] DX4 = {1, 0, -1, 0};
    private static final int[] DY4 = {0, 1, 0, -1};

    public enum State { IDLE, SCANNING, FILLING }
    private static State state = State.IDLE;

    private static Unit helperUnit;
    private static float lastScanTime = -SCAN_INTERVAL_TICKS;

    public static State state() { return state; }

    private KryptosSmartDrill() {}

    public static void init() {
        Events.on(WorldLoadEvent.class, e -> reset());
        Events.run(Trigger.update, KryptosSmartDrill::update);
    }

    public static void requestImmediateScan() {
        lastScanTime = -SCAN_INTERVAL_TICKS - 1f;
        ensureHelper();
    }

    private static void ensureHelper() {
        if (Vars.player == null) return;
        helperUnit = KryptosBuilderUnits.getOrSpawn(helperUnit, Vars.player.team(), Vars.player.unit().type);
    }

    private static void reset() {
        state = State.IDLE;
        lastScanTime = -SCAN_INTERVAL_TICKS;
        helperUnit = null;
        KryptosBuilderUnits.killAll();
        KryptosOreRegistry.reset();
    }

    private static void update() {
        if (!Vars.state.isGame()) return;

        if (!KryptosHud.autoplay || !KryptosAutomationPanel.autoSmartDrill) {
            state = State.IDLE;
            return;
        }
        if (Vars.player == null) return;

        Unit unit = Vars.player.unit();
        if (unit == null) return;

        if (unit.buildPlan() != null) {
            state = State.FILLING;
            return;
        }

        float now = Time.time;
        if (now - lastScanTime < SCAN_INTERVAL_TICKS) {
            state = State.IDLE;
            return;
        }
        lastScanTime = now;
        state = State.SCANNING;

        try {
            fillConnectedOre(Vars.player.team(), unit);
        } catch (Throwable t) {
            Log.err("[Kryptos] SmartDrill fill failed", t);
        }
    }

    private static void fillConnectedOre(Team team, Unit unit) {
        Building core = team.core();
        if (core == null) return;

        int w = world.width();
        int h = world.height();
        int px = Vars.player.tileX();
        int py = Vars.player.tileY();

        if (px < 0 || py < 0 || px >= w || py >= h) return;

        Tile startTile = world.tile(px, py);
        if (startTile == null) return;

        boolean[] seen = new boolean[w * h];
        ObjectMap<Item, Seq<Tile>> oreByItem = new ObjectMap<>();

        ArrayDeque<Tile> queue = new ArrayDeque<>();
        queue.add(startTile);
        seen[startTile.y * w + startTile.x] = true;

        int scanned = 0;
        while (!queue.isEmpty() && scanned < FILL_ALL_MAX_TILES) {
            Tile tile = queue.poll();
            scanned++;

            Block overlay = tile.overlay();
            if (overlay instanceof OreBlock) {
                Item item = getItemFromOre((OreBlock) overlay);
                if (item != null) {
                    Seq<Tile> list = oreByItem.get(item);
                    if (list == null) {
                        list = new Seq<>();
                        oreByItem.put(item, list);
                    }
                    list.add(tile);
                }
            }

            for (int dir = 0; dir < 4; dir++) {
                int nx = tile.x + DX4[dir];
                int ny = tile.y + DY4[dir];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                int nIdx = ny * w + nx;
                if (seen[nIdx]) continue;
                Tile neighbor = world.tile(nx, ny);
                if (neighbor == null) continue;
                seen[nIdx] = true;
                queue.add(neighbor);
            }
        }

        for (Item item : oreByItem.keys()) {
            Seq<Tile> tiles = oreByItem.get(item);
            Drill drill = findBestDrillForItem(team, item);
            if (drill == null) continue;

            placeDrillsOnOre(team, unit, drill, tiles);
        }
    }

    private static void placeDrillsOnOre(Team team, Unit unit, Drill drill, Seq<Tile> tiles) {
        int half = drill.size / 2;
        IntSet reservedTiles = new IntSet();
        int placed = 0;

        for (Tile oreTile : tiles) {
            boolean canPlace = true;
            for (int dx = -half; dx <= half; dx++) {
                for (int dy = -half; dy <= half; dy++) {
                    int tx = oreTile.x + dx;
                    int ty = oreTile.y + dy;
                    if (reservedTiles.contains(Point2.pack(tx, ty))) {
                        canPlace = false;
                        break;
                    }
                    Tile t = world.tile(tx, ty);
                    if (t == null) { canPlace = false; break; }
                    if (t.block() != Blocks.air && !(t.block() instanceof OreBlock)) {
                        canPlace = false; break;
                    }
                    if (t.floor().isLiquid) { canPlace = false; break; }
                    if (t.build != null && !(t.block() instanceof OreBlock)) {
                        canPlace = false; break;
                    }
                }
                if (!canPlace) break;
            }
            if (!canPlace) continue;

            BuildPlan plan = new BuildPlan(oreTile.x, oreTile.y, 0, drill);
            if (plan.placeable(team) && unit != null) {
                unit.addBuild(plan);
                placed++;
                for (int dx = -half; dx <= half; dx++) {
                    for (int dy = -half; dy <= half; dy++) {
                        reservedTiles.add(Point2.pack(oreTile.x + dx, oreTile.y + dy));
                    }
                }
            }
        }

        if (placed > 0) {
            Log.info("[Kryptos] SmartDrill: placed @ drills for @ on @ connected tiles.",
                placed, drill.localizedName, tiles.size);
        }
    }

    public static Drill findBestDrillForItem(Team team, Item item) {
        Drill existing = KryptosFieldTier.matchExistingDrill(team, item);
        if (existing != null) return existing;

        Seq<Block> blocks = Vars.content.blocks();
        Seq<Drill> candidates = new Seq<>();

        for (Block block : blocks) {
            if (!(block instanceof Drill)) continue;
            Drill drill = (Drill) block;
            if (!drill.unlockedNow() && !Vars.state.rules.infiniteResources) continue;
            if (drill.drillTime <= 0) continue;
            candidates.add(drill);
        }

        if (candidates.isEmpty()) return null;
        candidates.sort((a, b) -> Integer.compare(b.tier, a.tier));

        Building core = team.core();
        if (core != null) {
            for (Drill drill : candidates) {
                if (canAfford(core, drill)) return drill;
            }
        }

        return candidates.peek();
    }

    private static boolean canAfford(Building core, Block block) {
        for (ItemStack stack : block.requirements) {
            if (core.items.get(stack.item) < stack.amount) return false;
        }
        return true;
    }

    private static Item getItemFromOre(OreBlock ore) {
        if (ore == Blocks.oreCopper) return Items.copper;
        if (ore == Blocks.oreLead) return Items.lead;
        if (ore == Blocks.oreCoal) return Items.coal;
        if (ore == Blocks.oreTitanium) return Items.titanium;
        if (ore == Blocks.oreThorium) return Items.thorium;
        if (ore == Blocks.oreScrap) return Items.scrap;
        if (ore == KryptosBlocks.oreCustom) return KryptosItems.customOre;
        return null;
    }
}