package ic2m;

import mindustry.mod.Mod;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.consumers.ConsumeItems;
import mindustry.world.consumers.ConsumeItemFilter;

public class Ic2mMod extends Mod {
    public Ic2mMod() {
    }

    @Override
    public void loadContent() {
        addAlternateRecipes();
    }

    private void addAlternateRecipes() {
        Item copperIngot = findItem("copper-ingot");
        Item leadIngot = findItem("lead-ingot");
        Item titaniumIngot = findItem("titanium-ingot");
        Item coalIngot = findItem("coal-ingot");

        if (copperIngot == null || leadIngot == null || titaniumIngot == null || coalIngot == null) return;

        // These are alternatives, not replacements: the vanilla ingredient remains valid.
        replaceItemRecipes(Blocks.siliconSmelter,
            new Item[]{Items.copper, Items.coal}, new Item[]{copperIngot, coalIngot});
        replaceItemRecipes(Blocks.kiln,
            new Item[]{Items.lead}, new Item[]{leadIngot});
        replaceItemRecipes(Blocks.graphitePress,
            new Item[]{Items.coal}, new Item[]{coalIngot});
        replaceItemRecipes(Blocks.surgeSmelter,
            new Item[]{Items.titanium}, new Item[]{titaniumIngot});
    }

    private Item findItem(String suffix) {
        return Vars.content.items().find(item -> item.name.endsWith("-" + suffix));
    }

    private void replaceItemRecipes(Block block, Item[] vanilla, Item[] alternate) {
        block.removeConsumers(consume -> consume instanceof ConsumeItems);
        for (int i = 0; i < vanilla.length; i++) {
            Item normal = vanilla[i];
            Item replacement = alternate[i];
            block.consume(new ConsumeItemFilter(item -> item == normal || item == replacement));
        }
        block.reinitializeConsumers();
    }
}
