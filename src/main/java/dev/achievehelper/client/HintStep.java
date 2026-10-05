package dev.achievehelper.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * A drawable hint element: an item icon, or a short text ({@code →}, {@code +}, {@code 50m}).
 * {@code resolved} is false when the hint named an item/mob/biome that does not exist.
 */
public record HintStep(ItemStack stack, String text, Component label, boolean resolved) {
	public boolean isIcon() {
		return !stack.isEmpty();
	}

	/** The mob this step shows (spawn-egg icons), or null. */
	public net.minecraft.world.entity.EntityType<?> entity() {
		return IconResolver.entityOf(stack);
	}
}
