package dev.voltworks.quarry.client.jei;

import dev.voltworks.quarry.VoltworksQuarry;
import dev.voltworks.quarry.client.VoltworksQuarryClient;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * JEI support: an info page for the Quarry and each quarry upgrade, and the render-only items hidden.
 * Only loaded when JEI is installed (fabric.mod.json "jei_mod_plugin" entrypoint).
 */
@JeiPlugin
public class QuarryJeiPlugin implements IModPlugin {
	@Override
	public Identifier getPluginUid() {
		return VoltworksQuarry.id("jei");
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		info(registration, VoltworksQuarry.QUARRY.asItem(), "quarry", 3);
		info(registration, VoltworksQuarry.QUARRY_MARKER.asItem(), "quarry_marker", 2);
		info(registration, VoltworksQuarry.RANGE_UPGRADE, "range_upgrade", 1);
		info(registration, VoltworksQuarry.FORTUNE_UPGRADE, "fortune_upgrade", 1);
		info(registration, VoltworksQuarry.SILK_TOUCH_UPGRADE, "silk_touch_upgrade", 1);
		org.slf4j.LoggerFactory.getLogger(VoltworksQuarry.MOD_ID).info("JEI: registered the quarry info pages");
	}

	/** Lines "jei.voltworks_quarry.info.<name>.1" .. ".<lines>". */
	private static void info(IRecipeRegistration registration, Item item, String name, int lines) {
		Component[] text = new Component[lines];
		for (int i = 0; i < lines; i++) text[i] = Component.translatable("jei." + VoltworksQuarry.MOD_ID + ".info." + name + "." + (i + 1));
		registration.addIngredientInfo(item, text);
	}

	@Override
	public void onRuntimeAvailable(IJeiRuntime runtime) {
		// The drill head and laser only exist to be drawn in the world
		runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK,
				java.util.List.of(VoltworksQuarry.DRILL_HEAD.getDefaultInstance(), VoltworksQuarry.LASER.getDefaultInstance()));
		VoltworksQuarryClient.showRecipes = stack -> runtime.getRecipesGui().show(
				runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack));
	}
}
