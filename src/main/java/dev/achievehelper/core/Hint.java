package dev.achievehelper.core;

import java.util.List;

/**
 * How to get an advancement, as icons; {@code noteKey} names an optional short text line.
 * {@code effort} runs from 1 (first minutes of a world) to 5 (endgame) and steers suggestions.
 */
public record Hint(List<HintToken> steps, String noteKey, int effort) {
	public static final int DEFAULT_EFFORT = 3;

	public Hint {
		if (effort < 1 || effort > 5) throw new IllegalArgumentException("effort must be 1..5, got " + effort);
	}

	public static Hint parse(List<String> steps, String noteKey, int effort) {
		return new Hint(steps.stream().map(HintToken::parse).toList(), noteKey, effort);
	}

	/** Translation key for an advancement's note: {@code minecraft:story/mine_diamond → achievehelper.hint.minecraft.story.mine_diamond}. */
	public static String noteKeyFor(String advancementId) {
		return "achievehelper.hint." + advancementId.replace(':', '.').replace('/', '.');
	}
}
