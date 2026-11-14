package dev.voltworks.quarry.block;

import dev.voltworks.block.ItemPipeBlock;
import dev.voltworks.block.MachineBlock;
import dev.voltworks.item.WrenchItem;
import dev.voltworks.quarry.VoltworksQuarry;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
	 * Sneak + right-click with the Voltworks wrench picks the quarry up; its inventory drops as usual when the block
	 * is removed. The wrench itself only picks up blocks from the "voltworks" namespace, and a sneaking player's click
	 * never reaches the block's own use methods, so this runs from Fabric's block-use event
	 * (registered in {@link VoltworksQuarry#onInitialize}).
	 */
	public static InteractionResult wrenchPickUp(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		if (player.isSpectator() || !player.isSecondaryUseActive() || !(player.getItemInHand(hand).getItem() instanceof WrenchItem)
				|| !level.getBlockState(pos).is(VoltworksQuarry.QUARRY)) {
			return InteractionResult.PASS;
		}
		// The face a pipe plugs into: leave it to the wrench, which toggles that arm's Extract (as on every machine)
		Direction face = hit.getDirection();
		BlockState pipe = level.getBlockState(pos.relative(face));
		if (pipe.getBlock() instanceof ItemPipeBlock && ItemPipeBlock.get(pipe, face.getOpposite()).isOpen()) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			ItemStack drop = new ItemStack(VoltworksQuarry.QUARRY);
			level.destroyBlock(pos, false, player, 512);
			if (!player.getInventory().add(drop)) {
				Block.popResource(level, pos, drop);
			}
		}
		return InteractionResult.SUCCESS;
	}
}
