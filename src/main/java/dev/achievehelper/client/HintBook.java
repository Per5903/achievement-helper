package dev.achievehelper.client;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import dev.achievehelper.AchieveHelper;
import dev.achievehelper.core.Hint;

/**
 * Icon hints from {@code assets/<namespace>/achievehelper_hints/*.json} in every loaded mod and resource pack,
 * so modpacks can describe their own advancements. Loaded on first use, dropped on disconnect.
 */
public final class HintBook {
	private static final String DIRECTORY = "achievehelper_hints";

	private Map<String, Hint> hints;

	public Hint get(String advancementId) {
		if (hints == null) hints = load();
		return hints.get(advancementId);
	}

	public void invalidate() {
		hints = null;
	}

	private static Map<String, Hint> load() {
		Map<String, Hint> out = new HashMap<>();
		Language language = Language.getInstance();
		Map<Identifier, Resource> files = Minecraft.getInstance().getResourceManager()
				.listResources(DIRECTORY, id -> id.getPath().endsWith(".json"));
		for (Map.Entry<Identifier, Resource> file : files.entrySet()) {
			try (Reader reader = file.getValue().openAsReader()) {
				for (Map.Entry<String, JsonElement> e : JsonParser.parseReader(reader).getAsJsonObject().entrySet()) {
					if (e.getKey().startsWith("_")) continue;
					// Either ["steps"...] or {"effort": 1..5, "steps": [...]}
					JsonArray array;
					int effort = Hint.DEFAULT_EFFORT;
					if (e.getValue().isJsonObject()) {
						JsonObject obj = e.getValue().getAsJsonObject();
						array = obj.getAsJsonArray("steps");
						if (obj.has("effort")) effort = obj.get("effort").getAsInt();
					} else {
						array = e.getValue().getAsJsonArray();
					}
					List<String> steps = new ArrayList<>();
					for (JsonElement step : array) steps.add(step.getAsString());
					String noteKey = Hint.noteKeyFor(e.getKey());
					out.put(e.getKey(), Hint.parse(steps, language.has(noteKey) ? noteKey : null, effort));
				}
			} catch (Exception ex) {
				AchieveHelper.LOGGER.warn("Skipping broken hint file {}", file.getKey(), ex);
			}
		}
		AchieveHelper.LOGGER.info("Loaded {} advancement hints from {} files", out.size(), files.size());
		return out;
	}
}
