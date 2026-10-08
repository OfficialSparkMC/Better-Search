package com.bettersearch.search;

import java.util.Locale;

/**
 * Wurst-inspired fuzzy matching for module search.
 * Priority (lower score = better):
 * <ol>
 *   <li>exact match (0)</li>
 *   <li>prefix match</li>
 *   <li>word-prefix / all-words-contained</li>
 *   <li>substring</li>
 *   <li>subsequence (typed chars in order)</li>
 *   <li>typo-tolerant levenshtein (thresholded)</li>
 * </ol>
 * Returns {@link Integer#MAX_VALUE} when there is no match.
 *
 * @author OfficialSparkMC
 */
public final class FuzzyMatcher {
    private FuzzyMatcher() {}

    public static int score(String text, String query) {
        if (query == null || query.isEmpty()) return 0;
        if (text == null || text.isEmpty()) return Integer.MAX_VALUE;

        String t = text.toLowerCase(Locale.ROOT);
        String q = query.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) return 0;

        // 0 - exact
        if (t.equals(q)) return 0;

        // 1 - prefix: "kill" -> "kill aura"
        if (t.startsWith(q)) return 1 + q.length();

        String[] tWords = t.split("[\\s\\-_]+");
        String[] qWords = q.split("\\s+");

        // 2 - all query words are prefixes of title words (order independent)
        // e.g. "aura kill" -> "kill aura"
        if (allWordsPrefix(tWords, qWords)) {
            return 50 + q.length();
        }

        // 3 - all query words contained somewhere
        if (allWordsContained(t, qWords)) {
            return 100 + q.length();
        }

        // 4 - plain substring: "aura" in "kill aura"
        int idx = t.indexOf(q);
        if (idx >= 0) {
            return 200 + idx;
        }

        // 5 - subsequence: "kla" -> "kill aura" (k...l...a)
        int sub = subsequenceScore(t, q);
        if (sub != Integer.MAX_VALUE) {
            return 500 + sub;
        }

        // 6 - levenshtein typo tolerance, thresholded by length (like Meteor's text.length()/2 rule)
        // Perf: skip expensive matrix when length difference alone exceeds the threshold
        int threshold = Math.max(1, q.length() / 2);
        if (Math.abs(t.length() - q.length()) > threshold + 2) {
            return Integer.MAX_VALUE;
        }
        int lev = levenshtein(q, t);
        // Compare against the best word too, so "kil" still finds "kill"
        int bestWord = Integer.MAX_VALUE;
        for (String w : tWords) {
            if (Math.abs(w.length() - q.length()) > threshold + 2) continue;
            int d = levenshtein(q, w);
            if (d < bestWord) bestWord = d;
        }
        int best = Math.min(lev, bestWord);
        if (best <= threshold) {
            return 1000 + best * 10;
        }

        return Integer.MAX_VALUE;
    }

    public static boolean matches(String text, String query) {
        return score(text, query) != Integer.MAX_VALUE;
    }

    private static boolean allWordsPrefix(String[] tWords, String[] qWords) {
        outer:
        for (String qw : qWords) {
            if (qw.isEmpty()) continue;
            for (String tw : tWords) {
                if (tw.startsWith(qw)) continue outer;
            }
            return false;
        }
        return true;
    }

    private static boolean allWordsContained(String t, String[] qWords) {
        for (String qw : qWords) {
            if (qw.isEmpty()) continue;
            if (!t.contains(qw)) return false;
        }
        return true;
    }

    private static int subsequenceScore(String t, String q) {
        int ti = 0;
        int gaps = 0;
        int firstIndex = -1;
        for (int qi = 0; qi < q.length(); qi++) {
            char qc = q.charAt(qi);
            boolean found = false;
            while (ti < t.length()) {
                char tc = t.charAt(ti++);
                if (tc == qc) {
                    if (firstIndex < 0) firstIndex = ti;
                    found = true;
                    break;
                } else {
                    // only count gaps after first match started
                    if (firstIndex >= 0 || found) gaps++;
                }
            }
            if (!found) return Integer.MAX_VALUE;
        }
        return gaps + firstIndex;
    }

    public static int levenshtein(String a, String b) {
        int n = a.length();
        int m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;

        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;

        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[m];
    }
}
