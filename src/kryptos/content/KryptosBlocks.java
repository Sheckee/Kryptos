package kryptos.content;

import mindustry.ctype.ContentList;
import mindustry.world.blocks.environment.OreBlock;

/**
 * Kryptos environment/building blocks. Call load() once from
 * KryptosMod#loadContent() — after KryptosItems has already loaded, since
 * ore blocks reference an Item instance directly.
 *
 * NOTE: OreBlock's exact constructor signature and field names (oreDefault,
 * oreThreshold, oreScale, variants, etc.) are reproduced here from general
 * knowledge of the Mindustry API, not verified against source in this
 * environment (no internet access to check the current v146 API). If this
 * doesn't compile as-is, check OreBlock.java in the Mindustry source for the
 * current constructor/fields and adjust — the sprite naming convention
 * (ore-voidsteel1/2/3.png, already in sprites/blocks/) should still be
 * correct either way, since that's a fixed Mindustry content-loading
 * convention, not an API detail.
 */
public class KryptosBlocks implements ContentList {
    public static OreBlock oreVoidsteel;

    @Override
    public void load() {
        oreVoidsteel = new OreBlock("ore-voidsteel", KryptosItems.voidsteel) {{
            // how common this ore is during world generation - tune once
            // you're testing on an actual map. Lower threshold = more ore.
            oreThreshold = 0.82f;
            oreScale = 24f;
            variants = 3; // matches ore-voidsteel1/2/3.png
        }};
    }
}
