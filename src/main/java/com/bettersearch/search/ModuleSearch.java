package com.bettersearch.search;

import com.bettersearch.SearchTags;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wurst-Navigator-style module search for Meteor.
 * Searches name, title, description, category, aliases, settings and
 * {@link SearchTags}, then ranks by match quality + usage frequency.
 *
 * <p>Perf: per-module searchable strings are cached (lowercased once);
 * excellent name/title hits skip the expensive description/settings pass.</p>
 *
 * <p>Credits: Turbo</p>
 */
public final class ModuleSearch {
    private ModuleSearch() {}

    public record Result(Module module, String matchedText, int score, int uses) {}

    /** Cached searchable data per module (titles are static, so cache is safe). */
    private record Cached(String title, String name, List<String> aliases, List<String> tags,
                          String category, String description, List<String> settingTitles) {}

    private static final Map<Module, Cached> CACHE = new ConcurrentHashMap<>();

    private static Cached cached(Module m) {
        Cached c = CACHE.get(m);
        if (c != null) return c;

        List<String> aliases = new ArrayList<>();
        if (m.aliases != null) {
            for (String a : m.aliases) {
                if (a != null && !a.isBlank()) aliases.add(a);
            }
        }
        List<String> tags = getSearchTags(m);
        String category = m.category != null ? m.category.name : "";
        String description = m.description != null ? m.description : "";

        // Cache setting titles + names once (settings list is static per module)
        List<String> settingTitles = new ArrayList<>();
        try {
            for (meteordevelopment.meteorclient.settings.SettingGroup sg : m.settings) {
                for (meteordevelopment.meteorclient.settings.Setting<?> s : sg) {
                    if (s.title != null && !s.title.isBlank()) settingTitles.add(s.title);
                    if (s.name != null && !s.name.isBlank() && !s.name.equals(s.title)) settingTitles.add(s.name);
                }
            }
        } catch (Exception ignored) {}

        c = new Cached(m.title, m.name, List.copyOf(aliases), List.copyOf(tags),
            category, description, List.copyOf(settingTitles));
        CACHE.put(m, c);
        return c;
    }

    public static List<Result> search(String query, boolean searchDescription, boolean searchSettings, boolean searchTags, int maxResults) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        List<Result> out = new ArrayList<>();
        for (Module m : Modules.get().getAll()) {
            if (q.isEmpty()) {
                // Wurst behaviour: empty query shows everything, most-used first
                out.add(new Result(m, m.title, 0, UsageTracker.getCount(m)));
                continue;
            }

            Cached c = cached(m);
            int best = Integer.MAX_VALUE;
            String matched = c.title();

            // name + title (weight: strongest)
            int s = FuzzyMatcher.score(c.title(), q);
            if (s < best) {
                best = s;
                matched = c.title();
            }
            s = FuzzyMatcher.score(c.name(), q);
            if (s < best) {
                best = s;
                matched = c.title() + " (" + c.name() + ")";
            }

            // aliases (Meteor built-in synonyms)
            for (String alias : c.aliases()) {
                s = FuzzyMatcher.score(alias, q);
                if (s < best) {
                    best = s;
                    matched = c.title() + " (" + alias + ")";
                }
            }

            // custom SearchTags (Wurst @SearchTags equivalent)
            if (searchTags) {
                for (String tag : c.tags()) {
                    // tags get a small bonus so synonyms rank well
                    s = FuzzyMatcher.score(tag, q);
                    if (s != Integer.MAX_VALUE) s -= 5;
                    if (s < best) {
                        best = s;
                        matched = c.title() + " (" + tag + ")";
                    }
                }
            }

            // category, e.g. "combat" finds all combat modules (cheap single check, always run)
            s = FuzzyMatcher.score(c.category(), q);
            if (s != Integer.MAX_VALUE) s += 150; // weaker than name, stronger than description
            if (s < best) {
                best = s;
                matched = c.title() + " [" + c.category() + "]";
            }

            // Perf early-out: excellent name/title/alias/tag hit already — skip expensive passes
            if (best > 10) {
                // description
                if (searchDescription && !c.description().isEmpty()) {
                    s = FuzzyMatcher.score(c.description(), q);
                    if (s != Integer.MAX_VALUE) s += 400;
                    if (s < best) {
                        best = s;
                        matched = c.title();
                    }
                }

                // settings, e.g. "range" finds KillAura (cached titles)
                if (searchSettings && best > 10) {
                    String bestSetting = null;
                    int bestSettingScore = Integer.MAX_VALUE;
                    for (String st : c.settingTitles()) {
                        int ss = FuzzyMatcher.score(st, q);
                        if (ss < bestSettingScore) {
                            bestSettingScore = ss;
                            bestSetting = st;
                        }
                    }
                    if (bestSettingScore != Integer.MAX_VALUE) {
                        bestSettingScore += 250;
                        if (bestSettingScore < best) {
                            best = bestSettingScore;
                            matched = c.title() + " <" + bestSetting + ">";
                        }
                    }
                }
            }

            if (best != Integer.MAX_VALUE) {
                out.add(new Result(m, matched, best, UsageTracker.getCount(m)));
            }
        }

        // Rank: score first, then most-used (Wurst learning), then alphabetical
        out.sort(Comparator
            .comparingInt(Result::score)
            .thenComparing(Comparator.comparingInt(Result::uses).reversed())
            .thenComparing(r -> r.module().title, String.CASE_INSENSITIVE_ORDER));

        if (maxResults > 0 && out.size() > maxResults) {
            return out.subList(0, maxResults);
        }
        return out;
    }

    public static List<String> getSearchTags(Module module) {
        List<String> tags = new ArrayList<>();
        SearchTags ann = module.getClass().getAnnotation(SearchTags.class);
        if (ann != null) {
            for (String t : ann.value()) {
                if (t != null && !t.isBlank()) tags.add(t);
            }
        }
        return tags;
    }
}
