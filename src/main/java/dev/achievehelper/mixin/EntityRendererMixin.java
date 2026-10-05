package dev.achievehelper.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

import dev.achievehelper.client.WorldHints;

/** Gold outline for mobs a tracked advancement still needs; client-side only, nothing is sent to the server. */
@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void achievehelper$outline(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
		if (state.outlineColor == EntityRenderState.NO_OUTLINE && WorldHints.INSTANCE.isHighlighted(entity)) {
			state.outlineColor = WorldHints.OUTLINE_COLOR;
		}
	}
}
