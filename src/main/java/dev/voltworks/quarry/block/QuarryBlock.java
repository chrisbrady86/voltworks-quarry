package dev.voltworks.quarry.block;

import dev.voltworks.block.MachineBlock;
import dev.voltworks.item.WrenchItem;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Quarry: a Voltworks machine that digs out the square area behind it (opposite its front), layer by layer,
 * from its own height down to the bottom of the world. All Voltworks machine behaviour (cables, gauge, wrench
 * rotation and side configuration, item pipes) comes from {@link MachineBlock}.
 */
public class QuarryBlock extends MachineBlock {
	public QuarryBlock(Supplier<BlockEntityType<?>> type, Properties properties) {
		super(type, properties);
	}

	/**
	 * Sneak + right-click with the Voltworks wrench picks the quarry up. (The wrench itself only picks up blocks from
	 * the "voltworks" namespace.) Its inventory drops as usual when the block is removed.
	 */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		if (stack.getItem() instanceof WrenchItem && player.isSecondaryUseActive()) {
			if (!level.isClientSide()) {
				ItemStack drop = new ItemStack(asItem());
				level.destroyBlock(pos, false, player, 512);
				if (!player.getInventory().add(drop)) {
					Block.popResource(level, pos, drop);
				}
			}
			return InteractionResult.SUCCESS;
		}
		return super.useItemOn(stack, state, level, pos, player, hand, hit);
	}
}
