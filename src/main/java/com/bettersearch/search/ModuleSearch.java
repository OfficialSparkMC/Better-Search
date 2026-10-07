package com.bettersearch.search;

import com.bettersearch.SearchTags;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Wurst-Navigator-style module search for Meteor.
 * Searches name, title, description, category, aliases, settings and
 * {@link SearchTags}, then ranks by match quality + usage frequency.
 *
 * <p>Credits: Turbo</p>
 */
public final class ModuleSearch {
    private ModuleSearch() {}

    public record Result(Module module, String matchedText, int score, int uses) {}

    public static List<Result> search(String query, boolean searchDescription, boolean searchSettings, boolean searchTags, int maxResults) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        List<Result> out = new ArrayList<>();
        for (Module m : Modules.get().getAll()) {
            if (q.isEmpty()) {
                // Wurst behaviour: empty query shows everything, most-used first
                out.add(new Result(m, m.title, 0, UsageTracker.getCount(m)));
                continue;
            }

            int best = Integer.MAX_VALUE;
            String matched = m.title;

            // name + title (weight: strongest)
            int s = FuzzyMatcher.score(m.title, q);
            if (s < best) {
                best = s;
                matched = m.title;
            }
            s = FuzzyMatcher.score(m.name, q);
            if (s < best) {
                best = s;
                matched = m.title + " (" + m.name + ")";
            }

            // aliases (Meteor built-in synonyms)
            for (String alias : m.aliases) {
                s = FuzzyMatcher.score(alias, q);
                if (s < best) {
                    best = s;
                    matched = m.title + " (" + alias + ")";
                }
            }

            // custom SearchTags (Wurst @SearchTags equivalent)
            if (searchTags) {
                for (String tag : getSearchTags(m)) {
                    // tags get a small bonus so synonyms rank well
                    s = FuzzyMatcher.score(tag, q);
                    if (s != Integer.MAX_VALUE) s -= 5;
                    if (s < best) {
                        best = s;
                        matched = m.title + " (" + tag + ")";
                    }
                }
            }

            // category, e.g. "combat" finds all combat modules
            s = FuzzyMatcher.score(m.category.name, q);
            if (s != Integer.MAX_VALUE) s += 150; // weaker than name, stronger than description
            if (s < best) {
                best = s;
                matched = m.title + " [" + m.category.name + "]";
            }

            // description
            if (searchDescription && m.description != null) {
                s = FuzzyMatcher.score(m.description, q);
                if (s != Integer.MAX_VALUE) s += 400;
                if (s < best) {
                    best = s;
                    matched = m.title;
                }
            }

            // settings, e.g. "range" finds KillAura
            if (searchSettings) {
                String bestSetting = null;
                int bestSettingScore = Integer.MAX_VALUE;
                for (SettingGroup sg : m.settings) {
                    for (Setting<?> setting : sg) {
                        int ss = FuzzyMatcher.score(setting.title, q);
                        if (ss < bestSettingScore) {
                            bestSettingScore = ss;
                            bestSetting = setting.title;
                        }
                        ss = FuzzyMatcher.score(setting.name, q);
                        if (ss < bestSettingScore) {
                            bestSettingScore = ss;
                            bestSetting = setting.name;
                        }
                    }
                }
                if (bestSettingScore != Integer.MAX_VALUE) {
                    bestSettingScore += 250;
                    if (bestSettingScore < best) {
                        best = bestSettingScore;
                        matched = m.title + " <" + bestSetting + ">";
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
