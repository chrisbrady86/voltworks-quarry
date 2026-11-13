package dev.voltworks.quarry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.voltworks.quarry.VoltworksQuarry;
import dev.voltworks.quarry.block.QuarryBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * While the quarry digs, a spinning drill head hovers over the block being mined, hanging from a laser that
 * comes down from just above the quarry's height.
 */
public class QuarryRenderer implements BlockEntityRenderer<QuarryBlockEntity, QuarryRenderer.State> {
	private static final int FULL_BRIGHT = 0xF000F0;

	public static class State extends BlockEntityRenderState {
		boolean active;
		float dx, dy, dz, beamLength, spin, yaw;
		final ItemStackRenderState head = new ItemStackRenderState();
		final ItemStackRenderState beam = new ItemStackRenderState();
	}

	private final ItemModelResolver itemModelResolver;
	/** Made on first use: renderers are built before item components are bound. */
	private @Nullable ItemStack headStack, beamStack;

	public QuarryRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModelResolver = context.itemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(QuarryBlockEntity quarry, State state, float partialTick, Vec3 cameraPos,
			ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
		BlockEntityRenderer.super.extractRenderState(quarry, state, partialTick, cameraPos, crumbling);
		BlockPos target = quarry.getTarget();
		state.active = target != null && quarry.getStatus() == QuarryBlockEntity.Status.MINING && quarry.getLevel() != null;
		if (!state.active) return;
		BlockPos origin = quarry.getBlockPos();
		// Head sits just above the block it digs (inside the block it would be hidden)
		state.dx = target.getX() - origin.getX() + 0.5F;
		state.dz = target.getZ() - origin.getZ() + 0.5F;
		state.dy = target.getY() - origin.getY() + 1.35F;
		float top = 1.6F;
		state.beamLength = Math.max(0F, top - state.dy - 0.3F);
		double time = quarry.getLevel().getGameTime() + partialTick;
		state.spin = (float) ((time * 24) % 360);
		state.yaw = (float) Math.toDegrees(Math.atan2(cameraPos.x - (origin.getX() + state.dx), cameraPos.z - (origin.getZ() + state.dz)));
		int seed = (int) origin.asLong();
		if (headStack == null) {
			headStack = new ItemStack(VoltworksQuarry.DRILL_HEAD);
			beamStack = new ItemStack(VoltworksQuarry.LASER);
		}
		itemModelResolver.updateForTopItem(state.head, headStack, ItemDisplayContext.FIXED, quarry.getLevel(), null, seed);
		itemModelResolver.updateForTopItem(state.beam, beamStack, ItemDisplayContext.FIXED, quarry.getLevel(), null, seed + 1);
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.active) return;
		// Laser: one stretched sprite, turned to face the camera
		if (state.beamLength > 0.05F && !state.beam.isEmpty()) {
			pose.pushPose();
			pose.translate(state.dx, state.dy + 0.3F + state.beamLength / 2, state.dz);
			pose.rotateDegrees(Axis.YP, state.yaw);
			pose.scale(1F, state.beamLength, 1F);
			state.beam.submit(pose, collector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
			pose.popPose();
		}
		// Drill head: two crossed copies spinning around the vertical axis, so it reads as a solid bit from any side
		if (!state.head.isEmpty()) {
			for (int i = 0; i < 2; i++) {
				pose.pushPose();
				pose.translate(state.dx, state.dy, state.dz);
				pose.rotateDegrees(Axis.YP, state.spin + i * 90);
				pose.scale(0.75F, 0.75F, 0.75F);
				state.head.submit(pose, collector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
				pose.popPose();
			}
		}
	}

	/** The head can be far from the quarry block (33x33 pit, all the way down), so never cull by the quarry itself. */
	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 256;
	}
}
