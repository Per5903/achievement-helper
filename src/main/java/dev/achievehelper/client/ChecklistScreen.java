package dev.achievehelper.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.achievehelper.core.Goal;
import dev.achievehelper.core.GoalRanker;

/**
 * Every advancement as a colored icon cell, one row block per tab (the tab's root icon first).
 * Green = done, amber = started, gray = available, dark = locked; gold frame = pinned.
 * Left click pins, right click opens {@link GoalDetailScreen}. Icon buttons on top filter the cells;
 * the comparator in the corner opens {@link SettingsScreen}.
 */
public final class ChecklistScreen extends Screen {
	private static final int CELL = 20;
	private static final int PANEL_HEIGHT = 72;
	private static final int FILTER_Y = 18;
	private static final int GRID_TOP = FILTER_Y + CELL + 6;

	enum Filter {
		ALL(new ItemStack(Items.BOOK)),
		AVAILABLE(new ItemStack(Items.COMPASS)),
		STARTED(new ItemStack(Items.CLOCK)),
		EASY(new ItemStack(Items.WOODEN_PICKAXE)),
		PINNED(new ItemStack(Items.GOLD_NUGGET)),
		DONE(new ItemStack(Items.EMERALD));

		final ItemStack icon;

		Filter(ItemStack icon) {
			this.icon = icon;
		}

		boolean matches(Goal g, boolean available, boolean pinned) {
			return switch (this) {
				case ALL -> true;
				case AVAILABLE -> !g.done() && available;
				case STARTED -> !g.done() && g.started();
				case EASY -> !g.done() && available && g.effort() <= 2;
				case PINNED -> pinned;
				case DONE -> g.done();
			};
		}

		Component label() {
			return Component.translatable("achievehelper.filter." + name().toLowerCase(Locale.ROOT));
		}
	}

	private Filter filter;
	private double scroll;
	private int contentHeight;
	private TrackedGoal hovered;
	private Filter hoveredFilter;
	private boolean hoveredSettings;
	private static final ItemStack SETTINGS_ICON = new ItemStack(Items.COMPARATOR);

	public ChecklistScreen() {
		super(Component.translatable("achievehelper.screen.title"));
		try {
			filter = Filter.valueOf(AdvancementTracker.INSTANCE.config().checklistFilter);
		} catch (IllegalArgumentException e) {
			filter = Filter.ALL;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		// Own dimming, so the grid stays readable when other mods turn off the menu blur.
		g.fill(0, 0, width, height, 0xA0101010);
		AdvancementTracker tracker = AdvancementTracker.INSTANCE;
		g.centeredText(font, title, width / 2, 6, 0xFFFFFFFF);

		List<TrackedGoal> all = tracker.all();
		Map<String, Goal> byId = new HashMap<>();
		for (TrackedGoal t : all) byId.put(t.id(), t.goal());

		drawFilters(g, all, byId, mouseX, mouseY);

		Map<String, List<TrackedGoal>> tabs = new LinkedHashMap<>();
		for (TrackedGoal t : all) tabs.computeIfAbsent(t.tab(), k -> new ArrayList<>()).add(t);

		int left = 10;
		int right = width - 10;
		int bottom = height - PANEL_HEIGHT - 6;
		hovered = null;

		g.enableScissor(0, GRID_TOP, width, bottom);
		int y = GRID_TOP - (int) scroll;
		int shownTabs = 0;
		for (List<TrackedGoal> tab : tabs.values()) {
			TrackedGoal root = tab.getFirst();
			List<TrackedGoal> shown = tab.subList(1, tab.size()).stream()
					.filter(t -> filter.matches(t.goal(), GoalRanker.isAvailable(t.goal(), byId), tracker.isPinned(t.id())))
					.toList();
			if (shown.isEmpty() && filter != Filter.ALL) continue;
			shownTabs++;

			cell(g, root, left, y, byId, mouseX, mouseY, bottom);
			g.fill(left + CELL + 1, y + 2, left + CELL + 2, y + CELL - 2, 0x60FFFFFF);
			int x = left + CELL + 4;
			for (TrackedGoal t : shown) {
				if (x + CELL > right) {
					x = left + CELL + 4;
					y += CELL;
				}
				cell(g, t, x, y, byId, mouseX, mouseY, bottom);
				x += CELL;
			}
			y += CELL + 6;
		}
		g.disableScissor();
		contentHeight = y + (int) scroll - GRID_TOP;
		if (shownTabs == 0) {
			Component empty = Component.translatable(filter == Filter.ALL ? "achievehelper.screen.none_yet" : "achievehelper.screen.empty_filter");
			int cy = GRID_TOP + 20;
			for (var line : font.split(empty, Math.min(300, width - 40))) {
				g.centeredText(font, line, width / 2, cy, 0xFFAAAAAA);
				cy += font.lineHeight + 2;
			}
		}

		TrackedGoal info = hovered != null ? hovered : tracker.suggestion();
		if (info != null) {
			int accent = tracker.isPinned(info.id()) ? GoalPanel.PINNED_COLOR : GoalPanel.SUGGESTED_COLOR;
			GoalPanel.draw(g, font, info, 10, height - PANEL_HEIGHT, width - 20, GoalPanel.Style.FULL, accent, mouseX, mouseY);
		}
	}

	private void drawFilters(GuiGraphicsExtractor g, List<TrackedGoal> all, Map<String, Goal> byId, int mouseX, int mouseY) {
		hoveredFilter = null;
		int x = 10;
		for (Filter f : Filter.values()) {
			boolean selected = f == filter;
			g.fill(x + 1, FILTER_Y + 1, x + CELL - 1, FILTER_Y + CELL - 1, selected ? 0xC0505050 : 0xC0202020);
			g.item(f.icon, x + 2, FILTER_Y + 2);
			if (selected) g.outline(x, FILTER_Y, CELL, CELL, GoalPanel.PINNED_COLOR);
			if (mouseX >= x && mouseX < x + CELL && mouseY >= FILTER_Y && mouseY < FILTER_Y + CELL) {
				hoveredFilter = f;
				g.outline(x, FILTER_Y, CELL, CELL, 0xFFFFFFFF);
				long count = all.stream()
						.filter(t -> f.matches(t.goal(), GoalRanker.isAvailable(t.goal(), byId), AdvancementTracker.INSTANCE.isPinned(t.id())))
						.count();
				g.setTooltipForNextFrame(font, f.label().copy().append(" (" + count + ")"), mouseX, mouseY);
			}
			x += CELL + 2;
		}

		int sx = width - 10 - CELL;
		g.fill(sx + 1, FILTER_Y + 1, sx + CELL - 1, FILTER_Y + CELL - 1, 0xC0202020);
		g.item(SETTINGS_ICON, sx + 2, FILTER_Y + 2);
		hoveredSettings = mouseX >= sx && mouseX < sx + CELL && mouseY >= FILTER_Y && mouseY < FILTER_Y + CELL;
		if (hoveredSettings) {
			g.outline(sx, FILTER_Y, CELL, CELL, 0xFFFFFFFF);
			g.setTooltipForNextFrame(font, Component.translatable("achievehelper.screen.settings"), mouseX, mouseY);
		}
	}

	private void cell(GuiGraphicsExtractor g, TrackedGoal t, int x, int y, Map<String, Goal> byId, int mouseX, int mouseY, int bottom) {
		Goal goal = t.goal();
		boolean available = GoalRanker.isAvailable(goal, byId);
		int bg = goal.done() ? 0xC02E7D32 : goal.started() ? 0xC0A0761A : available ? 0xC0404040 : 0xC0181818;
		g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, bg);
		if (AdvancementTracker.INSTANCE.isPinned(t.id())) g.outline(x, y, CELL, CELL, GoalPanel.PINNED_COLOR);
		g.item(t.icon(), x + 2, y + 2);
		if (!goal.done() && !available) g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0x90000000);
		if (goal.isChecklist() && !goal.done()) {
			g.fill(x + 2, y + CELL - 3, x + CELL - 2, y + CELL - 2, 0xFF303030);
			g.fill(x + 2, y + CELL - 3, x + 2 + Math.round((CELL - 4) * goal.fraction()), y + CELL - 2, 0xFF55FF55);
		}
		if (mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL && mouseY >= GRID_TOP && mouseY < bottom) {
			hovered = t;
			g.outline(x, y, CELL, CELL, 0xFFFFFFFF);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && hoveredFilter != null) {
			filter = hoveredFilter;
			scroll = 0;
			AdvancementTracker.INSTANCE.config().checklistFilter = filter.name();
			AdvancementTracker.INSTANCE.config().save();
			click();
			return true;
		}
		if (event.button() == 0 && hoveredSettings) {
			click();
			minecraft.gui.setScreen(new SettingsScreen(this));
			return true;
		}
		if (event.button() == 0 && hovered != null) {
			AdvancementTracker.INSTANCE.togglePin(hovered.id());
			click();
			return true;
		}
		if (event.button() == 1 && hovered != null) {
			click();
			minecraft.gui.setScreen(new GoalDetailScreen(this, hovered.id()));
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void click() {
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int view = height - PANEL_HEIGHT - 6 - GRID_TOP;
		scroll = Math.clamp(scroll - scrollY * CELL, 0, Math.max(0, contentHeight - view));
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
