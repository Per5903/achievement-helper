package dev.achievehelper.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import dev.achievehelper.client.AdvancementTracker;
import dev.achievehelper.client.WorldHints;

/** Gold frame around items an unfinished advancement needs, in chests, the inventory and any other container. */
@Mixin(AbstractContainerScreen.class)
abstract class AbstractContainerScreenMixin {
	@Inject(method = "extractSlot", at = @At("TAIL"))
	private void achievehelper$frameWanted(GuiGraphicsExtractor g, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		if (!AdvancementTracker.INSTANCE.config().highlightItems || slot.getItem().isEmpty()) return;
		if (AdvancementTracker.INSTANCE.isWanted(slot.getItem().getItem())) {
			g.outline(slot.x - 1, slot.y - 1, 18, 18, WorldHints.OUTLINE_COLOR);
		}
	}
}
