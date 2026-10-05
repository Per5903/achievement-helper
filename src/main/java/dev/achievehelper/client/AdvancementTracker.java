package dev.achievehelper.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

import dev.achievehelper.core.Goal;
import dev.achievehelper.core.GoalRanker;
import dev.achievehelper.core.Hint;
import dev.achievehelper.core.PinList;
import dev.achievehelper.core.ProgressDiff;
import dev.achievehelper.mixin.ClientAdvancementsAccessor;

/** Mirrors the client's advancement state as {@link TrackedGoal}s and keeps pins and suggestions. */
public final class AdvancementTracker {
	public static final AdvancementTracker INSTANCE = new AdvancementTracker();

	private ModConfig config = new ModConfig();
	private PinList pins;
	private final IconResolver icons = new IconResolver();
	private final HintBook hints = new HintBook();
	private final ProgressDiff diff = new ProgressDiff();

	/** All displayable goals in tree order (tab by tab, depth first). */
	private Map<String, TrackedGoal> goals = Map.of();
	private List<TrackedGoal> ranked = List.of();
	private Map<Item, List<Component>> wantedItems = Map.of();
	private int suggestionOffset;

	private AdvancementTracker() {
	}

	public void init(ModConfig config) {
		this.config = config;
		applyConfig();
	}

	/** Re-reads limits that live outside the config object (pin count) after the settings screen changes them. */
	public void applyConfig() {
		List<String> current = pins == null ? config.pins : pins.ids();
		pins = new PinList(Math.max(1, config.maxPins));
		pins.setAll(current);
		config.pins = new ArrayList<>(pins.ids());
	}

	/** Looks up one tracked goal by advancement id. */
	public TrackedGoal get(String id) {
		return goals.get(id);
	}

	public ModConfig config() {
		return config;
	}

	public void onUpdate(ClientAdvancements advancements, boolean reset) {
		if (reset) diff.reset();
		Map<AdvancementHolder, AdvancementProgress> progress = ((ClientAdvancementsAccessor) advancements).achievehelper$progress();

		List<AdvancementNode> ordered = new ArrayList<>();
		for (AdvancementNode root : advancements.getTree().roots()) collect(root, ordered);

		Map<String, TrackedGoal> built = new LinkedHashMap<>();
		Map<Item, List<Component>> wanted = new HashMap<>();
		for (AdvancementNode node : ordered) {
			DisplayInfo display = node.holder().value().display().orElse(null);
			if (display == null) continue;
			TrackedGoal tracked = build(node, display, progress.get(node.holder()));
			built.put(tracked.id(), tracked);
			if (!tracked.goal().done()) {
				// Every missing criterion that names an item exactly ("diamond", "lava_bucket", the 40 foods...).
				for (Goal.Group group : tracked.goal().remaining()) {
					for (String criterion : group.criteria()) {
						IconResolver.Icon icon = icons.resolve(tracked.id(), criterion);
						if (!icon.exact()) continue;
						List<Component> goalsForItem = wanted.computeIfAbsent(icon.stack().getItem(), k -> new ArrayList<>());
						if (!goalsForItem.contains(tracked.title())) goalsForItem.add(tracked.title());
					}
				}
			}
		}
		goals = built;
		wantedItems = wanted;
		List<Goal> plain = built.values().stream().map(TrackedGoal::goal).toList();
		ranked = GoalRanker.rank(plain).stream().map(g -> built.get(g.id())).toList();

		for (ProgressDiff.Event event : diff.update(plain)) {
			TrackedGoal tracked = built.get(event.goal().id());
			switch (event) {
				case ProgressDiff.Gained gained -> {
					if (config.progressToasts) ProgressToast.show(tracked, icons.resolve(tracked.id(), gained.criterion()));
				}
				case ProgressDiff.Completed completed -> {
					if (pins.contains(tracked.id())) {
						pins.remove(tracked.id());
						savePins();
					}
				}
			}
		}
	}

	private static void collect(AdvancementNode node, List<AdvancementNode> out) {
		out.add(node);
		for (AdvancementNode child : node.children()) collect(child, out);
	}

	private TrackedGoal build(AdvancementNode node, DisplayInfo display, AdvancementProgress progress) {
		String id = node.holder().id().toString();
		List<Goal.Group> groups = new ArrayList<>();
		for (List<String> requirement : node.holder().value().requirements().requirements()) {
			boolean done = false;
			if (progress != null) {
				for (String criterion : requirement) {
					CriterionProgress cp = progress.getCriterion(criterion);
					if (cp != null && cp.isDone()) {
						done = true;
						break;
					}
				}
			}
			groups.add(new Goal.Group(List.copyOf(requirement), done));
		}
		String parent = node.parent() == null ? null : node.parent().holder().id().toString();
		boolean done = progress != null && progress.isDone();
		Hint hint = hints.get(id);
		Goal goal = new Goal(id, parent, display.getType() == AdvancementType.CHALLENGE, done, groups,
				hint == null ? Hint.DEFAULT_EFFORT : hint.effort());

		List<IconResolver.Icon> remaining = new ArrayList<>();
		List<IconResolver.Icon> completed = new ArrayList<>();
		if (goal.isChecklist()) {
			for (Goal.Group group : goal.groups()) {
				(group.done() ? completed : remaining).add(icons.resolve(id, group.primary()));
			}
		}
		List<HintStep> steps = hint == null ? List.of() : hint.steps().stream().map(icons::step).toList();
		Component note = hint == null || hint.noteKey() == null ? null : Component.translatable(hint.noteKey());
		return new TrackedGoal(goal, display.getTitle(), display.getDescription(), display.getIcon().create(),
				display.getType(), node.root().holder().id().toString(), List.copyOf(remaining), List.copyOf(completed), hint, steps, note);
	}

	public void clear() {
		goals = Map.of();
		ranked = List.of();
		wantedItems = Map.of();
		suggestionOffset = 0;
		hints.invalidate();
		MobPreview.clear();
		diff.reset();
	}

	public List<TrackedGoal> all() {
		return List.copyOf(goals.values());
	}

	public boolean isPinned(String id) {
		return pins.contains(id);
	}

	/** Pins or unpins, and says which in the action bar. */
	public void togglePin(String id) {
		boolean pinned = pins.toggle(id);
		savePins();
		TrackedGoal t = goals.get(id);
		if (t != null) {
			net.minecraft.client.Minecraft.getInstance().gui.hud.setOverlayMessage(
					Component.translatable(pinned ? "achievehelper.pin.pinned" : "achievehelper.pin.unpinned", t.title()), false);
		}
	}

	public void unpinAll() {
		pins.setAll(List.of());
		savePins();
		net.minecraft.client.Minecraft.getInstance().gui.hud.setOverlayMessage(Component.translatable("achievehelper.pin.cleared"), false);
	}

	public boolean hasPins() {
		return !pins.ids().isEmpty();
	}

	/** Goals to draw on the HUD: pins first, then (autopilot) the current suggestion. */
	public List<TrackedGoal> hudGoals() {
		List<TrackedGoal> out = new ArrayList<>();
		for (String id : pins.ids()) {
			TrackedGoal g = goals.get(id);
			if (g != null && !g.goal().done()) out.add(g);
		}
		if (config.autopilot && out.size() < config.maxPins) {
			TrackedGoal s = suggestion();
			if (s != null) out.add(s);
		}
		return out;
	}

	/** The suggested goal, skipping pinned ones; {@link #nextSuggestion()} moves along the ranking. */
	public TrackedGoal suggestion() {
		List<TrackedGoal> candidates = ranked.stream().filter(g -> !pins.contains(g.id())).toList();
		if (candidates.isEmpty()) return null;
		return candidates.get(Math.floorMod(suggestionOffset, candidates.size()));
	}

	public void nextSuggestion() {
		suggestionOffset++;
	}

	public void pinSuggestion() {
		TrackedGoal s = suggestion();
		if (s != null) togglePin(s.id());
	}

	/** Mobs that would advance a goal shown on the HUD: missing criteria plus mobs in its hint. */
	public Set<EntityType<?>> wantedEntities() {
		Set<EntityType<?>> out = new HashSet<>();
		for (TrackedGoal t : hudGoals()) {
			for (Goal.Group group : t.goal().remaining()) {
				for (String criterion : group.criteria()) {
					EntityType<?> type = icons.resolve(t.id(), criterion).entity();
					if (type != null) out.add(type);
				}
			}
			for (HintStep step : t.steps()) {
				if (step.entity() != null) out.add(step.entity());
			}
		}
		return out;
	}

	/** The hint book, for checks that cover advancements the player cannot see yet. */
	public HintBook hints() {
		return hints;
	}

	public IconResolver icons() {
		return icons;
	}

	/** True if some unfinished advancement needs exactly this item. */
	public boolean isWanted(Item item) {
		return wantedItems.containsKey(item);
	}

	public List<Component> wantedBy(Item item) {
		return wantedItems.getOrDefault(item, List.of());
	}

	private void savePins() {
		config.pins = new ArrayList<>(pins.ids());
		config.save();
	}

}
