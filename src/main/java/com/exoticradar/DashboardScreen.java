package com.exoticradar;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class DashboardScreen extends Screen {

    private final ScanTracker tracker;
    private int scrollOffset = 0;
    private String filterText = "";
    private String statusMsg  = "";
    private boolean showConfig = false;

    private static final int Y_TITLE   = 6;
    private static final int Y_STATS1  = 18;
    private static final int Y_STATS2  = 29;
    private static final int Y_API     = 40;
    private static final int Y_BTNS    = 56;
    private static final int BTN_H     = 14;
    private static final int Y_LOOKUP  = 74;
    private static final int LOOKUP_H  = 13;
    private static final int Y_COLHDR  = 91;
    private static final int COLHDR_H  = 11;
    private static final int Y_TABLE   = 103;
    private static final int FOOTER_H  = 16;
    private static final int ROW_H     = 13;
    private static final int CX_NAME   = 6;
    private static final int CX_UUID   = 112;
    private static final int CX_STATUS = 352;
    private static final int CX_EXOTIC = 404;
    private static final int CX_REASON = 448;
    private static final int CFG_X     = 8;
    private static final int CFG_W     = 390;

    private static final int C_BG     = 0xEE060606;
    private static final int C_HDR    = 0xFF0A0A0A;
    private static final int C_BORDER = 0xFF282828;
    private static final int C_WHITE  = 0xFFFFFFFF;
    private static final int C_GRAY   = 0xFF666666;

    private EditBox filterBox;
    private EditBox lookupBox;
    private EditBox apiKeyBox;

    // Track row widgets so we can remove them with removeWidget()
    private final List<ClickableRowWidget> rowWidgets = new ArrayList<>();

    public DashboardScreen(ScanTracker tracker) {
        super(Component.literal("Exotic Radar"));
        this.tracker = tracker;
    }

    @Override
    protected void init() {
        clearWidgets();
        rowWidgets.clear();
        filterBox = null;
        lookupBox = null;

        // ── Persistent buttons ──
        addRenderableWidget(Button.builder(
            Component.literal(tracker.isPaused() ? "▶ Resume" : "⏸ Pause"),
            btn -> { tracker.togglePause(); btn.setMessage(Component.literal(tracker.isPaused() ? "▶ Resume" : "⏸ Pause")); })
            .pos(CX_NAME, Y_BTNS).size(68, BTN_H).build());

        addRenderableWidget(Button.builder(Component.literal("⟳ Rescan"),
            btn -> { tracker.triggerForceRescan(); statusMsg = "§aRescan triggered"; })
            .pos(CX_NAME + 72, Y_BTNS).size(56, BTN_H).build());

        addRenderableWidget(Button.builder(Component.literal("Test Mojang"),
            btn -> testApi(false)).pos(CX_NAME + 132, Y_BTNS).size(72, BTN_H).build());

        addRenderableWidget(Button.builder(Component.literal("Test Hypixel"),
            btn -> testApi(true)).pos(CX_NAME + 208, Y_BTNS).size(76, BTN_H).build());

        addRenderableWidget(Button.builder(
            Component.literal(showConfig ? "◀ List" : "⚙ Config"),
            btn -> { showConfig = !showConfig; init(); })
            .pos(width - 70, Y_BTNS).size(62, BTN_H).build());

        // ── Lookup row ──
        lookupBox = new EditBox(font, CX_NAME, Y_LOOKUP, 158, LOOKUP_H, Component.literal(""));
        lookupBox.setHint(Component.literal("Lookup any username..."));
        lookupBox.setMaxLength(32);
        addRenderableWidget(lookupBox);
        addRenderableWidget(Button.builder(Component.literal("Go"),
            btn -> doLookup()).pos(CX_NAME + 161, Y_LOOKUP).size(24, LOOKUP_H).build());

        if (showConfig) {
            int cy = Y_TABLE + 8; // current Y for config items

            // ── Scan interval slider ──
            // (label rendered in renderConfigPanel)
            addRenderableWidget(new SliderWidget(CFG_X, cy + 24, 200, 9));

            // ── Preset buttons ──
            int[] presets = {10, 20, 30, 45, 60, 90, 120};
            int bx = CFG_X + 42;
            for (int p : presets) {
                final int sec = p;
                boolean active = Config.getScanIntervalSeconds() == sec;
                addRenderableWidget(Button.builder(
                    Component.literal((active ? "§a" : "") + sec + "s"),
                    btn -> { Config.setScanIntervalSeconds(sec); init(); })
                    .pos(bx, cy + 38).size(23, 11).build());
                bx += 26;
            }

            // ── HUD on/off toggle ──
            addRenderableWidget(Button.builder(
                Component.literal(Config.showHud ? "§aON" : "§cOFF"),
                btn -> { Config.showHud = !Config.showHud; init(); })
                .pos(CFG_X + 80, cy + 60).size(40, 11).build());

            // ── HUD mode toggle ──
            addRenderableWidget(Button.builder(
                Component.literal(Config.hudExoticOnly ? "§eExotic only" : "§7Everyone"),
                btn -> { Config.hudExoticOnly = !Config.hudExoticOnly; init(); })
                .pos(CFG_X + 80, cy + 74).size(80, 11).build());

            // ── API Key input ──
            apiKeyBox = new EditBox(font, CFG_X, cy + 94, 240, 13, Component.literal(""));
            apiKeyBox.setHint(Component.literal("Paste Hypixel API key..."));
            apiKeyBox.setMaxLength(64);
            apiKeyBox.setValue(Config.apiKey);
            addRenderableWidget(apiKeyBox);
            addRenderableWidget(Button.builder(Component.literal("Save"),
                btn -> {
                    Config.apiKey = apiKeyBox.getValue().trim();
                    statusMsg = Config.apiKey.isEmpty() ? "§cKey cleared" : "§aAPI key saved!";
                }).pos(CFG_X + 244, cy + 94).size(40, 13).build());

        } else {
            // ── Filter box ──
            filterBox = new EditBox(font, CX_NAME, Y_COLHDR, 100, COLHDR_H, Component.literal(""));
            filterBox.setHint(Component.literal("filter..."));
            filterBox.setMaxLength(32);
            filterBox.setValue(filterText);
            // Don't use setResponder — it fires on setValue above
            addRenderableWidget(filterBox);

            // ── Row widgets ──
            buildRows();
        }
    }

    private void buildRows() {
        // Remove old rows using removeWidget() which IS accessible
        for (ClickableRowWidget w : rowWidgets) removeWidget(w);
        rowWidgets.clear();

        List<PlayerScanResult> results = getFilteredResults();
        int visRows = (height - Y_TABLE - FOOTER_H) / ROW_H;
        int maxScroll = Math.max(0, results.size() - visRows);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        if (scrollOffset < 0) scrollOffset = 0;
        for (int i = 0; i < visRows && (i + scrollOffset) < results.size(); i++) {
            ClickableRowWidget w = new ClickableRowWidget(
                0, Y_TABLE + i * ROW_H, width - 5, ROW_H, results.get(i + scrollOffset), i, this);
            addRenderableWidget(w);
            rowWidgets.add(w);
        }
    }

    private List<PlayerScanResult> getFilteredResults() {
        // Read directly from filterBox value if available, otherwise use filterText
        String filter = filterBox != null ? filterBox.getValue().toLowerCase() : filterText;
        filterText = filter;
        List<PlayerScanResult> all = tracker.getAllResults();
        return filter.isEmpty() ? all
            : all.stream().filter(r -> r.username.toLowerCase().contains(filter))
                .collect(Collectors.toList());
    }

    private void testApi(boolean hypixel) {
        statusMsg = "§7Testing " + (hypixel ? "Hypixel" : "Mojang") + "...";
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                long t = System.currentTimeMillis();
                boolean ok;
                if (hypixel) {
                    var d = HypixelApiClient.get().fetchJson(
                        "https://api.hypixel.net/v2/skyblock/profiles?uuid=fbbf3db7a67a4b99ae6ee8d8d949f6d7");
                    ok = d != null && d.get("success").getAsBoolean();
                    tracker.setThrottledHypixel(!ok);
                } else {
                    ok = HypixelApiClient.get().getUUID("wwinbb") != null;
                    tracker.setThrottledMojang(!ok);
                }
                long ms = System.currentTimeMillis() - t;
                statusMsg = ok ? "§a" + (hypixel ? "Hypixel" : "Mojang") + " OK (" + ms + "ms)"
                              : "§c" + (hypixel ? "Hypixel" : "Mojang") + " FAILED";
            } catch (Exception e) { statusMsg = "§cError: " + e.getMessage(); }
        });
    }

    private void doLookup() {
        if (lookupBox == null) return;
        String username = lookupBox.getValue().trim();
        if (username.isEmpty()) return;
        statusMsg = "§7Looking up §f" + username + "...";
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                String uuid = HypixelApiClient.get().getUUID(username);
                if (uuid == null) { statusMsg = "§cNot found: " + username; return; }
                PlayerScanResult r = new PlayerScanResult(username);
                r.uuid = uuid;
                r.status = PlayerScanResult.Status.CHECKING;
                String[] result = HypixelApiClient.get().checkExoticWithReason(username, uuid, r);
                r.isExotic = Boolean.parseBoolean(result[0]);
                r.exoticReason = result[1];
                r.status = PlayerScanResult.Status.DONE;
                statusMsg = "§aLoaded §f" + username;
                Minecraft.getInstance().execute(() ->
                    Minecraft.getInstance().setScreen(new PlayerDetailScreen(r, this)));
            } catch (Exception e) { statusMsg = "§cError: " + e.getMessage(); }
        });
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, C_BG);
        g.fill(0, 0, width, Y_TABLE, C_HDR);
        g.fill(0, Y_TABLE - 1, width, Y_TABLE, C_BORDER);

        g.drawString(font, "§6§lEXOTIC RADAR §r§8— Dashboard", CX_NAME, Y_TITLE, C_WHITE);
        g.drawString(font, "§fLobby: §e"   + tracker.getLobbySize(),     CX_NAME,       Y_STATS1, C_WHITE);
        g.drawString(font, "§fChecked: §a" + tracker.getTotalChecked(),   CX_NAME + 80,  Y_STATS1, C_WHITE);
        g.drawString(font, "§fExotic: §c"  + tracker.getExoticCount(),    CX_NAME + 165, Y_STATS1, C_WHITE);
        g.drawString(font, "§fPending: §9" + tracker.getPendingCount(),   CX_NAME + 244, Y_STATS1, C_WHITE);
        g.drawString(font, "§fFailed: §4"  + tracker.getFailedCount(),    CX_NAME + 328, Y_STATS1, C_WHITE);
        g.drawString(font, "§7Session: "   + tracker.getSessionTotal(),   CX_NAME + 408, Y_STATS1, C_WHITE);
        g.drawString(font, "§fLast scan: §7" + tracker.getLastScanTime(), CX_NAME,       Y_STATS2, C_WHITE);
        g.drawString(font, "§fNext: §7"      + tracker.getNextScanIn(),   CX_NAME + 148, Y_STATS2, C_WHITE);
        if (tracker.isPaused()) g.drawString(font, "§e⏸ PAUSED", CX_NAME + 275, Y_STATS2, C_WHITE);

        if (tracker.isThrottledMojang())
            g.drawString(font, "§cMojang: THROTTLED (~" + tracker.getMojangThrottleCountdown() + ")", CX_NAME, Y_API, C_WHITE);
        else
            g.drawString(font, "§aMojang: OK", CX_NAME, Y_API, C_WHITE);
        if (tracker.isThrottledHypixel())
            g.drawString(font, "§cHypixel: THROTTLED (~" + tracker.getHypixelThrottleCountdown() + ")", CX_NAME + 150, Y_API, C_WHITE);
        else
            g.drawString(font, "§aHypixel: OK", CX_NAME + 150, Y_API, C_WHITE);
        if (!statusMsg.isEmpty())
            g.drawString(font, statusMsg, CX_NAME + 340, Y_API, C_WHITE);

        g.fill(0, Y_COLHDR - 1, width, Y_TABLE, 0xFF0D0D0D);
        g.drawString(font, "§7UUID",   CX_UUID,   Y_COLHDR + 1, C_GRAY);
        g.drawString(font, "§7STATUS", CX_STATUS, Y_COLHDR + 1, C_GRAY);
        g.drawString(font, "§7EXOTIC", CX_EXOTIC, Y_COLHDR + 1, C_GRAY);
        g.drawString(font, "§7REASON", CX_REASON, Y_COLHDR + 1, C_GRAY);

        if (showConfig) {
            renderConfigPanel(g);
        } else {
            List<PlayerScanResult> results = getFilteredResults();
            int visRows = (height - Y_TABLE - FOOTER_H) / ROW_H;
            if (results.size() > visRows && visRows > 0) {
                int bx = width - 4, bah = height - FOOTER_H - Y_TABLE;
                int bh = Math.max(15, bah * visRows / results.size());
                int ms2 = Math.max(1, results.size() - visRows);
                int by = Y_TABLE + (bah - bh) * scrollOffset / ms2;
                g.fill(bx, Y_TABLE, bx + 3, Y_TABLE + bah, 0xFF1A1A1A);
                g.fill(bx, by, bx + 3, by + bh, 0xFF555555);
            }
            g.fill(0, height - FOOTER_H, width, height, C_HDR);
            g.fill(0, height - FOOTER_H, width, height - FOOTER_H + 1, C_BORDER);
            g.drawString(font, "§7Click row → profile  •  type in filter box  •  scroll: wheel  •  " + results.size() + " shown",
                CX_NAME, height - FOOTER_H + 4, C_GRAY);
        }

        super.render(g, mouseX, mouseY, delta);
    }

    private void renderConfigPanel(GuiGraphics g) {
        int cy = Y_TABLE + 8;
        int panelH = 120;
        g.fill(CFG_X - 2, Y_TABLE, CFG_X + CFG_W, Y_TABLE + panelH, 0xFF0D0D0D);
        g.fill(CFG_X - 2, Y_TABLE, CFG_X + CFG_W, Y_TABLE + 1, C_BORDER);

        g.drawString(font, "§e§lSettings", CFG_X, cy, C_WHITE);
        g.drawString(font, "§fScan interval: §a" + Config.getScanIntervalSeconds() + "s", CFG_X, cy + 12, C_WHITE);
        // slider at cy+24 (widget)
        g.drawString(font, "§7Quick:", CFG_X, cy + 38, C_WHITE);
        // preset buttons at cy+38 (widgets)
        g.drawString(font, "§7Show HUD:", CFG_X, cy + 60, C_WHITE);
        // hud toggle at cy+60 (widget)
        g.drawString(font, "§7HUD mode:", CFG_X, cy + 74, C_WHITE);
        // hud mode at cy+74 (widget)
        g.drawString(font, "§7API Key:", CFG_X, cy + 94, C_WHITE);
        // api key box at cy+94 (widget) — but draw "not set" hint
        if (Config.apiKey.isEmpty()) {
            g.drawString(font, "§c(not set — enter below)", CFG_X + 55, cy + 94, C_WHITE);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (!showConfig) {
            scrollOffset = Math.max(0, scrollOffset - (int) v * 2);
            buildRows();
        }
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        // Rebuild rows every tick so status updates live
        if (!showConfig && minecraft.screen == this) buildRows();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
