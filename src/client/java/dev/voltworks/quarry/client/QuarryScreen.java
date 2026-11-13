package dev.voltworks.quarry.client;

import dev.voltworks.quarry.block.QuarryBlockEntity;
import dev.voltworks.quarry.menu.QuarryMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Drawn from flat rectangles: the grey panel of the Voltworks machine screens, with tier 2 slate-and-gold trim and a
 * dark display under the output. The display shows a map of the pit (the current layer: done, still to dig, and the
 * drill), a depth gauge from the quarry's height to the bottom of the world, and the layer, area, row and count.
 */
public class QuarryScreen extends AbstractContainerScreen<QuarryMenu> {
	private static final int PANEL = 0xFFC6C6C6, LIGHT = 0xFFFFFFFF, SHADOW = 0xFF555555, SLOT_BG = 0xFF8B8B8B, DARK = 0xFF373737;
	private static final int ENERGY = 0xFFE03C28, ENERGY_TOP = 0xFFFF8A5C, PROGRESS = 0xFF4FC34F;
	/** Voltworks tier 2 casing and trim. */
	private static final int SLATE = 0xFF404E62, SLATE_LIGHT = 0xFF627084, SLATE_DARK = 0xFF1E2836, GOLD = 0xFFF0AA32;
	/** The display: background, scanlines, text, the pit map's cells and the depth fill. */
	private static final int SCREEN = 0xFF141A24, SCANLINE = 0xFF19202C, TEXT = 0xFF9ADCFF, TEXT_DIM = 0xFF5E8AA6;
	private static final int CELL_TODO = 0xFF4A5C74, CELL_DONE = 0xFF222C3A, DEPTH = 0xFF40C0F0;

	private static final int BAR_X = 12, BAR_Y = 17, BAR_W = 10, BAR_H = 52;
	/** The display, and inside it the map box, the depth gauge and the text column. */
	private static final int DISPLAY_X = 8, DISPLAY_Y = 78, DISPLAY_W = 160, DISPLAY_H = 50;
	private static final int MAP_X = 14, MAP_Y = 83, MAP_SIZE = 38;
	private static final int DEPTH_X = 58, DEPTH_W = 6;
	private static final int TEXT_X = 72;

	public QuarryScreen(QuarryMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, QuarryMenu.INVENTORY_Y + 82);
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
			boolean upgrade = slot instanceof QuarryMenu.UpgradeSlot;
			g.fill(sx - 1, sy - 1, sx + 17, sy + 17, upgrade ? SLATE_DARK : DARK);
			g.fill(sx, sy, sx + 17, sy + 17, upgrade ? SLATE_LIGHT : LIGHT);
			g.fill(sx, sy, sx + 16, sy + 16, upgrade ? 0xFF7A8AA0 : SLOT_BG);
		}

		// Energy bar in a slate housing with gold rivets
		casing(g, x + BAR_X - 3, y + BAR_Y - 3, BAR_W + 6, BAR_H + 6);
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
		QuarryBlockEntity.Status status = menu.getStatus();
		int w = status == QuarryBlockEntity.Status.MINING ? Math.round(menu.getProgressFraction() * pw) : 0;
		if (w > 0) g.fill(px, py, px + w, py + 3, PROGRESS);

		// Status (coloured) on the title row, right-aligned
		int color = switch (status) {
			case MINING -> 0xFF2A7A2A;
			case FINISHED -> 0xFF2A4A9A;
			case PAUSED, WAITING -> 0xFF8A6A10;
			default -> 0xFFB02020;
		};
		g.text(font, status.label(), x + imageWidth - 8 - font.width(status.label()), y + 6, color, false);

		display(g, x, y, status);
	}

	/** The slate display under the output: pit map, depth gauge and text. */
	private void display(GuiGraphicsExtractor g, int x, int y, QuarryBlockEntity.Status status) {
		casing(g, x + DISPLAY_X, y + DISPLAY_Y, DISPLAY_W, DISPLAY_H);
		int sx0 = x + DISPLAY_X + 3, sy0 = y + DISPLAY_Y + 3, sx1 = x + DISPLAY_X + DISPLAY_W - 3, sy1 = y + DISPLAY_Y + DISPLAY_H - 3;
		g.fill(sx0, sy0, sx1, sy1, SCREEN);
		for (int row = sy0 + 1; row < sy1; row += 2) g.fill(sx0, row, sx1, row + 1, SCANLINE);

		int width = Math.max(1, menu.getWidth()), length = Math.max(1, menu.getLength());
		boolean finished = status == QuarryBlockEntity.Status.FINISHED;
		boolean tooBig = status == QuarryBlockEntity.Status.AREA_TOO_BIG;
		int done = finished ? width * length : Math.min(menu.getLayerIndex(), width * length);
		boolean blink = (System.currentTimeMillis() / 400) % 2 == 0;

		// Map: one cell per block of the layer, rows starting at the bottom, snaking like the quarry digs
		int cell = Math.max(1, Math.min(MAP_SIZE / width, MAP_SIZE / length));
		int mw = cell * width, mh = cell * length;
		int mx = x + MAP_X + (MAP_SIZE - mw) / 2, my = y + MAP_Y + (MAP_SIZE - mh) / 2;
		g.fill(mx - 1, my - 1, mx + mw + 1, my + mh + 1, tooBig ? 0xFFB02020 : GOLD);
		g.fill(mx, my, mx + mw, my + mh, SCREEN);
		int gap = cell >= 3 ? 1 : 0;
		for (int row = 0; row < length; row++) {
			for (int col = 0; col < width; col++) {
				int index = row * width + (row % 2 == 0 ? col : width - 1 - col);
				int c = index < done ? CELL_DONE : CELL_TODO;
				if (index == done && status == QuarryBlockEntity.Status.MINING) c = blink ? GOLD : 0xFFFFFFFF;
				int cx = mx + col * cell, cy = my + (length - 1 - row) * cell;
				g.fill(cx, cy, cx + cell - gap, cy + cell - gap, c);
			}
		}

		// Depth gauge: how far down the pit is, from the quarry's height to the bottom of the world
		int gx = x + DEPTH_X, gy = y + MAP_Y, gh = MAP_SIZE;
		g.fill(gx - 1, gy - 1, gx + DEPTH_W + 1, gy + gh + 1, SLATE_LIGHT);
		g.fill(gx, gy, gx + DEPTH_W, gy + gh, CELL_DONE);
		int start = menu.getStartY(), bottom = menu.getBottomY(), span = Math.max(1, start - bottom + 1);
		float depth = finished ? 1F : Math.clamp((float) (start - menu.getLayerY()) / span, 0F, 1F);
		int dh = Math.round(depth * gh);
		if (dh > 0) g.fill(gx, gy, gx + DEPTH_W, gy + dh, DEPTH);
		int mark = gy + Math.min(gh - 1, dh);
		g.fill(gx - 2, mark, gx + DEPTH_W + 2, mark + 1, GOLD);

		// Text
		int ty = y + MAP_Y + 2;
		Component[] lines = tooBig
				? new Component[]{tr("area", width, length), tr("needs_range"), tr("mined", menu.getBlocksMined())}
				: finished
						? new Component[]{tr("area", width, length), tr("bottom"), tr("mined", menu.getBlocksMined())}
						: new Component[]{tr("layer", menu.getLayerY()), tr("area", width, length),
								tr("row", Math.min(length, done / width + 1), length), tr("mined", menu.getBlocksMined())};
		for (int i = 0; i < lines.length; i++) {
			g.text(font, lines[i], x + TEXT_X, ty + i * 9, i == 0 ? TEXT : TEXT_DIM, false);
		}
	}

	private static Component tr(String key, Object... args) {
		return Component.translatable("gui.voltworks_quarry.screen." + key, args);
	}

	/** A slate frame, 3px deep: light top-left edge, dark bottom-right edge, a gold rivet in each corner. */
	private static void casing(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, SLATE);
		g.fill(x, y, x + w - 1, y + 1, SLATE_LIGHT);
		g.fill(x, y, x + 1, y + h - 1, SLATE_LIGHT);
		g.fill(x + 1, y + h - 1, x + w, y + h, SLATE_DARK);
		g.fill(x + w - 1, y + 1, x + w, y + h, SLATE_DARK);
		g.fill(x + 1, y + 1, x + 2, y + 2, GOLD);
		g.fill(x + w - 2, y + 1, x + w - 1, y + 2, GOLD);
		g.fill(x + 1, y + h - 2, x + 2, y + h - 1, GOLD);
		g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, GOLD);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		int bx = leftPos + BAR_X, by = topPos + BAR_Y;
		if (mouseX >= bx && mouseX < bx + BAR_W && mouseY >= by && mouseY < by + BAR_H) {
			g.setTooltipForNextFrame(Component.translatable("gui.voltworks.energy", menu.getEnergy(), menu.getCapacity()), mouseX, mouseY);
		}
		int gx = leftPos + DEPTH_X, gy = topPos + MAP_Y;
		if (mouseX >= gx - 1 && mouseX <= gx + DEPTH_W && mouseY >= gy && mouseY < gy + MAP_SIZE) {
			g.setTooltipForNextFrame(Component.translatable("gui.voltworks_quarry.screen.depth", menu.getStartY(), menu.getLayerY(), menu.getBottomY()), mouseX, mouseY);
		}
	}
}
