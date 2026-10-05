package dev.achievehelper.client;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Drag the goals panel anywhere with the mouse; the wheel changes its size, arrows nudge it by one pixel.
 * The position is stored relative to the nearest screen corner, so it stays put when the window is resized.
 */
public final class HudEditorScreen extends Screen {
	private final Screen parent;
	private boolean dragging;
	private double grabX;
	private double grabY;

	public HudEditorScreen(Screen parent) {
		super(Component.translatable("achievehelper.editor.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		ModConfig c = AdvancementTracker.INSTANCE.config();
		int w = 100;
		int gap = 4;
		int x = width / 2 - (4 * w + 3 * gap) / 2;
		int y = height - 28;
		addRenderableWidget(Button.builder(Component.translatable("achievehelper.editor.flip_side"), b -> {
			c.hudCorner = c.hudCorner.flipSide();
		}).bounds(x, y, w, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("achievehelper.editor.flip_vertical"), b -> {
			c.hudCorner = c.hudCorner.flipVertical();
		}).bounds(x + (w + gap), y, w, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("achievehelper.editor.reset"), b -> {
			c.hudCorner = ModConfig.HudCorner.TOP_LEFT;
			c.hudOffsetX = 0;
			c.hudOffsetY = 0;
			c.hudScale = 100;
		}).bounds(x + 2 * (w + gap), y, w, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(x + 3 * (w + gap), y, w, 20).build());
	}

	private HudOverlay.Placement placement() {
		List<TrackedGoal> goals = AdvancementTracker.INSTANCE.hudGoals();
		if (goals.isEmpty()) goals = AdvancementTracker.INSTANCE.all().stream().limit(1).toList();
		return HudOverlay.place(width, height, goals, AdvancementTracker.INSTANCE.config());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		HudOverlay.Placement p = placement();
		if (p.goals().isEmpty()) {
			// No advancements yet: a stand-in box, so there is still something to drag.
			int h = 40;
			p = new HudOverlay.Placement(p.scale(), p.x(), Math.clamp(p.y() - (AdvancementTracker.INSTANCE.config().hudCorner.bottom() ? h : 0), 0, p.screenH()),
					p.width(), h, p.screenW(), p.screenH(), List.of(), List.of());
			int x0 = Math.round(p.x() * p.scale());
			int y0 = Math.round(p.y() * p.scale());
			g.fill(x0, y0, x0 + Math.round(p.width() * p.scale()), y0 + Math.round(h * p.scale()), 0xA0000000);
			g.centeredText(font, Component.translatable("achievehelper.editor.sample"), x0 + Math.round(p.width() * p.scale() / 2),
					y0 + Math.round(h * p.scale() / 2) - 4, 0xFFAAAAAA);
		} else {
			HudOverlay.draw(g, p);
		}
		int x0 = Math.round(p.x() * p.scale());
		int y0 = Math.round(p.y() * p.scale());
		int w = Math.round(p.width() * p.scale());
		int h = Math.round(p.height() * p.scale());
		boolean hover = p.contains(mouseX, mouseY);
		g.outline(x0 - 1, y0 - 1, w + 2, h + 2, dragging || hover ? 0xFFFFFFFF : GoalPanel.PINNED_COLOR);

		g.centeredText(font, title, width / 2, height / 2 - 20, 0xFFFFFFFF);
		g.centeredText(font, Component.translatable("achievehelper.editor.help"), width / 2, height / 2 - 6, 0xFFAAAAAA);
		ModConfig c = AdvancementTracker.INSTANCE.config();
		g.centeredText(font, Component.translatable("achievehelper.corner." + c.hudCorner.name().toLowerCase(java.util.Locale.ROOT))
				.append(" · " + c.hudScale + "%"), width / 2, height / 2 + 8, 0xFFFFDD55);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		HudOverlay.Placement p = placement();
		if (event.button() == 0 && (p.contains(event.x(), event.y()) || p.goals().isEmpty())) {
			dragging = true;
			grabX = event.x() / p.scale() - p.x();
			grabY = event.y() / p.scale() - p.y();
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (!dragging) return super.mouseDragged(event, dx, dy);
		HudOverlay.Placement p = placement();
		int x = (int) Math.round(event.x() / p.scale() - grabX);
		int y = (int) Math.round(event.y() / p.scale() - grabY);
		moveTo(p, x, y);
		return true;
	}

	/** Re-anchor to the corner nearest the panel's centre and store the distance from it. */
	private static void moveTo(HudOverlay.Placement p, int x, int y) {
		ModConfig c = AdvancementTracker.INSTANCE.config();
		int height = Math.max(p.height(), 1);
		x = Math.clamp(x, 0, Math.max(0, p.screenW() - p.width()));
		y = Math.clamp(y, 0, Math.max(0, p.screenH() - height));
		boolean right = x + p.width() / 2 > p.screenW() / 2;
		boolean bottom = y + height / 2 > p.screenH() / 2;
		c.hudCorner = ModConfig.HudCorner.of(right, bottom);
		c.hudOffsetX = right ? p.screenW() - HudOverlay.MARGIN - p.width() - x : x - HudOverlay.MARGIN;
		c.hudOffsetY = bottom ? p.screenH() - HudOverlay.MARGIN - HudOverlay.BOTTOM_CLEARANCE - height - y : y - HudOverlay.MARGIN;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging) {
			dragging = false;
			AdvancementTracker.INSTANCE.config().save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		ModConfig c = AdvancementTracker.INSTANCE.config();
		c.hudScale = Math.clamp(c.hudScale + (scrollY > 0 ? 10 : -10), 50, 200);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int dx = switch (event.key()) {
			case GLFW.GLFW_KEY_LEFT -> -1;
			case GLFW.GLFW_KEY_RIGHT -> 1;
			default -> 0;
		};
		int dy = switch (event.key()) {
			case GLFW.GLFW_KEY_UP -> -1;
			case GLFW.GLFW_KEY_DOWN -> 1;
			default -> 0;
		};
		if (dx != 0 || dy != 0) {
			HudOverlay.Placement p = placement();
			moveTo(p, p.x() + dx, p.y() + dy);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		AdvancementTracker.INSTANCE.config().save();
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
