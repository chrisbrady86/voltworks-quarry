package dev.voltworks.quarry.block;

import dev.voltworks.block.MachineBlock;
import dev.voltworks.block.entity.PoweredContainerBlockEntity;
import dev.voltworks.energy.EnergyReceiver;
import dev.voltworks.item.UpgradeItem;
import dev.voltworks.quarry.VoltworksQuarry;
import dev.voltworks.quarry.item.QuarryUpgradeItem;
import dev.voltworks.quarry.menu.QuarryMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Mines an N x N area behind the quarry (N = 9, +8 per Range Upgrade), one block at a time, starting at the
 * quarry's own height and working down layer by layer to the bottom of the world. Unbreakable blocks
 * (bedrock) are skipped, fluids are cleared, and chests in the way are emptied into the output.
 * Mined items go into 18 output slots and are pushed into any adjacent inventory; Voltworks item pipes
 * pull from it like from any other machine. A redstone signal pauses it.
 */
public class QuarryBlockEntity extends PoweredContainerBlockEntity implements EnergyReceiver {
	public static final int OUTPUT_SLOTS = 18;
	public static final int CAPACITY = 20_000;
	/** Power drawn per tick while digging, before upgrades. */
	public static final int ENERGY_PER_TICK = 20;
	public static final int BASE_SIZE = 9;
	public static final int SIZE_PER_RANGE = 8;
	public static final int MAX_RANGE_UPGRADES = 3;
	public static final int MAX_FORTUNE = 3;
	/** Positions checked per tick while looking for the next block (air is skipped this fast). */
	private static final int SCAN_PER_TICK = 512;
	private static final int EJECT_INTERVAL = 10;
	private static final int[] OUTPUTS = java.util.stream.IntStream.range(0, OUTPUT_SLOTS).toArray();

	/** Shown in the GUI. */
	public enum Status {
		MINING, NO_POWER, OUTPUT_FULL, PAUSED, FINISHED, WAITING,
		/** The marked area is wider or longer than the Range Upgrades allow. */
		AREA_TOO_BIG;

		public Component label() {
			return Component.translatable("gui.voltworks_quarry.status." + name().toLowerCase(java.util.Locale.ROOT));
		}
	}

	/** The area this cursor belongs to; a different facing or size restarts from the top. */
	private int areaKey = -1;
	/** Current layer, and the position within it (row-major, snaking). */
	private int layerY;
	private int layerIndex;
	private @Nullable BlockPos target;
	private int blocksMined;
	private Status status = Status.MINING;
	/** Drops that didn't fit in the output; nothing more is mined until they are out. */
	private final List<ItemStack> overflow = new ArrayList<>();
	private int ejectTimer;
	private @Nullable ItemStack tool;
	private int toolKey = -1;
	/** Area set with Quarry Markers, {minX, minZ, maxX, maxZ}; null = the default square behind the quarry. */
	private int @Nullable [] markedArea;
	private int markerScanTimer;
	/** How often to look for a marker touching the quarry. */
	private static final int MARKER_SCAN_INTERVAL = 40;

	/** Synced to the GUI: the base machine values plus status, layer, size and blocks mined (split into two shorts). */
	private final ContainerData quarryData = new ContainerData() {
		@Override
		public int get(int id) {
			return switch (id) {
				case 5 -> status.ordinal();
				case 6 -> layerY;
				case 7 -> areaWidth();
				case 10 -> areaLength();
				case 8 -> blocksMined & 0x7FFF;
				case 9 -> blocksMined >>> 15;
				default -> data.get(id);
			};
		}

		@Override
		public void set(int id, int value) {
			if (id < DATA_COUNT) data.set(id, value);
		}

		@Override
		public int getCount() {
			return QuarryMenu.DATA_COUNT;
		}
	};

	public QuarryBlockEntity(BlockPos pos, BlockState state) {
		super(VoltworksQuarry.QUARRY_ENTITY, pos, state, OUTPUT_SLOTS, CAPACITY, true);
		this.layerY = pos.getY();
	}

	// ---------------- upgrades ----------------

	public int countQuarryUpgrades(QuarryUpgradeItem.Type type) {
		int count = 0;
		for (int i = OUTPUT_SLOTS; i < items.size(); i++) {
			if (items.get(i).getItem() instanceof QuarryUpgradeItem u && u.type == type) count += items.get(i).getCount();
		}
		return count;
	}

	/** The biggest width and length the pit may have: 9, +8 per Range Upgrade. */
	public int getSize() {
		return BASE_SIZE + SIZE_PER_RANGE * Math.min(MAX_RANGE_UPGRADES, countQuarryUpgrades(QuarryUpgradeItem.Type.RANGE));
	}

	private boolean silkTouch() {
		return countQuarryUpgrades(QuarryUpgradeItem.Type.SILK_TOUCH) > 0;
	}

	private int fortune() {
		return silkTouch() ? 0 : Math.min(MAX_FORTUNE, countQuarryUpgrades(QuarryUpgradeItem.Type.FORTUNE));
	}

	/** Power per tick while digging: Speed Upgrades as on every machine, then +50% per Fortune level, x2 for Silk Touch. */
	public int energyPerTick() {
		double factor = silkTouch() ? 2.0 : 1.0 + 0.5 * fortune();
		return (int) Math.ceil(upgradedEnergyPerTick(ENERGY_PER_TICK) * factor);
	}

	/** Harder blocks take longer: dirt 5 ticks, stone 8, deepslate 12, obsidian 153 (before Speed Upgrades). */
	public int ticksFor(BlockState state, BlockPos pos) {
		if (state.getBlock() instanceof LiquidBlock) return 1;
		float hardness = state.getDestroySpeed(level, pos);
		return upgradedTicks(Math.max(2, Math.round(3 + 3 * hardness)));
	}

	/** The pickaxe the quarry "mines" with: decides drops (Fortune, Silk Touch, tool tiers). */
	private ItemStack tool(ServerLevel level) {
		int key = silkTouch() ? 100 : fortune();
		if (tool == null || key != toolKey) {
			ItemStack pick = new ItemStack(Items.NETHERITE_PICKAXE);
			var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
			if (silkTouch()) pick.enchant(enchantments.getOrThrow(Enchantments.SILK_TOUCH), 1);
			else if (fortune() > 0) pick.enchant(enchantments.getOrThrow(Enchantments.FORTUNE), fortune());
			tool = pick;
			toolKey = key;
		}
		return tool;
	}

	// ---------------- area ----------------

	/** The pit starts right behind the quarry and is centred on it sideways. */
	private Direction back() {
		BlockState state = getBlockState();
		return state.hasProperty(MachineBlock.FACING) ? state.getValue(MachineBlock.FACING).getOpposite() : Direction.SOUTH;
	}

	/** Position {@code index} (0 .. size²-1) of a layer. Rows go away from the quarry and snake back and forth. */
	private BlockPos positionAt(int index, int y) {
		if (markedArea != null) {
			// Marked area: rows run along X, one after another along Z, snaking back and forth
			int w = areaWidth(), row = index / w, col = index % w;
			if (row % 2 == 1) col = w - 1 - col;
			return new BlockPos(markedArea[0] + col, y, markedArea[1] + row);
		}
		int size = getSize(), half = size / 2;
		int row = index / size, col = index % size;
		if (row % 2 == 1) col = size - 1 - col;
		Direction back = back(), side = back.getClockWise();
		return new BlockPos(worldPosition.getX() + back.getStepX() * (row + 1) + side.getStepX() * (col - half), y,
				worldPosition.getZ() + back.getStepZ() * (row + 1) + side.getStepZ() * (col - half));
	}

	/** Corners of the area (inclusive), for the renderer and tests: {minX, minZ, maxX, maxZ}. */
	public int[] areaBounds() {
		if (markedArea != null) return markedArea.clone();
		BlockPos a = positionAt(0, 0), b = positionAt(getSize() * getSize() - 1, 0);
		BlockPos c = positionAt(getSize() - 1, 0);
		return new int[]{Math.min(Math.min(a.getX(), b.getX()), c.getX()), Math.min(Math.min(a.getZ(), b.getZ()), c.getZ()),
				Math.max(Math.max(a.getX(), b.getX()), c.getX()), Math.max(Math.max(a.getZ(), b.getZ()), c.getZ())};
	}

	/** Width (X) of the pit: the marked area, or the default square. */
	public int areaWidth() {
		return markedArea != null ? markedArea[2] - markedArea[0] + 1 : getSize();
	}

	/** Length (Z) of the pit. */
	public int areaLength() {
		return markedArea != null ? markedArea[3] - markedArea[1] + 1 : getSize();
	}

	public boolean hasMarkedArea() {
		return markedArea != null;
	}

	private int cellsPerLayer() {
		return areaWidth() * areaLength();
	}

	/**
	 * A marker touching the quarry, paired with another marker in range, sets the area: the rectangle between them.
	 * Both markers are picked up into the output.
	 */
	private void linkMarkers(ServerLevel level, BlockPos pos) {
		for (Direction dir : Direction.values()) {
			BlockPos marker = pos.relative(dir);
			if (!level.getBlockState(marker).is(VoltworksQuarry.QUARRY_MARKER)) continue;
			BlockPos partner = QuarryMarkerBlock.findPartner(level, marker, getSize());
			if (partner == null) continue;
			markedArea = QuarryMarkerBlock.rectangle(marker, partner);
			for (BlockPos p : List.of(marker, partner)) {
				level.removeBlock(p, false);
				ItemStack rest = insert(new ItemStack(VoltworksQuarry.QUARRY_MARKER));
				if (!rest.isEmpty()) overflow.add(rest);
			}
			level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 1.6F);
			setChanged();
			return;
		}
	}

	private boolean shouldMine(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir() || pos.equals(worldPosition)) return false; // a marked area may run under the quarry itself
		if (state.getBlock() instanceof LiquidBlock) return true; // cleared, so the pit doesn't flood
		return state.getDestroySpeed(level, pos) >= 0; // bedrock, barriers, end portal frames...
	}

	// ---------------- ticking ----------------

	@Override
	public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
		if (energy > getCapacity()) energy = getCapacity();
		if (++ejectTimer >= EJECT_INTERVAL) {
			ejectTimer = 0;
			eject(level, pos);
		}
		Status before = status;
		BlockPos targetBefore = target;
		status = work(level, pos);
		boolean working = status == Status.MINING && target != null;
		setActive(level, pos, state, working);
		if (status != before || !java.util.Objects.equals(target, targetBefore)) {
			setChanged();
			level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS); // the renderer follows the target
		}
	}

	private Status work(ServerLevel level, BlockPos pos) {
		if (!overflow.isEmpty()) {
			overflow.replaceAll(this::insert);
			overflow.removeIf(ItemStack::isEmpty);
			if (!overflow.isEmpty()) return Status.OUTPUT_FULL;
		}
		if (--markerScanTimer <= 0) {
			markerScanTimer = MARKER_SCAN_INTERVAL;
			linkMarkers(level, pos);
		}
		if (markedArea != null && (areaWidth() > getSize() || areaLength() > getSize())) {
			clearTarget(level);
			return Status.AREA_TOO_BIG;
		}
		if (level.hasNeighborSignal(pos)) {
			clearTarget(level);
			return Status.PAUSED;
		}
		// Default square: as before, so quarries placed earlier keep their place. Marked area: its corners.
		int key = markedArea != null ? 0x10000 + (java.util.Arrays.hashCode(markedArea) & 0x7FFEFFFF) : back().ordinal() * 1000 + getSize();
		if (key != areaKey) {
			// New pit (first run, turned, a Range Upgrade changed or a new marked area): start again from the top. Dug-out parts are
			// just air, which is skipped quickly.
			clearTarget(level);
			areaKey = key;
			layerY = pos.getY();
			layerIndex = 0;
			progress = 0;
		}
		if (layerY < level.getMinY()) return Status.FINISHED;

		if (target == null && !findTarget(level)) {
			return layerY < level.getMinY() ? Status.FINISHED : level.isLoaded(positionAt(layerIndex, layerY)) ? Status.MINING : Status.WAITING;
		}
		BlockState state = level.getBlockState(target);
		if (!shouldMine(level, target, state)) { // changed since we picked it
			clearTarget(level);
			return Status.MINING;
		}
		int ticks = ticksFor(state, target);
		int cost = energyPerTick();
		progressTotal = ticks;
		if (energy < cost) return Status.NO_POWER;
		energy -= cost;
		if (++progress < ticks) {
			level.destroyBlockProgress(breakerId(), target, progress * 10 / ticks);
			return Status.MINING;
		}
		mine(level, target, state);
		clearTarget(level);
		advance();
		blocksMined++;
		return Status.MINING;
	}

	/** Moves the cursor forward until it finds a block worth mining. */
	private boolean findTarget(ServerLevel level) {
		for (int i = 0; i < SCAN_PER_TICK && layerY >= level.getMinY(); i++) {
			BlockPos p = positionAt(layerIndex, layerY);
			if (!level.isLoaded(p)) return false;
			if (shouldMine(level, p, level.getBlockState(p))) {
				target = p;
				progress = 0;
				return true;
			}
			advance();
		}
		return false;
	}

	private void advance() {
		if (++layerIndex >= cellsPerLayer()) {
			layerIndex = 0;
			layerY--;
		}
	}

	private int breakerId() {
		return Long.hashCode(worldPosition.asLong()) | 0x40000000;
	}

	private void clearTarget(ServerLevel level) {
		if (target != null) {
			level.destroyBlockProgress(breakerId(), target, -1);
			target = null;
		}
		progress = 0;
	}

	private void mine(ServerLevel level, BlockPos at, BlockState state) {
		if (state.getBlock() instanceof LiquidBlock) {
			level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			return;
		}
		BlockEntity be = level.getBlockEntity(at);
		List<ItemStack> drops = new ArrayList<>();
		if (be instanceof Container container) {
			// Chests in the way: their contents go into the output instead of spilling into the pit
			for (int i = 0; i < container.getContainerSize(); i++) {
				if (!container.getItem(i).isEmpty()) drops.add(container.getItem(i).copy());
			}
			container.clearContent();
		}
		drops.addAll(Block.getDrops(state, level, at, be, null, tool(level)));
		level.levelEvent(2001, at, Block.getId(state)); // break particles and sound
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0.05);
		level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		for (ItemStack drop : drops) {
			ItemStack rest = insert(drop);
			if (!rest.isEmpty()) overflow.add(rest);
		}
	}

	/** Puts a stack into the output slots, merging first. Returns what didn't fit. */
	private ItemStack insert(ItemStack stack) {
		for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
			for (int i = 0; i < OUTPUT_SLOTS && !stack.isEmpty(); i++) {
				ItemStack slot = items.get(i);
				if (pass == 0 && !slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack)) {
					int move = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
					slot.grow(move);
					stack.shrink(move);
				} else if (pass == 1 && slot.isEmpty()) {
					items.set(i, stack.copy());
					stack = ItemStack.EMPTY;
				}
			}
		}
		setChanged();
		return stack;
	}

	/** Pushes the output into adjacent inventories on sides set to Output (all sides by default). No Ejector needed. */
	private void eject(ServerLevel level, BlockPos pos) {
		for (Direction dir : Direction.values()) {
			if (!getSideMode(dir).output) continue;
			Container target = HopperBlockEntity.getContainerAt(level, pos.relative(dir));
			if (target == null || target == this) continue;
			for (int slot : OUTPUTS) {
				ItemStack stack = items.get(slot);
				if (stack.isEmpty()) continue;
				ItemStack rest = HopperBlockEntity.addItem(this, target, stack.copy(), dir.getOpposite());
				if (rest.getCount() != stack.getCount()) {
					items.set(slot, rest);
					setChanged();
				}
			}
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);
		if (level != null) {
			for (ItemStack stack : overflow) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
			overflow.clear();
			if (level instanceof ServerLevel server) clearTarget(server);
		}
	}

	// ---------------- accessors (GUI, renderer, tests) ----------------

	public Status getStatus() {
		return status;
	}

	public @Nullable BlockPos getTarget() {
		return target;
	}

	public int getBlocksMined() {
		return blocksMined;
	}

	public int getLayerY() {
		return layerY;
	}

	// ---------------- machine plumbing ----------------

	@Override
	public int receiveEnergy(int maxAmount, boolean simulate) {
		return acceptEnergy(maxAmount, simulate);
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return isUpgradeSlot(slot) && isUpgrade(stack);
	}

	public static boolean isUpgrade(ItemStack stack) {
		return stack.getItem() instanceof UpgradeItem || stack.getItem() instanceof QuarryUpgradeItem;
	}

	@Override
	protected int[] inputSlots() {
		return new int[0];
	}

	@Override
	protected int[] outputSlots() {
		return OUTPUTS;
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.voltworks_quarry.quarry");
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return new QuarryMenu(containerId, inventory, this, quarryData);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		areaKey = input.getIntOr("area_key", -1);
		layerY = input.getIntOr("layer_y", worldPosition.getY());
		layerIndex = input.getIntOr("layer_index", 0);
		blocksMined = input.getIntOr("blocks_mined", 0);
		status = Status.values()[Math.floorMod(input.getIntOr("status", 0), Status.values().length)];
		target = input.getBooleanOr("has_target", false)
				? new BlockPos(input.getIntOr("target_x", 0), input.getIntOr("target_y", 0), input.getIntOr("target_z", 0)) : null;
		markedArea = input.getIntArray("marked_area").filter(a -> a.length == 4).orElse(null);
		overflow.clear();
		input.listOrEmpty("overflow", ItemStack.CODEC).forEach(overflow::add);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("area_key", areaKey);
		output.putInt("layer_y", layerY);
		output.putInt("layer_index", layerIndex);
		output.putInt("blocks_mined", blocksMined);
		output.putInt("status", status.ordinal());
		if (markedArea != null) output.putIntArray("marked_area", markedArea);
		writeTarget(output);
		var list = output.list("overflow", ItemStack.CODEC);
		overflow.forEach(list::add);
	}

	private void writeTarget(ValueOutput output) {
		output.putBoolean("has_target", target != null);
		if (target != null) {
			output.putInt("target_x", target.getX());
			output.putInt("target_y", target.getY());
			output.putInt("target_z", target.getZ());
		}
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	/** The client only needs what the renderer draws: the block being dug and the status. */
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("status", status.ordinal());
		tag.putInt("area_key", areaKey);
		tag.putBoolean("has_target", target != null);
		if (target != null) {
			tag.putInt("target_x", target.getX());
			tag.putInt("target_y", target.getY());
			tag.putInt("target_z", target.getZ());
		}
		return tag;
	}
}
