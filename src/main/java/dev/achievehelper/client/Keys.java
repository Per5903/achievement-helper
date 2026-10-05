package dev.achievehelper.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import dev.achievehelper.AchieveHelper;

public final class Keys {
	private static KeyMapping checklist;
	private static KeyMapping toggleHud;
	private static KeyMapping nextSuggestion;
	private static KeyMapping pinSuggestion;

	private Keys() {
	}

	public static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(AchieveHelper.MOD_ID, "main"));
		checklist = key("checklist", GLFW.GLFW_KEY_J, category);
		toggleHud = key("toggle_hud", GLFW.GLFW_KEY_H, category);
		nextSuggestion = key("next", GLFW.GLFW_KEY_N, category);
		pinSuggestion = key("pin", GLFW.GLFW_KEY_M, category);
		ClientTickEvents.END_CLIENT_TICK.register(Keys::tick);
	}

	private static KeyMapping key(String name, int code, KeyMapping.Category category) {
		return KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key." + AchieveHelper.MOD_ID + "." + name, InputConstants.Type.KEYSYM, code, category));
	}

	private static void tick(Minecraft mc) {
		AdvancementTracker tracker = AdvancementTracker.INSTANCE;
		while (checklist.consumeClick()) {
			mc.gui.setScreen(new ChecklistScreen());
		}
		while (toggleHud.consumeClick()) {
			ModConfig config = tracker.config();
			config.hud = !config.hud;
			config.save();
			mc.gui.hud.setOverlayMessage(Component.translatable(config.hud ? "achievehelper.hud.on" : "achievehelper.hud.off"), false);
		}
		while (nextSuggestion.consumeClick()) {
			tracker.nextSuggestion();
		}
		while (pinSuggestion.consumeClick()) {
			tracker.pinSuggestion();
		}
	}
}
