package com.exoticradar;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class HudOverlay {
    private final CopyOnWriteArrayList<String> exoticPlayers = new CopyOnWriteArrayList<>();
    private final ScanTracker tracker;
    private static final int X = 5;
    private static final int Y = 5;

    public HudOverlay(ScanTracker tracker) {
        this.tracker = tracker;
    }

    public void addExoticPlayer(String username) {
        if (!exoticPlayers.contains(username)) exoticPlayers.add(username);
    }

    public void clearExoticPlayers() {
        exoticPlayers.clear();
    }

    public void render(GuiGraphics g, Minecraft client) {
        if (!Config.showHud) return;
        if (client.font == null) return;
        List<String> players = new ArrayList<>(exoticPlayers);
        int pending = tracker.getPendingCount();
        int lineHeight = 10;
        int padding = 3;
        int width = 130;
        int lines = Math.max(players.size(), 1) + (pending > 0 ? 1 : 0);
        int height = padding + lineHeight + (lines * lineHeight) + padding;

        g.fill(X, Y, X + width, Y + height, 0xAA000000);
        g.drawString(client.font, "§6Exotic Armor §7(" + players.size() + ")", X + padding, Y + padding, 0xFFFFFFFF);

        if (pending > 0) {
            g.drawString(client.font, "§9Checking " + pending + " players...", X + padding, Y + padding + lineHeight, 0xFFFFFFFF);
        }

        int offset = pending > 0 ? 2 : 1;
        if (players.isEmpty() && pending == 0) {
            g.drawString(client.font, "§7None detected", X + padding, Y + padding + lineHeight, 0xFFFFFFFF);
        } else {
            for (int i = 0; i < players.size(); i++) {
                g.drawString(client.font, "§c" + players.get(i), X + padding, Y + padding + (lineHeight * (i + offset)), 0xFFFFFFFF);
            }
        }
    }
}
