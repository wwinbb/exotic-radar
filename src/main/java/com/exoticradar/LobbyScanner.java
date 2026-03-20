package com.exoticradar;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LobbyScanner {
    // Single thread = truly sequential, one call per second max
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ScanTracker tracker;

    public LobbyScanner(ScanTracker tracker) { this.tracker = tracker; }

    public void scanLobby(Minecraft client, HudOverlay overlay) {
        if (client.getConnection() == null) return;
        List<String> usernames = new ArrayList<>();
        for (PlayerInfo entry : client.getConnection().getListedOnlinePlayers()) {
            String name = entry.getProfile().name();
            if (name != null && !name.contains("!") && !name.isBlank())
                usernames.add(name);
        }
        ExoticRadar.LOGGER.info("Starting scan of {} players", usernames.size());
        tracker.beginScan(usernames, usernames.size());
        overlay.clearExoticPlayers();

        // Submit one task per player — executor is single-threaded so they run sequentially
        for (String username : usernames) {
            executor.submit(() -> {
                tracker.setChecking(username);
                PlayerScanResult result = tracker.getResult(username);
                try {
                    // getUUID handles its own rate limiting (1.1s between calls)
                    String uuid = HypixelApiClient.get().getUUID(username);
                    if (uuid == null) {
                        tracker.setFailed(username, "uuid not found (Mojang)");
                        return;
                    }
                    // checkExotic handles its own rate limiting (350ms between calls)
                    String[] check = HypixelApiClient.get().checkExoticWithReason(username, uuid, result);
                    boolean isExotic = Boolean.parseBoolean(check[0]);
                    tracker.setResult(username, uuid, isExotic, check[1]);
                    tracker.setThrottledMojang(false);
                    tracker.setThrottledHypixel(false);
                    if (isExotic) {
                        overlay.addExoticPlayer(username);
                        ExoticRadar.LOGGER.info("EXOTIC: {} ({})", username, check[1]);
                    }
                } catch (Exception e) {
                    String msg = e.getMessage() != null ? e.getMessage() : "unknown";
                    tracker.setFailed(username, msg);
                    if (msg.contains("MOJANG_THROTTLE"))  tracker.setThrottledMojang(true);
                    if (msg.contains("HYPIXEL_THROTTLE")) tracker.setThrottledHypixel(true);
                    ExoticRadar.LOGGER.warn("Scan failed {}: {}", username, msg);
                }
            });
        }
    }
}
