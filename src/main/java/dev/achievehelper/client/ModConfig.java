package dev.achievehelper.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import net.fabricmc.loader.api.FabricLoader;

import dev.achievehelper.AchieveHelper;

/** Settings and pins, stored in config/achievehelper.json. */
public final class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve(AchieveHelper.MOD_ID + ".json");

	public boolean hud = true;
	public HudCorner hudCorner = HudCorner.TOP_LEFT;
	/** Distance from the chosen corner, in HUD pixels (set by dragging in {@link HudEditorScreen}). */
	public int hudOffsetX = 0;
	public int hudOffsetY = 0;
	/** Percent of the normal GUI size. */
	public int hudScale = 100;
	public int hudWidth = 172;
	/** Hide advancement names on the HUD: icons and numbers only. */
	public boolean compact = false;
	/** Show the short hint note (or the description when there is no hint). */
	public boolean showNotes = true;
	/** Draw cats, wolves and frogs of the right variant instead of identical spawn eggs. */
	public boolean mobModels = true;
	/** Show the best next goal on the HUD when fewer than {@link #maxPins} goals are pinned. */
	public boolean autopilot = true;
	public boolean progressToasts = true;
	public boolean itemTooltips = true;
	/** Gold outline on mobs needed by HUD goals. */
	public boolean highlightMobs = true;
	/** Blocks; 0 = every mob the client has loaded (the server's entity view distance). */
	public int highlightRange = 0;
	/** Only outline mobs in plain sight (for servers that treat outlines through walls as cheating). */
	public boolean highlightOnlyVisible = false;
	/** Gold frame on needed items in chests/inventory, and an outline on needed items lying on the ground. */
	public boolean highlightItems = true;
	/** Toast when entering a biome some unfinished advancement asks for. */
	public boolean biomeReminders = true;
	public String checklistFilter = "ALL";
	public int maxPins = 3;
	public int iconRows = 2;
	public List<String> pins = new ArrayList<>();

	public enum HudCorner {
		TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

		public boolean right() {
			return this == TOP_RIGHT || this == BOTTOM_RIGHT;
		}

		public boolean bottom() {
			return this == BOTTOM_LEFT || this == BOTTOM_RIGHT;
		}

		public static HudCorner of(boolean right, boolean bottom) {
			return bottom ? (right ? BOTTOM_RIGHT : BOTTOM_LEFT) : (right ? TOP_RIGHT : TOP_LEFT);
		}

		public HudCorner flipSide() {
			return of(!right(), bottom());
		}

		public HudCorner flipVertical() {
			return of(right(), !bottom());
		}
	}

	/** Pull values back into range after hand-editing the file. */
	public ModConfig sanitize() {
		if (hudCorner == null) hudCorner = HudCorner.TOP_LEFT;
		hudScale = Math.clamp(hudScale, 50, 200);
		hudWidth = Math.clamp(hudWidth, 120, 320);
		maxPins = Math.clamp(maxPins, 1, 6);
		iconRows = Math.clamp(iconRows, 0, 6);
		highlightRange = Math.max(0, highlightRange);
		if (pins == null) pins = new ArrayList<>();
		if (checklistFilter == null) checklistFilter = "ALL";
		return this;
	}

	public static ModConfig load() {
		if (Files.exists(FILE)) {
			try (Reader r = Files.newBufferedReader(FILE)) {
				ModConfig cfg = GSON.fromJson(r, ModConfig.class);
				if (cfg != null) return cfg.sanitize();
			} catch (IOException | JsonParseException e) {
				AchieveHelper.LOGGER.warn("Could not read {}, using defaults", FILE, e);
			}
		}
		return new ModConfig();
	}

	public void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer w = Files.newBufferedWriter(FILE)) {
				GSON.toJson(this, w);
			}
		} catch (IOException e) {
			AchieveHelper.LOGGER.warn("Could not save {}", FILE, e);
		}
	}
}
