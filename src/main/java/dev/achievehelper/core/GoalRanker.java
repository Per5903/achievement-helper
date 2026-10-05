package dev.achievehelper.core;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Picks which unfinished goals are worth suggesting next. */
public final class GoalRanker {
	private GoalRanker() {
	}

	/**
	 * Returns unfinished goals whose parent is done (or that have no parent), best first.
	 * Started checklists close to completion rank highest, then single actions; harder goals and challenges sink.
	 */
	public static List<Goal> rank(List<Goal> goals) {
		Map<String, Goal> byId = goals.stream().collect(Collectors.toMap(Goal::id, Function.identity(), (a, b) -> a));
		return goals.stream()
				.filter(g -> !g.done())
				.filter(g -> isAvailable(g, byId))
				.sorted(Comparator.comparingDouble(GoalRanker::score).reversed().thenComparing(Goal::id))
				.toList();
	}

	public static boolean isAvailable(Goal goal, Map<String, Goal> byId) {
		if (goal.parentId() == null) return true;
		Goal parent = byId.get(goal.parentId());
		return parent == null || parent.done();
	}

	static double score(Goal g) {
		double s = g.fraction() * 100;
		if (g.started()) s += 25;
		if (!g.isChecklist()) s += 30; // a single action is usually quick
		s -= Math.min(g.remaining().size(), 40) * 0.5;
		if (g.challenge()) s -= 40;
		s -= (g.effort() - 1) * 12;
		return s;
	}
}
