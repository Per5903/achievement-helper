package dev.achievehelper.client;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

/** Vanilla-style options list for {@link ModConfig}; also opened from Mod Menu. */
public final class SettingsScreen extends OptionsSubScreen {
	private static final String KEY = "achievehelper.option.";

	public SettingsScreen(Screen parent) {
		super(parent, Minecraft.getInstance().options, Component.translatable("achievehelper.settings.title"));
	}

	@Override
	protected void addOptions() {
		ModConfig c = AdvancementTracker.INSTANCE.config();

		list.addHeader(Component.translatable("achievehelper.settings.hud"));
		list.addBig(Button.builder(Component.translatable("achievehelper.editor.open"),
				b -> minecraft.gui.setScreen(new HudEditorScreen(this))).build());
		list.addSmall(
				bool("hud", () -> c.hud, v -> c.hud = v),
				side("hudSide", "left", "right", () -> c.hudCorner.right(), v -> c.hudCorner = ModConfig.HudCorner.of(v, c.hudCorner.bottom())),
				side("hudVertical", "top", "bottom", () -> c.hudCorner.bottom(), v -> c.hudCorner = ModConfig.HudCorner.of(c.hudCorner.right(), v)),
				percent("hudScale", 5, 20, () -> c.hudScale / 10, v -> c.hudScale = v * 10),
				number("hudWidth", 12, 32, () -> c.hudWidth / 10, v -> c.hudWidth = v * 10, v -> Component.literal(String.valueOf(v * 10))),
				number("maxPins", 1, 6, () -> c.maxPins, v -> c.maxPins = v, v -> Component.literal(String.valueOf(v))),
				number("iconRows", 0, 6, () -> c.iconRows, v -> c.iconRows = v, v -> Component.literal(String.valueOf(v))),
				bool("showTitles", () -> !c.compact, v -> c.compact = !v),
				bool("showNotes", () -> c.showNotes, v -> c.showNotes = v),
				bool("mobModels", () -> c.mobModels, v -> c.mobModels = v),
				bool("autopilot", () -> c.autopilot, v -> c.autopilot = v));

		list.addHeader(Component.translatable("achievehelper.settings.hints"));
		list.addSmall(
				bool("progressToasts", () -> c.progressToasts, v -> c.progressToasts = v),
				bool("biomeReminders", () -> c.biomeReminders, v -> c.biomeReminders = v),
				bool("itemTooltips", () -> c.itemTooltips, v -> c.itemTooltips = v),
				bool("highlightItems", () -> c.highlightItems, v -> c.highlightItems = v));

		list.addHeader(Component.translatable("achievehelper.settings.mobs"));
		list.addSmall(
				bool("highlightMobs", () -> c.highlightMobs, v -> c.highlightMobs = v),
				bool("highlightOnlyVisible", () -> c.highlightOnlyVisible, v -> c.highlightOnlyVisible = v),
				number("highlightRange", 0, 16, () -> c.highlightRange / 8, v -> c.highlightRange = v * 8,
						v -> v == 0 ? Component.translatable(KEY + "range.all") : Component.translatable(KEY + "range.blocks", v * 8)));
	}

	@Override
	public void removed() {
		super.removed();
		AdvancementTracker.INSTANCE.config().sanitize().save();
		AdvancementTracker.INSTANCE.applyConfig();
	}

	private static OptionInstance<Boolean> bool(String name, BooleanSupplier get, Consumer<Boolean> set) {
		return OptionInstance.createBoolean(KEY + name, tooltip(name), get.getAsBoolean(), set::accept);
	}

	private static OptionInstance<Integer> number(String name, int min, int max, IntSupplier get, Consumer<Integer> set,
			IntFunction<Component> label) {
		return new OptionInstance<>(KEY + name, tooltip(name),
				(caption, v) -> Options.genericValueLabel(caption, label.apply(v)),
				new OptionInstance.IntRange(min, max), Math.clamp(get.getAsInt(), min, max), set::accept);
	}

	private static OptionInstance<Integer> percent(String name, int min, int max, IntSupplier get, Consumer<Integer> set) {
		return number(name, min, max, get, set, v -> Component.literal(v * 10 + "%"));
	}

	/** A two-way switch shown as words ("left"/"right") instead of ON/OFF. */
	private static OptionInstance<Boolean> side(String name, String off, String on, BooleanSupplier get, Consumer<Boolean> set) {
		return OptionInstance.createBoolean(KEY + name, tooltip(name),
				(caption, v) -> Component.translatable(KEY + "side." + (v ? on : off)), get.getAsBoolean(), set::accept);
	}

	/** Tooltip from "achievehelper.option.<name>.tooltip" when the language file has one. */
	private static <T> OptionInstance.TooltipSupplier<T> tooltip(String name) {
		String key = KEY + name + ".tooltip";
		return net.minecraft.locale.Language.getInstance().has(key)
				? OptionInstance.cachedConstantTooltip(Component.translatable(key))
				: OptionInstance.noTooltip();
	}
}
