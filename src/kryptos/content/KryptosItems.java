package kryptos.content;

import arc.graphics.Color;
import mindustry.type.Item;
import mindustry.ctype.ContentList;

/**
 * Kryptos item definitions. Call load() once from KryptosMod#loadContent().
 */
public class KryptosItems implements ContentList {
    public static Item voidsteel;

    @Override
    public void load() {
        voidsteel = new Item("voidsteel", Color.valueOf("7b8494")) {{
            hardness = 4;
            cost = 1.1f;
            charge = 0f;
            explosive = false;
            radioactive = false;
            flammability = 0f;
            // deposits onto ores placed via content/blocks (e.g. a KryptosOreVoidsteel floor block)
            // once world-gen content exists; for now this is a craftable-only resource.
        }};
    }
}
