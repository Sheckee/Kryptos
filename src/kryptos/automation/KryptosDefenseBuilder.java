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
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.Wall;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.blocks.distribution.Conveyor;
import mindustry.world.blocks.distribution.Junction;
import mindustry.world.blocks.distribution.Router;
import mindustry.world.blocks.distribution.MassDriver;
import mindustry.world.blocks.production.Drill;
import mindustry.world.blocks.environment.OreBlock;

import java.util.ArrayDeque;

import static mindustry.Vars.world;

/**
 * Builds defensive structures (walls, turrets) near enemy spawn points
 * (enemy cores), and also fills nearby ore deposits with drills.
 *
 * Spawn count logic:
 * - 1 enemy core: single defense builder works alongside the smart drill
 * - 2 enemy cores: defense builder splits, one per core
 * - 3+ enemy cores: auto-spawn additional defense builder units
 */
public final class KryptosDefenseBuilder {

    private static final float SCAN_INTERVAL_TICKS = 60f * 5f;
    private static final int MAX_DRILLS_PER_CYCLE = 4;
    private static final int MAX_DEFENSE_PER_CYCLE = 20;
    private static final int MAX_TURRETS_PER_CYCLE = 8;

    private static final int[] DX4 = {1, 0, -1, 0};
    private static final int[] DY4 = {0, 1, 0, -1};

    public enum State { IDLE, SCANNING, BUILDING }
    private static State state = State.IDLE;

    public static State state() { return state; }

    private static float lastScanTime = -SCAN_INTERVAL_TICKS;

    private static Unit helperUnit;

    private KryptosDefenseBuilder() {}

    public static void init() {
        Events.on(WorldLoadEvent.class, e -> reset());
        Events.run(Trigger.update, KryptosDefenseBuilder::update);
    }

    public static void requestImmediateScan() {
        lastScanTime = -SCAN_INTERVAL_TICKS - 1f;
        ensureHelper();
    }

    private static void ensureHelper() {
        if (Vars.player == null) return;
        helperUnit = KryptosBuilderUnits.getOrSpawn(helperUnit, Vars.player.team(), KryptosUnits.defenseBuilder);
    }

    private static int countNeededUnits() {
        int coreCount = countEnemyCores();
        if (coreCount <= 1) return 1;
        if (coreCount == 2) return 2;
        return coreCount;
    }

    private static int countEnemyCores() {
        int count = 0;
        for (Team team : Team.all) {
            if (team == Vars.player.team() || team == null) continue;
            if (team.core() != null) count++;
        }
        return Math.max(count, 1);
    }

    private static void reset() {
        lastScanTime = -SCAN_INTERVAL_TICKS;
        helperUnit = null;
        KryptosBuilderUnits.killAll();
        KryptosOreRegistry.reset();
    }

    private static float lastGateLogTime = -1e9f;

    private static void update() {
        if (!Vars.state.isGame()) return;

        if (Time.time - lastGateLogTime > 300f) {
            lastGateLogTime = Time.time;
            Log.info("[Kryptos] DefenseBuilder gate check: autoplay=@ defenseBuilder=@ helperUnit=@",
                KryptosHud.autoplay, KryptosAutomationPanel.autoDefenseBuilder, helperUnit);
        }

        if (!KryptosHud.autoplay || !KryptosAutomationPanel.autoDefenseBuilder) {
            state = State.IDLE;
            return;
        }
        if (Vars.player == null) return;

        ensureHelper();
        if (helperUnit == null) {
            state = State.IDLE;
            return;
        }

        float now = Time.time;
        if (now - lastScanTime < SCAN_INTERVAL_TICKS) {
            state = helperUnit.buildPlan() != null ? State.BUILDING : State.IDLE;
            return;
        }
        lastScanTime = now;
        state = State.SCANNING;

        try {
            scanAndBuild();
        } catch (Throwable t) {
            Log.err("[Kryptos] DefenseBuilder scan failed, disabling module to avoid repeat crashes", t);
            KryptosAutomationPanel.autoDefenseBuilder = false;
            state = State.IDLE;
            return;
        }

        state = helperUnit.buildPlan() != null ? State.BUILDING : State.IDLE;
    }

    private static void scanAndBuild() {
        Team team = Vars.player.team();
        Building core = team.core();
        if (core == null) return;

        int coreX = core.tile.x;
        int coreY = core.tile.y;

        Seq<Building> enemyCores = new Seq<>();
        for (Team enemyTeam : Team.all) {
            if (enemyTeam == team || enemyTeam == null) continue;
            Building enemyCore = enemyTeam.core();
            if (enemyCore != null) {
                enemyCores.add(enemyCore);
            }
        }

        if (enemyCores.isEmpty()) return;

        int needed = countNeededUnits();
        int handled = 0;

        for (Building enemyCore : enemyCores) {
            if (handled >= needed * 2) break;
            int index = handled % Math.max(needed, 1);
            handleCore(team, coreX, coreY, enemyCore, index);
            handled++;
        }

        fillNearbyOre(team, coreX, coreY);
    }

    private static void handleCore(Team team, int coreX, int coreY, Building enemyCore, int unitIndex) {
        int ex = enemyCore.tile.x;
        int ey = enemyCore.tile.y;

        buildDefensePerimeter(team, enemyCore);
        buildTurretsNearCore(team, enemyCore);
        buildDrillsNearCore(team, enemyCore);
    }

    private static void buildDefensePerimeter(Team team, Building target) {
        int tx = target.tile.x;
        int ty = target.tile.y;
        int placed = 0;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (dx == 0 && dy == 0) continue;
                if (Math.abs(dx) == 2 && Math.abs(dy) == 2) continue;

                int wx = tx + dx;
                int wy = ty + dy;
                Tile tile = world.tile(wx, wy);
                if (tile == null) continue;
                Block existing = tile.block();
                if (existing != Blocks.air && !(existing instanceof OreBlock)) continue;
                if (tile.floor().isLiquid) continue;
                if (tile.build != null) continue;

                BuildPlan plan = new BuildPlan(wx, wy, 0, Blocks.stoneWall);
                if (!plan.placeable(team) || helperUnit == null) continue;

                helperUnit.addBuild(plan);
                placed++;

                if (placed >= MAX_DEFENSE_PER_CYCLE) return;
            }
        }

        if (placed > 0) {
            Log.info("[Kryptos] DefenseBuilder: placed @ walls near enemy at @,@", placed, tx, ty);
        }
    }

    private static void buildTurretsNearCore(Team team, Building target) {
        int tx = target.tile.x;
        int ty = target.tile.y;
        int placed = 0;

        Block[] turrets = {
            Blocks.arc,
            Blocks.lancer,
            Blocks.tsunami,
            Blocks.fuse,
            Blocks.ripple,
        };

        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -5; dy <= 5; dy++) {
                if (dx == 0 && dy == 0) continue;

                int wx = tx + dx;
                int wy = ty + dy;
                Tile tile = world.tile(wx, wy);
                if (tile == null) continue;
                Block existing = tile.block();
                if (existing != Blocks.air && !(existing instanceof OreBlock)) continue;
                if (tile.floor().isLiquid) continue;
                if (tile.build != null) continue;

                for (Block turret : turrets) {
                    BuildPlan plan = new BuildPlan(wx, wy, 0, turret);
                    if (plan.placeable(team) && helperUnit != null) {
                        helperUnit.addBuild(plan);
                        placed++;
                        break;
                    }
                }

                if (placed >= MAX_TURRETS_PER_CYCLE) return;
            }
        }

        if (placed > 0) {
            Log.info("[Kryptos] DefenseBuilder: placed @ turrets near enemy at @,@", placed, tx, ty);
        }
    }

    private static void buildDrillsNearCore(Team team, Building target) {
        int tx = target.tile.x;
        int ty = target.tile.y;
        int range = 8;
        int placed = 0;

        ObjectMap<Item, Seq<Tile>> oreByItem = new ObjectMap<>();

        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                int wx = tx + dx;
                int wy = ty + dy;
                Tile tile = world.tile(wx, wy);
                if (tile == null) continue;

                Block overlay = tile.overlay();
                if (!(overlay instanceof OreBlock)) continue;
                Item item = getItemFromOre((OreBlock) overlay);
                if (item == null) continue;

                Seq<Tile> list = oreByItem.get(item);
                if (list == null) {
                    list = new Seq<>();
                    oreByItem.put(item, list);
                }
                list.add(tile);
            }
        }

        for (Item item : oreByItem.keys()) {
            Seq<Tile> tiles = oreByItem.get(item);
            Drill drill = findBestDrillForItem(team, item);
            if (drill == null) continue;

            int drillsToBuild = Math.min(MAX_DRILLS_PER_CYCLE, tiles.size);
            for (int i = 0; i < drillsToBuild; i++) {
                Tile oreTile = tiles.get(i);
                BuildPlan plan = new BuildPlan(oreTile.x, oreTile.y, 0, drill);
                if (plan.placeable(team) && helperUnit != null) {
                    helperUnit.addBuild(plan);
                    placed++;
                }
            }
        }

        if (placed > 0) {
            Log.info("[Kryptos] DefenseBuilder: placed @ drills near enemy at @,@", placed, tx, ty);
        }
    }

    private static void fillNearbyOre(Team team, int coreX, int coreY) {
        int w = world.width();
        int h = world.height();

        boolean[] seen = new boolean[w * h];
        ObjectMap<Item, Seq<IntSeq>> depositsByItem = new ObjectMap<>();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                if (seen[idx]) continue;

                Tile tile = world.tile(x, y);
                if (tile == null) { seen[idx] = true; continue; }

                Block overlay = tile.overlay();
                if (!(overlay instanceof OreBlock)) { seen[idx] = true; continue; }

                OreBlock oreBlock = (OreBlock) overlay;
                Item item = getItemFromOre(oreBlock);
                if (item == null) { seen[idx] = true; continue; }

                int dist = Math.abs(x - coreX) + Math.abs(y - coreY);
                if (dist > 40) { seen[idx] = true; continue; }

                IntSeq cluster = floodFillCluster(tile, oreBlock, seen, w, h);
                if (cluster.size < 2) continue;

                int key = clusterKey(cluster);
                if (KryptosOreRegistry.isClaimed(key)) continue;

                OreDeposit deposit = new OreDeposit(key, cluster, item, x, y, dist);
                Seq<IntSeq> list = depositsByItem.get(item);
                if (list == null) {
                    list = new Seq<>();
                    depositsByItem.put(item, list);
                }
                list.add(cluster);
            }
        }

        int placed = 0;
        for (Item item : depositsByItem.keys()) {
            Seq<IntSeq> clusters = depositsByItem.get(item);
            clusters.sort((a, b) -> {
                int centerA = centerDist(a, coreX, coreY);
                int centerB = centerDist(b, coreX, coreY);
                return Integer.compare(centerA, centerB);
            });

            Drill drill = findBestDrillForItem(team, item);
            if (drill == null) continue;

            int drillCount = Math.min(MAX_DRILLS_PER_CYCLE, clusters.size);
            for (int i = 0; i < drillCount; i++) {
                IntSeq cluster = clusters.get(i);
                int bestX = cluster.items[0] % w;
                int bestY = cluster.items[0] / w;
                int bestDist = Integer.MAX_VALUE;
                for (int j = 0; j < cluster.size; j++) {
                    int cx = Point2.x(cluster.items[j]);
                    int cy = Point2.y(cluster.items[j]);
                    int dist = Math.abs(cx - coreX) + Math.abs(cy - coreY);
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestX = cx;
                        bestY = cy;
                    }
                }

                BuildPlan plan = new BuildPlan(bestX, bestY, 0, drill);
                if (plan.placeable(team) && helperUnit != null) {
                    helperUnit.addBuild(plan);
                    placed++;
                }
            }
        }

        if (placed > 0) {
            Log.info("[Kryptos] DefenseBuilder: filled @ nearby ore deposits with drills.", placed);
        }
    }

    private static int centerDist(IntSeq cluster, int coreX, int coreY) {
        int sumX = 0, sumY = 0;
        for (int i = 0; i < cluster.size; i++) {
            sumX += Point2.x(cluster.items[i]);
            sumY += Point2.y(cluster.items[i]);
        }
        return Math.abs(sumX / cluster.size - coreX) + Math.abs(sumY / cluster.size - coreY);
    }

    private static IntSeq floodFillCluster(Tile start, Block overlay, boolean[] seen, int w, int h) {
        IntSeq cluster = new IntSeq();
        ArrayDeque<Integer> queue = new ArrayDeque<>();

        queue.add(start.pos());
        seen[start.y * w + start.x] = true;

        while (!queue.isEmpty()) {
            int packed = queue.poll();
            int x = Point2.x(packed);
            int y = Point2.y(packed);
            cluster.add(packed);

            for (int dir = 0; dir < 4; dir++) {
                int nx = x + DX4[dir];
                int ny = y + DY4[dir];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;

                int nIdx = ny * w + nx;
                if (seen[nIdx]) continue;

                Tile neighbor = world.tile(nx, ny);
                if (neighbor == null || neighbor.overlay() != overlay) {
                    seen[nIdx] = true;
                    continue;
                }

                seen[nIdx] = true;
                queue.add(neighbor.pos());
            }
        }

        return cluster;
    }

    private static int clusterKey(IntSeq cluster) {
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < cluster.size; i++) {
            min = Math.min(min, cluster.items[i]);
        }
        return min;
    }

    private static Drill findBestDrillForItem(Team team, Item item) {
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
                if (core.items.get(drill.requirements[0].item) >= drill.requirements[0].amount) {
                    return drill;
                }
            }
        }

        return candidates.peek();
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

    private static class OreDeposit {
        final int key;
        final IntSeq cluster;
        final Item item;
        final int centerX, centerY;
        final int coreDist;

        OreDeposit(int key, IntSeq cluster, Item item, int cx, int cy, int dist) {
            this.key = key;
            this.cluster = cluster;
            this.item = item;
            this.centerX = cx;
            this.centerY = cy;
            this.coreDist = dist;
        }
    }
}