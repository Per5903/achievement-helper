package dev.achievehelper.client;

import java.util.List;

import org.joml.Matrix3x2fStack;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * One advancement in full: hint, then every missing item/mob/biome with its name, then everything already done.
 * Answers "which cats are still left to tame?".
 */
public final class GoalDetailScreen extends Screen {
	private static final int CELL_W = 46;
	private static final int CELL_H = 42;
	private static final int ICON = 24;
	private static final int TOP = 24;

	private final Screen parent;
	private final String goalId;
	private double scroll;
	private int contentHeight;

	public GoalDetailScreen(Screen parent, String goalId) {
		super(Component.empty());
		this.parent = parent;
		this.goalId = goalId;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		g.fill(0, 0, width, height, 0xA0101010);
		AdvancementTracker tracker = AdvancementTracker.INSTANCE;
		TrackedGoal t = tracker.get(goalId);
		if (t == null) {
			onClose();
			return;
		}
		ModConfig config = tracker.config();
		int left = 10;
		int panelWidth = width - 20;
		int accent = tracker.isPinned(goalId) ? GoalPanel.PINNED_COLOR : GoalPanel.SUGGESTED_COLOR;
		// Header: same panel as on the HUD, without the icon grid (shown in full below).
		int y = 6 + GoalPanel.draw(g, font, t, left, 6, panelWidth, new GoalPanel.Style(0, true, true, config.mobModels),
				accent, mouseX, mouseY) + 6;

		int top = y;
		int bottom = height - 6;
		g.enableScissor(0, top, width, bottom);
		int cy = top - (int) scroll;
		cy = section(g, Component.translatable("achievehelper.detail.remaining", t.remainingIcons().size()), 0xFFFFDD55,
				t.remainingIcons(), false, left, cy, panelWidth, mouseX, mouseY, top, bottom, config.mobModels);
		cy = section(g, Component.translatable("achievehelper.detail.done", t.doneIcons().size()), 0xFF55FF55,
				t.doneIcons(), true, left, cy, panelWidth, mouseX, mouseY, top, bottom, config.mobModels);
		g.disableScissor();
		contentHeight = cy + (int) scroll - top;
	}

	private int section(GuiGraphicsExtractor g, Component header, int color, List<IconResolver.Icon> icons, boolean done,
			int left, int y, int width, int mouseX, int mouseY, int top, int bottom, boolean mobs) {
		if (icons.isEmpty()) return y;
		g.text(font, header, left, y, color, true);
		y += font.lineHeight + 3;
		int perRow = Math.max(1, width / CELL_W);
		for (int i = 0; i < icons.size(); i++) {
			int x = left + (i % perRow) * CELL_W;
			int cy = y + (i / perRow) * CELL_H;
			if (cy + CELL_H < top || cy > bottom) continue;
			cell(g, icons.get(i), done, x, cy, mouseX, mouseY, top, bottom, mobs);
		}
		return y + ((icons.size() + perRow - 1) / perRow) * CELL_H + 6;
	}

	private void cell(GuiGraphicsExtractor g, IconResolver.Icon icon, boolean done, int x, int y, int mouseX, int mouseY,
			int top, int bottom, boolean mobs) {
		g.fill(x + 1, y + 1, x + CELL_W - 1, y + CELL_H - 1, done ? 0x802E7D32 : 0x80303030);
		int ix = x + (CELL_W - ICON) / 2;
		GoalPanel.drawIcon(g, font, icon, ix, y + 2, ICON, mobs, false);
		if (done) g.text(font, "✔", x + CELL_W - 9, y + 2, 0xFF55FF55, true);

		// Name under the icon at 3/4 size, up to two lines.
		List<FormattedCharSequence> lines = font.split(icon.label(), (int) ((CELL_W - 2) / 0.75f));
		Matrix3x2fStack pose = g.pose();
		for (int i = 0; i < Math.min(2, lines.size()); i++) {
			pose.pushMatrix();
			pose.translate(x + CELL_W / 2f, y + ICON + 4 + i * 7);
			pose.scale(0.75f, 0.75f);
			g.text(font, lines.get(i), -font.width(lines.get(i)) / 2, 0, done ? 0xFFAAAAAA : 0xFFFFFFFF, true);
			pose.popMatrix();
		}
		if (mouseX >= x && mouseX < x + CELL_W && mouseY >= y && mouseY < y + CELL_H && mouseY >= top && mouseY < bottom) {
			g.outline(x, y, CELL_W, CELL_H, 0xFFFFFFFF);
			g.setComponentTooltipForNextFrame(font, icon.tooltip(), mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll = Math.clamp(scroll - scrollY * CELL_H / 2, 0, Math.max(0, contentHeight - (height - TOP * 3)));
		return true;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
