package dev.achievehelper.client;

import java.util.Optional;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.biome.Biome;

import dev.achievehelper.core.HintToken;

/** Hints in the world itself: outlines on needed mobs and a reminder when entering a needed biome. */
public final class WorldHints {
	public static final WorldHints INSTANCE = new WorldHints();
	public static final int OUTLINE_COLOR = 0xFFFFAA00;

	/**
	 * By reference, not entity id: GUI previews (ours, the inventory player, other mods') render entities that never
	 * got an id, and {@code getId()} throws for those.
	 */
	private final ReferenceSet<Entity> highlighted = new ReferenceOpenHashSet<>();
	private ResourceKey<Biome> lastBiome;
	private int ticks;

	private WorldHints() {
	}

	/** Called for every rendered entity, so it only looks up a set rebuilt a few times per second. */
	public boolean isHighlighted(Entity entity) {
		return highlighted.contains(entity);
	}

	public void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			highlighted.clear();
			lastBiome = null;
			return;
		}
		ticks++;
		if (ticks % 5 == 0) updateHighlights(mc);
		if (ticks % 20 == 0) checkBiome(mc);
	}

	private void updateHighlights(Minecraft mc) {
		highlighted.clear();
		ModConfig config = AdvancementTracker.INSTANCE.config();
		var wanted = config.highlightMobs ? AdvancementTracker.INSTANCE.wantedEntities() : java.util.Set.<EntityType<?>>of();
		if (wanted.isEmpty() && !config.highlightItems) return;
		double range = config.highlightRange > 0 ? (double) config.highlightRange * config.highlightRange : Double.MAX_VALUE;
		// Every loaded entity: the client only knows those within the server's tracking range anyway.
		for (Entity entity : mc.level.entitiesForRendering()) {
			boolean needed = entity instanceof ItemEntity item
					? config.highlightItems && AdvancementTracker.INSTANCE.isWanted(item.getItem().getItem())
					: wanted.contains(entity.getType());
			if (!needed || entity.distanceToSqr(mc.player) > range) continue;
			if (entity instanceof TamableAnimal animal && animal.isTame()) continue;
			if (config.highlightOnlyVisible && !mc.player.hasLineOfSight(entity)) continue;
			highlighted.add(entity);
		}
	}

	private void checkBiome(Minecraft mc) {
		Optional<ResourceKey<Biome>> key = mc.level.getBiome(mc.player.blockPosition()).unwrapKey();
		if (key.isEmpty() || key.get().equals(lastBiome)) return;
		boolean firstCheck = lastBiome == null;
		lastBiome = key.get();
		if (firstCheck || !AdvancementTracker.INSTANCE.config().biomeReminders) return;

		HintToken.Biome token = new HintToken.Biome(lastBiome.identifier().toString());
		for (TrackedGoal t : AdvancementTracker.INSTANCE.all()) {
			if (t.goal().done() || t.hint() == null || !t.hint().steps().contains(token)) continue;
			ProgressToast.remind(t, AdvancementTracker.INSTANCE.icons().step(token));
		}
	}
}
