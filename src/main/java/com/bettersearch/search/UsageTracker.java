package com.bettersearch.search;

import com.bettersearch.BetterSearchAddon;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Module;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Wurst-Navigator-style usage learning: counts how often each module
 * is toggled/opened and boosts frequently used modules in search results.
 * Persisted to {@code meteor-client/better-search-usage.json} (minimal JSON, no extra deps).
 *
 * <p>Credits: Turbo</p>
 */
public final class UsageTracker {
    private static final Map<String, Integer> COUNTS = new HashMap<>();
    private static File file;

    private UsageTracker() {}

    private static synchronized File getFile() {
        if (file == null) {
            file = new File(MeteorClient.FOLDER, "better-search-usage.json");
        }
        return file;
    }

    public static synchronized void load() {
        try {
            File f = getFile();
            if (!f.exists()) return;
            String json = Files.readString(f.toPath()).trim();
            if (json.isEmpty()) return;
            // Minimal parser for {"key":123,...} — keys are lower-case module names (no quotes/escapes needed)
            if (json.startsWith("{") && json.endsWith("}")) {
                json = json.substring(1, json.length() - 1).trim();
                if (json.isEmpty()) return;
                for (String entry : json.split(",")) {
                    String[] kv = entry.split(":", 2);
                    if (kv.length != 2) continue;
                    String k = kv[0].trim().replaceAll("^\"|\"$", "");
                    String v = kv[1].trim().replaceAll("[^0-9-]", "");
                    if (k.isEmpty() || v.isEmpty()) continue;
                    try {
                        COUNTS.put(k.toLowerCase(Locale.ROOT), Integer.parseInt(v));
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (Exception e) {
            BetterSearchAddon.LOG.warn("Better Search (by Turbo): failed to load usage file", e);
        }
    }

    public static synchronized void save() {
        try {
            File f = getFile();
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Integer> e : COUNTS.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(e.getKey().replace("\"", "")).append("\":").append(e.getValue());
            }
            sb.append("}");
            Files.writeString(f.toPath(), sb.toString());
        } catch (Exception e) {
            BetterSearchAddon.LOG.warn("Better Search (by Turbo): failed to save usage file", e);
        }
    }

    /** Key is stable across sessions: lowercase module name. */
    public static String key(Module module) {
        return module == null ? "" : module.name.toLowerCase(Locale.ROOT);
    }

    public static synchronized void record(Module module) {
        if (module == null) return;
        COUNTS.merge(key(module), 1, Integer::sum);
    }

    public static synchronized int getCount(Module module) {
        if (module == null) return 0;
        return COUNTS.getOrDefault(key(module), 0);
    }
}
