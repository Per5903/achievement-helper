package dev.achievehelper.client;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix3x2fStack;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Pinned goals and the current suggestion, anchored to a screen corner with an offset and a scale. */
public final class HudOverlay {
	static final int MARGIN = 4;
	private static final int GAP = 3;
	/** Bottom corners start above the hotbar. */
	static final int BOTTOM_CLEARANCE = 24;

	/**
	 * Where the panels go, in HUD pixels (screen pixels divided by {@code scale}).
	 * {@code screenW/H} are the screen size in the same units.
	 */
	record Placement(float scale, int x, int y, int width, int height, int screenW, int screenH, List<TrackedGoal> goals,
			List<Integer> heights) {
		boolean contains(double guiX, double guiY) {
			double hx = guiX / scale;
			double hy = guiY / scale;
			return hx >= x && hx < x + width && hy >= y && hy < y + height;
		}
	}

	private HudOverlay() {
	}

	public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		ModConfig config = AdvancementTracker.INSTANCE.config();
		if (!config.hud || mc.player == null || mc.gui.hud.isHidden() || mc.getDebugOverlay().showDebugScreen()) return;
		// Our own screens show the same goals in full (the editor draws the HUD itself); drawing it under them
		// only clutters, and with background blur disabled by other mods it shows straight through.
		if (mc.gui.screen() instanceof ChecklistScreen || mc.gui.screen() instanceof GoalDetailScreen
				|| mc.gui.screen() instanceof SettingsScreen || mc.gui.screen() instanceof HudEditorScreen) return;
		List<TrackedGoal> goals = AdvancementTracker.INSTANCE.hudGoals();
		if (goals.isEmpty()) return;
		draw(g, place(g.guiWidth(), g.guiHeight(), goals, config));
	}

	static Placement place(int guiW, int guiH, List<TrackedGoal> goals, ModConfig config) {
		Minecraft mc = Minecraft.getInstance();
		float scale = config.hudScale / 100f;
		int screenW = Math.round(guiW / scale);
		int screenH = Math.round(guiH / scale);
		int width = Math.min(config.hudWidth, screenW - 2 * MARGIN);
		GoalPanel.Style style = GoalPanel.Style.hud(config);

		// Never cover more than half of the screen height.
		List<TrackedGoal> shown = new ArrayList<>();
		List<Integer> heights = new ArrayList<>();
		int total = 0;
		for (TrackedGoal t : goals) {
			int h = GoalPanel.height(mc.font, t, width, style);
			if (!shown.isEmpty() && total + GAP + h > screenH / 2) break;
			total += (shown.isEmpty() ? 0 : GAP) + h;
			shown.add(t);
			heights.add(h);
		}

		int x = config.hudCorner.right() ? screenW - MARGIN - width - config.hudOffsetX : MARGIN + config.hudOffsetX;
		int y = config.hudCorner.bottom() ? screenH - MARGIN - BOTTOM_CLEARANCE - total - config.hudOffsetY : MARGIN + config.hudOffsetY;
		// Keep it on screen whatever the window size.
		x = Math.clamp(x, 0, Math.max(0, screenW - width));
		y = Math.clamp(y, 0, Math.max(0, screenH - total));
		return new Placement(scale, x, y, width, total, screenW, screenH, shown, heights);
	}

	static void draw(GuiGraphicsExtractor g, Placement p) {
		Minecraft mc = Minecraft.getInstance();
		AdvancementTracker tracker = AdvancementTracker.INSTANCE;
		GoalPanel.Style style = GoalPanel.Style.hud(tracker.config());
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.scale(p.scale(), p.scale());
		int y = p.y();
		for (int i = 0; i < p.goals().size(); i++) {
			TrackedGoal t = p.goals().get(i);
			GoalPanel.Mark mark = tracker.isPinned(t.id()) ? GoalPanel.Mark.PINNED : GoalPanel.Mark.SUGGESTED;
			GoalPanel.draw(g, mc.font, t, p.x(), y, p.width(), style, mark, -1, -1);
			y += p.heights().get(i) + GAP;
		}
		pose.popMatrix();
	}
}
