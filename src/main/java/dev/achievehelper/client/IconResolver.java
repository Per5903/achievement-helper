package dev.achievehelper.client;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;

import dev.achievehelper.AchieveHelper;
import dev.achievehelper.core.CriterionNames;
import dev.achievehelper.core.HintToken;

/** Finds an item icon (and a short name) for an advancement criterion. */
public final class IconResolver {
	/**
	 * @param stack  icon, or {@link ItemStack#EMPTY} when nothing matched
	 * @param label  readable name, shown when the icon alone is ambiguous
	 * @param exact  the criterion name is this item's own id, so holding the item is what counts
	 * @param mob    draw this mob instead of the item when mob models are on, or null
	 * @param feed   for breeding/taming advancements: what to give the mob, best first (else empty)
	 * @param feedMode "breed" or "tame" when {@code feed} is set, for the tooltip wording
	 */
	public record Icon(ItemStack stack, Component label, boolean exact, MobPreview mob, List<ItemStack> feed, String feedMode) {
		public Icon(ItemStack stack, Component label, boolean exact) {
			this(stack, label, exact, null);
		}

		public Icon(ItemStack stack, Component label, boolean exact, MobPreview mob) {
			this(stack, label, exact, mob, List.of(), null);
		}

		Icon withFeed(List<ItemStack> items, String mode) {
			return new Icon(stack, label, exact, mob, items, mode);
		}

		/** Tooltip: the name, then what to feed and an optional note (e.g. "breed a horse with a donkey"). */
		public List<Component> tooltip() {
			if (feed.isEmpty()) return List.of(label);
			List<Component> lines = new java.util.ArrayList<>();
			lines.add(label);
			Component names = net.minecraft.network.chat.ComponentUtils.formatList(
					feed.stream().map(ItemStack::getHoverName).toList(), Component.literal(", "));
			lines.add(Component.translatable("achievehelper.feed." + feedMode, names).withStyle(net.minecraft.ChatFormatting.GOLD));
			EntityType<?> type = entity();
			if (type != null) {
				String note = "achievehelper.feed." + feedMode + "." + BuiltInRegistries.ENTITY_TYPE.getKey(type).toLanguageKey();
				if (net.minecraft.locale.Language.getInstance().has(note)) {
					lines.add(Component.translatable(note).withStyle(net.minecraft.ChatFormatting.GRAY));
				}
			}
			return lines;
		}

		/** The mob this icon stands for (mob previews and spawn-egg icons), or null. */
		public EntityType<?> entity() {
			return mob != null ? mob.type() : entityOf(stack);
		}
	}

	static EntityType<?> entityOf(ItemStack stack) {
		return stack.getItem() instanceof SpawnEggItem ? SpawnEggItem.getType(stack) : null;
	}

	private final Map<String, String> biomes = new HashMap<>();
	private final Map<String, Map<String, JsonElement>> perAdvancement = new HashMap<>();
	private final Map<String, Icon> cache = new HashMap<>();
	/** Advancement id → "breed" / "tame", and mode → mob id → items. */
	private final Map<String, String> feedModes = new HashMap<>();
	private final Map<String, Map<String, List<String>>> feedTables = new HashMap<>();
	private final Map<EntityType<?>, List<ItemStack>> foundFood = new HashMap<>();

	public IconResolver() {
		try (InputStream in = IconResolver.class.getResourceAsStream("/assets/" + AchieveHelper.MOD_ID + "/icons.json")) {
			if (in == null) return;
			JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("biomes").entrySet()) {
				biomes.put(e.getKey(), e.getValue().getAsString());
			}
			for (Map.Entry<String, JsonElement> adv : root.getAsJsonObject("advancements").entrySet()) {
				Map<String, JsonElement> m = new HashMap<>();
				for (Map.Entry<String, JsonElement> e : adv.getValue().getAsJsonObject().entrySet()) {
					m.put(e.getKey(), e.getValue());
				}
				perAdvancement.put(adv.getKey(), m);
			}
		} catch (Exception e) {
			AchieveHelper.LOGGER.error("Could not load icon table", e);
		}
		try (InputStream in = IconResolver.class.getResourceAsStream("/assets/" + AchieveHelper.MOD_ID + "/feeding.json")) {
			if (in == null) return;
			JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("advancements").entrySet()) {
				String mode = e.getValue().getAsString();
				feedModes.put(e.getKey(), mode);
				if (feedTables.containsKey(mode) || !root.has(mode)) continue;
				Map<String, List<String>> table = new HashMap<>();
				for (Map.Entry<String, JsonElement> mob : root.getAsJsonObject(mode).entrySet()) {
					List<String> items = new java.util.ArrayList<>();
					for (JsonElement item : mob.getValue().getAsJsonArray()) items.add(item.getAsString());
					table.put(mob.getKey(), items);
				}
				feedTables.put(mode, table);
			}
		} catch (Exception e) {
			AchieveHelper.LOGGER.error("Could not load feeding table", e);
		}
	}

	public HintStep step(HintToken token) {
		return switch (token) {
			case HintToken.Symbol symbol -> new HintStep(ItemStack.EMPTY, symbol.glyph(), Component.literal(symbol.glyph()), true);
			case HintToken.Text text -> new HintStep(ItemStack.EMPTY, text.text(), Component.literal(text.text()), true);
			case HintToken.Item item -> {
				Identifier id = Identifier.tryParse(item.id());
				Optional<ItemStack> stack = id == null ? Optional.empty() : byItemOrBlock(id);
				yield stack.map(st -> new HintStep(st, "", st.getHoverName(), true)).orElseGet(() -> missing(item.id()));
			}
			case HintToken.Entity entity -> {
				Identifier id = Identifier.tryParse(entity.id());
				if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) yield missing(entity.id());
				EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
				ItemStack egg = spawnEgg(type);
				yield egg.isEmpty() ? new HintStep(ItemStack.EMPTY, type.getDescription().getString(), type.getDescription(), true)
						: new HintStep(egg, "", type.getDescription(), true);
			}
			case HintToken.Biome biome -> {
				String itemId = biomes.get(biome.id());
				Identifier id = Identifier.tryParse(biome.id());
				ItemStack stack = itemId == null ? ItemStack.EMPTY : item(itemId);
				if (id == null || stack.isEmpty()) yield missing(biome.id());
				yield new HintStep(stack, "", Component.translatable(id.toLanguageKey("biome")), true);
			}
		};
	}

	private static HintStep missing(String id) {
		AchieveHelper.LOGGER.warn("Hint refers to unknown id {}", id);
		return new HintStep(ItemStack.EMPTY, "?", Component.literal(id), false);
	}

	private static ItemStack spawnEgg(EntityType<?> type) {
		return SpawnEggItem.byId(type).map(h -> new ItemStack(h.value())).orElse(ItemStack.EMPTY);
	}

	public Icon resolve(String advancementId, String criterion) {
		return cache.computeIfAbsent(advancementId + "|" + criterion, k -> withFeed(advancementId, compute(advancementId, criterion)));
	}

	/** Adds what to feed the mob when the advancement is about breeding or taming. */
	private Icon withFeed(String advancementId, Icon icon) {
		String mode = feedModes.get(advancementId);
		EntityType<?> type = icon.entity();
		if (mode == null || type == null) return icon;
		List<String> listed = feedTables.getOrDefault(mode, Map.of()).get(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
		List<ItemStack> feed = listed != null
				? listed.stream().map(IconResolver::item).filter(s -> !s.isEmpty()).toList()
				: "breed".equals(mode) ? foundFood.computeIfAbsent(type, IconResolver::findFood) : List.of();
		return feed.isEmpty() ? icon : icon.withFeed(feed, mode);
	}

	/** For mobs from other mods: ask the animal itself which items count as its food. */
	private static List<ItemStack> findFood(EntityType<?> type) {
		var level = net.minecraft.client.Minecraft.getInstance().level;
		if (level == null) return List.of();
		try {
			if (!(type.create(level, net.minecraft.world.entity.EntitySpawnReason.LOAD) instanceof net.minecraft.world.entity.animal.Animal animal)) {
				return List.of();
			}
			List<ItemStack> out = new java.util.ArrayList<>();
			for (Item item : BuiltInRegistries.ITEM) {
				ItemStack stack = new ItemStack(item);
				if (!stack.isEmpty() && animal.isFood(stack)) out.add(stack);
				if (out.size() == 3) break;
			}
			return List.copyOf(out);
		} catch (RuntimeException e) {
			AchieveHelper.LOGGER.debug("No food lookup for {}", type, e);
			return List.of();
		}
	}

	private Icon compute(String advancementId, String criterion) {
		Map<String, JsonElement> table = perAdvancement.get(advancementId);
		if (table != null) {
			JsonElement entry = table.containsKey(criterion) ? table.get(criterion) : table.get("*");
			if (entry != null) return fromTable(entry, criterion);
		}

		List<String> candidates = CriterionNames.candidates(criterion);
		if (!candidates.isEmpty()) {
			String biomeItem = biomes.get(candidates.getFirst());
			if (biomeItem != null) {
				Identifier id = Identifier.parse(candidates.getFirst());
				return new Icon(item(biomeItem), Component.translatable(id.toLanguageKey("biome")), false);
			}
		}

		for (int i = 0; i < candidates.size(); i++) {
			Identifier id = Identifier.tryParse(candidates.get(i));
			if (id == null) continue;
			// "minecraft:chicken" in Two by Two is the mob; bare "chicken" in A Balanced Diet is the food.
			if (i == 0 && criterion.indexOf(':') >= 0 && BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
				EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
				ItemStack egg = spawnEgg(type);
				if (!egg.isEmpty()) return new Icon(egg, type.getDescription(), false);
			}
			Optional<ItemStack> found = byItemOrBlock(id);
			if (found.isPresent()) return new Icon(found.get(), found.get().getHoverName(), i == 0);
			Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.containsKey(id)
					? BuiltInRegistries.ENTITY_TYPE.getOptional(id) : Optional.empty();
			if (type.isPresent()) {
				return new Icon(spawnEgg(type.get()), type.get().getDescription(), false);
			}
		}
		return new Icon(ItemStack.EMPTY, Component.literal(CriterionNames.humanize(criterion)), false);
	}

	/** A table value: an item id, or {item, mob, component, registry} where the criterion names the variant. */
	private static Icon fromTable(JsonElement entry, String criterion) {
		if (!entry.isJsonObject()) return new Icon(item(entry.getAsString()), label(criterion), false);
		JsonObject obj = entry.getAsJsonObject();
		ItemStack fallback = obj.has("item") ? item(obj.get("item").getAsString()) : ItemStack.EMPTY;
		MobPreview mob = null;
		Identifier mobId = obj.has("mob") ? Identifier.tryParse(obj.get("mob").getAsString()) : null;
		if (mobId != null && BuiltInRegistries.ENTITY_TYPE.containsKey(mobId)) {
			String variant = criterion.indexOf(':') >= 0 ? criterion : "minecraft:" + criterion;
			mob = new MobPreview(BuiltInRegistries.ENTITY_TYPE.getValue(mobId),
					obj.has("component") ? obj.get("component").getAsString() : null,
					obj.has("registry") ? obj.get("registry").getAsString() : null,
					obj.has("component") ? variant : null);
		}
		return new Icon(fallback, label(criterion), false, mob);
	}

	private static Optional<ItemStack> byItemOrBlock(Identifier id) {
		if (BuiltInRegistries.ITEM.containsKey(id)) {
			Item item = BuiltInRegistries.ITEM.getValue(id);
			if (item != Items.AIR) return Optional.of(new ItemStack(item));
		}
		if (BuiltInRegistries.BLOCK.containsKey(id)) {
			Block block = BuiltInRegistries.BLOCK.getValue(id);
			Item item = block.asItem();
			if (item != Items.AIR) return Optional.of(new ItemStack(item));
		}
		return Optional.empty();
	}

	private static ItemStack item(String id) {
		Identifier parsed = Identifier.tryParse(id);
		return parsed != null && BuiltInRegistries.ITEM.containsKey(parsed)
				? new ItemStack(BuiltInRegistries.ITEM.getValue(parsed)) : ItemStack.EMPTY;
	}

	private static Component label(String criterion) {
		return Component.literal(CriterionNames.humanize(criterion));
	}
}
