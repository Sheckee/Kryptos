package kryptos.content;

import mindustry.world.blocks.environment.OreBlock;

public class KryptosBlocks {
    public static OreBlock oreCustom;

    public static void load() {
        oreCustom = new OreBlock("ore-kryptos", KryptosItems.customOre) {{
            oreDefault = true;
            oreThreshold = 0.82f;
            oreScale = 24f;
            variants = 3;
        }};
    }
}
