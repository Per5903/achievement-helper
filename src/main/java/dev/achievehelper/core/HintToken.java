package dev.achievehelper.core;

/**
 * One element of an icon hint chain such as {@code ["raw_iron", "+", "furnace", ">", "iron_ingot"]}.
 * <ul>
 * <li>{@code apple}, {@code mod:thing} — item (or block) icon</li>
 * <li>{@code entity:zombie} — spawn egg of the mob</li>
 * <li>{@code biome:meadow} — biome icon from the icon table</li>
 * <li>{@code >} arrow, {@code +} plus, {@code |} gap between two ideas</li>
 * <li>{@code #50m} — short literal text (numbers, coordinates); keep it language-neutral</li>
 * </ul>
 */
public sealed interface HintToken {
	record Item(String id) implements HintToken {
	}

	record Entity(String id) implements HintToken {
	}

	record Biome(String id) implements HintToken {
	}

	record Symbol(String glyph) implements HintToken {
	}

	record Text(String text) implements HintToken {
	}

	static HintToken parse(String raw) {
		String s = raw.trim();
		if (s.isEmpty()) throw new IllegalArgumentException("empty hint token");
		return switch (s) {
			case ">" -> new Symbol("→");
			case "+" -> new Symbol("+");
			case "|" -> new Symbol("|");
			default -> {
				if (s.startsWith("#")) {
					if (s.length() == 1) throw new IllegalArgumentException("empty text token");
					yield new Text(s.substring(1));
				}
				if (s.startsWith("entity:")) yield new Entity(withNamespace(s.substring(7)));
				if (s.startsWith("biome:")) yield new Biome(withNamespace(s.substring(6)));
				yield new Item(withNamespace(s));
			}
		};
	}

	private static String withNamespace(String id) {
		return id.indexOf(':') >= 0 ? id : "minecraft:" + id;
	}
}
