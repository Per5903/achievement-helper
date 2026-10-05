package dev.achievehelper.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Remembers completed criteria between updates and reports what changed. */
public final class ProgressDiff {
	public sealed interface Event permits Gained, Completed {
		Goal goal();
	}

	/** One more group of a checklist goal was satisfied by {@code criterion}. */
	public record Gained(Goal goal, String criterion) implements Event {
	}

	public record Completed(Goal goal) implements Event {
	}

	private final Map<String, Set<String>> doneCriteria = new HashMap<>();
	private final Set<String> doneGoals = new HashSet<>();
	private boolean baseline = true;

	/** Forget everything; the next {@link #update} only records state and reports nothing. */
	public void reset() {
		doneCriteria.clear();
		doneGoals.clear();
		baseline = true;
	}

	public List<Event> update(List<Goal> goals) {
		List<Event> events = new ArrayList<>();
		for (Goal goal : goals) {
			Set<String> now = new HashSet<>();
			for (Goal.Group g : goal.groups()) {
				if (g.done()) now.addAll(g.criteria());
			}
			Set<String> before = doneCriteria.put(goal.id(), now);
			boolean wasDone = !goal.done() ? doneGoals.remove(goal.id()) : !doneGoals.add(goal.id());
			if (baseline || before == null) continue;

			if (goal.done() && !wasDone) {
				events.add(new Completed(goal));
			} else if (!goal.done() && goal.isChecklist()) {
				for (Goal.Group g : goal.groups()) {
					if (!g.done()) continue;
					for (String c : g.criteria()) {
						if (now.contains(c) && !before.contains(c)) {
							events.add(new Gained(goal, c));
							break;
						}
					}
				}
			}
		}
		baseline = false;
		return events;
	}
}
