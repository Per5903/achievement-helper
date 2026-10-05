package dev.achievehelper.core;

import java.util.List;

/**
 * Version-independent snapshot of one advancement: its requirement groups and progress.
 * A group is satisfied when any of its criteria is done; the goal is done when all groups are.
 * {@code effort} (1..5) comes from the hint, {@link Hint#DEFAULT_EFFORT} without one.
 */
public record Goal(String id, String parentId, boolean challenge, boolean done, List<Group> groups, int effort) {

	public record Group(List<String> criteria, boolean done) {
		/** The criterion that best represents this group (the first one). */
		public String primary() {
			return criteria.getFirst();
		}
	}

	public int total() {
		return groups.size();
	}

	public int completed() {
		int n = 0;
		for (Group g : groups) {
			if (g.done()) n++;
		}
		return n;
	}

	public List<Group> remaining() {
		return groups.stream().filter(g -> !g.done()).toList();
	}

	public float fraction() {
		return total() == 0 ? (done ? 1f : 0f) : (float) completed() / total();
	}

	public boolean started() {
		return completed() > 0;
	}

	/** True when the goal is a checklist (several things to collect) rather than a single action. */
	public boolean isChecklist() {
		return total() > 1;
	}
}
