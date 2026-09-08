/** In-game Mod Menu screen for editing the renderer's existing numeric settings. */
package com.xewowa4144.distantbeacons;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.DoubleConsumer;

public final class DistantBeaconsConfigScreen extends Screen {
    private final Screen parent;

    private LinearSlider widthDivisor;
    private LinearSlider widthMax;
    private LinearSlider cullDistance;
    private LinearSlider beamHeight;
    private LinearSlider beamDepth;

    public DistantBeaconsConfigScreen(Screen parent) {
        super(Component.literal("Distant Beacons Configuration"));
        this.parent = parent;
    }

    @Override
    // Build the five linear sliders and the Done/Reset controls.
    protected void init() {
        int centerX = width / 2;
        int startY = Math.max(40, height / 2 - 105);
        int sliderWidth = Math.min(360, width - 80);
        int sliderX = centerX - sliderWidth / 2;

        widthDivisor = slider(sliderX, startY, sliderWidth,
            "Width distance divisor", DistantBeaconsConfig.widthDivisor, 1, 256,
            value -> DistantBeaconsConfig.widthDivisor = (float) value);
        widthMax = slider(sliderX, startY + 58, sliderWidth,
            "Width maximum multiplier", DistantBeaconsConfig.widthMax, 1, 16384,
            value -> DistantBeaconsConfig.widthMax = (float) value);
        cullDistance = slider(sliderX, startY + 116, sliderWidth,
            "Camera cull distance", DistantBeaconsConfig.cullDistance, 1024, 1048576,
            value -> DistantBeaconsConfig.cullDistance = (float) value);
        beamHeight = slider(sliderX, startY + 174, sliderWidth,
            "Beam height", DistantBeaconsConfig.beamHeight, 2048, 2000000,
            value -> DistantBeaconsConfig.beamHeight = (float) value);
        beamDepth = slider(sliderX, startY + 232, sliderWidth,
            "Beam depth", DistantBeaconsConfig.beamDepth, 0, 2000000,
            value -> DistantBeaconsConfig.beamDepth = (float) value);

        addRenderableWidget(widthDivisor);
        addRenderableWidget(widthMax);
        addRenderableWidget(cullDistance);
        addRenderableWidget(beamHeight);
        addRenderableWidget(beamDepth);

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> saveAndClose())
            .bounds(centerX - 105, startY + 290, 100, 20)
            .build());
        addRenderableWidget(Button.builder(Component.literal("Reset"), button -> resetFields())
            .bounds(centerX + 5, startY + 290, 100, 20)
            .build());
    }

    // All settings use one shared linear slider implementation; only the range and target value differ.
    private LinearSlider slider(
        int x, int y, int width, String label, double initial,
        double min, double max, DoubleConsumer consumer
    ) {
        return new LinearSlider(x, y, width, label, initial, min, max, consumer);
    }

    private void resetFields() {
        DistantBeaconsConfig.reset();
        widthDivisor.setNumericValue(DistantBeaconsConfig.widthDivisor);
        widthMax.setNumericValue(DistantBeaconsConfig.widthMax);
        cullDistance.setNumericValue(DistantBeaconsConfig.cullDistance);
        beamHeight.setNumericValue(DistantBeaconsConfig.beamHeight);
        beamDepth.setNumericValue(DistantBeaconsConfig.beamDepth);
    }

    private void saveAndClose() {
        DistantBeaconsConfig.save();
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 18, 0xFFFFFFFF);
        graphics.centeredText(font,
            Component.literal("Lower divisor = thicker sooner • Higher maximum = thicker at extreme distance"),
            width / 2, 32, 0xFFAAAAAA);
        graphics.centeredText(font,
            Component.literal("Cull distance = visibility range • Height = above • Depth = below"),
            width / 2, 46, 0xFFAAAAAA);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.gui.setScreen(parent);
        }
    }

    private static final class LinearSlider extends AbstractSliderButton {
        private final String label;
        private final double min;
        private final double max;
        private final DoubleConsumer consumer;

        private LinearSlider(
            int x, int y, int width, String label, double initial,
            double min, double max, DoubleConsumer consumer
        ) {
            super(x, y, width, 20, Component.empty(), normalize(initial, min, max));
            this.label = label;
            this.min = min;
            this.max = max;
            this.consumer = consumer;
            setNumericValue(initial);
        }

        private static double normalize(double value, double min, double max) {
            double clamped = Math.max(min, Math.min(max, value));
            return (clamped - min) / (max - min);
        }

        // Convert the slider's normalized 0..1 value back into the configured numeric range.
        private double numericValue() {
            double normalized = Math.max(0.0D, Math.min(1.0D, value));
            return Math.round(min + normalized * (max - min));
        }

        private void setNumericValue(double numeric) {
            value = normalize(numeric, min, max);
            updateMessage();
            applyValue();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label + ": " + String.format(Locale.ROOT, "%,d", Math.round(numericValue()))));
        }

        @Override
        protected void applyValue() {
            consumer.accept(numericValue());
        }
    }
}
