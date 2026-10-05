package dev.achievehelper.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Items;

import dev.achievehelper.client.AdvancementTracker;
import dev.achievehelper.client.ChecklistScreen;
import dev.achievehelper.client.GoalDetailScreen;
import dev.achievehelper.client.HudEditorScreen;
import dev.achievehelper.client.ModConfig;
import dev.achievehelper.client.SettingsScreen;
import dev.achievehelper.client.ProgressToast;
import dev.achievehelper.client.WorldHints;
import dev.achievehelper.client.TrackedGoal;
import dev.achievehelper.core.Hint;
import dev.achievehelper.core.HintToken;

/** Grants some advancement progress and screenshots the HUD, the progress toast and the checklist. */
public final class AchieveHelperClientTest implements FabricClientGameTest {
	private static final String DIET = "minecraft:husbandry/balanced_diet";
	private static final String BIOMES = "minecraft:adventure/adventuring_time";
	private static final String CATS = "minecraft:husbandry/complete_catalogue";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set day");
			world.getServer().runCommand("advancement grant @a only minecraft:story/root");
			world.getServer().runCommand("advancement grant @a only minecraft:husbandry/root");
			world.getServer().runCommand("advancement grant @a only minecraft:adventure/root");
			for (String food : new String[] {"apple", "bread", "beef", "carrot", "cookie", "melon_slice"}) {
				world.getServer().runCommand("advancement grant @a only " + DIET + " " + food);
			}
			for (String biome : new String[] {"plains", "forest", "river", "beach", "desert"}) {
				world.getServer().runCommand("advancement grant @a only " + BIOMES + " minecraft:" + biome);
			}
			world.getServer().runCommand("advancement grant @a only " + CATS + " minecraft:tabby");
			world.getConnection().waitForClientboundPackets();
			context.waitTicks(5);

			context.runOnClient(mc -> {
				AdvancementTracker tracker = AdvancementTracker.INSTANCE;
				for (String id : new String[] {DIET, BIOMES, CATS}) {
					if (!tracker.isPinned(id)) tracker.togglePin(id);
				}
				TrackedGoal diet = tracker.all().stream().filter(g -> g.id().equals(DIET)).findFirst().orElseThrow();
				// "minecraft:chicken" in Two by Two is the mob, not the raw chicken item
				var chicken = tracker.icons().resolve("minecraft:husbandry/bred_all_animals", "minecraft:chicken");
				check(chicken.entity() == EntityTypes.CHICKEN, "two by two chicken icon: " + chicken.stack());
				check(tracker.icons().resolve(DIET, "chicken").stack().is(Items.CHICKEN), "balanced diet chicken icon");
				check(diet.goal().completed() == 6, "diet progress " + diet.goal().completed());
				check(!tracker.wantedBy(Items.GOLDEN_APPLE).isEmpty(), "golden apple should be wanted");
				check(tracker.wantedBy(Items.APPLE).isEmpty(), "apple already eaten");
				long noIcon = tracker.all().stream().flatMap(g -> g.remainingIcons().stream()).filter(i -> i.stack().isEmpty()).count();
				System.out.println("[achievehelper-test] goals=" + tracker.all().size() + " iconless criteria=" + noIcon);
				tracker.all().stream().flatMap(g -> g.remainingIcons().stream().filter(i -> i.stack().isEmpty())
						.map(i -> g.id() + " -> " + i.label().getString())).forEach(s -> System.out.println("[achievehelper-test] no icon: " + s));
			});
			context.waitTicks(10);
			context.takeScreenshot("achievehelper-hud");

			checkHintCoverage(context, world);
			context.runOnClient(mc -> {
				AdvancementTracker tracker = AdvancementTracker.INSTANCE;
				for (String id : new String[] {DIET, BIOMES, CATS}) tracker.togglePin(id);
				for (String id : new String[] {"minecraft:story/mine_stone", "minecraft:husbandry/plant_seed"}) {
					if (!tracker.isPinned(id)) tracker.togglePin(id);
				}
				TrackedGoal suggestion = tracker.suggestion();
				System.out.println("[achievehelper-test] top suggestions: " + tracker.all().stream().filter(t -> t == suggestion).map(TrackedGoal::id).toList());
				check(suggestion != null && suggestion.goal().effort() <= 2, "suggestion should be an easy goal: " + (suggestion == null ? null : suggestion.id()));
			});
			context.waitTicks(5);
			context.takeScreenshot("achievehelper-hints");
			context.runOnClient(mc -> {
				AdvancementTracker tracker = AdvancementTracker.INSTANCE;
				for (String id : new String[] {"minecraft:story/mine_stone", "minecraft:husbandry/plant_seed"}) tracker.togglePin(id);
				for (String id : new String[] {DIET, BIOMES, CATS}) tracker.togglePin(id);
			});

			checkWorldHints(context, world);
			checkDisplay(context, world);
			checkFeedingAndItems(context, world);
			checkHudEditor(context);
			checkUnpin(context);

			world.getServer().runCommand("advancement grant @a only " + DIET + " golden_apple");
			world.getConnection().waitForClientboundPackets();
			context.waitTicks(15);
			context.takeScreenshot("achievehelper-toast");

			context.setScreen(ChecklistScreen::new);
			context.waitTicks(5);
			context.getInput().setCursorPos(15 * 2 * 2 + 4, 25 * 2 * 2 + 4);
			context.waitTicks(3);
			context.takeScreenshot("achievehelper-checklist");
			context.getInput().setCursorPos((10 + 22 * 3 + 10) * 2, (18 + 10) * 2);
			context.waitTick();
			context.getInput().pressMouse(0);
			context.waitTicks(3);
			context.takeScreenshot("achievehelper-checklist-easy");
			context.runOnClient(mc -> check("EASY".equals(AdvancementTracker.INSTANCE.config().checklistFilter), "easy filter selected"));
			context.getInput().setCursorPos((10 + 10) * 2, (18 + 10) * 2);
			context.waitTick();
			context.getInput().pressMouse(0);
			context.setScreen(() -> null);
		}
	}

	/** A needed mob in sight gets an outline, an unneeded one does not; entering a hinted biome shows a reminder. */
	private static void checkWorldHints(ClientGameTestContext context, TestSingleplayerContext world) {
		String killMob = "minecraft:adventure/kill_a_mob";
		context.runOnClient(mc -> {
			AdvancementTracker tracker = AdvancementTracker.INSTANCE;
			for (String id : new String[] {DIET, BIOMES, CATS}) tracker.togglePin(id);
			tracker.togglePin(killMob);
		});
		world.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 0");
		world.getServer().runCommand("execute as @a at @s run summon minecraft:husk ~-2 ~ ~6 {NoAI:1b,PersistenceRequired:1b}");
		world.getServer().runCommand("execute as @a at @s run summon minecraft:cow ~2 ~ ~6 {NoAI:1b,PersistenceRequired:1b}");
		// Far away and behind a wall: still outlined, the whole loaded area counts.
		world.getServer().runCommand("execute as @a at @s run summon minecraft:husk ~10 ~ ~60 {NoAI:1b,PersistenceRequired:1b,Tags:[\"far\"]}");
		world.getServer().runCommand("execute as @a at @s run fill ~6 ~ ~55 ~14 ~3 ~55 minecraft:stone");
		world.getConnection().waitForClientboundPackets();
		context.waitTicks(20);
		context.runOnClient(mc -> {
			boolean husk = false;
			boolean cow = false;
			int husks = 0;
			for (var e : mc.level.entitiesForRendering()) {
				if (e.getType() == EntityTypes.HUSK) {
					husks++;
					check(WorldHints.INSTANCE.isHighlighted(e), "every husk should be outlined, distance " + (int) e.distanceTo(mc.player));
				}
				if (e.getType() == EntityTypes.HUSK) husk |= WorldHints.INSTANCE.isHighlighted(e);
				if (e.getType() == EntityTypes.COW) cow |= WorldHints.INSTANCE.isHighlighted(e);
			}
			check(husk && husks == 2, "both husks should be loaded and outlined, loaded: " + husks);
			check(!cow, "cow is not needed and must not be outlined");
		});
		context.takeScreenshot("achievehelper-outline");

		// Sweet Dreams unlocks Sound of Music, whose hint names the Meadow biome. The player stands at y=-60,
		// so the box must stay above the world bottom or the whole command is rejected.
		world.getServer().runCommand("advancement grant @a only minecraft:adventure/sleep_in_bed");
		world.getServer().runCommand("execute as @a at @s run fillbiome ~-12 -64 ~-12 ~12 -32 ~12 minecraft:meadow");
		world.getConnection().waitForClientboundPackets();
		context.waitTicks(45);
		context.takeScreenshot("achievehelper-biome-reminder");
		context.runOnClient(mc -> {
			check(mc.gui.toastManager().getToast(ProgressToast.class, "minecraft:adventure/play_jukebox_in_meadows") != null,
					"entering a Meadow should remind about Sound of Music");
			AdvancementTracker tracker = AdvancementTracker.INSTANCE;
			tracker.togglePin(killMob);
			for (String id : new String[] {DIET, BIOMES, CATS}) tracker.togglePin(id);
		});
		world.getServer().runCommand("kill @e[type=!player]");
	}

	/** Cat variants as models, the detail screen, the settings screen, and a compact HUD in another corner. */
	private static void checkDisplay(ClientGameTestContext context, TestSingleplayerContext world) {
		for (String cat : new String[] {"black", "calico", "siamese"}) {
			world.getServer().runCommand("advancement grant @a only " + CATS + " minecraft:" + cat);
		}
		world.getConnection().waitForClientboundPackets();
		context.waitFor(mc -> AdvancementTracker.INSTANCE.get(CATS) != null && AdvancementTracker.INSTANCE.get(CATS).goal().completed() == 4);
		context.runOnClient(mc -> {
			AdvancementTracker tracker = AdvancementTracker.INSTANCE;
			for (String id : new String[] {DIET, BIOMES}) {
				if (tracker.isPinned(id)) tracker.togglePin(id);
			}
			if (!tracker.isPinned(CATS)) tracker.togglePin(CATS);
			TrackedGoal cats = tracker.get(CATS);
			check(cats.goal().completed() == 4 && cats.remainingIcons().size() == 7, "cats 4 done / 7 left: " + cats.goal().completed());
			for (var icon : cats.remainingIcons()) {
				check(icon.mob() != null, "cat icon should be a mob preview: " + icon.label().getString());
				var cat = icon.mob().entity();
				check(cat != null, "cat preview entity for " + icon.label().getString());
				var variant = cat.get(DataComponents.CAT_VARIANT);
				check(variant != null && variant.unwrapKey().orElseThrow().identifier().toString().equals(icon.mob().variant()),
						"cat variant applied: " + icon.mob().variant() + " got " + variant);
			}
		});
		context.waitTicks(160); // let earlier toasts fade
		context.takeScreenshot("achievehelper-cats-hud");

		context.setScreen(() -> new GoalDetailScreen(null, CATS));
		context.waitTicks(5);
		context.takeScreenshot("achievehelper-cats-detail");

		context.setScreen(() -> new SettingsScreen(null));
		context.waitTicks(5);
		context.takeScreenshot("achievehelper-settings");
		context.setScreen(() -> null);

		context.runOnClient(mc -> {
			ModConfig c = AdvancementTracker.INSTANCE.config();
			c.compact = true;
			c.hudCorner = ModConfig.HudCorner.TOP_RIGHT;
			c.hudScale = 80;
		});
		context.waitTicks(3);
		context.takeScreenshot("achievehelper-hud-compact");
		context.runOnClient(mc -> {
			ModConfig c = AdvancementTracker.INSTANCE.config();
			c.compact = false;
			c.hudCorner = ModConfig.HudCorner.TOP_LEFT;
			c.hudScale = 100;
			AdvancementTracker tracker = AdvancementTracker.INSTANCE;
			tracker.togglePin(CATS);
			for (String id : new String[] {DIET, BIOMES}) tracker.togglePin(id);
		});
	}

	/** Breeding food on Two by Two icons; needed items framed in the inventory and outlined on the ground. */
	private static void checkFeedingAndItems(ClientGameTestContext context, TestSingleplayerContext world) {
		String twoByTwo = "minecraft:husbandry/bred_all_animals";
		world.getServer().runCommand("advancement grant @a only minecraft:husbandry/breed_an_animal");
		world.getServer().runCommand("advancement grant @a only " + twoByTwo + " minecraft:sheep");
		context.waitFor(mc -> AdvancementTracker.INSTANCE.get(twoByTwo) != null);
		context.runOnClient(mc -> {
			AdvancementTracker tracker = AdvancementTracker.INSTANCE;
			var cow = tracker.icons().resolve(twoByTwo, "minecraft:cow");
			check(!cow.feed().isEmpty() && cow.feed().getFirst().is(Items.WHEAT), "cow should be fed wheat: " + cow.feed());
			var mule = tracker.icons().resolve(twoByTwo, "minecraft:mule");
			check(mule.tooltip().size() == 3, "mule tooltip should explain crossing: " + mule.tooltip());
			var cat = tracker.icons().resolve(CATS, "minecraft:jellie");
			check(!cat.feed().isEmpty() && cat.feed().getFirst().is(Items.COD), "cats are tamed with cod: " + cat.feed());
			check(tracker.isWanted(Items.GOLDEN_CARROT), "golden carrot is still needed for A Balanced Diet");
			check(!tracker.isWanted(Items.APPLE), "apple was already eaten");
		});
		context.setScreen(() -> new GoalDetailScreen(null, twoByTwo));
		context.waitTicks(5);
		context.takeScreenshot("achievehelper-two-by-two");

		world.getServer().runCommand("give @a minecraft:golden_carrot 3");
		world.getServer().runCommand("give @a minecraft:apple 3");
		world.getServer().runCommand("give @a minecraft:spider_eye 1");
		world.getServer().runCommand("execute as @a at @s run summon minecraft:item ~1 ~ ~4 {Item:{id:\"minecraft:golden_carrot\",count:1},PickupDelay:32767}");
		world.getConnection().waitForClientboundPackets();
		context.waitTicks(10);
		context.runOnClient(mc -> {
			boolean outlined = false;
			for (var e : mc.level.entitiesForRendering()) {
				if (e instanceof net.minecraft.world.entity.item.ItemEntity item && item.getItem().is(Items.GOLDEN_CARROT)) {
					outlined |= WorldHints.INSTANCE.isHighlighted(e);
				}
			}
			check(outlined, "dropped golden carrot should be outlined");
		});
		context.setScreen(() -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
		context.waitTicks(5);
		context.takeScreenshot("achievehelper-inventory");
		context.setScreen(() -> null);
		world.getServer().runCommand("clear @a");
		world.getServer().runCommand("kill @e[type=item]");
	}

	/** The detail screen's button unpins; "unpin all" clears every pin. */
	private static void checkUnpin(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			if (!AdvancementTracker.INSTANCE.isPinned(CATS)) AdvancementTracker.INSTANCE.togglePin(CATS);
		});
		context.setScreen(() -> new GoalDetailScreen(null, CATS));
		context.waitTicks(3);
		context.takeScreenshot("achievehelper-detail-unpin");
		// The pin button sits in the lower right corner (GUI 427x240 at scale 2).
		context.getInput().setCursorPos((427 - 10 - 55) * 2, (240 - 16) * 2);
		context.waitTick();
		context.getInput().pressMouse(0);
		context.waitTicks(2);
		context.runOnClient(mc -> check(!AdvancementTracker.INSTANCE.isPinned(CATS), "the detail screen button should unpin"));
		context.setScreen(() -> null);
		context.runOnClient(mc -> {
			AdvancementTracker tracker = AdvancementTracker.INSTANCE;
			tracker.unpinAll();
			check(!tracker.hasPins(), "unpin all leaves no pins");
			for (String id : new String[] {DIET, BIOMES}) tracker.togglePin(id);
		});
	}

	/** Dragging the panel to the lower right re-anchors it there. */
	private static void checkHudEditor(ClientGameTestContext context) {
		context.setScreen(() -> new HudEditorScreen(null));
		context.waitTicks(3);
		context.getInput().setCursorPos(60 * 2, 12 * 2);
		context.waitTick();
		context.getInput().holdMouse(0);
		context.waitTick();
		context.getInput().moveCursor(250, 150);
		context.waitTick();
		context.getInput().moveCursor(250, 150);
		context.waitTick();
		context.getInput().releaseMouse(0);
		context.waitTicks(2);
		context.takeScreenshot("achievehelper-hud-editor");
		context.runOnClient(mc -> {
			ModConfig c = AdvancementTracker.INSTANCE.config();
			check(c.hudCorner == ModConfig.HudCorner.BOTTOM_RIGHT, "dragged to the lower right: " + c.hudCorner + " " + c.hudOffsetX + "," + c.hudOffsetY);
		});
		context.setScreen(() -> null);
		context.waitTicks(2);
		context.takeScreenshot("achievehelper-hud-moved");
		context.runOnClient(mc -> {
			ModConfig c = AdvancementTracker.INSTANCE.config();
			c.hudCorner = ModConfig.HudCorner.TOP_LEFT;
			c.hudOffsetX = 0;
			c.hudOffsetY = 0;
		});
	}

	/** Every vanilla single-action advancement needs a hint, and every hint token must resolve. */
	private static void checkHintCoverage(ClientGameTestContext context, TestSingleplayerContext world) {
		List<String[]> all = world.getServer().computeOnServer(server -> server.getAdvancements().getAllAdvancements().stream()
				.filter(h -> h.value().display().isPresent())
				.map(h -> new String[] {h.id().toString(), String.valueOf(h.value().requirements().size())})
				.toList());
		context.runOnClient(mc -> {
			AdvancementTracker tracker = AdvancementTracker.INSTANCE;
			List<String> problems = new ArrayList<>();
			int hinted = 0;
			for (String[] adv : all) {
				Hint hint = tracker.hints().get(adv[0]);
				if (hint == null) {
					if (adv[0].startsWith("minecraft:") && adv[1].equals("1")) problems.add("no hint: " + adv[0]);
					continue;
				}
				hinted++;
				for (HintToken token : hint.steps()) {
					if (!tracker.icons().step(token).resolved()) problems.add("unresolved " + token + " in " + adv[0]);
				}
			}
			System.out.println("[achievehelper-test] advancements=" + all.size() + " hinted=" + hinted);
			problems.forEach(p -> System.out.println("[achievehelper-test] " + p));
			check(problems.isEmpty(), problems.size() + " hint problems");
		});
	}

	private static void check(boolean ok, String message) {
		if (!ok) throw new AssertionError(message);
	}
}
