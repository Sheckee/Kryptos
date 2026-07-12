package kryptos.content;

import mindustry.world.blocks.environment.OreBlock;

public class KryptosBlocks {
    public static OreBlock oreVoidsteel;

    public static void load() {
        oreVoidsteel = new OreBlock("ore-voidsteel", KryptosItems.voidsteel) {{
            oreThreshold = 0.82f;
            oreScale = 24f;
            variants = 3;
        }};
    }
}
