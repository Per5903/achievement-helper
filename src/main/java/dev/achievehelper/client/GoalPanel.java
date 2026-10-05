package dev.achievehelper.client;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.joml.Matrix3x2fStack;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;

import dev.achievehelper.core.Goal;

/** Draws one goal as: icon, title, progress bar, hint chain, then a grid of what is still missing. */
public final class GoalPanel {
	public static final int PINNED_COLOR = 0xFFFFAA00;
	public static final int SUGGESTED_COLOR = 0xFF55FFFF;
	public static final int CHALLENGE_COLOR = 0xFFAA55FF;

	/** Why a goal is shown: the player pinned it, the autopilot suggested it, or neither (just looked at). */
	public enum Mark {
		PINNED(PINNED_COLOR), SUGGESTED(SUGGESTED_COLOR), PLAIN(0xFF808080);

		final int color;

		Mark(int color) {
			this.color = color;
		}

		public static Mark of(String id) {
			AdvancementTracker tracker = AdvancementTracker.INSTANCE;
			if (tracker.isPinned(id)) return PINNED;
			TrackedGoal s = tracker.suggestion();
			return s != null && s.id().equals(id) && tracker.config().autopilot ? SUGGESTED : PLAIN;
		}
	}

	private static final net.minecraft.world.item.ItemStack COMPASS = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COMPASS);
	private static final int MARK_WIDTH = 10;
	private static final int CELL = 17;
	private static final int HEADER = 20;

	/**
	 * @param iconRows max rows of missing-item icons ("+N" in the last cell when more)
	 * @param titles   show the advancement name (off = compact: icons and numbers only)
	 * @param notes    show the hint note, or the description when there is no hint
	 * @param mobs     draw mob models for variant icons (cats, wolves...) instead of spawn eggs
	 */
	public record Style(int iconRows, boolean titles, boolean notes, boolean mobs) {
		public static final Style FULL = new Style(2, true, true, true);

		public static Style hud(ModConfig config) {
			return new Style(config.iconRows, !config.compact, config.showNotes, config.mobModels);
		}
	}

	/** Vertical layout, shared by {@link #height} and {@link #draw} so both always agree. */
	private record Layout(int perRow, int rows, boolean hasSteps, List<FormattedCharSequence> lines, int stepsY, int iconsY,
			int textY, int height) {
	}

	private GoalPanel() {
	}

	private static Layout layout(Font font, TrackedGoal t, int width, Style style) {
		Goal goal = t.goal();
		int perRow = Math.max(1, (width - 8) / CELL);
		int rows = Math.min(style.iconRows(), (t.remainingIcons().size() + perRow - 1) / perRow);
		boolean hasSteps = !t.steps().isEmpty();
		// With a hint the icons say it all; the description is only a fallback.
		Component text = !style.notes() ? null
				: t.note() != null ? t.note() : !hasSteps && !goal.isChecklist() ? t.description() : null;
		List<FormattedCharSequence> lines = text == null ? List.of() : font.split(text, width - 8);
		if (lines.size() > 2) lines = lines.subList(0, 2);

		int stepsY = HEADER;
		int iconsY = stepsY + (hasSteps ? CELL + 1 : 0);
		int textY = iconsY + (rows > 0 ? rows * CELL + 2 : 0);
		int height = textY + lines.size() * (font.lineHeight + 1);
		return new Layout(perRow, rows, hasSteps, lines, stepsY, iconsY, textY, height);
	}

	public static int height(Font font, TrackedGoal t, int width, Style style) {
		return layout(font, t, width, style).height();
	}

	/** @return the panel height. Pass mouse coordinates to get tooltips over icons, or -1. */
	public static int draw(GuiGraphicsExtractor g, Font font, TrackedGoal t, int x, int y, int width, Style style,
			Mark mark, int mouseX, int mouseY) {
		int accent = mark.color;
		Goal goal = t.goal();
		Layout l = layout(font, t, width, style);
		g.fill(x, y, x + width, y + l.height(), 0xA0000000);
		g.fill(x, y, x + 2, y + l.height(), accent);

		g.item(t.icon(), x + 4, y + 2);
		String count = goal.isChecklist() ? goal.completed() + "/" + goal.total() : "";
		int countWidth = font.width(count);
		int markWidth = mark == Mark.PLAIN ? 0 : MARK_WIDTH;
		if (style.titles()) {
			String title = font.plainSubstrByWidth(t.title().getString(), width - 26 - countWidth - markWidth - 4);
			g.text(font, title, x + 22, y + 3, 0xFFFFFFFF, true);
		}
		if (!count.isEmpty()) g.text(font, count, x + width - 3 - countWidth, y + 3, 0xFFAAAAAA, true);
		// Pinned: gold star. Suggested by the autopilot: a small compass. So the two are never confused.
		int mx = x + width - 3 - countWidth - (count.isEmpty() ? 0 : 2) - 8;
		if (mark == Mark.PINNED) {
			g.text(font, "★", mx, y + 3, PINNED_COLOR, true);
		} else if (mark == Mark.SUGGESTED) {
			Matrix3x2fStack pose = g.pose();
			pose.pushMatrix();
			pose.translate(mx, y + 2);
			pose.scale(0.5f, 0.5f);
			g.item(COMPASS, 0, 0);
			pose.popMatrix();
		}
		if (mark != Mark.PLAIN && mouseX >= mx - 1 && mouseX < mx + 9 && mouseY >= y + 1 && mouseY < y + 12) {
			g.setTooltipForNextFrame(font, Component.translatable(mark == Mark.PINNED ? "achievehelper.mark.pinned" : "achievehelper.mark.suggested"), mouseX, mouseY);
		}
		if (mouseX >= x + 4 && mouseX < x + 20 && mouseY >= y + 2 && mouseY < y + 18) {
			g.setTooltipForNextFrame(font, t.title(), mouseX, mouseY);
		}

		int barLeft = x + 22;
		int barRight = x + width - 4;
		g.fill(barLeft, y + 14, barRight, y + 16, 0xFF404040);
		g.fill(barLeft, y + 14, barLeft + Math.round((barRight - barLeft) * goal.fraction()), y + 16,
				goal.challenge() ? CHALLENGE_COLOR : 0xFF55FF55);

		if (l.hasSteps()) drawSteps(g, font, t.steps(), x + 4, y + l.stepsY(), x + width - 4, mouseX, mouseY);

		List<IconResolver.Icon> icons = t.remainingIcons();
		int limit = l.perRow() * l.rows();
		int shown = Math.min(limit, icons.size());
		Set<Item> duplicates = duplicates(icons.subList(0, shown));
		for (int i = 0; i < shown; i++) {
			int cx = x + 4 + (i % l.perRow()) * CELL;
			int cy = y + l.iconsY() + (i / l.perRow()) * CELL;
			if (i == limit - 1 && icons.size() > limit) {
				String more = "+" + (icons.size() - limit + 1);
				g.text(font, more, cx + 8 - font.width(more) / 2, cy + 4, 0xFFFFFFFF, true);
				break;
			}
			IconResolver.Icon icon = icons.get(i);
			drawIcon(g, font, icon, cx, cy, 16, style.mobs(), duplicates.contains(icon.stack().getItem()));
			if (mouseX >= cx && mouseX < cx + 16 && mouseY >= cy && mouseY < cy + 16) {
				g.setComponentTooltipForNextFrame(font, icon.tooltip(), mouseX, mouseY);
			}
		}

		int ty = y + l.textY();
		for (FormattedCharSequence line : l.lines()) {
			g.text(font, line, x + 4, ty, 0xFFBBBBBB, false);
			ty += font.lineHeight + 1;
		}
		return l.height();
	}

	/**
	 * One criterion icon in a {@code size} box: the mob model when available and wanted, else the item,
	 * else a box with the name. {@code labelled} adds a tiny name (for identical icons such as cat eggs).
	 */
	static void drawIcon(GuiGraphicsExtractor g, Font font, IconResolver.Icon icon, int x, int y, int size, boolean mobs,
			boolean labelled) {
		if (!(mobs && icon.mob() != null && icon.mob().draw(g, x, y, size))) {
			if (icon.stack().isEmpty()) {
				g.fill(x, y, x + size, y + size, 0x40FFFFFF);
				tiny(g, font, icon.label().getString(), x + 1, y + size / 2 - 3);
			} else {
				int offset = (size - 16) / 2;
				g.item(icon.stack(), x + offset, y + offset);
				if (labelled) tiny(g, font, icon.label().getString(), x, y + size - 4);
			}
		}
		// Breeding/taming: what to feed, as a small icon in the corner.
		if (!icon.feed().isEmpty()) {
			Matrix3x2fStack pose = g.pose();
			pose.pushMatrix();
			float s = size >= 24 ? 0.75f : 0.5f;
			pose.translate(x + size - 16 * s, y + size - 16 * s);
			pose.scale(s, s);
			g.item(icon.feed().getFirst(), 0, 0);
			pose.popMatrix();
		}
	}

	/** One row: item icons joined by arrows, pluses and short yellow labels; cut off at {@code right}. */
	private static void drawSteps(GuiGraphicsExtractor g, Font font, List<HintStep> steps, int x, int y, int right,
			int mouseX, int mouseY) {
		int cx = x;
		for (HintStep step : steps) {
			if (step.isIcon()) {
				if (cx + 16 > right) break;
				g.item(step.stack(), cx, y);
				if (mouseX >= cx && mouseX < cx + 16 && mouseY >= y && mouseY < y + 16) {
					g.setTooltipForNextFrame(font, step.label(), mouseX, mouseY);
				}
				cx += CELL;
			} else {
				int w = font.width(step.text());
				if (cx + w > right) break;
				boolean symbol = "→".equals(step.text()) || "+".equals(step.text()) || "|".equals(step.text());
				int color = !step.resolved() ? 0xFFFF5555 : symbol ? 0xFFAAAAAA : 0xFFFFDD55;
				g.text(font, step.text(), cx + 1, y + 4, color, true);
				cx += w + 3;
			}
		}
	}

	/** Half-size text, cut to the width of an icon cell. */
	static void tiny(GuiGraphicsExtractor g, Font font, String text, int x, int y) {
		String cut = font.plainSubstrByWidth(text, 32);
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(0.5f, 0.5f);
		g.text(font, cut, 0, 0, 0xFFFFFFFF, true);
		pose.popMatrix();
	}

	private static Set<Item> duplicates(List<IconResolver.Icon> icons) {
		Set<Item> seen = new HashSet<>();
		Set<Item> dup = new HashSet<>();
		for (IconResolver.Icon icon : icons) {
			if (!icon.stack().isEmpty() && !seen.add(icon.stack().getItem())) dup.add(icon.stack().getItem());
		}
		return dup;
	}
}
