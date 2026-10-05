package dev.achievehelper.client;

import java.util.List;

import net.minecraft.advancements.AdvancementType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import dev.achievehelper.core.Goal;
import dev.achievehelper.core.Hint;

/** A {@link Goal} plus what is needed to draw it; without a hint, {@code hint} and {@code note} are null and {@code steps} is empty. */
public record TrackedGoal(Goal goal, Component title, Component description, ItemStack icon, AdvancementType type,
		String tab, List<IconResolver.Icon> remainingIcons, List<IconResolver.Icon> doneIcons, Hint hint, List<HintStep> steps,
		Component note) {

	public String id() {
		return goal.id();
	}
}
