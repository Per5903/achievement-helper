package dev.achievehelper.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.multiplayer.ClientAdvancements;

@Mixin(ClientAdvancements.class)
public interface ClientAdvancementsAccessor {
	@Accessor("progress")
	Map<AdvancementHolder, AdvancementProgress> achievehelper$progress();
}
