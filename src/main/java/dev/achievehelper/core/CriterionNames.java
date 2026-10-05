package dev.achievehelper.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns advancement criterion names into candidate registry ids.
 * Vanilla names are often ids already ({@code minecraft:zombie}, {@code apple}); others embed one
 * ({@code place_pale_oak_log}, {@code armor_trimmed_minecraft:rib_armor_trim_smithing_template_smithing_trim}).
 */
public final class CriterionNames {
	private CriterionNames() {
	}

	/** Candidate ids in {@code namespace:path} form, most specific first. */
	public static List<String> candidates(String criterion) {
		Set<String> out = new LinkedHashSet<>();
		String name = criterion.toLowerCase(Locale.ROOT);

		String namespace = "minecraft";
		String path = name;
		int colon = name.lastIndexOf(':');
		if (colon >= 0) {
			String before = name.substring(0, colon);
			path = name.substring(colon + 1);
			int sep = before.lastIndexOf('_');
			// "armor_trimmed_minecraft" → namespace "minecraft"
			namespace = sep >= 0 ? before.substring(sep + 1) : before;
			if (sep < 0) out.add(namespace + ":" + path);
		}
		if (!isValidPath(path)) return List.copyOf(out);

		String[] tokens = path.split("_");
		for (int len = tokens.length; len >= 1; len--) {
			for (int start = 0; start + len <= tokens.length; start++) {
				String window = String.join("_", java.util.Arrays.copyOfRange(tokens, start, start + len));
				if (!window.isEmpty() && !STOP_WORDS.contains(window)) out.add(namespace + ":" + window);
			}
		}
		return new ArrayList<>(out);
	}

	/** "british_shorthair" → "British shorthair". */
	public static String humanize(String criterion) {
		String path = criterion.substring(criterion.lastIndexOf(':') + 1).replace('_', ' ').trim();
		if (path.isEmpty()) return criterion;
		return Character.toUpperCase(path.charAt(0)) + path.substring(1);
	}

	private static boolean isValidPath(String path) {
		if (path.isEmpty()) return false;
		for (int i = 0; i < path.length(); i++) {
			char c = path.charAt(i);
			if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '/' || c == '.' || c == '-')) return false;
		}
		return true;
	}

	/** Single words that happen to be item ids but never describe what a criterion wants. */
	private static final Set<String> STOP_WORDS = Set.of(
			"has", "get", "got", "obtain", "place", "placed", "use", "used", "kill", "killed", "loot",
			"crafted", "the", "a", "of", "with", "any", "all", "other", "directly", "smithing", "trim",
			"trimmed", "awake", "dormant", "common", "bridge", "treasure", "something", "by", "item", "block");
}
