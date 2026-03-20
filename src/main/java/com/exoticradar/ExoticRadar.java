package com.exoticradar;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExoticRadar implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("exotic-radar");
    // Interval now comes from Config

    public static final ScanTracker tracker = new ScanTracker();
    private final LobbyScanner lobbyScanner = new LobbyScanner(tracker);
    private final HudOverlay hudOverlay = new HudOverlay(tracker);
    private int tickCounter = Config.scanIntervalTicks;

    private static KeyMapping dashboardKey;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Exotic Radar loaded.");
        // Pre-load items API at startup so it's ready before first scan
        java.util.concurrent.Executors.newSingleThreadExecutor().submit(() -> {
            HypixelApiClient.get().ensureItemsLoaded();
            LOGGER.info("Items API pre-loaded: {} entries",
                HypixelApiClient.get().getItemDefaultColors() != null ?
                HypixelApiClient.get().getItemDefaultColors().size() : 0);
        });

        KeyMapping.Category category = new KeyMapping.Category(
            ResourceLocation.fromNamespaceAndPath("exotic-radar", "general")
        );

        dashboardKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.exotic-radar.dashboard",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            if (!isOnHypixel(client)) return;

            while (dashboardKey.consumeClick()) {
                client.setScreen(new DashboardScreen(tracker));
            }

            if (tracker.isPaused()) return;

            // Force rescan triggered from dashboard
            if (tracker.shouldForceRescan()) {
                tickCounter = 0;
            }

            tickCounter--;
            if (tickCounter <= 0) {
                tickCounter = Config.scanIntervalTicks;
                lobbyScanner.scanLobby(client, hudOverlay);
            }
            tracker.setNextScanInTicks(tickCounter);
        });

        HudRenderCallback.EVENT.register((guiGraphics, tickDelta) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;
            if (!isOnHypixel(client)) return;
            hudOverlay.render(guiGraphics, client);
        });
    }

    private boolean isOnHypixel(Minecraft client) {
        if (client.getCurrentServer() == null) return false;
        return client.getCurrentServer().ip.toLowerCase().contains("hypixel.net");
    }
}
