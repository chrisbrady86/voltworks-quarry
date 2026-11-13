package dev.voltworks.quarry.client;

import dev.voltworks.quarry.block.QuarryBlockEntity;
import dev.voltworks.quarry.menu.QuarryMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Drawn from flat rectangles in the same style as the Voltworks machine screens. */
public class QuarryScreen extends AbstractContainerScreen<QuarryMenu> {
	private static final int PANEL = 0xFFC6C6C6, LIGHT = 0xFFFFFFFF, SHADOW = 0xFF555555, SLOT_BG = 0xFF8B8B8B, DARK = 0xFF373737;
	private static final int ENERGY = 0xFFE03C28, ENERGY_TOP = 0xFFFF8A5C, PROGRESS = 0xFF4FC34F;
	private static final int BAR_X = 12, BAR_Y = 17, BAR_W = 10, BAR_H = 52;
	private static final int INFO_Y = 76;

	public QuarryScreen(QuarryMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 184);
		this.inventoryLabelY = QuarryMenu.INVENTORY_Y - 11;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(g, mouseX, mouseY, partialTick);
		int x = leftPos, y = topPos;
		g.fill(x, y, x + imageWidth, y + imageHeight, PANEL);
		g.fill(x, y, x + imageWidth - 1, y + 1, LIGHT);
		g.fill(x, y, x + 1, y + imageHeight - 1, LIGHT);
		g.fill(x + 1, y + imageHeight - 1, x + imageWidth, y + imageHeight, SHADOW);
		g.fill(x + imageWidth - 1, y + 1, x + imageWidth, y + imageHeight, SHADOW);

		for (Slot slot : menu.slots) {
			int sx = x + slot.x, sy = y + slot.y;
			g.fill(sx - 1, sy - 1, sx + 17, sy + 17, DARK);
			g.fill(sx, sy, sx + 17, sy + 17, LIGHT);
			g.fill(sx, sy, sx + 16, sy + 16, slot instanceof QuarryMenu.UpgradeSlot ? 0xFF7A8AA0 : SLOT_BG);
		}

		// Energy bar
		g.fill(x + BAR_X - 1, y + BAR_Y - 1, x + BAR_X + BAR_W + 1, y + BAR_Y + BAR_H + 1, DARK);
		g.fill(x + BAR_X, y + BAR_Y, x + BAR_X + BAR_W, y + BAR_Y + BAR_H, 0xFF1E1E1E);
		int filled = Math.round(menu.getEnergyFraction() * BAR_H);
		if (filled > 0) {
			int top = y + BAR_Y + BAR_H - filled;
			g.fill(x + BAR_X, top, x + BAR_X + BAR_W, y + BAR_Y + BAR_H, ENERGY);
			g.fill(x + BAR_X, top, x + BAR_X + BAR_W, top + 1, ENERGY_TOP);
		}

		// Progress on the current block, under the output grid
		int px = x + QuarryMenu.OUTPUT_X - 1, pw = 6 * 18, py = y + 71;
		g.fill(px, py, px + pw, py + 3, DARK);
		int w = menu.getStatus() == QuarryBlockEntity.Status.MINING ? Math.round(menu.getProgressFraction() * pw) : 0;
		if (w > 0) g.fill(px, py, px + w, py + 3, PROGRESS);

		// Status (coloured) and where the quarry is
		QuarryBlockEntity.Status status = menu.getStatus();
		int color = switch (status) {
			case MINING -> 0xFF2A7A2A;
			case FINISHED -> 0xFF2A4A9A;
			case PAUSED, WAITING -> 0xFF8A6A10;
			default -> 0xFFB02020;
		};
		// Status on the title row (right), details under the output grid: long statuses never run into the details
		g.text(font, status.label(), x + imageWidth - 8 - font.width(status.label()), y + 6, color, false);
		Component info = switch (status) {
			case FINISHED -> Component.translatable("gui.voltworks_quarry.info_done", menu.getWidth(), menu.getLength(), menu.getBlocksMined());
			case AREA_TOO_BIG -> Component.translatable("gui.voltworks_quarry.info_too_big", menu.getWidth(), menu.getLength());
			default -> Component.translatable("gui.voltworks_quarry.info", menu.getLayerY(), menu.getWidth(), menu.getLength(), menu.getBlocksMined());
		};
		g.text(font, info, x + QuarryMenu.OUTPUT_X - 1, y + INFO_Y + 3, 0xFF404040, false);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		int bx = leftPos + BAR_X, by = topPos + BAR_Y;
		if (mouseX >= bx && mouseX < bx + BAR_W && mouseY >= by && mouseY < by + BAR_H) {
			g.setTooltipForNextFrame(Component.translatable("gui.voltworks.energy", menu.getEnergy(), menu.getCapacity()), mouseX, mouseY);
		}
	}
}
