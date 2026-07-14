package kryptos.content;

import mindustry.world.blocks.environment.OreBlock;

public class KryptosBlocks {
    public static OreBlock oreCustom;

    public static void load() {
        oreCustom = new OreBlock("ore-kryptos", KryptosItems.customOre) {{
            // oreDefault left false (default) on purpose: this ore must NOT
            // compete with Copper/Lead/Titanium/etc. in automatic map
            // generation, or it crowds them out of every eligible tile.
            // It can still be placed by hand in the map editor.
            oreThreshold = 0.82f;
            oreScale = 24f;
            variants = 3;
        }};
    }
}
