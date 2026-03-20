package com.exoticradar;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class SliderWidget extends AbstractWidget {
    private boolean dragging = false;
    private static final int TRACK_COLOR  = 0xFF222222;
    private static final int FILL_COLOR   = 0xFF5555BB;
    private static final int KNOB_COLOR   = 0xFFAABBFF;

    public SliderWidget(int x, int y, int w, int h) {
        super(x, y, w, h, Component.empty());
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(getX(), getY() + 1, getX() + width, getY() + height - 1, TRACK_COLOR);
        g.fill(getX(), getY(), getX() + width, getY() + 1, 0xFF444444);
        float t = (Config.getScanIntervalSeconds() - 10f) / 110f;
        int kx = getX() + (int)(t * width);
        g.fill(getX(), getY() + 2, kx, getY() + height - 2, FILL_COLOR);
        g.fill(kx - 3, getY() - 1, kx + 3, getY() + height + 1, KNOB_COLOR);
        // Label
        // (rendered by parent)
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean bl) {
        dragging = true;
        applyX((int) event.x());
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        dragging = false;
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        if (dragging) applyX((int) event.x());
    }

    private void applyX(int mx) {
        float frac = Math.max(0f, Math.min(1f, (float)(mx - getX()) / width));
        Config.setScanIntervalSeconds(10 + (int)(frac * 110));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput o) {}
}
