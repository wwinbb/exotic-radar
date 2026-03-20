package com.exoticradar;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.google.gson.*;
import java.util.*;

public class PlayerDetailScreen extends Screen {

    private final PlayerScanResult player;
    private final Screen parent;

    private int tab = 0; // 0=Overview 1=Armor 2=Inventory 3=EnderChest 4=Skills 5=Slayers 6=Dungeons 7=Collections
    private int scrollOffset = 0;
    private static final String[] TAB_NAMES = {"Overview","Armor","Inventory","Ender Chest","Skills","Slayers","Dungeons","Collections"};

    private static final int C_BG      = 0xEE060606;
    private static final int C_PANEL   = 0xFF0D0D0D;
    private static final int C_BORDER  = 0xFF2A2A2A;
    private static final int C_TAB     = 0xFF1A1A1A;
    private static final int C_TABON   = 0xFF2A2A4A;
    private static final int C_WHITE   = 0xFFFFFFFF;
    private static final int C_GRAY    = 0xFF888888;
    private static final int C_LGRAY   = 0xFFAAAAAA;
    private static final int C_GOLD    = 0xFFFFAA00;
    private static final int C_GREEN   = 0xFF55FF55;
    private static final int C_RED     = 0xFFFF5555;
    private static final int C_YELLOW  = 0xFFFFFF55;
    private static final int C_TEAL    = 0xFF55FFFF;
    private static final int C_PURPLE  = 0xFFAA00AA;
    private static final int LINE      = 12;
    private static final int TAB_H     = 18;
    private static final int HEADER_H  = 48;

    public PlayerDetailScreen(PlayerScanResult player, Screen parent) {
        super(Component.literal("Player Detail: " + player.username));
        this.player = player;
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Tab buttons
        int tabW = Math.min(80, (width - 20) / TAB_NAMES.length);
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int idx = i;
            addRenderableWidget(Button.builder(
                Component.literal(TAB_NAMES[i]),
                btn -> { tab = idx; scrollOffset = 0; })
                .pos(10 + i * (tabW + 2), HEADER_H).size(tabW, TAB_H).build());
        }
        // Back button
        addRenderableWidget(Button.builder(
            Component.literal("◀ Back"),
            btn -> minecraft.setScreen(parent))
            .pos(width - 70, 8).size(60, 16).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, C_BG);

        // Header
        g.fill(0, 0, width, HEADER_H, 0xFF0A0A0A);
        g.fill(0, HEADER_H - 1, width, HEADER_H, C_BORDER);
        g.drawString(font, "§6§l" + player.username, 10, 8, C_WHITE);
        g.drawString(font, "§7UUID: §f" + player.uuid, 10, 22, C_WHITE);
        String exoticStr = player.isExotic ? "§c§lEXOTIC" : "§7not exotic";
        g.drawString(font, "§7Exotic: " + exoticStr + "  §7Reason: §f" + player.exoticReason, 10, 34, C_WHITE);

        int contentY = HEADER_H + TAB_H + 6;
        g.fill(0, HEADER_H + TAB_H + 2, width, HEADER_H + TAB_H + 3, C_BORDER);

        switch (tab) {
            case 0 -> renderOverview(g, contentY);
            case 1 -> renderArmor(g, contentY);
            case 2 -> renderInventory(g, contentY, "inv_contents", "Main Inventory");
            case 3 -> renderInventory(g, contentY, "ender_chest_contents", "Ender Chest");
            case 4 -> renderSkills(g, contentY);
            case 5 -> renderSlayers(g, contentY);
            case 6 -> renderDungeons(g, contentY);
            case 7 -> renderCollections(g, contentY);
        }

        super.render(g, mouseX, mouseY, delta);
    }

    // ──────────────── OVERVIEW ────────────────
    private void renderOverview(GuiGraphics g, int startY) {
        if (player.rawMemberData == null) { g.drawString(font, "§cNo data available", 10, startY, C_WHITE); return; }
        JsonObject m = player.rawMemberData;
        int x = 10, y = startY;

        g.drawString(font, "§e§lGeneral", x, y, C_WHITE); y += LINE + 2;

        // Purse
        double purse = getDouble(m, "currencies", "coin_purse");
        g.drawString(font, "§7Coin Purse: §6" + formatCoins(purse), x, y, C_WHITE); y += LINE;

        // Skyblock level
        double sbXp = getDouble(m, "leveling", "experience");
        int sbLevel = (int)(sbXp / 100);
        g.drawString(font, "§7SkyBlock Level: §a" + sbLevel + " §8(" + formatNum(sbXp) + " XP)", x, y, C_WHITE); y += LINE;

        // First join
        long firstJoin = getLong(m, "profile", "first_join");
        if (firstJoin > 0) {
            g.drawString(font, "§7First Join: §f" + new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date(firstJoin)), x, y, C_WHITE); y += LINE;
        }

        // Fairy souls
        int fairySouls = getInt(m, "player_data", "fairy_souls");
        g.drawString(font, "§7Fairy Souls: §d" + fairySouls, x, y, C_WHITE); y += LINE;

        // Deaths / Kills
        int deaths = getInt(m, "player_data", "deaths");
        int kills  = getInt(m, "player_data", "kills");
        g.drawString(font, "§7Kills: §a" + formatNum(kills) + "  §7Deaths: §c" + formatNum(deaths), x, y, C_WHITE); y += LINE;

        y += 6; g.fill(x, y, width - 10, y + 1, C_BORDER); y += 6;
        g.drawString(font, "§e§lSkill Summary", x, y, C_WHITE); y += LINE + 2;

        // Quick skill overview
        String[] skills = {"FARMING","MINING","COMBAT","FORAGING","FISHING","ENCHANTING","ALCHEMY","CARPENTRY","RUNECRAFTING","SOCIAL"};
        JsonObject pd = getObj(m, "player_data");
        if (pd != null && pd.has("experience")) {
            JsonObject exp = pd.getAsJsonObject("experience");
            for (String skill : skills) {
                String key = "SKILL_" + skill;
                double xp = exp.has(key) ? exp.get(key).getAsDouble() : 0;
                int lvl = xpToSkillLevel(xp, skill);
                g.drawString(font, "§7" + cap(skill) + ": §a" + lvl + " §8(" + formatNum(xp) + ")", x, y, C_WHITE); y += LINE;
            }
        } else {
            g.drawString(font, "§8Skills API disabled by player", x, y, C_GRAY); y += LINE;
        }
    }

    // ──────────────── ARMOR ────────────────
    private void renderArmor(GuiGraphics g, int startY) {
        int x = 10, y = startY;
        g.drawString(font, "§e§lEquipped Armor", x, y, C_WHITE); y += LINE + 4;

        if (player.armorDetail == null) { g.drawString(font, "§cNo armor data", x, y, C_WHITE); return; }
        String[] slots = {"Boots","Leggings","Chestplate","Helmet"};
        for (int i = 0; i < 4; i++) {
            String hex = player.armorDetail.slotHex[i];
            ExoticDetector.ColorType type = player.armorDetail.slotType[i];
            String typeLabel = switch (type) {
                case EXOTIC -> "§c§lEXOTIC";
                case CRYSTAL -> "§dCRYSTAL DYE";
                case FAIRY -> "§5FAIRY DYE";
                case API_DEFAULT -> "§7API default";
                case OFFICIAL_DYE -> "§aOFFICIAL DYE (✦)";
                case DEFAULT -> "§7default leather";
                case NONE -> "§8no armor / undyed";
            };
            // Color swatch
            if (!hex.equals("none") && type != ExoticDetector.ColorType.NONE) {
                try {
                    int rgb = 0xFF000000 | Integer.parseInt(hex.substring(1), 16);
                    g.fill(x, y, x + 12, y + 11, rgb);
                    g.fill(x, y, x + 12, y + 1, 0x44FFFFFF);
                } catch (Exception ignored) {}
            }
            g.drawString(font, "§f" + slots[i] + ": " + hex + " §8— " + typeLabel, x + 16, y + 1, C_WHITE);
            y += LINE + 2;
        }

        // Also show raw inventory items in armor slots if available
        y += 6; g.fill(x, y, width - 10, y + 1, C_BORDER); y += 6;
        g.drawString(font, "§e§lArmor Item IDs", x, y, C_WHITE); y += LINE + 2;
        if (player.rawMemberData != null) {
            JsonObject inv = getObj(player.rawMemberData, "inventory");
            if (inv != null && inv.has("inv_armor")) {
                String data = inv.getAsJsonObject("inv_armor").get("data").getAsString();
                List<NbtParser.NbtItem> items = NbtParser.parseInventory(data);
                String[] armorSlots = {"Boots slot","Leggings slot","Chestplate slot","Helmet slot"};
                for (int i = 0; i < Math.min(4, items.size()); i++) {
                    NbtParser.NbtItem item = items.get(i);
                    String name = item.isEmpty ? "§8empty" : "§f" + (item.displayName.isEmpty() ? item.skyblockId : item.displayName);
                    String id   = item.isEmpty ? "" : " §8[" + item.skyblockId + "]";
                    g.drawString(font, "§7" + armorSlots[i] + ": " + name + id, x, y, C_WHITE); y += LINE;
                }
            }
        }
    }

    // ──────────────── INVENTORY / ENDER CHEST ────────────────
    private void renderInventory(GuiGraphics g, int startY, String field, String title) {
        if (player.rawMemberData == null) { g.drawString(font, "§cNo data", 10, startY, C_WHITE); return; }
        JsonObject inv = getObj(player.rawMemberData, "inventory");
        if (inv == null || !inv.has(field)) {
            g.drawString(font, "§8" + title + " not available (API disabled or empty)", 10, startY, C_WHITE); return;
        }
        String data = inv.getAsJsonObject(field).get("data").getAsString();
        List<NbtParser.NbtItem> items = NbtParser.parseInventory(data);

        g.drawString(font, "§e§l" + title + " §8(" + items.stream().filter(i -> !i.isEmpty).count() + " items)", 10, startY, C_WHITE);

        int x = 10, y = startY + LINE + 4;
        int maxY = height - 20;
        int lineH = LINE;
        int visStart = scrollOffset;

        // Filter non-empty
        List<NbtParser.NbtItem> nonEmpty = items.stream().filter(i -> !i.isEmpty).collect(java.util.stream.Collectors.toList());
        int maxScroll = Math.max(0, nonEmpty.size() - (maxY - y) / lineH);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        for (int i = visStart; i < nonEmpty.size() && y < maxY; i++) {
            NbtParser.NbtItem item = nonEmpty.get(i);
            String name = item.displayName.isEmpty() ? item.skyblockId : item.displayName;
            String colorStr = item.color != 0 ? " §8[color:" + String.format("#%06X", item.color) + "]" : "";
            String idStr = item.skyblockId.isEmpty() ? "" : " §8[" + item.skyblockId + "]";
            g.drawString(font, "§7" + (i+1) + ". §f" + (item.count > 1 ? item.count + "x " : "") + name + idStr + colorStr, x, y, C_WHITE);
            y += lineH;
        }

        if (nonEmpty.isEmpty()) g.drawString(font, "§8(empty)", x, y, C_WHITE);
    }

    // ──────────────── SKILLS ────────────────
    private void renderSkills(GuiGraphics g, int startY) {
        if (player.rawMemberData == null) { g.drawString(font, "§cNo data", 10, startY, C_WHITE); return; }
        int x = 10, y = startY;
        g.drawString(font, "§e§lSkills", x, y, C_WHITE); y += LINE + 4;

        JsonObject pd = getObj(player.rawMemberData, "player_data");
        if (pd == null || !pd.has("experience")) {
            g.drawString(font, "§8Skills API disabled by player", x, y, C_GRAY); return;
        }
        JsonObject exp = pd.getAsJsonObject("experience");

        String[][] skills = {
            {"FARMING","Farming"},{"MINING","Mining"},{"COMBAT","Combat"},
            {"FORAGING","Foraging"},{"FISHING","Fishing"},{"ENCHANTING","Enchanting"},
            {"ALCHEMY","Alchemy"},{"CARPENTRY","Carpentry"},{"RUNECRAFTING","Runecrafting"},
            {"SOCIAL","Social"},{"TAMING","Taming"}
        };

        double totalLevel = 0;
        int count = 0;
        for (String[] skill : skills) {
            String key = "SKILL_" + skill[0];
            double xp = exp.has(key) ? exp.get(key).getAsDouble() : 0;
            int lvl = xpToSkillLevel(xp, skill[0]);
            totalLevel += lvl; count++;
            // XP bar
            double[] thresholds = getSkillThresholds(skill[0]);
            double progress = 0;
            if (lvl < thresholds.length) {
                double needed = thresholds[lvl] - (lvl > 0 ? thresholds[lvl-1] : 0);
                double current = xp - (lvl > 0 ? thresholds[lvl-1] : 0);
                progress = needed > 0 ? Math.min(1.0, current / needed) : 1.0;
            }
            g.drawString(font, "§7" + skill[1] + ": §a" + lvl + " §8(" + formatNum(xp) + " XP)", x, y, C_WHITE);
            // Mini progress bar
            int barX = x + 160, barW = 100, barY = y + 2, barH = 8;
            g.fill(barX, barY, barX + barW, barY + barH, 0xFF1A1A1A);
            g.fill(barX, barY, barX + (int)(barW * progress), barY + barH, 0xFF55AA55);
            y += LINE + 1;
        }
        y += 4;
        g.drawString(font, "§7Average Skill Level: §a" + String.format("%.2f", totalLevel / count), x, y, C_WHITE);
    }

    // ──────────────── SLAYERS ────────────────
    private void renderSlayers(GuiGraphics g, int startY) {
        if (player.rawMemberData == null) { g.drawString(font, "§cNo data", 10, startY, C_WHITE); return; }
        int x = 10, y = startY;
        g.drawString(font, "§e§lSlayers", x, y, C_WHITE); y += LINE + 4;

        JsonObject slayer = getObj(player.rawMemberData, "slayer");
        if (slayer == null || !slayer.has("slayer_bosses")) {
            g.drawString(font, "§8No slayer data", x, y, C_GRAY); return;
        }
        JsonObject bosses = slayer.getAsJsonObject("slayer_bosses");

        String[][] types = {
            {"zombie","Revenant Horror","§a"},
            {"spider","Tarantula Broodfather","§c"},
            {"wolf","Sven Packmaster","§f"},
            {"enderman","Voidgloom Seraph","§5"},
            {"blaze","Inferno Demonlord","§6"},
            {"vampire","Riftstalker Bloodfiend","§4"}
        };

        for (String[] type : types) {
            String key = type[0];
            String name = type[1];
            String color = type[2];
            if (!bosses.has(key)) { g.drawString(font, color + name + ": §8not started", x, y, C_WHITE); y += LINE; continue; }
            JsonObject boss = bosses.getAsJsonObject(key);
            long xp = boss.has("xp") ? boss.get("xp").getAsLong() : 0;
            int lvl = slayerXpToLevel(key, xp);
            // Boss kills per tier
            StringBuilder kills = new StringBuilder();
            for (int t = 0; t <= 4; t++) {
                String killKey = "boss_kills_tier_" + t;
                if (boss.has(killKey)) kills.append("T").append(t+1).append(":").append(boss.get(killKey).getAsInt()).append(" ");
            }
            g.drawString(font, color + name + ": §aLvl " + lvl + " §8(" + formatNum(xp) + " XP) " + kills, x, y, C_WHITE);
            y += LINE;
        }
    }

    // ──────────────── DUNGEONS ────────────────
    private void renderDungeons(GuiGraphics g, int startY) {
        if (player.rawMemberData == null) { g.drawString(font, "§cNo data", 10, startY, C_WHITE); return; }
        int x = 10, y = startY;
        g.drawString(font, "§e§lDungeons", x, y, C_WHITE); y += LINE + 4;

        JsonObject dungeons = getObj(player.rawMemberData, "dungeons");
        if (dungeons == null) { g.drawString(font, "§8No dungeon data", x, y, C_GRAY); return; }

        // Catacombs level
        if (dungeons.has("dungeon_types")) {
            JsonObject types = dungeons.getAsJsonObject("dungeon_types");
            for (String dungeonType : new String[]{"catacombs","master_catacombs"}) {
                if (!types.has(dungeonType)) continue;
                JsonObject dt = types.getAsJsonObject(dungeonType);
                double xp = dt.has("experience") ? dt.get("experience").getAsDouble() : 0;
                int lvl = catacombsXpToLevel(xp);
                String label = dungeonType.equals("catacombs") ? "Catacombs" : "Master Mode";
                g.drawString(font, "§b" + label + ": §aLvl " + lvl + " §8(" + formatNum(xp) + " XP)", x, y, C_WHITE); y += LINE;

                // Floor completions
                if (dt.has("tier_completions")) {
                    JsonObject tc = dt.getAsJsonObject("tier_completions");
                    StringBuilder floors = new StringBuilder("§7Floors: ");
                    for (int f = 0; f <= 7; f++) {
                        String fk = String.valueOf(f);
                        if (tc.has(fk)) floors.append("F").append(f).append(":§a").append(tc.get(fk).getAsInt()).append("§7 ");
                    }
                    g.drawString(font, floors.toString(), x + 10, y, C_WHITE); y += LINE;
                }
                // Best scores
                if (dt.has("best_score")) {
                    JsonObject bs = dt.getAsJsonObject("best_score");
                    StringBuilder scores = new StringBuilder("§7Best scores: ");
                    for (int f = 0; f <= 7; f++) {
                        String fk = String.valueOf(f);
                        if (bs.has(fk)) scores.append("F").append(f).append(":§e").append(bs.get(fk).getAsInt()).append("§7 ");
                    }
                    g.drawString(font, scores.toString(), x + 10, y, C_WHITE); y += LINE;
                }
                y += 4;
            }
        }

        // Classes
        if (dungeons.has("player_classes")) {
            g.fill(x, y, width - 10, y + 1, C_BORDER); y += 5;
            g.drawString(font, "§e§lDungeon Classes", x, y, C_WHITE); y += LINE + 2;
            JsonObject classes = dungeons.getAsJsonObject("player_classes");
            String selected = dungeons.has("selected_dungeon_class") ? dungeons.get("selected_dungeon_class").getAsString() : "";
            String[] classNames = {"healer","mage","berserk","archer","tank"};
            String[] classColors = {"§d","§b","§c","§a","§f"};
            for (int i = 0; i < classNames.length; i++) {
                String cn = classNames[i];
                if (!classes.has(cn)) continue;
                JsonObject cls = classes.getAsJsonObject(cn);
                double xp = cls.has("experience") ? cls.get("experience").getAsDouble() : 0;
                int lvl = catacombsXpToLevel(xp);
                String active = cn.equals(selected) ? " §6(active)" : "";
                g.drawString(font, classColors[i] + cap(cn) + ": §aLvl " + lvl + " §8(" + formatNum(xp) + " XP)" + active, x, y, C_WHITE); y += LINE;
            }
        }
    }

    // ──────────────── COLLECTIONS ────────────────
    private void renderCollections(GuiGraphics g, int startY) {
        if (player.rawMemberData == null) { g.drawString(font, "§cNo data", 10, startY, C_WHITE); return; }
        JsonObject collection = getObj(player.rawMemberData, "collection");
        if (collection == null) { g.drawString(font, "§8Collections API disabled or empty", 10, startY, C_GRAY); return; }

        g.drawString(font, "§e§lCollections §8(" + collection.size() + " items)", 10, startY, C_WHITE);

        // Sort by count descending
        List<Map.Entry<String, JsonElement>> entries = new ArrayList<>(collection.entrySet());
        entries.sort((a, b) -> Long.compare(b.getValue().getAsLong(), a.getValue().getAsLong()));

        int x = 10, y = startY + LINE + 4;
        int lineH = LINE;
        int maxY = height - 20;
        int visStart = scrollOffset;
        int maxScroll = Math.max(0, entries.size() - (maxY - y) / lineH);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        for (int i = visStart; i < entries.size() && y < maxY; i++) {
            Map.Entry<String, JsonElement> e = entries.get(i);
            String name = e.getKey().replace("_", " ");
            long count = e.getValue().getAsLong();
            g.drawString(font, "§7" + name + ": §a" + formatNum(count), x, y, C_WHITE);
            y += lineH;
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        scrollOffset = Math.max(0, scrollOffset - (int)v * 2);
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ──────────────── Helpers ────────────────

    private JsonObject getObj(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonObject()) return null;
        return parent.getAsJsonObject(key);
    }

    private double getDouble(JsonObject m, String section, String key) {
        JsonObject s = getObj(m, section);
        if (s == null || !s.has(key)) return 0;
        try { return s.get(key).getAsDouble(); } catch (Exception e) { return 0; }
    }

    private long getLong(JsonObject m, String section, String key) {
        JsonObject s = getObj(m, section);
        if (s == null || !s.has(key)) return 0;
        try { return s.get(key).getAsLong(); } catch (Exception e) { return 0; }
    }

    private int getInt(JsonObject m, String section, String key) {
        JsonObject s = getObj(m, section);
        if (s == null || !s.has(key)) return 0;
        try { return s.get(key).getAsInt(); } catch (Exception e) { return 0; }
    }

    private String formatCoins(double c) {
        if (c >= 1_000_000_000) return String.format("%.2fB", c / 1_000_000_000);
        if (c >= 1_000_000)     return String.format("%.2fM", c / 1_000_000);
        if (c >= 1_000)         return String.format("%.1fK", c / 1_000);
        return String.format("%.0f", c);
    }

    private String formatNum(double n) {
        if (n >= 1_000_000_000) return String.format("%.1fB", n / 1_000_000_000);
        if (n >= 1_000_000)     return String.format("%.1fM", n / 1_000_000);
        if (n >= 1_000)         return String.format("%.1fK", n / 1_000);
        return String.format("%.0f", n);
    }

    private String formatNum(long n) { return formatNum((double)n); }

    private String cap(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    // Skill XP thresholds (cumulative) — standard Hypixel SkyBlock values
    private static final double[] SKILL_XP = {
        50,175,375,675,1175,1925,2925,4425,6425,9925,14925,22425,32425,47425,
        67425,97425,147425,222425,322425,522425,822425,1222425,1722425,2322425,
        3022425,3822425,4722425,5722425,6822425,8022425,9322425,10722425,12222425,
        13822425,15522425,17322425,19222425,21222425,23322425,25522425,27822425,
        30222425,32722425,35322425,38072425,40972425,44072425,47472425,51172425,55172425
    };

    private int xpToSkillLevel(double xp, String skill) {
        int maxLevel = skill.equals("RUNECRAFTING") || skill.equals("SOCIAL") ? 25 : 60;
        for (int i = 0; i < Math.min(SKILL_XP.length, maxLevel); i++) {
            if (xp < SKILL_XP[i]) return i;
        }
        return maxLevel;
    }

    private double[] getSkillThresholds(String skill) { return SKILL_XP; }

    private static final double[] CATA_XP = {
        50,75,110,160,230,330,480,680,1000,1430,2050,2925,4135,5900,8425,
        12025,17150,24500,35000,50000,70000,100000,143000,204000,291000,415000,
        591540,842400,1200600,1708200,2432700,3464100,4931100,7016100,9986100,
        14218100,20238100,28809100,41015100,58415100,83115100,118315100,168315100,
        238315100,338315100,488315100,688315100,988315100,1388315100,1888315100
    };

    private int catacombsXpToLevel(double xp) {
        for (int i = 0; i < CATA_XP.length; i++) {
            if (xp < CATA_XP[i]) return i;
        }
        return 50;
    }

    private int slayerXpToLevel(String type, long xp) {
        int[] thresholds = switch (type) {
            case "vampire" -> new int[]{20, 75, 240, 840, 2400};
            default        -> new int[]{5, 15, 200, 1000, 5000, 20000, 100000, 400000, 1000000};
        };
        int lvl = 0;
        for (int t : thresholds) { if (xp >= t) lvl++; else break; }
        return lvl;
    }
}
