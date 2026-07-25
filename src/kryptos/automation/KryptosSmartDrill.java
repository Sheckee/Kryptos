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
import kryptos.content.KryptosUnits;
import mindustry.entities.units.BuildPlan;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.production.Drill;
import mindustry.world.blocks.environment.OreBlock;
import mindustry.world.blocks.distribution.Conveyor;
import mindustry.world.blocks.distribution.Junction;
import mindustry.world.blocks.distribution.Router;
import mindustry.world.blocks.distribution.MassDriver;

import java.util.ArrayDeque;

import static mindustry.Vars.world;

/**
 * Player-controlled smart drill that fills ore deposits with
 * connected drills and bridges.
 *
 * When activated, the player's unit scans nearby ore deposits and
 * auto-places drills on all connected ore tiles, filling the deposit
 * entirely. This is inspired by mod-mindustry's SmartDrillFeature:
 * right-click an ore tile, pick a direction and drill type, and the
 * entire connected vein gets drilled with conveyor/duct connections.
 *
 * The player controls the unit directly (not an automation drone),
 * so they choose where and how to fill ore.
 */
public final class KryptosSmartDrill {

    private static final float FILL_ALL_MAX_TILES = 5000;
    private static final int DEFAULT_MAX_TILES = 100;

    private static final int[] DX4 = {1, 0, -1, 0};
    private static final int[] DY4 = {0, 1, 0, -1};

    public enum State { IDLE, SCANNING, FILLING }
    private static State state = State.IDLE;

    public static State state() { return state; }

    private KryptosSmartDrill() {}

    public static void init() {
        Events.on(WorldLoadEvent.class, e -> reset());
        Events.run(Trigger.update, KryptosSmartDrill::update);
    }

    public static void requestImmediateScan() {
        if (Vars.player == null) return;
        Unit unit = Vars.player.unit();
        if (unit == null) return;
        Log.info("[Kryptos] SmartDrill: fill activated by player.");
        state = State.SCANNING;
        try {
            fillConnectedOre(Vars.player.team(), UnitCommandMode.FILL_ALL);
        } catch (Throwable t) {
            Log.err("[Kryptos] SmartDrill fill failed", t);
        }
        state = State.IDLE;
    }

    private static void reset() {
        state = State.IDLE;
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

        state = State.IDLE;
    }

    private enum UnitCommandMode {
        FILL_ALL,
        FILL_DIRECTIONAL
    }

    /**
     * Fills all connected ore tiles of the same type with drills,
     * mimicking mod-mindustry's SmartDrillFeature.fill-all behavior.
     * Places the best available drill on each ore tile and connects
     * them with bridges/ducts.
     */
    private static void fillConnectedOre(Team team, UnitCommandMode mode) {
        Unit unit = Vars.player.unit();
        if (unit == null) return;

        Building core = team.core();
        if (core == null) return;

        int w = world.width();
        int h = world.height();

        Tile targetTile = null;

        if (mode == UnitCommandMode.FILL_ALL) {
            // Fill all reachable ore from the player's position
            int px = Vars.player.tileX();
            int py = Vars.player.tileY();
            int maxScan = 200;

            boolean[] seen = new boolean[w * h];
            ObjectMap<Item, Seq<Tile>> oreByItem = new ObjectMap<>();

            ArrayDeque<Tile> queue = new ArrayDeque<>();
            Tile startTile = world.tile(px, py);
            if (startTile != null) {
                queue.add(startTile);
                seen[startTile.y * w + startTile.x] = true;
            }

            int scanned = 0;
            while (!queue.isEmpty() && scanned < maxScan) {
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
                fillOreTiles(team, unit, core, tiles, item);
            }

            return;
        }
    }

    private static void fillOreTiles(Team team, Unit unit, Building core, Seq<Tile> tiles, Item item) {
        Drill drill = findBestDrillForItem(team, item);
        if (drill == null) return;

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
                        canPlace = false;
                        break;
                    }
                    if (t.floor().isLiquid) { canPlace = false; break; }
                    if (t.build != null && !(t.block() instanceof OreBlock)) {
                        canPlace = false;
                        break;
                    }
                }
                if (!canPlace) break;
            }

            if (!canPlace) continue;

            int offX = (oreTile.x % drill.size + drill.size) % drill.size;
            int offY = (oreTile.y % drill.size + drill.size) % drill.size;
            int baseX = oreTile.x - offX;
            int baseY = oreTile.y - offY;

            for (int dx = -half; dx <= half; dx++) {
                for (int dy = -half; dy <= half; dy++) {
                    int gx = baseX + dx;
                    int gy = baseY + dy;
                    reservedTiles.add(Point2.pack(gx, gy));
                }
            }

            BuildPlan plan = new BuildPlan(baseX, baseY, 0, drill);
            if (plan.placeable(team)) {
                unit.addBuild(plan);
                placed++;
            }
        }

        if (placed > 0) {
            Log.info("[Kryptos] SmartDrill: filled @ connected tiles with @.", placed, drill.localizedName);
        }
    }

    /**
     * Finds the best drill tier for mining {@code item} that the
     * player already has unlocked, falling back to lowest tier.
     */
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