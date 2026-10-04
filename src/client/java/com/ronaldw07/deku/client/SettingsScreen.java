package com.ronaldw07.deku.client;

import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class SettingsScreen extends Screen {
	private static final int WIDGET_WIDTH = 200;
	private static final int WIDGET_HEIGHT = 20;
	private static final int ROW_SPACING = 24;
	private static final double SECONDS_STEP = 0.1;

	public SettingsScreen() {
		super(Component.translatable("screen.deku.settings"));
	}

	@Override
	protected void init() {
		int x = this.width / 2 - WIDGET_WIDTH / 2;
		int y = this.height / 4;
		DekuSettings settings = DekuSettings.get();

		addSlider(x, y, "Cowling power", 1, 100, 1, settings.cowlingPower(), SettingsScreen::percent,
			v -> DekuSettings.set(DekuSettings.get().withCowlingPower((int) v)));
		addSlider(x, y + ROW_SPACING, "Cowling ramp-up", 0, DekuSettings.MAX_SECONDS, SECONDS_STEP,
			settings.cowlingRampSeconds(), SettingsScreen::seconds,
			v -> DekuSettings.set(DekuSettings.get().withCowlingRampSeconds(v)));
		addSlider(x, y + ROW_SPACING * 2, "Punch power", 1, 100, 1, settings.punchPower(), SettingsScreen::percent,
			v -> DekuSettings.set(DekuSettings.get().withPunchPower((int) v)));
		addSlider(x, y + ROW_SPACING * 3, "Punch charge time", 0, DekuSettings.MAX_SECONDS, SECONDS_STEP,
			settings.punchChargeSeconds(), SettingsScreen::seconds,
			v -> DekuSettings.set(DekuSettings.get().withPunchChargeSeconds(v)));

		addRenderableWidget(CycleButton.onOffBuilder(settings.dangerSense())
			.create(x, y + ROW_SPACING * 4, WIDGET_WIDTH, WIDGET_HEIGHT, Component.literal("Danger Sense"),
				(button, on) -> DekuSettings.set(DekuSettings.get().withDangerSense(on))));

		addRenderableWidget(CycleButton.onOffBuilder(!settings.noCooldowns())
			.create(x, y + ROW_SPACING * 5, WIDGET_WIDTH, WIDGET_HEIGHT, Component.literal("Cooldowns"),
				(button, on) -> DekuSettings.set(DekuSettings.get().withNoCooldowns(!on))));

		addRenderableWidget(CycleButton.<Integer>builder(SettingsScreen::detailName, settings.particleDetail())
			.withValues(DekuSettings.LOW_DETAIL, DekuSettings.NORMAL_DETAIL, DekuSettings.HIGH_DETAIL)
			.create(x, y + ROW_SPACING * 6, WIDGET_WIDTH, WIDGET_HEIGHT, Component.literal("Particle detail"),
				(button, detail) -> DekuSettings.set(DekuSettings.get().withParticleDetail(detail))));

		addRenderableWidget(CycleButton.onOffBuilder(!settings.noScreenEffects())
			.create(x, y + ROW_SPACING * 7, WIDGET_WIDTH, WIDGET_HEIGHT, Component.literal("Screen shake and flash"),
				(button, on) -> DekuSettings.set(DekuSettings.get().withNoScreenEffects(!on))));

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
			.bounds(x, y + ROW_SPACING * 8 + 12, WIDGET_WIDTH, WIDGET_HEIGHT)
			.build());
	}

	private void addSlider(int x, int y, String label, double min, double max, double step, double initial,
			DoubleFunction<String> format, DoubleConsumer onChange) {
		addRenderableWidget(new SettingSlider(x, y, WIDGET_WIDTH, WIDGET_HEIGHT, label, min, max, step, initial, format, onChange));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, this.height / 4 - 20, -1);
	}

	@Override
	public void onClose() {
		DekuSettings.save();
		super.onClose();
	}

	private static Component detailName(int detail) {
		return Component.literal(switch (detail) {
			case DekuSettings.LOW_DETAIL -> "Low";
			case DekuSettings.HIGH_DETAIL -> "High";
			default -> "Normal";
		});
	}

	private static String percent(double value) {
		return (int) value + "%";
	}

	private static String seconds(double value) {
		return value == 0 ? "Instant" : String.format(Locale.ROOT, "%.1fs", value);
	}
}
