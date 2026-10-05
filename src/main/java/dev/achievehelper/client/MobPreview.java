package dev.achievehelper.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.joml.Matrix3x2fStack;
import org.joml.Vector2f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import dev.achievehelper.AchieveHelper;

/**
 * A mob drawn as an icon, optionally with a variant: {@code component} is the data component that holds it
 * (e.g. {@code minecraft:cat/variant}), {@code registry} the registry its values come from
 * ({@code minecraft:cat_variant}) and {@code variant} the value's id ({@code minecraft:tabby}).
 */
public record MobPreview(EntityType<?> type, String component, String registry, String variant) {
	private static final Map<MobPreview, LivingEntity> CACHE = new HashMap<>();
	/** Rendering a living entity needs an id; negative ones never clash with server-assigned ids. */
	private static int nextId = -1_000_000;
	private static Object cacheLevel;

	/** Draws the mob looking at the viewer inside the box; returns false if it cannot be shown. */
	public boolean draw(GuiGraphicsExtractor g, int x, int y, int size) {
		LivingEntity entity = entity();
		if (entity == null) return false;
		// Entity previews are placed in screen space and ignore the pose, so apply it (HUD scale) by hand.
		Matrix3x2fStack pose = g.pose();
		Vector2f from = pose.transformPosition(x, y, new Vector2f());
		Vector2f to = pose.transformPosition(x + size, y + size, new Vector2f());
		int x0 = Math.round(from.x);
		int y0 = Math.round(from.y);
		int box = Math.max(4, Math.round(to.x - from.x));

		float tallest = Math.max(entity.getBbHeight(), entity.getBbWidth());
		int scale = Math.max(2, Math.round(box * 0.85f / tallest));
		pose.pushMatrix();
		pose.identity();
		InventoryScreen.extractEntityInInventoryFollowsMouse(g, x0, y0, x0 + box, y0 + box, scale, 0.0625f,
				x0 + box / 2f, y0 + box / 2f, entity);
		pose.popMatrix();
		return true;
	}

	/** The cached preview mob, or null if it cannot be created (no world, unknown type). */
	public LivingEntity entity() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return null;
		// Entities keep a reference to their level, so rebuild after a world change.
		if (cacheLevel != mc.level) {
			CACHE.clear();
			cacheLevel = mc.level;
		}
		return CACHE.computeIfAbsent(this, MobPreview::create);
	}

	private static LivingEntity create(MobPreview preview) {
		Minecraft mc = Minecraft.getInstance();
		try {
			Entity entity = preview.type.create(mc.level, EntitySpawnReason.LOAD);
			if (!(entity instanceof LivingEntity living)) return null;
			living.setId(nextId--);
			if (preview.component != null) applyVariant(living, preview);
			return living;
		} catch (RuntimeException e) {
			AchieveHelper.LOGGER.warn("Cannot preview {}", preview, e);
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private static void applyVariant(LivingEntity entity, MobPreview preview) {
		DataComponentType<Object> type = (DataComponentType<Object>) BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse(preview.component));
		ResourceKey<Registry<Object>> registryKey = ResourceKey.createRegistryKey(Identifier.parse(preview.registry));
		Optional<Registry<Object>> registry = Minecraft.getInstance().level.registryAccess().lookup(registryKey);
		Optional<? extends Holder<Object>> value = registry.flatMap(r -> r.get(Identifier.parse(preview.variant)));
		if (type == null || value.isEmpty()) {
			AchieveHelper.LOGGER.warn("Unknown variant {} for {}", preview.variant, preview.component);
			return;
		}
		entity.setComponent(type, value.get());
	}

	public static void clear() {
		CACHE.clear();
		cacheLevel = null;
	}
}
