package com.exoticradar;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ScanTracker {
    private final CopyOnWriteArrayList<PlayerScanResult> results = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, PlayerScanResult> byName = new ConcurrentHashMap<>();
    private int lobbySize = 0;
    private long lastScanTimestamp = 0;
    private int nextScanInTicks = 0;
    private boolean throttledMojang = false;
    private boolean throttledHypixel = false;
    private long mojangThrottleTime = 0;
    private long hypixelThrottleTime = 0;
    private int sessionTotal = 0;
    private boolean paused = false;
    private boolean forceRescan = false;

    // Mojang resets after ~60s, Hypixel after ~300s
    private static final long MOJANG_THROTTLE_RESET_MS = 60_000;
    private static final long HYPIXEL_THROTTLE_RESET_MS = 300_000;
    private static final SimpleDateFormat SDF = new SimpleDateFormat("HH:mm:ss");

    public void beginScan(List<String> usernames, int lobbySize) {
        this.lobbySize = lobbySize;
        this.lastScanTimestamp = System.currentTimeMillis();
        this.sessionTotal += usernames.size();
        this.forceRescan = false;
        results.clear(); byName.clear();
        for (String name : usernames) {
            PlayerScanResult r = new PlayerScanResult(name);
            results.add(r); byName.put(name, r);
        }
    }

    public void setChecking(String username) {
        PlayerScanResult r = byName.get(username);
        if (r != null) r.status = PlayerScanResult.Status.CHECKING;
    }

    public PlayerScanResult getResult(String username) { return byName.get(username); }

    public void setResult(String username, String uuid, boolean isExotic, String reason) {
        PlayerScanResult r = byName.get(username);
        if (r != null) {
            r.uuid = uuid != null ? uuid : "unknown";
            r.isExotic = isExotic; r.exoticReason = reason;
            r.status = PlayerScanResult.Status.DONE;
            r.checkedAt = System.currentTimeMillis();
        }
    }

    public void setFailed(String username, String reason) {
        PlayerScanResult r = byName.get(username);
        if (r != null) { r.status = PlayerScanResult.Status.FAILED; r.failReason = reason != null ? reason : "unknown"; }
    }

    public void setThrottledMojang(boolean t) {
        this.throttledMojang = t;
        if (t) this.mojangThrottleTime = System.currentTimeMillis();
    }

    public void setThrottledHypixel(boolean t) {
        this.throttledHypixel = t;
        if (t) this.hypixelThrottleTime = System.currentTimeMillis();
    }

    public void setNextScanInTicks(int ticks) { this.nextScanInTicks = ticks; }
    public void togglePause() { this.paused = !this.paused; }
    public boolean isPaused() { return paused; }
    public void triggerForceRescan() { this.forceRescan = true; }
    public boolean shouldForceRescan() { if (forceRescan) { forceRescan = false; return true; } return false; }

    public List<PlayerScanResult> getAllResults() { return new ArrayList<>(results); }
    public int getLobbySize() { return lobbySize; }
    public int getExoticCount() { return (int) results.stream().filter(r -> r.isExotic).count(); }
    public int getTotalChecked() { return (int) results.stream().filter(r -> r.status == PlayerScanResult.Status.DONE).count(); }
    public int getPendingCount() { return (int) results.stream().filter(r -> r.status == PlayerScanResult.Status.PENDING || r.status == PlayerScanResult.Status.CHECKING).count(); }
    public int getFailedCount() { return (int) results.stream().filter(r -> r.status == PlayerScanResult.Status.FAILED).count(); }
    public boolean isThrottledMojang() { return throttledMojang && (System.currentTimeMillis() - mojangThrottleTime < MOJANG_THROTTLE_RESET_MS); }
    public boolean isThrottledHypixel() { return throttledHypixel && (System.currentTimeMillis() - hypixelThrottleTime < HYPIXEL_THROTTLE_RESET_MS); }
    public int getSessionTotal() { return sessionTotal; }
    public int getNextScanInTicks() { return nextScanInTicks; }

    public String getMojangThrottleCountdown() {
        long elapsed = System.currentTimeMillis() - mojangThrottleTime;
        long remaining = Math.max(0, MOJANG_THROTTLE_RESET_MS - elapsed) / 1000;
        return remaining + "s";
    }

    public String getHypixelThrottleCountdown() {
        long elapsed = System.currentTimeMillis() - hypixelThrottleTime;
        long remaining = Math.max(0, HYPIXEL_THROTTLE_RESET_MS - elapsed) / 1000;
        return remaining + "s";
    }

    public String getLastScanTime() {
        if (lastScanTimestamp == 0) return "never";
        return SDF.format(new Date(lastScanTimestamp));
    }

    public String getNextScanIn() {
        if (paused) return "PAUSED";
        return (nextScanInTicks / 20) + "s";
    }
}
