package com.bettersearch.tabs;

import com.bettersearch.modules.BetterSearchModule;
import com.bettersearch.search.ModuleSearch;
import com.bettersearch.search.UsageTracker;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.WindowTabScreen;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.NbtUtils;
import net.minecraft.client.input.KeyInput;

import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Clean, scrollable Navigator tab (Wurst-style).
 * Search bar on top, scrollable result list below.
 * Left-click toggles, right-click opens settings.
 * Keyboard: Up/Down + Enter + Right.
 *
 * <p>Credits: Turbo</p>
 */
public class BetterSearchTabScreen extends WindowTabScreen {
    private WTextBox searchBox;
    private WVerticalList list;
    private meteordevelopment.meteorclient.gui.widgets.WLabel statusLabel;

    private List<ModuleSearch.Result> current = List.of();
    private int selected = 0;

    public BetterSearchTabScreen(GuiTheme theme, Tab tab) {
        super(theme, tab);
    }

    @Override
    public void initWidgets() {
        // Search bar — always visible at top of the scrollable window
        searchBox = add(theme.textBox("", "Search modules...")).expandX().widget();
        searchBox.setFocused(true);
        searchBox.minWidth = 260;
        searchBox.action = () -> {
            selected = 0;
            refreshResults();
        };

        statusLabel = add(theme.label("")).expandX().widget();
        try {
            statusLabel.color(theme.textSecondaryColor());
        } catch (Exception ignored) {}

        list = theme.verticalList();
        add(list).expandX().widget();

        refreshResults();
    }

    private BetterSearchModule config() {
        return Modules.get().get(BetterSearchModule.class);
    }

    private void refreshResults() {
        if (list == null || searchBox == null) return;

        BetterSearchModule cfg = config();
        int max = cfg != null ? cfg.maxResults.get() : 30;
        boolean desc = cfg == null || cfg.searchDescriptions.get();
        boolean sett = cfg == null || cfg.searchSettings.get();
        boolean tags = cfg == null || cfg.searchTags.get();
        boolean learn = cfg == null || cfg.learnUsage.get();

        String query = searchBox.get();
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
        if (selected >= current.size()) selected = Math.max(0, current.size() - 1);
        if (current.isEmpty()) selected = 0;

        // Status line — clean, single line
        if (statusLabel != null) {
            if (current.isEmpty()) {
                statusLabel.set(query.isEmpty() ? "No modules" : "No results for \"" + query + "\"");
            } else if (query.isEmpty()) {
                statusLabel.set(current.size() + " modules • most used first • by Turbo");
            } else {
                ModuleSearch.Result best = current.get(Math.min(selected, current.size() - 1));
                statusLabel.set(current.size() + " results • selected: " + best.module().title + " (Enter toggle, Right settings)");
            }
        }

        list.clear();

        for (int i = 0; i < current.size(); i++) {
            ModuleSearch.Result r = current.get(i);
            boolean isSelected = i == selected;

            WHorizontalList row = theme.horizontalList();
            row.spacing = 4;

            WWidget modWidget = theme.module(r.module());
            int uses = UsageTracker.getCount(r.module());
            String state = r.module().isActive() ? "ON" : "OFF";
            modWidget.tooltip = r.matchedText()
                + "  [" + r.module().category.name + "]  (" + state + ")"
                + "\n" + r.module().description
                + (uses > 0 ? "\nUsed " + uses + "x" : "")
                + "\nLeft-click toggle • Right-click settings";
            row.add(modWidget).expandX();

            // Clean right-side meta: category only (bright when keyboard-selected)
            var cat = row.add(theme.label(r.module().category.name)).right().widget();
            try {
                cat.color(isSelected ? theme.textColor() : theme.textSecondaryColor());
            } catch (Exception ignored) {}

            list.add(row).expandX().widget();
        }

        // Footer credit — clean, secondary color
        var footer = list.add(theme.label("by Turbo • left toggle • right settings")).expandX().widget();
        try {
            footer.color(theme.textSecondaryColor());
        } catch (Exception ignored) {}
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

    @Override
    public boolean toClipboard() {
        return NbtUtils.toClipboard(Modules.get());
    }

    @Override
    public boolean fromClipboard() {
        return NbtUtils.fromClipboard(Modules.get());
    }

    @Override
    public void reload() {
    }
}
