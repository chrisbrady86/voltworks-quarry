package dev.voltworks.quarry.menu;

import dev.voltworks.quarry.VoltworksQuarry;
import dev.voltworks.quarry.block.QuarryBlockEntity;
import dev.voltworks.block.entity.PoweredContainerBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Quarry GUI: energy bar, 3x6 output, three upgrade slots and a status line. */
public class QuarryMenu extends AbstractContainerMenu {
	/** energy, capacity, progress, progressTotal, (unused), status, layer Y, size, mined low, mined high */
	public static final int DATA_COUNT = 10;
	public static final int OUTPUT_X = 36, OUTPUT_Y = 17, UPGRADE_X = 152;
	public static final int INVENTORY_Y = 102;
	private static final int MACHINE_SLOTS = QuarryBlockEntity.OUTPUT_SLOTS + PoweredContainerBlockEntity.UPGRADE_SLOTS;

	private final Container container;
	private final ContainerData data;

	public QuarryMenu(int containerId, Inventory inventory, Container container, ContainerData data) {
		super(VoltworksQuarry.QUARRY_MENU, containerId);
		this.container = container;
		this.data = data;
		checkContainerSize(container, MACHINE_SLOTS);
		checkContainerDataCount(data, DATA_COUNT);
		for (int i = 0; i < QuarryBlockEntity.OUTPUT_SLOTS; i++) {
			addSlot(new OutputSlot(container, i, OUTPUT_X + (i % 6) * 18, OUTPUT_Y + (i / 6) * 18));
		}
		for (int i = 0; i < PoweredContainerBlockEntity.UPGRADE_SLOTS; i++) {
			addSlot(new UpgradeSlot(container, QuarryBlockEntity.OUTPUT_SLOTS + i, UPGRADE_X, OUTPUT_Y + i * 18));
		}
		addStandardInventorySlots(inventory, 8, INVENTORY_Y);
		addDataSlots(data);
	}

	public static QuarryMenu client(int containerId, Inventory inventory) {
		return new QuarryMenu(containerId, inventory, new SimpleContainer(MACHINE_SLOTS), new SimpleContainerData(DATA_COUNT));
	}

	public int getEnergy() {
		return data.get(0);
	}

	public int getCapacity() {
		return Math.max(1, data.get(1));
	}

	public float getEnergyFraction() {
		return Math.min(1f, (float) getEnergy() / getCapacity());
	}

	public float getProgressFraction() {
		int total = data.get(3);
		return total <= 0 ? 0f : Math.min(1f, (float) data.get(2) / total);
	}

	public QuarryBlockEntity.Status getStatus() {
		QuarryBlockEntity.Status[] all = QuarryBlockEntity.Status.values();
		return all[Math.floorMod(data.get(5), all.length)];
	}

	public int getLayerY() {
		return (short) data.get(6); // negative layers survive the short sync
	}

	public int getSize() {
		return data.get(7);
	}

	public int getBlocksMined() {
		return (data.get(8) & 0x7FFF) | (data.get(9) << 15);
	}

	@Override
	public boolean stillValid(Player player) {
		return container.stillValid(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) return ItemStack.EMPTY;
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		int hotbarStart = MACHINE_SLOTS + 27, end = hotbarStart + 9;
		if (index < MACHINE_SLOTS) {
			if (!moveItemStackTo(stack, MACHINE_SLOTS, end, true)) return ItemStack.EMPTY;
		} else if (QuarryBlockEntity.isUpgrade(stack)) {
			boolean moved = false;
			for (int i = QuarryBlockEntity.OUTPUT_SLOTS; i < MACHINE_SLOTS && !stack.isEmpty(); i++) {
				if (!slots.get(i).hasItem()) moved |= moveItemStackTo(stack, i, i + 1, false);
			}
			if (!moved) return ItemStack.EMPTY;
		} else if (index < hotbarStart) {
			if (!moveItemStackTo(stack, hotbarStart, end, false)) return ItemStack.EMPTY;
		} else if (!moveItemStackTo(stack, MACHINE_SLOTS, hotbarStart, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
		else slot.setChanged();
		if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
		slot.onTake(player, stack);
		return original;
	}

	public static class UpgradeSlot extends Slot {
		public UpgradeSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return QuarryBlockEntity.isUpgrade(stack);
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}

		@Override
		public int getMaxStackSize(ItemStack stack) {
			return 1;
		}
	}

	private static class OutputSlot extends Slot {
		OutputSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}
	}
}
