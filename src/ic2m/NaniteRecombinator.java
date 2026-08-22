package ic2m;

import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.consumers.ConsumeItems;
import mindustry.world.consumers.ConsumeLiquid;

/** Synthesizes Nanite Gel from Surge Alloy + Oil. Power-free so it runs off the
 *  base's logistics without needing an IC2 power tap. */
public class NaniteRecombinator extends GenericCrafter {
    public NaniteRecombinator(String name) {
        super(name);
        size = 2;
        solid = true;
        hasPower = false;
        hasLiquids = true;
        liquidCapacity = 30f;
        craftTime = 90f;

        consume(new ConsumeItems(ItemStack.with(Items.surgeAlloy, 1)));
        consume(new ConsumeLiquid(Liquids.oil, 0.15f));
        // output item is wired up in Ic2mMod.init() once the Nanite Gel item exists.
        outputItems = new ItemStack[0];

        requirements = ItemStack.with(Items.copper, 80, Items.lead, 60, Items.silicon, 40);
        category = Category.crafting;
    }
}
