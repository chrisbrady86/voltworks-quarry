package dev.voltworks.quarry.item;

import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Goes in the Quarry's upgrade slots, next to the regular Voltworks Speed / Capacity / Ejector upgrades. */
public class QuarryUpgradeItem extends Item {
	public enum Type {
		/** Each: the pit is 8 blocks wider (9x9, 17x17, 25x25, 33x33). */
		RANGE,
		/** Each: one level of Fortune (max III), +50% power per block per level. */
		FORTUNE,
		/** Blocks drop themselves; double power per block. Overrides Fortune. */
		SILK_TOUCH
	}

	public final Type type;

	public QuarryUpgradeItem(Properties properties, Type type) {
		super(properties.stacksTo(1));
		this.type = type;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.voltworks_quarry.upgrade." + type.name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("tooltip.voltworks_quarry.upgrade.hint").withStyle(ChatFormatting.DARK_GRAY));
	}
}
