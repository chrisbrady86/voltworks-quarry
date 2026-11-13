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
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The quarry's gantry, drawn while it works its pit: a steel frame round the edge of the area on four corner posts,
 * a bridge across it that follows the row being dug, a carriage on the bridge, and a shaft down to a spinning drill
 * head sitting on the block being mined. The head glides from block to block rather than jumping.
 *
 * <p>All positions are relative to the quarry block. Every part is a small 3D item model ({@code gantry_beam},
 * {@code gantry_joint}, {@code gantry_carriage}, {@code drill_shaft}, {@code drill_head}).
 */
public class QuarryRenderer implements BlockEntityRenderer<QuarryBlockEntity, QuarryRenderer.State> {
	/** Height of the frame's beams above the quarry's base. */
	private static final float FRAME_Y = 2.5F;
	/** The bridge rides on top of the frame. */
	private static final float BRIDGE_Y = FRAME_Y + 0.25F;
	/** Bottom of the carriage's sleeve, where the shaft comes out; the drill head's height from tip to top. */
	private static final float SLEEVE_BOTTOM = BRIDGE_Y - 0.875F, HEAD_HEIGHT = 15 / 16F;
	/** Drill head glide speed, blocks per tick. */
	private static final double SPEED_SIDEWAYS = 0.35, SPEED_DOWN = 0.9;

	public static class State extends BlockEntityRenderState {
		boolean visible, spinning;
		float x0, z0, x1, z1;
		float headX, headY, headZ, spin;
		int frameLight, headLight;
		final ItemStackRenderState beam = new ItemStackRenderState();
		final ItemStackRenderState joint = new ItemStackRenderState();
		final ItemStackRenderState carriage = new ItemStackRenderState();
		final ItemStackRenderState shaft = new ItemStackRenderState();
		final ItemStackRenderState head = new ItemStackRenderState();
	}

	private final ItemModelResolver itemModelResolver;
	/** Made on first use: renderers are built before item components are bound. */
	private @Nullable ItemStack beamStack, jointStack, carriageStack, shaftStack, headStack;

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
		Level level = quarry.getLevel();
		int[] bounds = quarry.getSyncedBounds();
		QuarryBlockEntity.Status status = quarry.getStatus();
		state.visible = level != null && bounds != null
				&& status != QuarryBlockEntity.Status.FINISHED && status != QuarryBlockEntity.Status.AREA_TOO_BIG;
		if (!state.visible) {
			quarry.gantryX = Double.NaN;
			return;
		}
		BlockPos origin = quarry.getBlockPos();
		// The frame runs along the middle of the ring of blocks just outside the area
		state.x0 = bounds[0] - origin.getX() - 0.5F;
		state.z0 = bounds[1] - origin.getZ() - 0.5F;
		state.x1 = bounds[2] - origin.getX() + 1.5F;
		state.z1 = bounds[3] - origin.getZ() + 1.5F;

		// Where the head wants to be: on top of the block being dug, or tucked up under the carriage
		BlockPos target = quarry.getTarget();
		double time = level.getGameTime() + partialTick;
		double parkY = SLEEVE_BOTTOM - HEAD_HEIGHT;
		double wantX, wantY, wantZ;
		if (target != null) {
			wantX = target.getX() - origin.getX() + 0.5;
			wantY = target.getY() - origin.getY() + 1.0;
			wantZ = target.getZ() - origin.getZ() + 0.5;
		} else {
			wantX = Double.isNaN(quarry.gantryX) ? (state.x0 + state.x1) / 2 : quarry.gantryX;
			wantZ = Double.isNaN(quarry.gantryX) ? (state.z0 + state.z1) / 2 : quarry.gantryZ;
			wantY = parkY;
		}
		wantX = Math.clamp(wantX, state.x0 + 0.5, state.x1 - 0.5);
		wantZ = Math.clamp(wantZ, state.z0 + 0.5, state.z1 - 0.5);
		wantY = Math.min(wantY, parkY);
		if (Double.isNaN(quarry.gantryX) || Math.abs(wantX - quarry.gantryX) + Math.abs(wantZ - quarry.gantryZ) > 80) {
			quarry.gantryX = wantX;
			quarry.gantryY = wantY;
			quarry.gantryZ = wantZ;
		} else {
			double dt = Math.clamp(time - quarry.gantryTime, 0, 10);
			double dx = wantX - quarry.gantryX, dz = wantZ - quarry.gantryZ, flat = Math.sqrt(dx * dx + dz * dz);
			double step = SPEED_SIDEWAYS * dt;
			if (flat <= step) {
				quarry.gantryX = wantX;
				quarry.gantryZ = wantZ;
			} else {
				quarry.gantryX += dx / flat * step;
				quarry.gantryZ += dz / flat * step;
			}
			// While it travels to a far block, the head stays at its height; it only goes down once it is nearly there
			double goalY = flat > 1.5 ? Math.max(wantY, Math.min(parkY, quarry.gantryY)) : wantY;
			double dy = goalY - quarry.gantryY, stepY = SPEED_DOWN * dt;
			quarry.gantryY = Math.abs(dy) <= stepY ? goalY : quarry.gantryY + Math.signum(dy) * stepY;
		}
		quarry.gantryTime = time;
		state.headX = (float) quarry.gantryX;
		state.headY = (float) quarry.gantryY;
		state.headZ = (float) quarry.gantryZ;
		state.spinning = target != null && status == QuarryBlockEntity.Status.MINING;
		state.spin = state.spinning ? (float) ((time * 30) % 360) : 0;

		// Light where the parts are, so the gantry darkens at night and down in the pit
		state.frameLight = LightCoordsUtil.getLightCoords(level, origin.above(2));
		state.headLight = LightCoordsUtil.getLightCoords(level,
				BlockPos.containing(origin.getX() + state.headX, origin.getY() + state.headY + 0.5, origin.getZ() + state.headZ));

		if (headStack == null) {
			beamStack = new ItemStack(VoltworksQuarry.GANTRY_BEAM);
			jointStack = new ItemStack(VoltworksQuarry.GANTRY_JOINT);
			carriageStack = new ItemStack(VoltworksQuarry.GANTRY_CARRIAGE);
			shaftStack = new ItemStack(VoltworksQuarry.DRILL_SHAFT);
			headStack = new ItemStack(VoltworksQuarry.DRILL_HEAD);
		}
		int seed = (int) origin.asLong();
		resolve(state.beam, beamStack, level, seed);
		resolve(state.joint, jointStack, level, seed);
		resolve(state.carriage, carriageStack, level, seed);
		resolve(state.shaft, shaftStack, level, seed);
		resolve(state.head, headStack, level, seed);
	}

	private void resolve(ItemStackRenderState into, ItemStack stack, Level level, int seed) {
		itemModelResolver.updateForTopItem(into, stack, ItemDisplayContext.FIXED, level, null, seed);
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.visible) return;
		int fl = state.frameLight;
		// Frame: four beams round the area, a joint at each corner, and posts down to the quarry's level
		beamX(state, pose, collector, state.x0, state.x1, FRAME_Y, state.z0, fl);
		beamX(state, pose, collector, state.x0, state.x1, FRAME_Y, state.z1, fl);
		beamZ(state, pose, collector, state.x0, FRAME_Y, state.z0, state.z1, fl);
		beamZ(state, pose, collector, state.x1, FRAME_Y, state.z0, state.z1, fl);
		for (float x : new float[]{state.x0, state.x1}) {
			for (float z : new float[]{state.z0, state.z1}) {
				run(state, pose, collector, x, 0, z, x, FRAME_Y, z, fl);
				part(state.joint, pose, collector, x, FRAME_Y, z, fl);
			}
		}
		// Bridge across the area at the head's row, riding on the frame, with a joint at each end
		beamX(state, pose, collector, state.x0, state.x1, BRIDGE_Y, state.headZ, fl);
		part(state.joint, pose, collector, state.x0, BRIDGE_Y, state.headZ, fl);
		part(state.joint, pose, collector, state.x1, BRIDGE_Y, state.headZ, fl);
		part(state.carriage, pose, collector, state.headX, BRIDGE_Y, state.headZ, fl);

		// Shaft from the carriage's sleeve down to the top of the drill head, stretched to fit
		float headTop = state.headY + HEAD_HEIGHT;
		float length = SLEEVE_BOTTOM + 0.1F - headTop;
		if (length > 0.02F && !state.shaft.isEmpty()) {
			pose.pushPose();
			pose.translate(state.headX, headTop + length / 2, state.headZ);
			pose.scale(1F, length, 1F);
			state.shaft.submit(pose, collector, state.frameLight, OverlayTexture.NO_OVERLAY, 0);
			pose.popPose();
		}
		// Drill head: tip on the block being dug, spinning while it works
		if (!state.head.isEmpty()) {
			pose.pushPose();
			pose.translate(state.headX, state.headY + 0.5F, state.headZ);
			pose.rotateDegrees(Axis.YP, state.spin);
			state.head.submit(pose, collector, state.headLight, OverlayTexture.NO_OVERLAY, 0);
			pose.popPose();
		}
	}

	private static void part(ItemStackRenderState part, PoseStack pose, SubmitNodeCollector collector, float x, float y, float z, int light) {
		if (part.isEmpty()) return;
		pose.pushPose();
		pose.translate(x, y, z);
		part.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
		pose.popPose();
	}

	private static void beamX(State s, PoseStack pose, SubmitNodeCollector c, float xFrom, float xTo, float y, float z, int light) {
		run(s, pose, c, xFrom, y, z, xTo, y, z, light);
	}

	private static void beamZ(State s, PoseStack pose, SubmitNodeCollector c, float x, float y, float zFrom, float zTo, int light) {
		run(s, pose, c, x, y, zFrom, x, y, zTo, light);
	}

	/**
	 * A straight girder between two points along one axis, built from block-long segments (stretched or squeezed a
	 * little so they fit exactly), so the rivets stay one per block however long the beam is.
	 */
	private static void run(State s, PoseStack pose, SubmitNodeCollector c, float ax, float ay, float az, float bx, float by, float bz, int light) {
		if (s.beam.isEmpty()) return;
		float dx = bx - ax, dy = by - ay, dz = bz - az;
		float length = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
		if (length < 0.01F) return;
		int count = Math.max(1, Math.round(length));
		float segment = length / count;
		for (int i = 0; i < count; i++) {
			float t = (i + 0.5F) / count;
			pose.pushPose();
			pose.translate(ax + dx * t, ay + dy * t, az + dz * t);
			if (dx != 0) pose.rotateDegrees(Axis.YP, 90);
			else if (dy != 0) pose.rotateDegrees(Axis.XP, 90);
			pose.scale(1F, 1F, segment); // the model lies along Z
			s.beam.submit(pose, c, light, OverlayTexture.NO_OVERLAY, 0);
			pose.popPose();
		}
	}

	/** The gantry can reach far past the quarry block (33x33, all the way down), so never cull by the quarry itself. */
	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 256;
	}
}
