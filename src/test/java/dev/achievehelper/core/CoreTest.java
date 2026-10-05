package dev.achievehelper.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class CoreTest {
	private static Goal goal(String id, String parent, boolean challenge, boolean... groupsDone) {
		List<Goal.Group> groups = new java.util.ArrayList<>();
		boolean all = true;
		for (int i = 0; i < groupsDone.length; i++) {
			groups.add(new Goal.Group(List.of(id + "_c" + i), groupsDone[i]));
			all &= groupsDone[i];
		}
		return new Goal(id, parent, challenge, all, groups, Hint.DEFAULT_EFFORT);
	}

	@Test
	void criterionCandidates() {
		assertEquals("minecraft:apple", CriterionNames.candidates("apple").getFirst());
		assertEquals("minecraft:zombie", CriterionNames.candidates("minecraft:zombie").getFirst());
		assertTrue(CriterionNames.candidates("place_pale_oak_log").contains("minecraft:pale_oak_log"));
		assertTrue(CriterionNames.candidates("place_creaking_heart_awake").contains("minecraft:creaking_heart"));
		assertTrue(CriterionNames.candidates("armor_trimmed_minecraft:rib_armor_trim_smithing_template_smithing_trim")
				.contains("minecraft:rib_armor_trim_smithing_template"));
		assertFalse(CriterionNames.candidates("place_creaking_heart_awake").contains("minecraft:place"));
		assertEquals("British shorthair", CriterionNames.humanize("minecraft:british_shorthair"));
	}

	@Test
	void rankerPrefersStartedChecklistsAndSkipsLocked() {
		Goal root = goal("root", null, false, true);
		Goal fresh = goal("fresh", "root", false, false);
		Goal half = goal("half", "root", false, true, true, false, false);
		Goal locked = goal("locked", "fresh", false, false);
		Goal challenge = goal("challenge", "root", true, true, false, false, false);

		List<Goal> ranked = GoalRanker.rank(List.of(root, fresh, half, locked, challenge));
		assertEquals(List.of("half", "fresh", "challenge"), ranked.stream().map(Goal::id).toList());
	}

	@Test
	void rankerPrefersLowEffort() {
		Goal root = goal("root", null, false, true);
		Goal hard = new Goal("a_hard", "root", false, false, List.of(new Goal.Group(List.of("x"), false)), 5);
		Goal easy = new Goal("z_easy", "root", false, false, List.of(new Goal.Group(List.of("x"), false)), 1);
		assertEquals(List.of("z_easy", "a_hard"), GoalRanker.rank(List.of(root, hard, easy)).stream().map(Goal::id).toList());
	}

	@Test
	void diffReportsGainsAfterBaseline() {
		ProgressDiff diff = new ProgressDiff();
		assertTrue(diff.update(List.of(goal("g", null, false, false, false))).isEmpty());

		List<ProgressDiff.Event> events = diff.update(List.of(goal("g", null, false, true, false)));
		assertEquals(1, events.size());
		assertEquals("g_c0", assertInstanceOf(ProgressDiff.Gained.class, events.getFirst()).criterion());

		events = diff.update(List.of(goal("g", null, false, true, true)));
		assertInstanceOf(ProgressDiff.Completed.class, events.getFirst());
		assertTrue(diff.update(List.of(goal("g", null, false, true, true))).isEmpty());
	}

	@Test
	void pinListDropsOldest() {
		PinList pins = new PinList(2);
		pins.toggle("a");
		pins.toggle("b");
		pins.toggle("c");
		assertEquals(List.of("b", "c"), pins.ids());
		assertFalse(pins.toggle("b"));
		assertEquals(List.of("c"), pins.ids());
	}
}
