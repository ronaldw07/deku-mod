package com.ronaldw07.deku.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.ronaldw07.deku.DekuMod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;

/**
 * Player-tunable ability settings, saved to config/deku.json.
 * Powers are percentages (1-100); times are seconds (0 = instant).
 */
public record DekuSettings(int cowlingPower, double cowlingRampSeconds, int punchPower, double punchChargeSeconds) {
	public static final double MAX_SECONDS = 5.0;
	private static final DekuSettings DEFAULTS = new DekuSettings(100, 1.0, 100, 1.0);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("deku.json");

	private static DekuSettings current = DEFAULTS;

	public static DekuSettings get() {
		return current;
	}

	public static void set(DekuSettings settings) {
		current = settings.clamped();
	}

	public static void load() {
		if (!Files.exists(PATH)) {
			return;
		}

		try {
			DekuSettings loaded = GSON.fromJson(Files.readString(PATH), DekuSettings.class);
			if (loaded != null) {
				current = loaded.clamped();
			}
		} catch (IOException | JsonParseException e) {
			DekuMod.LOGGER.warn("Couldn't read {}, using defaults", PATH, e);
		}
	}

	public static void save() {
		try {
			Files.writeString(PATH, GSON.toJson(current));
		} catch (IOException e) {
			DekuMod.LOGGER.warn("Couldn't save {}", PATH, e);
		}
	}

	public DekuSettings withCowlingPower(int value) {
		return new DekuSettings(value, cowlingRampSeconds, punchPower, punchChargeSeconds);
	}

	public DekuSettings withCowlingRampSeconds(double value) {
		return new DekuSettings(cowlingPower, value, punchPower, punchChargeSeconds);
	}

	public DekuSettings withPunchPower(int value) {
		return new DekuSettings(cowlingPower, cowlingRampSeconds, value, punchChargeSeconds);
	}

	public DekuSettings withPunchChargeSeconds(double value) {
		return new DekuSettings(cowlingPower, cowlingRampSeconds, punchPower, value);
	}

	private DekuSettings clamped() {
		return new DekuSettings(
			Mth.clamp(cowlingPower, 1, 100),
			Mth.clamp(cowlingRampSeconds, 0.0, MAX_SECONDS),
			Mth.clamp(punchPower, 1, 100),
			Mth.clamp(punchChargeSeconds, 0.0, MAX_SECONDS)
		);
	}
}
