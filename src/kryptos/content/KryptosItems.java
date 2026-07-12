package kryptos.content;

import arc.graphics.Color;
import mindustry.type.Item;

public class KryptosItems {
    public static Item voidsteel;

    public static void load() {
        voidsteel = new Item("voidsteel", Color.valueOf("7b8494")) {{
            hardness = 4;
            cost = 1.1f;
            charge = 0f;
            explosiveness = 0f;
            radioactivity = 0f;
            flammability = 0f;
        }};
    }
}
