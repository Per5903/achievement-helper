package dev.achievehelper.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class HintTest {
	@Test
	void parsesAllTokenKinds() {
		Hint hint = Hint.parse(List.of("raw_iron", "+", "entity:blaze", ">", "biome:meadow", "|", "#Y-58", "mymod:gear"), null, 2);
		assertEquals(List.of(
				new HintToken.Item("minecraft:raw_iron"),
				new HintToken.Symbol("+"),
				new HintToken.Entity("minecraft:blaze"),
				new HintToken.Symbol("→"),
				new HintToken.Biome("minecraft:meadow"),
				new HintToken.Symbol("|"),
				new HintToken.Text("Y-58"),
				new HintToken.Item("mymod:gear")), hint.steps());
	}

	@Test
	void rejectsEmptyTokens() {
		assertThrows(IllegalArgumentException.class, () -> HintToken.parse(" "));
		assertThrows(IllegalArgumentException.class, () -> HintToken.parse("#"));
		assertThrows(IllegalArgumentException.class, () -> Hint.parse(List.of("apple"), null, 0));
	}

	@Test
	void noteKey() {
		assertEquals("achievehelper.hint.minecraft.story.mine_diamond", Hint.noteKeyFor("minecraft:story/mine_diamond"));
	}
}
