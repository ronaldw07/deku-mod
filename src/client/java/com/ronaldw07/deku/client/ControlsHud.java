package com.ronaldw07.deku.client;

import com.ronaldw07.deku.DekuItems;
import com.ronaldw07.deku.client.Cooldowns.Ability;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Bottom-right panel listing the held quirk's moves and their keys, in the quirk's color.
 * A move that's recharging greys out and shows a shrinking bar and the seconds left.
 */
final class ControlsHud {
	private static final int ONE_FOR_ALL_COLOR = 0xFF55FF55;
	private static final int EXPLOSION_COLOR = 0xFFFF9030;
	private static final int LABEL_COLOR = 0xFFFFFFFF;
	private static final int COOLING_COLOR = 0xFF808080;
	private static final int BACKGROUND = 0x90000000;
	private static final int BAR_BACKGROUND = 0xFF303030;
	private static final int MARGIN = 4;
	private static final int PADDING = 4;
	private static final int ROW_HEIGHT = 12;
	private static final int COLUMN_GAP = 12;
	private static final int HOTBAR_HALF_WIDTH = 91;
	private static final int HOTBAR_AND_BARS_HEIGHT = 60; // hotbar plus health, food and armor rows
	private static final float TICKS_PER_SECOND = 20f;

	private record Row(Ability ability, Supplier<Component> key) {
	}

	private record Panel(String title, int color, List<Row> rows) {
	}

	private static final Panel ONE_FOR_ALL = new Panel("One For All", ONE_FOR_ALL_COLOR, List.of(
		new Row(Ability.FULL_COWLING, () -> key(DekuModClient.COWLING_KEY)),
		new Row(Ability.SMASH, () -> hold(DekuModClient.SMASH_KEY)),
		new Row(Ability.SMOKESCREEN, () -> key(DekuModClient.SMOKESCREEN_KEY)),
		new Row(Ability.FLOAT, () -> hold(DekuModClient.FLOAT_KEY).copy().append(" / ").append(doubleTap())),
		new Row(Ability.BLACKWHIP, () -> key(DekuModClient.BLACKWHIP_KEY)),
		new Row(Ability.LAUNCH, () -> hold(Minecraft.getInstance().options.keyJump)),
		new Row(Ability.DANGER_SENSE, () -> key(DekuModClient.DANGER_SENSE_KEY))));

	private static final Panel EXPLOSION = new Panel("Explosion", EXPLOSION_COLOR, List.of(
		new Row(Ability.AP_SHOT, () -> key(Minecraft.getInstance().options.keyUse)),
		new Row(Ability.FLIGHT, ControlsHud::doubleTap),
		new Row(Ability.HOWITZER, () -> hold(DekuModClient.SMASH_KEY)),
		new Row(Ability.GROUND_BLAST, () -> hold(DekuModClient.COWLING_KEY)),
		new Row(Ability.CLUSTER, () -> key(DekuModClient.CLUSTER_KEY)),
		new Row(Ability.DANGER_SENSE, () -> key(DekuModClient.DANGER_SENSE_KEY))));

	private ControlsHud() {
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		Panel panel = DekuItems.isHolding(player, DekuItems.ONE_FOR_ALL) ? ONE_FOR_ALL
			: DekuItems.isHolding(player, DekuItems.EXPLOSION) ? EXPLOSION : null;
		if (panel == null) {
			return;
		}

		Font font = minecraft.font;
		List<Component> keys = panel.rows().stream().map(row -> row.key().get()).toList();
		int labelWidth = font.width(panel.title());
		int keyWidth = 0;
		for (int i = 0; i < keys.size(); i++) {
			labelWidth = Math.max(labelWidth, font.width(panel.rows().get(i).ability().label));
			keyWidth = Math.max(keyWidth, font.width(keys.get(i)));
		}

		int width = PADDING * 2 + labelWidth + COLUMN_GAP + keyWidth;
		int height = PADDING * 2 + ROW_HEIGHT * (panel.rows().size() + 1);
		int left = graphics.guiWidth() - MARGIN - width;
		// On narrow screens the panel would cover the hotbar's health and food, so it sits above them.
		boolean overlapsHotbar = left < graphics.guiWidth() / 2 + HOTBAR_HALF_WIDTH + MARGIN;
		int top = graphics.guiHeight() - MARGIN - height - (overlapsHotbar ? HOTBAR_AND_BARS_HEIGHT : 0);
		int right = left + width - PADDING;
		graphics.fill(left, top, left + width, top + height, BACKGROUND);

		int y = top + PADDING;
		graphics.text(font, panel.title(), left + PADDING, y, panel.color());
		for (int i = 0; i < keys.size(); i++) {
			y += ROW_HEIGHT;
			Ability ability = panel.rows().get(i).ability();
			float remaining = Cooldowns.fractionLeft(ability);
			if (remaining <= 0) {
				graphics.text(font, ability.label, left + PADDING, y, LABEL_COLOR);
				graphics.text(font, keys.get(i), right - font.width(keys.get(i)), y, panel.color());
				continue;
			}

			String seconds = String.format(Locale.ROOT, "%.1fs", remaining * ability.ticks / TICKS_PER_SECOND);
			graphics.text(font, ability.label, left + PADDING, y, COOLING_COLOR);
			graphics.text(font, seconds, right - font.width(seconds), y, COOLING_COLOR);
			int barTop = y + font.lineHeight;
			int barRight = left + PADDING + (int) ((right - left - PADDING) * (1 - remaining));
			graphics.fill(left + PADDING, barTop, right, barTop + 1, BAR_BACKGROUND);
			graphics.fill(left + PADDING, barTop, barRight, barTop + 1, panel.color());
		}
	}

	private static Component key(KeyMapping mapping) {
		return mapping.getTranslatedKeyMessage();
	}

	private static Component hold(KeyMapping mapping) {
		return Component.literal("Hold ").append(mapping.getTranslatedKeyMessage());
	}

	private static Component doubleTap() {
		return Component.literal("2x ").append(Minecraft.getInstance().options.keyJump.getTranslatedKeyMessage());
	}
}
