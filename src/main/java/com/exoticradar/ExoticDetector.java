package com.exoticradar;

import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.*;
import java.util.zip.GZIPInputStream;

public class ExoticDetector {

    private static final int DEFAULT_LEATHER_COLOR = 10511680; // 0xA06540

    private static final Set<String> LEATHER_ARMOR_IDS = Set.of(
        "LEATHER_HELMET", "LEATHER_CHESTPLATE", "LEATHER_LEGGINGS", "LEATHER_BOOTS"
    );

    // Crystal armor colors (light level 0–15), from LeaPhant's verified data
    private static final Set<Integer> CRYSTAL_COLORS = Set.of(
        0x1F0030, 0x46085E, 0x54146E, 0x5D1C78, 0x63237D,
        0x6A2C82, 0x7E4196, 0x8E51A6, 0x9C64B3, 0xA875BD,
        0xB88BC9, 0xC6A3D4, 0xD9C1E3, 0xE5D1ED, 0xEFE1F5, 0xFCF3FF
    );

    // Fairy armor chroma cycle colors, from LeaPhant's verified data
    private static final Set<Integer> FAIRY_COLORS = Set.of(
        0x660066, 0x660033, 0x99004C, 0xCC0066, 0xFF007F,
        0xFF3399, 0xFF66B2, 0xFF99CC, 0xFFCCE5, 0x990099,
        0xCC00CC, 0xFF00FF, 0xFF33FF, 0xFF66FF, 0xFF99FF,
        0xFFCCFF, 0xE5CCFF, 0xCC99FF, 0xB266FF, 0x9933FF,
        0x7F00FF, 0x6600CC, 0x4C0099, 0x330066
    );

    public enum ColorType {
        NONE,           // no armor / no color tag
        DEFAULT,        // vanilla brown 0xA06540
        CRYSTAL,        // crystal dye
        FAIRY,          // fairy chroma
        API_DEFAULT,    // matches the item's API-listed color (e.g. Wise Dragon blue)
        OFFICIAL_DYE,   // post-0.12.3 anvil dye (✦ in name)
        EXOTIC          // everything else
    }

    public static class SlotResult {
        public String skyblockId = "";
        public String displayName = "";
        public int color = 0;
        public String hex = "none";
        public ColorType type = ColorType.NONE;
        public String reason = "no armor";
        public boolean isLeather = false;
    }

    public static class ArmorScanResult {
        public SlotResult[] slots = new SlotResult[]{
            new SlotResult(), new SlotResult(), new SlotResult(), new SlotResult()
        };
        public String exoticHex = null;
        public String exoticSlot = null;
        public String noExoticReason = "no exotic armor";
        public String error = null;
        // Legacy compat
        public String[] slotHex = {"none","none","none","none"};
        public ColorType[] slotType = {ColorType.NONE,ColorType.NONE,ColorType.NONE,ColorType.NONE};
    }

    public static boolean hasExoticArmor(JsonObject memberData) {
        return Boolean.parseBoolean(hasExoticArmorWithReason(memberData)[0]);
    }

    public static String[] hasExoticArmorWithReason(JsonObject memberData) {
        ArmorScanResult r = scanArmor(memberData, HypixelApiClient.get().getItemDefaultColors());
        if (r.error != null) return new String[]{"false", r.error};
        if (r.exoticHex != null) return new String[]{"true", "exotic: " + r.exoticSlot + " " + r.exoticHex};
        return new String[]{"false", r.noExoticReason};
    }

    public static ArmorScanResult scanArmor(JsonObject memberData, Map<String, Integer> itemColors) {
        ArmorScanResult result = new ArmorScanResult();
        try {
            if (!memberData.has("inventory")) { result.error = "no inventory data"; return result; }
            JsonObject inventory = memberData.getAsJsonObject("inventory");
            if (!inventory.has("inv_armor")) { result.error = "no inv_armor"; return result; }
            JsonObject invArmor = inventory.getAsJsonObject("inv_armor");
            if (!invArmor.has("data")) { result.error = "no data field"; return result; }

            List<NbtParser.NbtItem> items = NbtParser.parseInventory(invArmor.get("data").getAsString());
            String[] slotNames = {"boots", "leggings", "chestplate", "helmet"};

            StringBuilder noExoticReasons = new StringBuilder();

            for (int i = 0; i < 4 && i < items.size(); i++) {
                NbtParser.NbtItem nbtItem = items.get(i);
                SlotResult slot = result.slots[i];
                slot.skyblockId = nbtItem.skyblockId;
                slot.displayName = nbtItem.displayName;
                slot.color = nbtItem.color;
                slot.hex = nbtItem.color != 0 ? String.format("#%06X", nbtItem.color) : "none";
                result.slotHex[i] = slot.hex;

                // ── Step 1: Is it leather armor? ──
                boolean isVanillaLeather = LEATHER_ARMOR_IDS.contains(nbtItem.skyblockId.toUpperCase());
                // Also treat any leather material item (SkyBlock custom leather armors)
                // as leather if the items API lists it as LEATHER_* material
                boolean isLeather = isVanillaLeather || isLeatherByApiMaterial(nbtItem.skyblockId, itemColors);
                slot.isLeather = isLeather;

                if (!isLeather || nbtItem.isEmpty) {
                    slot.type = ColorType.NONE;
                    slot.reason = nbtItem.isEmpty ? "empty slot" : "not leather";
                    result.slotType[i] = slot.type;
                    noExoticReasons.append(slotNames[i]).append(":not-leather ");
                    continue;
                }

                // ── Step 2: Does color exist? ──
                if (nbtItem.color == 0) {
                    slot.type = ColorType.DEFAULT;
                    slot.reason = "undyed (no color tag)";
                    result.slotType[i] = slot.type;
                    noExoticReasons.append(slotNames[i]).append(":undyed ");
                    continue;
                }

                // ── Step 3: Is it default vanilla brown? ──
                if (nbtItem.color == DEFAULT_LEATHER_COLOR) {
                    slot.type = ColorType.DEFAULT;
                    slot.reason = "default brown";
                    result.slotType[i] = slot.type;
                    noExoticReasons.append(slotNames[i]).append(":default-brown ");
                    continue;
                }

                // ── Step 4: Crystal color? ──
                if (CRYSTAL_COLORS.contains(nbtItem.color)) {
                    slot.type = ColorType.CRYSTAL;
                    slot.reason = "crystal dyed";
                    result.slotType[i] = slot.type;
                    noExoticReasons.append(slotNames[i]).append(":crystal ");
                    continue;
                }

                // ── Step 5: Fairy color? ──
                if (FAIRY_COLORS.contains(nbtItem.color)) {
                    slot.type = ColorType.FAIRY;
                    slot.reason = "fairy dyed";
                    result.slotType[i] = slot.type;
                    noExoticReasons.append(slotNames[i]).append(":fairy ");
                    continue;
                }

                // ── Step 6a: Check for official dye marker (✦ in name) ──
                // This must happen BEFORE the API lookup because items with no
                // API default color but an official dye applied are NOT exotic.
                if (nbtItem.displayName.contains("✦") || nbtItem.displayName.contains("\u2726") || nbtItem.displayName.contains("✿") || nbtItem.displayName.contains("\u273F")) {
                    slot.type = ColorType.OFFICIAL_DYE;
                    slot.reason = "official dye (✦ in name)";
                    result.slotType[i] = slot.type;
                    noExoticReasons.append(slotNames[i]).append(":official-dye ");
                    continue;
                }

                // ── Step 6+7: Look up API default color for this item ──
                String itemId = nbtItem.skyblockId.toUpperCase();
                boolean itemInApi = itemColors != null && itemColors.containsKey(itemId);
                Integer apiColor = itemColors != null ? itemColors.get(itemId) : null;
                ExoticRadar.LOGGER.info("Checking: item={} color={} itemsMapSize={} inApi={} apiColor={}",
                    itemId, nbtItem.color,
                    itemColors != null ? itemColors.size() : -1,
                    itemInApi, apiColor);

                if (itemInApi && apiColor == null) {
                    // Item exists in API but has no color field → step 9 path
                    // Has a color but API says no default → exotic
                    slot.type = ColorType.EXOTIC;
                    slot.reason = "no API default, colored → exotic";
                    slot.hex = String.format("#%06X", nbtItem.color);
                    result.slotType[i] = slot.type;
                    result.exoticHex = slot.hex;
                    result.exoticSlot = slotNames[i];
                    ExoticRadar.LOGGER.info("Exotic (step 9): {} slot={} color={}", itemId, slotNames[i], slot.hex);
                    continue;
                }

                if (itemInApi && apiColor != null) {
                    // ── Step 8: Does color match API default? ──
                    ExoticRadar.LOGGER.info("Step 8 check: item={} nbtColor={} apiColor={} match={}",
                        itemId, nbtItem.color, apiColor, nbtItem.color == apiColor);
                    if (nbtItem.color == apiColor) {
                        slot.type = ColorType.API_DEFAULT;
                        slot.reason = "API default color " + String.format("#%06X", apiColor);
                        result.slotType[i] = slot.type;
                        noExoticReasons.append(slotNames[i]).append(":api-default ");
                        continue;
                    }

                    // Color differs from API default — check HOW it was changed

                    // ── Step 8a: Does name contain ✦ (post-0.12.3 official dye)? ──
                    if (nbtItem.displayName.contains("✦")) {
                        slot.type = ColorType.OFFICIAL_DYE;
                        slot.reason = "official anvil dye (✦)";
                        result.slotType[i] = slot.type;
                        noExoticReasons.append(slotNames[i]).append(":official-dye ");
                        continue;
                    }

                    // ── Step 8b: Crystal over default? ──
                    if (CRYSTAL_COLORS.contains(nbtItem.color)) {
                        slot.type = ColorType.CRYSTAL;
                        slot.reason = "crystal dyed over default";
                        result.slotType[i] = slot.type;
                        noExoticReasons.append(slotNames[i]).append(":crystal ");
                        continue;
                    }

                    // ── Step 8c: Fairy over default? ──
                    if (FAIRY_COLORS.contains(nbtItem.color)) {
                        slot.type = ColorType.FAIRY;
                        slot.reason = "fairy dyed over default";
                        result.slotType[i] = slot.type;
                        noExoticReasons.append(slotNames[i]).append(":fairy ");
                        continue;
                    }

                    // ── Step 8d: Stripped back to vanilla brown? ──
                    if (nbtItem.color == DEFAULT_LEATHER_COLOR) {
                        slot.type = ColorType.DEFAULT;
                        slot.reason = "stripped to vanilla brown";
                        result.slotType[i] = slot.type;
                        noExoticReasons.append(slotNames[i]).append(":stripped ");
                        continue;
                    }

                    // ── Step 8e: EXOTIC ──
                    slot.type = ColorType.EXOTIC;
                    slot.hex = String.format("#%06X", nbtItem.color);
                    slot.reason = "exotic (changed from API default " + String.format("#%06X", apiColor) + ")";
                    result.slotType[i] = slot.type;
                    result.exoticHex = slot.hex;
                    result.exoticSlot = slotNames[i];
                    ExoticRadar.LOGGER.info("Exotic (step 8e): {} slot={} color={} apiDefault={}",
                        itemId, slotNames[i], slot.hex, String.format("#%06X", apiColor));
                    continue;
                }

                // ── Step 9: Item not in API, has non-default color ──
                // Pre-Nov 2019 or glitch dyed
                slot.type = ColorType.EXOTIC;
                slot.hex = String.format("#%06X", nbtItem.color);
                slot.reason = "exotic (not in items API, colored)";
                result.slotType[i] = slot.type;
                result.exoticHex = slot.hex;
                result.exoticSlot = slotNames[i];
                ExoticRadar.LOGGER.info("Exotic (step 9): {} slot={} color={}", itemId, slotNames[i], slot.hex);
            }

            if (result.exoticHex == null) {
                result.noExoticReason = noExoticReasons.toString().trim();
                if (result.noExoticReason.isEmpty()) result.noExoticReason = "no leather armor";
            }

        } catch (Exception e) {
            result.error = "scan error: " + e.getMessage();
            ExoticRadar.LOGGER.warn("ExoticDetector error: {}", e.getMessage());
        }
        return result;
    }

    // Check if item is leather by looking at the SkyBlock item ID pattern
    // SkyBlock leather armors have IDs like FAIRY_ARMOR_HELMET, FARM_ARMOR_BOOTS etc.
    // We rely on the items API color field existence as the indicator since
    // all leather armor in SkyBlock has a color field when colored
    private static boolean isLeatherByApiMaterial(String skyblockId, Map<String, Integer> itemColors) {
        // Only leather-material items can have a color tag in Minecraft.
        // So any SkyBlock armor item with a color tag IS leather-material.
        // However: if the items API hasn't loaded yet (null map), we can't be sure.
        // In that case, be conservative and return true so we don't miss exotics.
        // The API color check at step 7-8 will handle false positives once loaded.
        if (itemColors == null) return true;
        // If item is in the API with a color field -> confirmed leather
        if (itemColors.containsKey(skyblockId.toUpperCase()) && itemColors.get(skyblockId.toUpperCase()) != null) return true;
        // If item is in the API with null color -> Hypixel says it has no color -> NOT leather for our purposes
        if (itemColors.containsKey(skyblockId.toUpperCase()) && itemColors.get(skyblockId.toUpperCase()) == null) return false;
        // Item not in API at all -> unknown, treat as leather (conservative)
        return true;
    }
}
