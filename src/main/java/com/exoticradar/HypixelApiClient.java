package com.exoticradar;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HypixelApiClient {
    public static final String API_KEY = "f4d8b06a-1e8d-48ae-ab19-301181b4049f";
    private static final String BASE_URL = "https://api.hypixel.net/v2";

    // ── Singleton — one instance for the whole mod session ──
    private static final HypixelApiClient INSTANCE = new HypixelApiClient();
    public static HypixelApiClient get() { return INSTANCE; }

    // UUID cache — persists for entire game session
    private final ConcurrentHashMap<String, String> uuidCache = new ConcurrentHashMap<>();

    // Items cache — loaded once, never again
    private Map<String, Integer> itemDefaultColors = null;
    private boolean itemsLoaded = false;

    // Rate limiting — track last call times
    private long lastMojangCallMs  = 0;
    private long lastHypixelCallMs = 0;
    private static final long MOJANG_DELAY_MS  = 1100; // 1.1s between Mojang calls
    private static final long HYPIXEL_DELAY_MS = 350;  // 350ms between Hypixel calls

    private HypixelApiClient() {}

    // ── Public API ──

    public String[] checkExoticWithReason(String username, String uuid, PlayerScanResult result) throws Exception {
        if (uuid == null) return new String[]{"false", "uuid lookup failed"};
        ensureItemsLoaded();
        rateLimitHypixel();
        JsonObject profileData = fetchJson(BASE_URL + "/skyblock/profiles?uuid=" + uuid);
        if (profileData == null) return new String[]{"false", "no profile data"};
        if (!profileData.get("success").getAsBoolean()) {
            String cause = profileData.has("cause") ? profileData.get("cause").getAsString() : "unknown";
            if (cause.toLowerCase().contains("throttle")) throw new Exception("HYPIXEL_THROTTLE: " + cause);
            return new String[]{"false", "hypixel error: " + cause};
        }
        JsonArray profiles = profileData.getAsJsonArray("profiles");
        if (profiles == null || profiles.isEmpty()) return new String[]{"false", "no skyblock profiles"};
        JsonObject member = getMostRecentMember(profiles, uuid);
        if (member == null) return new String[]{"false", "no member data"};
        if (result != null) result.rawMemberData = member;
        ExoticDetector.ArmorScanResult scan = ExoticDetector.scanArmor(member, HypixelApiClient.get().getItemDefaultColors());
        if (result != null) result.armorDetail = scan;
        if (scan.error != null) return new String[]{"false", scan.error};
        if (scan.exoticHex != null) return new String[]{"true", "exotic: " + scan.exoticSlot + " " + scan.exoticHex};
        return new String[]{"false", scan.noExoticReason};
    }

    public boolean playerHasExoticArmor(String username) throws Exception {
        String uuid = getUUID(username);
        return Boolean.parseBoolean(checkExoticWithReason(username, uuid, null)[0]);
    }

    public String getUUID(String username) {
        if (uuidCache.containsKey(username)) {
            ExoticRadar.LOGGER.info("UUID cache hit: {}", username);
            return uuidCache.get(username);
        }
        try {
            rateLimitMojang();
            JsonObject data = fetchInternal("https://api.mojang.com/users/profiles/minecraft/" + username, false);
            if (data == null || !data.has("id")) return null;
            String uuid = data.get("id").getAsString();
            uuidCache.put(username, uuid);
            ExoticRadar.LOGGER.info("UUID fetched: {} -> {}", username, uuid);
            return uuid;
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("429") || msg.contains("MOJANG_THROTTLE"))
                throw new RuntimeException("MOJANG_THROTTLE: rate limited", e);
            ExoticRadar.LOGGER.warn("UUID lookup failed {}: {}", username, msg);
            return null;
        }
    }

    // Rate limiters — sleep if we called too recently
    private synchronized void rateLimitMojang() throws InterruptedException {
        long now = System.currentTimeMillis();
        long wait = MOJANG_DELAY_MS - (now - lastMojangCallMs);
        if (wait > 0) Thread.sleep(wait);
        lastMojangCallMs = System.currentTimeMillis();
    }

    private synchronized void rateLimitHypixel() throws InterruptedException {
        long now = System.currentTimeMillis();
        long wait = HYPIXEL_DELAY_MS - (now - lastHypixelCallMs);
        if (wait > 0) Thread.sleep(wait);
        lastHypixelCallMs = System.currentTimeMillis();
    }

    // Load items list once
    public synchronized void ensureItemsLoaded() {
        if (itemsLoaded) return;
        // Items endpoint is public — no API key needed
        try {
            ExoticRadar.LOGGER.info("Loading SkyBlock items...");
            JsonObject resp = fetchInternal(BASE_URL + "/resources/skyblock/items", false);
            itemDefaultColors = new HashMap<>();
            if (resp != null && resp.get("success").getAsBoolean()) {
                for (JsonElement el : resp.getAsJsonArray("items")) {
                    JsonObject item = el.getAsJsonObject();
                    if (!item.has("id")) continue;
                    String id = item.get("id").getAsString();
                    if (item.has("color")) {
                        try {
                            String[] p = item.get("color").getAsString().split(",");
                            int color = (Integer.parseInt(p[0].trim()) << 16)
                                      | (Integer.parseInt(p[1].trim()) << 8)
                                      |  Integer.parseInt(p[2].trim());
                            itemDefaultColors.put(id, color);
                        } catch (Exception ignored) {}
                    } else {
                        itemDefaultColors.put(id, null);
                    }
                }
                ExoticRadar.LOGGER.info("Loaded {} items", itemDefaultColors.size());
            }
        } catch (Exception e) {
            ExoticRadar.LOGGER.warn("Items load failed: {}", e.getMessage());
            itemDefaultColors = new HashMap<>();
        }
        itemsLoaded = true;
    }

    public Map<String, Integer> getItemDefaultColors() { return itemDefaultColors; }

    private JsonObject getMostRecentMember(JsonArray profiles, String uuid) {
        String plain  = uuid.replace("-", "");
        String dashed = plain.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
        JsonObject latest = null; long latestTime = 0;
        for (JsonElement el : profiles) {
            JsonObject p = el.getAsJsonObject();
            if (!p.has("members")) continue;
            JsonObject members = p.getAsJsonObject("members");
            JsonObject member = members.has(plain)  ? members.getAsJsonObject(plain)
                              : members.has(dashed) ? members.getAsJsonObject(dashed) : null;
            if (member == null) continue;
            long lastSave = 1;
            if (member.has("profile") && member.getAsJsonObject("profile").has("last_save"))
                lastSave = member.getAsJsonObject("profile").get("last_save").getAsLong();
            if (lastSave > latestTime) { latestTime = lastSave; latest = member; }
        }
        return latest;
    }

    public JsonObject fetchJson(String url) throws Exception { return fetchInternal(url, true); }

    public JsonObject fetchInternal(String urlString, boolean useKey) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(10000);
        if (useKey) conn.setRequestProperty("API-Key", Config.apiKey);
        int code = conn.getResponseCode();
        if (code == 429) throw new Exception((useKey ? "HYPIXEL_THROTTLE" : "MOJANG_THROTTLE") + ": 429");
        if (code != 200) return null;
        try (InputStreamReader r = new InputStreamReader(conn.getInputStream())) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }
}
