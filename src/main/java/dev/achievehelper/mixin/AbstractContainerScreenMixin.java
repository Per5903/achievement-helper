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

/**
 * Items an unfinished advancement needs, in chests, the inventory and any other container:
 * a softly pulsing gold glow behind the item and a 2-pixel gold frame around it.
 */
@Mixin(AbstractContainerScreen.class)
abstract class AbstractContainerScreenMixin {
	@Inject(method = "extractSlot", at = @At("HEAD"))
	private void achievehelper$glowWanted(GuiGraphicsExtractor g, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		if (!achievehelper$wanted(slot)) return;
		// Pulse between ~30% and ~70% opacity, once every 1.2 seconds.
		double phase = (System.currentTimeMillis() % 1200) / 1200.0 * Math.PI * 2;
		int alpha = (int) (0x50 + 0x38 * Math.sin(phase));
		g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, alpha << 24 | (WorldHints.OUTLINE_COLOR & 0xFFFFFF));
	}

	@Inject(method = "extractSlot", at = @At("TAIL"))
	private void achievehelper$frameWanted(GuiGraphicsExtractor g, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		if (!achievehelper$wanted(slot)) return;
		// Two pixels thick, kept inside the slot's own 18x18 cell.
		g.outline(slot.x - 1, slot.y - 1, 18, 18, WorldHints.OUTLINE_COLOR);
		g.outline(slot.x, slot.y, 16, 16, WorldHints.OUTLINE_COLOR);
	}

	private static boolean achievehelper$wanted(Slot slot) {
		return AdvancementTracker.INSTANCE.config().highlightItems && !slot.getItem().isEmpty()
				&& AdvancementTracker.INSTANCE.isWanted(slot.getItem().getItem());
	}
}
