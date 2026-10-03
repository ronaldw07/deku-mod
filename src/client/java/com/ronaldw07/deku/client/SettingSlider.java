package com.ronaldw07.deku.client;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** Slider over [min, max] that snaps to multiples of step. */
class SettingSlider extends AbstractSliderButton {
	private final String label;
	private final double min;
	private final double max;
	private final double step;
	private final DoubleFunction<String> format;
	private final DoubleConsumer onChange;

	SettingSlider(int x, int y, int width, int height, String label, double min, double max, double step,
			double initial, DoubleFunction<String> format, DoubleConsumer onChange) {
		super(x, y, width, height, Component.empty(), (initial - min) / (max - min));
		this.label = label;
		this.min = min;
		this.max = max;
		this.step = step;
		this.format = format;
		this.onChange = onChange;
		updateMessage();
	}

	private double snapped() {
		double raw = min + value * (max - min);
		return Math.round(raw / step) * step;
	}

	@Override
	protected void updateMessage() {
		setMessage(Component.literal(label + ": " + format.apply(snapped())));
	}

	@Override
	protected void applyValue() {
		onChange.accept(snapped());
	}
}
