package dev.achievehelper.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Small toast with the goal icon and a second icon:
 * either "+1" progress on a checklist advancement, or a reminder that the player is where a goal can be done.
 */
public final class ProgressToast implements Toast {
	private static final long DISPLAY_MS = 3000;

	private TrackedGoal goal;
	private ItemStack second;
	/** Reminder text; null for progress, which shows the count and a bar instead. */
	private Component detail;
	private boolean changed = true;
	private long shownAt;
	private Visibility visibility = Visibility.SHOW;

	private ProgressToast(TrackedGoal goal, ItemStack second, Component detail) {
		this.goal = goal;
		this.second = second;
		this.detail = detail;
	}

	public static void show(TrackedGoal goal, IconResolver.Icon gained) {
		put(goal, gained.stack(), null);
	}

	/** "You are in the right place": {@code place} is the hint step that matched (a biome). */
	public static void remind(TrackedGoal goal, HintStep place) {
		put(goal, place.stack(), place.label());
	}

	private static void put(TrackedGoal goal, ItemStack second, Component detail) {
		ToastManager toasts = Minecraft.getInstance().gui.toastManager();
		ProgressToast existing = toasts.getToast(ProgressToast.class, goal.id());
		if (existing != null) {
			existing.goal = goal;
			existing.second = second;
			existing.detail = detail;
			existing.changed = true;
		} else {
			toasts.addToast(new ProgressToast(goal, second, detail));
		}
	}

	@Override
	public Object getToken() {
		return goal.id();
	}

	@Override
	public Visibility getWantedVisibility() {
		return visibility;
	}

	@Override
	public void update(ToastManager manager, long time) {
		if (changed) {
			shownAt = time;
			changed = false;
		}
		long duration = detail == null ? DISPLAY_MS : DISPLAY_MS * 2;
		visibility = time - shownAt < duration * manager.getNotificationDisplayTimeMultiplier() ? Visibility.SHOW : Visibility.HIDE;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, Font font, long time) {
		int w = width();
		int h = height();
		g.fill(0, 0, w, h, 0xE0181818);
		g.outline(0, 0, w, h, detail == null ? GoalPanel.SUGGESTED_COLOR : GoalPanel.PINNED_COLOR);

		g.item(goal.icon(), 6, 8);
		if (!second.isEmpty()) {
			g.item(second, 26, 8);
		} else {
			g.text(font, "+1", 28, 12, 0xFF55FF55, true);
		}

		g.text(font, font.plainSubstrByWidth(goal.title().getString(), w - 52), 46, 6, 0xFFFFFFFF, true);
		if (detail != null) {
			g.text(font, font.plainSubstrByWidth("→ " + detail.getString(), w - 52), 46, 18, 0xFFFFDD55, true);
			return;
		}
		String count = goal.goal().completed() + "/" + goal.goal().total();
		g.text(font, count, 46, 18, 0xFF55FF55, true);
		int barLeft = 50 + font.width(count);
		int barRight = w - 6;
		g.fill(barLeft, 21, barRight, 23, 0xFF404040);
		g.fill(barLeft, 21, barLeft + Math.round((barRight - barLeft) * goal.goal().fraction()), 23, 0xFF55FF55);
	}
}
