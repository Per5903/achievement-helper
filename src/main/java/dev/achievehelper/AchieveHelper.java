package dev.achievehelper;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.Identifier;

import dev.achievehelper.client.AdvancementTracker;
import dev.achievehelper.client.HudOverlay;
import dev.achievehelper.client.Keys;
import dev.achievehelper.client.ModConfig;
import dev.achievehelper.client.WorldHints;

public final class AchieveHelper implements ClientModInitializer {
	public static final String MOD_ID = "achievehelper";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		AdvancementTracker tracker = AdvancementTracker.INSTANCE;
		tracker.init(ModConfig.load());

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(MOD_ID, "goals"), HudOverlay::extract);
		Keys.register();
		ClientTickEvents.END_CLIENT_TICK.register(WorldHints.INSTANCE::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> tracker.clear());

		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (!tracker.config().itemTooltips) return;
			List<Component> goals = tracker.wantedBy(stack.getItem());
			if (goals.isEmpty()) return;
			Component names = ComponentUtils.formatList(goals.subList(0, Math.min(2, goals.size())), Component.literal(", "));
			if (goals.size() > 2) names = names.copy().append(" +" + (goals.size() - 2));
			lines.add(Component.translatable("achievehelper.tooltip.needed_for", names).withStyle(ChatFormatting.GOLD));
		});
	}
}
