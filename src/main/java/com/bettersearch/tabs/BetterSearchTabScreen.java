package com.bettersearch.tabs;

import com.bettersearch.modules.BetterSearchModule;
import com.bettersearch.search.ModuleSearch;
import com.bettersearch.search.UsageTracker;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WView;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.input.KeyInput;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Modern, clean, fixed (non-draggable) Navigator tab, Wurst-style.
 * Uses Meteor's shared GUI renderer (theme widgets only, no custom GL).
 * Search bar fixed on top, scrollable result list below.
 * Empty query shows FULL module list grouped by category (like Wurst shows
 * every feature with its category); typing filters to a flat ranked list.
 * Left-click toggles, right-click opens settings.
 * Keyboard: Up/Down + Enter + Right. Opens with Right-Ctrl.
 *
 * <p>Credits: Turbo</p>
 */
public class BetterSearchTabScreen extends TabScreen {
    private WTextBox searchBox;
    private WLabel statusLabel;
    private WVerticalList list;
    private WVerticalList panel;
    private WView scroll;

    /** Flat list in display order for keyboard nav (grouped mode is flattened). */
    private List<ModuleSearch.Result> current = List.of();
    private int selected = 0;

    public BetterSearchTabScreen(GuiTheme theme, Tab tab) {
        super(theme, tab);
    }

    @Override
    public void initWidgets() {
        // Fixed modern panel: centered, customizable width, NOT draggable (no WWindow).
        panel = theme.verticalList();
        panel.spacing = 6;
        add(panel).centerX().marginTop(46).widget();
        panel.minWidth = 500;

        searchBox = panel.add(theme.textBox("", "Search modules... (Right-Ctrl to open)")).expandX().widget();
        searchBox.setFocused(true);
        searchBox.minWidth = 480;
        searchBox.action = () -> {
            selected = 0;
            refreshResults();
        };

        statusLabel = panel.add(theme.label("")).expandX().widget();
        try {
            statusLabel.color(theme.textSecondaryColor());
        } catch (Exception ignored) {}

        // Scrollable results (shared renderer, scrollbar visible). Search box stays above it.
        scroll = theme.view();
        scroll.scrollOnlyWhenMouseOver = false;
        scroll.hasScrollBar = true;
        panel.add(scroll).expandX().widget();

        list = theme.verticalList();
        list.spacing = 2;
        scroll.add(list).expandX().widget();

        refreshResults();
    }

    private BetterSearchModule config() {
        return Modules.get().get(BetterSearchModule.class);
    }

    private int appearancePanelWidth(BetterSearchModule cfg) {
        return cfg != null ? cfg.panelWidth.get() : 500;
    }

    private void refreshResults() {
        if (list == null || searchBox == null) return;

        BetterSearchModule cfg = config();
        int max = cfg != null ? cfg.maxResults.get() : 100;
        boolean desc = cfg == null || cfg.searchDescriptions.get();
        boolean sett = cfg == null || cfg.searchSettings.get();
        boolean tags = cfg == null || cfg.searchTags.get();
        boolean learn = cfg == null || cfg.learnUsage.get();

        // Customizable module/row sizes
        int panelWidth = appearancePanelWidth(cfg);
        int rowGap = cfg != null ? cfg.rowGap.get() : 2;
        boolean showStatus = cfg == null || cfg.showStatus.get();
        if (panel != null) panel.minWidth = panelWidth;
        searchBox.minWidth = Math.max(200, panelWidth - 20);
        list.spacing = rowGap;
        if (statusLabel != null) statusLabel.visible = showStatus;

        String query = searchBox.get().trim();
        list.clear();

        if (query.isEmpty()) {
            refreshGrouped(learn);
        } else {
            refreshFiltered(query, max, desc, sett, tags, learn);
        }

        if (selected >= current.size()) selected = Math.max(0, current.size() - 1);
        if (current.isEmpty()) selected = 0;
    }

    /** Empty query: FULL module list grouped by category (Wurst shows everything with category). */
    private void refreshGrouped(boolean learn) {
        List<ModuleSearch.Result> flat = new ArrayList<>();

        for (Category category : Modules.loopCategories()) {
            List<Module> group = new ArrayList<>();
            for (Module m : Modules.get().getGroup(category)) {
                if (Config.get().hiddenModules.get().contains(m)) continue;
                group.add(m);
            }
            if (group.isEmpty()) continue;

            group.sort(Comparator
                .comparingInt((Module m) -> learn ? -UsageTracker.getCount(m) : 0)
                .thenComparing(m -> m.title, String.CASE_INSENSITIVE_ORDER));

            // Modern category header
            WLabel header = list.add(theme.label(category.name, true)).expandX().widget();
            try {
                header.color(theme.textSecondaryColor());
            } catch (Exception ignored) {}

            for (Module m : group) {
                ModuleSearch.Result r = new ModuleSearch.Result(m, m.title, 0, UsageTracker.getCount(m));
                flat.add(r);
                addRow(r, flat.size() - 1, false);
            }
        }

        current = flat;
        if (statusLabel != null) {
            statusLabel.set(flat.size() + " modules • grouped by category • by Turbo");
        }

        WLabel footer = list.add(theme.label("by Turbo • Right-Ctrl to reopen • left toggle • right settings")).expandX().widget();
        try {
            footer.color(theme.textSecondaryColor());
        } catch (Exception ignored) {}
    }

    /** Non-empty query: flat fuzzy-ranked list with category shown per row (Wurst-style). */
    private void refreshFiltered(String query, int max, boolean desc, boolean sett, boolean tags, boolean learn) {
        List<ModuleSearch.Result> found = ModuleSearch.search(query, desc, sett, tags, max);
        if (!learn) {
            found = found.stream()
                .sorted((a, b) -> {
                    int c = Integer.compare(a.score(), b.score());
                    if (c != 0) return c;
                    return a.module().title.compareToIgnoreCase(b.module().title);
                })
                .toList();
        }
        current = found;

        if (statusLabel != null) {
            if (found.isEmpty()) {
                statusLabel.set("No results for \"" + query + "\"");
            } else {
                ModuleSearch.Result best = found.get(Math.min(selected, found.size() - 1));
                statusLabel.set(found.size() + " results • selected: " + best.module().title + " (Enter toggle, Right settings)");
            }
        }

        if (found.isEmpty()) return;

        for (int i = 0; i < found.size(); i++) {
            addRow(found.get(i), i, true);
        }
    }

    private void addRow(ModuleSearch.Result r, int index, boolean showCategory) {
        BetterSearchModule cfg = config();
        boolean showDot = cfg == null || cfg.showDot.get();
        boolean showCatSetting = cfg == null || cfg.showCategory.get();
        int innerGap = cfg != null ? cfg.rowInnerGap.get() : 4;

        boolean isSelected = index == selected;
        boolean active = r.module().isActive();
        int uses = UsageTracker.getCount(r.module());

        WHorizontalList row = theme.horizontalList();
        row.spacing = innerGap;

        // Customizable dot (row size/density via gaps, dot toggle)
        if (showDot) {
            var dot = row.add(theme.label(active ? "●" : "○")).widget();
            try {
                dot.color(active ? Color.GREEN : Color.GRAY);
            } catch (Exception ignored) {}
        }

        WWidget modWidget = theme.module(r.module());
        String state = active ? "ON" : "OFF";
        modWidget.tooltip = r.matchedText()
            + "  [" + r.module().category.name + "]  (" + state + ")"
            + "\n" + r.module().description
            + (uses > 0 ? "\nUsed " + uses + "x" : "")
            + "\nLeft-click toggle • Right-click settings";
        row.add(modWidget).expandX();

        boolean showCatHere = showCategory && showCatSetting;
        String meta = showCatHere
            ? r.module().category.name + " • " + state + (uses > 0 ? " • " + uses : "")
            : state + (uses > 0 ? " • " + uses : "");
        var metaLabel = row.add(theme.label(meta)).right().widget();
        try {
            metaLabel.color(isSelected ? theme.textColor() : theme.textSecondaryColor());
        } catch (Exception ignored) {}

        list.add(row).expandX().widget();
    }

    private void moveSelection(int delta) {
        if (current.isEmpty()) return;
        selected = Math.floorMod(selected + delta, current.size());
        refreshResults();
    }

    private void toggleSelected() {
        if (current.isEmpty() || selected < 0 || selected >= current.size()) return;
        ModuleSearch.Result r = current.get(selected);
        r.module().toggle();
        UsageTracker.record(r.module());
        UsageTracker.save();
        refreshResults();
    }

    private void openSelectedSettings() {
        if (current.isEmpty() || selected < 0 || selected >= current.size()) return;
        ModuleSearch.Result r = current.get(selected);
        UsageTracker.record(r.module());
        UsageTracker.save();
        meteordevelopment.meteorclient.MeteorClient.mc.setScreen(theme.moduleScreen(r.module()));
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
            if (!current.isEmpty()) {
                toggleSelected();
                return true;
            }
        }
        if (key == GLFW_KEY_RIGHT) {
            if (!current.isEmpty()) {
                openSelectedSettings();
                return true;
            }
        }

        return super.keyPressed(input);
    }
}
