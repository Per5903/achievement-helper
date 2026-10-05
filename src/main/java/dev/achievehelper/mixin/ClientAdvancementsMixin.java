package dev.achievehelper.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;

import dev.achievehelper.client.AdvancementTracker;

/**
 * Vanilla lets only one listener (the advancements screen) observe ClientAdvancements,
 * so we hook the packet handler instead of replacing that listener.
 */
@Mixin(ClientAdvancements.class)
abstract class ClientAdvancementsMixin {
	@Inject(method = "update", at = @At("TAIL"))
	private void achievehelper$afterUpdate(ClientboundUpdateAdvancementsPacket packet, CallbackInfo ci) {
		AdvancementTracker.INSTANCE.onUpdate((ClientAdvancements) (Object) this, packet.shouldReset());
	}
}
