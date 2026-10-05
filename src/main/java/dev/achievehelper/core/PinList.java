package dev.achievehelper.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Ordered set of pinned goal ids with a size limit; the oldest pin drops off when full. */
public final class PinList {
	private final List<String> ids = new ArrayList<>();
	private final int limit;

	public PinList(int limit) {
		this.limit = limit;
	}

	public boolean contains(String id) {
		return ids.contains(id);
	}

	/** Pins or unpins {@code id}; returns true if it is pinned afterwards. */
	public boolean toggle(String id) {
		if (ids.remove(id)) return false;
		ids.add(id);
		while (ids.size() > limit) ids.removeFirst();
		return true;
	}

	public void remove(String id) {
		ids.remove(id);
	}

	public void setAll(Collection<String> newIds) {
		ids.clear();
		for (String id : newIds) {
			if (!ids.contains(id)) ids.add(id);
		}
		while (ids.size() > limit) ids.removeFirst();
	}

	public List<String> ids() {
		return List.copyOf(ids);
	}
}
