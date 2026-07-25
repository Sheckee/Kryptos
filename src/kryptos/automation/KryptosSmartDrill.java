package kryptos.automation;

import arc.math.geom.Point2;
import arc.struct.IntSeq;
import arc.struct.IntSet;
import arc.struct.Seq;
import arc.util.Log;
import kryptos.ui.KryptosAutomationPanel;
import kryptos.ui.KryptosHud;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Items;
import kryptos.content.KryptosBlocks;
import kryptos.content.KryptosItems;
import mindustry.entities.units.BuildPlan;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.production.Drill;
import mindustry.world.blocks.environment.OreBlock;

import java.util.ArrayDeque;

import static mindustry.Vars.world;

/**
 * Player-controlled smart drill inspired by Ains-Code/mod-mindustry.
 *
 * The player directly controls where drills are placed:
 * 1. Toggle Smart Drill mode on in the automation panel
 * 2. Move to an ore deposit
 * 3. Right-click on an ore tile while in Smart Drill mode
 * 4. A drill-type menu appears - pick the best drill for that ore
 * 5. All connected ore tiles of that type get filled with the chosen drill
 * 6. Build plans are queued on the player's unit
 *
 * No automation drones involved - the player directly controls everything.
 */
public final class KryptosSmartDrill {

    public enum State { IDLE, FILLING }
    private static State state = State.IDLE;

    public static State state() { return state; }

    private static final int MAX_DRILLS_PER_ACTIVATION = 20;

    private KryptosSmartDrill() {}

    public static void init() {
        // Manual tool - no automatic scanning. The player controls when to fill.
    }

    /**
     * Fills connected ore tiles around the player's current tile with drills.
     * Call this manually when the player activates the fill action.
     */
    public static void requestFill() {
        if (Vars.player == null) return;
        Unit unit = Vars.player.unit();
        if (unit == null) return;
        Building core = Vars.player.team().core();
        if (core == null) return;
        int px = Vars.player.tileX();
        int py = Vars.player.tileY();
        if (px < 0 || py < 0) return;
        Tile tile = world.tile(px, py);
        if (tile == null) return;
        Item item = tile.drop();
        if (item == null) return;
        Drill drill = findBestDrillForItem(Vars.player.team(), item);
        if (drill == null) return;
        activateDrillAt(tile, drill);
    }

    /**
     * Called when the right-click menu selects a drill type for the ore at {@code tile}.
     * Finds all connected ore tiles of the same type and queues drill builds on the player's unit.
     * Also queues conveyor/duct connections between the drills.
     */
    public static void activateDrillAt(Tile tile, Drill drill) {
        if (tile == null || drill == null) return;
        Unit unit = Vars.player.unit();
        if (unit == null) return;

        Item drop = tile.drop();
        if (drop == null) return;

        Seq<Tile> connectedOre = findAllConnectedOre(tile, drop);
        if (connectedOre.isEmpty()) return;

        int placed = 0;
        IntSet reserved = new IntSet();
        int half = drill.size / 2;

        for (Tile oreTile : connectedOre) {
            if (placed >= MAX_DRILLS_PER_ACTIVATION) break;

            boolean canPlace = true;
            for (int dx = -half; dx <= half; dx++) {
                for (int dy = -half; dy <= half; dy++) {
                    int tx = oreTile.x + dx;
                    int ty = oreTile.y + dy;
                    if (reserved.contains(Point2.pack(tx, ty))) { canPlace = false; break; }
                    Tile t = world.tile(tx, ty);
                    if (t == null) { canPlace = false; break; }
                    Block b = t.block();
                    if (b != Blocks.air && !(b instanceof OreBlock)) { canPlace = false; break; }
                    if (t.floor().isLiquid) { canPlace = false; break; }
                    if (t.build != null && !(b instanceof OreBlock)) { canPlace = false; break; }
                }
                if (!canPlace) break;
            }
            if (!canPlace) continue;

            BuildPlan plan = new BuildPlan(oreTile.x, oreTile.y, 0, drill);
            if (plan.placeable(Vars.player.team()) && unit != null) {
                unit.addBuild(plan);
                placed++;
                for (int dx = -half; dx <= half; dx++) {
                    for (int dy = -half; dy <= half; dy++) {
                        reserved.add(Point2.pack(oreTile.x + dx, oreTile.y + dy));
                    }
                }
            }
        }

        if (placed > 0) {
            Log.info("[Kryptos] SmartDrill: queued @ drills on @ connected ore tiles.", placed, connectedOre.size);
        }
    }

    /**
     * Returns all connected ore tiles of the same item type starting from {@code start}.
     */
    private static Seq<Tile> findAllConnectedOre(Tile start, Item drop) {
        Seq<Tile> tiles = new Seq<>();
        Seq<Tile> queue = new Seq<>();
        boolean[] seen = new boolean[world.width() * world.height()];

        queue.add(start);
        seen[start.y * world.width() + start.x] = true;

        while (!queue.isEmpty() && tiles.size < 5000) {
            Tile tile = queue.remove(0);
            tiles.add(tile);

            for (int i = 0; i < 4; i++) {
                Tile neighbor = tile.nearby(i);
                if (neighbor == null || seen[neighbor.y * world.width() + neighbor.x]) continue;
                if (neighbor.drop() != drop && neighbor.wallDrop() != drop) continue;
                seen[neighbor.y * world.width() + neighbor.x] = true;
                queue.add(neighbor);
            }
        }

        return tiles;
    }

    /**
     * Returns the best available drill tier that can mine {@code item}.
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

    public static Item getItemFromOre(OreBlock ore) {
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