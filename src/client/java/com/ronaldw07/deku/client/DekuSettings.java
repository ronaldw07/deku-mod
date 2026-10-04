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
public record DekuSettings(int cowlingPower, double cowlingRampSeconds, int punchPower, double punchChargeSeconds,
		boolean dangerSense, boolean noCooldowns, int particleDetail, boolean noScreenEffects) {
	public static final double MAX_SECONDS = 5.0;
	public static final int LOW_DETAIL = 1;
	public static final int NORMAL_DETAIL = 2;
	public static final int HIGH_DETAIL = 3;
	private static final double[] DETAIL_SCALES = {0.4, 1.0, 1.6};
	private static final DekuSettings DEFAULTS = new DekuSettings(100, 1.0, 100, 1.0, true, false, NORMAL_DETAIL, false);
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
		return new DekuSettings(value, cowlingRampSeconds, punchPower, punchChargeSeconds, dangerSense, noCooldowns, particleDetail, noScreenEffects);
	}

	public DekuSettings withCowlingRampSeconds(double value) {
		return new DekuSettings(cowlingPower, value, punchPower, punchChargeSeconds, dangerSense, noCooldowns, particleDetail, noScreenEffects);
	}

	public DekuSettings withPunchPower(int value) {
		return new DekuSettings(cowlingPower, cowlingRampSeconds, value, punchChargeSeconds, dangerSense, noCooldowns, particleDetail, noScreenEffects);
	}

	public DekuSettings withPunchChargeSeconds(double value) {
		return new DekuSettings(cowlingPower, cowlingRampSeconds, punchPower, value, dangerSense, noCooldowns, particleDetail, noScreenEffects);
	}

	public DekuSettings withDangerSense(boolean value) {
		return new DekuSettings(cowlingPower, cowlingRampSeconds, punchPower, punchChargeSeconds, value, noCooldowns, particleDetail, noScreenEffects);
	}

	public DekuSettings withNoCooldowns(boolean value) {
		return new DekuSettings(cowlingPower, cowlingRampSeconds, punchPower, punchChargeSeconds, dangerSense, value, particleDetail, noScreenEffects);
	}

	public DekuSettings withParticleDetail(int value) {
		return new DekuSettings(cowlingPower, cowlingRampSeconds, punchPower, punchChargeSeconds, dangerSense, noCooldowns, value, noScreenEffects);
	}

	public DekuSettings withNoScreenEffects(boolean value) {
		return new DekuSettings(cowlingPower, cowlingRampSeconds, punchPower, punchChargeSeconds, dangerSense, noCooldowns, particleDetail, value);
	}

	/** How much to multiply particle counts by: below 1 on Low, above 1 on High. */
	public double detailScale() {
		return DETAIL_SCALES[Mth.clamp(particleDetail, LOW_DETAIL, HIGH_DETAIL) - 1];
	}

	private DekuSettings clamped() {
		return new DekuSettings(
			Mth.clamp(cowlingPower, 1, 100),
			Mth.clamp(cowlingRampSeconds, 0.0, MAX_SECONDS),
			Mth.clamp(punchPower, 1, 100),
			Mth.clamp(punchChargeSeconds, 0.0, MAX_SECONDS),
			dangerSense,
			noCooldowns,
			// An older config file has no detail, which reads back as 0: treat that as Normal.
			particleDetail < LOW_DETAIL || particleDetail > HIGH_DETAIL ? NORMAL_DETAIL : particleDetail,
			noScreenEffects
		);
	}
}
