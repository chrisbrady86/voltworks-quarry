package dev.voltworks.quarry.client;

import dev.voltworks.client.GuideScreen;
import dev.voltworks.quarry.VoltworksQuarry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.network.chat.Component;

public class VoltworksQuarryClient implements ClientModInitializer {
	/** Opens JEI on how an item is made (its info page is a tab there); set by the JEI plugin when JEI is installed (used by the in-game test). */
	public static java.util.function.Consumer<net.minecraft.world.item.ItemStack> showRecipes = stack -> {
	};

	@Override
	public void onInitializeClient() {
		MenuScreens.register(VoltworksQuarry.QUARRY_MENU, QuarryScreen::new);
		BlockEntityRenderers.register(VoltworksQuarry.QUARRY_ENTITY, QuarryRenderer::new);
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (stack.is(VoltworksQuarry.QUARRY.asItem())) {
				lines.add(Component.translatable("tooltip.voltworks_quarry.quarry").withStyle(ChatFormatting.GRAY));
				lines.add(Component.translatable("tooltip.voltworks_quarry.quarry.power").withStyle(ChatFormatting.DARK_GRAY));
			} else if (stack.is(VoltworksQuarry.QUARRY_MARKER.asItem())) {
				lines.add(Component.translatable("tooltip.voltworks_quarry.quarry_marker").withStyle(ChatFormatting.GRAY));
				lines.add(Component.translatable("tooltip.voltworks_quarry.quarry_marker.limit").withStyle(ChatFormatting.DARK_GRAY));
			}
		});
		// The Voltworks Guide gains the quarry's pages ("guide.voltworks_quarry.page.1", "...2", ...) at the end.
		GuideScreen.addPages("guide.voltworks_quarry.page.");
	}

}
