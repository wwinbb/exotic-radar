package com.exoticradar;

public class Config {
    // Hypixel API key — set this in-game via the Config panel
    public static String apiKey = "";

    // Scan interval in ticks (20 ticks = 1 second)
    public static int scanIntervalTicks = 700; // 35 seconds default

    // Whether to show the HUD overlay
    public static boolean showHud = true;

    // Whether to only show exotic players in HUD (vs showing count always)
    public static boolean hudExoticOnly = true;

    // Minimum skill level to bother scanning (0 = scan everyone)
    public static int minSkyblockLevel = 0;

    public static int getScanIntervalSeconds() {
        return scanIntervalTicks / 20;
    }

    public static void setScanIntervalSeconds(int seconds) {
        scanIntervalTicks = Math.max(10, seconds) * 20;
    }
}
