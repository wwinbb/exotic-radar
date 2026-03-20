package com.exoticradar;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.*;
import java.util.zip.GZIPInputStream;

public class NbtParser {

    private static final byte TAG_END      = 0;
    private static final byte TAG_BYTE     = 1;
    private static final byte TAG_SHORT    = 2;
    private static final byte TAG_INT      = 3;
    private static final byte TAG_LONG     = 4;
    private static final byte TAG_FLOAT    = 5;
    private static final byte TAG_DOUBLE   = 6;
    private static final byte TAG_BYTE_ARR = 7;
    private static final byte TAG_STRING   = 8;
    private static final byte TAG_LIST     = 9;
    private static final byte TAG_COMPOUND = 10;
    private static final byte TAG_INT_ARR  = 11;
    private static final byte TAG_LONG_ARR = 12;

    public static class NbtItem {
        public String displayName = "";
        public String skyblockId = "";
        public int count = 0;
        public int color = 0;  // from tag.display.color (the correct location)
        public boolean isEmpty = true;

        @Override
        public String toString() {
            if (isEmpty) return "empty";
            String name = displayName.isEmpty() ? skyblockId : displayName;
            return (count > 1 ? count + "x " : "") + name;
        }
    }

    public static List<NbtItem> parseInventory(String base64Data) {
        List<NbtItem> items = new ArrayList<>();
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Data);
            DataInputStream in = new DataInputStream(
                new GZIPInputStream(new ByteArrayInputStream(decoded)));
            byte rootType = in.readByte();
            if (rootType != TAG_COMPOUND) return items;
            readString(in); // root name
            Map<String, Object> root = readCompound(in);
            in.close();

            Object iObj = root.get("i");
            if (!(iObj instanceof List)) return items;

            for (Object entry : (List<?>) iObj) {
                NbtItem item = new NbtItem();
                if (entry instanceof Map) {
                    Map<?, ?> compound = (Map<?, ?>) entry;
                    Object countObj = compound.get("Count");
                    if (countObj instanceof Number) {
                        item.count = ((Number) countObj).intValue();
                        item.isEmpty = item.count == 0;
                    }
                    Object tagObj = compound.get("tag");
                    if (tagObj instanceof Map) {
                        Map<?, ?> tag = (Map<?, ?>) tagObj;

                        // tag.display → Name and color
                        Object displayObj = tag.get("display");
                        if (displayObj instanceof Map) {
                            Map<?, ?> display = (Map<?, ?>) displayObj;
                            Object nameObj = display.get("Name");
                            if (nameObj instanceof String)
                                item.displayName = stripFormatting((String) nameObj);

                            // ── CRITICAL: color lives here per your algorithm ──
                            Object colorObj = display.get("color");
                            if (colorObj instanceof Number)
                                item.color = ((Number) colorObj).intValue();
                        }

                        // tag.ExtraAttributes → SkyBlock ID
                        Object extraObj = tag.get("ExtraAttributes");
                        if (extraObj instanceof Map) {
                            Object idObj = ((Map<?, ?>) extraObj).get("id");
                            if (idObj instanceof String) item.skyblockId = (String) idObj;
                        }
                    }
                }
                items.add(item);
            }
        } catch (Exception e) {
            ExoticRadar.LOGGER.warn("NBT parse error: {}", e.getMessage());
        }
        return items;
    }

    private static String stripFormatting(String s) {
        if (s == null) return "";
        // JSON text component: {"text":"..."}
        if (s.startsWith("{")) {
            int idx = s.indexOf("\"text\":\"");
            if (idx >= 0) {
                int start = idx + 8;
                int end = s.indexOf("\"", start);
                if (end > start) s = s.substring(start, end);
            }
        }
        // Unescape JSON unicode escapes
        s = s.replace("\\u2736", "✦").replace("\\u00a7", "§");
        // Strip § color codes
        return s.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readCompound(DataInputStream in) throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        while (true) {
            byte type = in.readByte();
            if (type == TAG_END) break;
            String name = readString(in);
            map.put(name, readPayload(in, type));
        }
        return map;
    }

    private static Object readPayload(DataInputStream in, byte type) throws Exception {
        return switch (type) {
            case TAG_BYTE     -> in.readByte();
            case TAG_SHORT    -> in.readShort();
            case TAG_INT      -> in.readInt();
            case TAG_LONG     -> in.readLong();
            case TAG_FLOAT    -> in.readFloat();
            case TAG_DOUBLE   -> in.readDouble();
            case TAG_BYTE_ARR -> { int len = in.readInt(); byte[] b = new byte[len]; in.readFully(b); yield b; }
            case TAG_STRING   -> readString(in);
            case TAG_LIST     -> readList(in);
            case TAG_COMPOUND -> readCompound(in);
            case TAG_INT_ARR  -> { int len = in.readInt(); int[] a = new int[len]; for (int i=0;i<len;i++) a[i]=in.readInt(); yield a; }
            case TAG_LONG_ARR -> { int len = in.readInt(); long[] a = new long[len]; for (int i=0;i<len;i++) a[i]=in.readLong(); yield a; }
            default -> throw new Exception("Unknown tag type: " + type);
        };
    }

    private static List<Object> readList(DataInputStream in) throws Exception {
        byte elemType = in.readByte();
        int len = in.readInt();
        List<Object> list = new ArrayList<>(len);
        for (int i = 0; i < len; i++) list.add(readPayload(in, elemType));
        return list;
    }

    private static String readString(DataInputStream in) throws Exception {
        int len = in.readUnsignedShort();
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, "UTF-8");
    }
}
