package com.bettersearch.gui;

import com.bettersearch.modules.BetterSearchModule;
import com.bettersearch.search.ModuleSearch;
import com.bettersearch.search.UsageTracker;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.input.KeyInput;

import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Wurst-Navigator-style search GUI.
 * <ul>
 *   <li>Search bar on top, big scrollable list of every module</li>
 *   <li>Fuzzy matching, synonyms, category + setting search</li>
 *   <li>Most-used first when empty (learns preferences)</li>
 *   <li>Left-click toggle, right-click settings, keyboard: Up/Down + Enter + Right</li>
 * </ul>
 * Credits: Turbo.
 */
public class BetterSearchScreen extends WindowScreen {
    private WTextBox searchBox;
    private WVerticalList results;
    private WVerticalList hint;

    private List<ModuleSearch.Result> current = List.of();
    private int selected = 0;

    public BetterSearchScreen(GuiTheme theme) {
        super(theme, "Better Search — by Turbo");
    }

    @Override
    public void initWidgets() {
        hint = add(theme.verticalList()).expandX().widget();
        hint.add(theme.label("Type to filter • Up/Down navigate • Enter toggle • Right open settings • Esc close")).widget();
        hint.add(theme.label("Left-click toggles, right-click opens settings (Wurst-style).")).widget();

        searchBox = add(theme.textBox("", "Search modules... (try 'kill', 'fly', 'esp')")).expandX().widget();
        searchBox.setFocused(true);
        searchBox.action = this::refreshResults;

        results = add(theme.verticalList()).expandX().widget();
        refreshResults();
    }

    private BetterSearchModule config() {
        BetterSearchModule m = Modules.get().get(BetterSearchModule.class);
        return m;
    }

    private void refreshResults() {
        if (results == null || searchBox == null) return;

        BetterSearchModule cfg = config();
        int max = cfg != null ? cfg.maxResults.get() : 30;
        boolean desc = cfg == null || cfg.searchDescriptions.get();
        boolean sett = cfg == null || cfg.searchSettings.get();
        boolean tags = cfg == null || cfg.searchTags.get();
        boolean learn = cfg == null || cfg.learnUsage.get();

        String query = searchBox.get();
        // When learning is off, still search but ignore usage for ranking
        List<ModuleSearch.Result> list = ModuleSearch.search(query, desc, sett, tags, max);
        if (!learn) {
            // re-sort without usage boost: score then name
            list = list.stream()
                .sorted((a, b) -> {
                    int c = Integer.compare(a.score(), b.score());
                    if (c != 0) return c;
                    return a.module().title.compareToIgnoreCase(b.module().title);
                })
                .toList();
        }
        current = list;
        if (selected >= current.size()) selected = Math.max(0, current.size() - 1);
        if (current.isEmpty()) selected = 0;

        results.clear();

        if (current.isEmpty()) {
            results.add(theme.label("No modules found for \"" + query + "\"")).expandX().widget();
            return;
        }

        for (int i = 0; i < current.size(); i++) {
            ModuleSearch.Result r = current.get(i);
            boolean isSelected = i == selected;

            WHorizontalList row = theme.horizontalList();
            row.spacing = 2;

            // Selection arrow (keyboard navigation, Wurst-style)
            var arrow = row.add(theme.label(isSelected ? ">" : " ")).widget();
            try {
                if (isSelected) arrow.color(theme.textColor());
                else arrow.color(theme.textSecondaryColor());
            } catch (Exception ignored) {}

            WWidget modWidget = theme.module(r.module());
            int uses = UsageTracker.getCount(r.module());
            String state = r.module().isActive() ? "ON" : "OFF";
            modWidget.tooltip = r.matchedText()
                + "  [" + r.module().category.name + "]  (" + state + ")"
                + "\n" + r.module().description
                + "\nUses: " + uses + " — left toggle, right settings";
            row.add(modWidget).expandX();

            // Category + usage, like Wurst showing category next to type
            String side = r.module().category.name + (uses > 0 ? " • " + uses : "");
            var cat = row.add(theme.label(side)).right().widget();
            try {
                cat.color(theme.textSecondaryColor());
            } catch (Exception ignored) {}

            results.add(row).expandX();
        }
    }

    private void moveSelection(int delta) {
        if (current.isEmpty()) return;
        selected = Math.floorMod(selected + delta, current.size());
        refreshResults();
    }

    private void toggleSelected() {
        if (current.isEmpty()) return;
        if (selected < 0 || selected >= current.size()) return;
        ModuleSearch.Result r = current.get(selected);
        r.module().toggle();
        UsageTracker.record(r.module());
        UsageTracker.save();
        refreshResults();
    }

    private void openSelectedSettings() {
        if (current.isEmpty()) return;
        if (selected < 0 || selected >= current.size()) return;
        ModuleSearch.Result r = current.get(selected);
        UsageTracker.record(r.module());
        UsageTracker.save();
        MeteorClient.mc.setScreen(theme.moduleScreen(r.module()));
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();

        if (key == GLFW_KEY_DOWN) {
            moveSelection(1);
            return true;
        }
        if (key == GLFW_KEY_UP) {
            moveSelection(-1);
            return true;
        }
        if (key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER) {
            // If search box is focused and there are results, Enter toggles (Wurst-style).
            // Otherwise let the textbox/screen handle it.
            if (!current.isEmpty()) {
                toggleSelected();
                return true;
            }
        }
        if (key == GLFW_KEY_RIGHT || key == GLFW_KEY_TAB) {
            if (!current.isEmpty()) {
                openSelectedSettings();
                return true;
            }
        }

        boolean handled = super.keyPressed(input);
        // Typing changed the query -> reset selection to top (Wurst shows best match first)
        if (!handled || searchBox.isFocused()) {
            // WTextBox.action already refreshes on change, but ensure selection reset on new input
            // (action fires, refresh keeps selected index; reset to 0 when query changed via typing)
            // We detect printable keys crudely: if key is not a navigation key, reset.
            if (key != GLFW_KEY_UP && key != GLFW_KEY_DOWN && key != GLFW_KEY_ENTER
                && key != GLFW_KEY_KP_ENTER && key != GLFW_KEY_RIGHT && key != GLFW_KEY_TAB
                && key != GLFW_KEY_ESCAPE) {
                if (selected != 0) {
                    selected = 0;
                    refreshResults();
                }
            }
        }
        return handled;
    }
}
