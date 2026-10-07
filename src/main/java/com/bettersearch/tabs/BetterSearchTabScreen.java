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
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.input.KeyInput;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Modern, clean, fixed (non-draggable) Navigator tab, Wurst-style.
 * Rounded module cards rendered with Meteor's shared GuiRenderer.
 * Search bar fixed on top, scrollable result list below.
 * Empty query shows FULL module list grouped by category; typing filters
 * to a flat ranked list. Left-click toggles, right-click opens settings
 * inline (locked) or classic draggable window (toggleable).
 * Keyboard: Up/Down + Enter + Right. Opens with Right-Ctrl.
 *
 * <p>Credits: Turbo</p>
 */
public class BetterSearchTabScreen extends TabScreen {
    private WTextBox searchBox;
    private WLabel statusLabel;
    private WDragPanel panel;
    private WView scroll;
    private WVerticalList list;

    /** Flat list in display order for keyboard nav (grouped mode is flattened). */
    private List<ModuleSearch.Result> current = List.of();
    private int selected = 0;

    /** Non-null while inline (locked) settings are open. */
    private Module inlineModule = null;

    /** Session-persistent drag offset for the panel. */
    private static double savedDragX = 0;
    private static double savedDragY = 0;

    public BetterSearchTabScreen(GuiTheme theme, Tab tab) {
        super(theme, tab);
    }

    /** Fixed panel that can optionally be dragged (no WWindow, no movable-window style). */
    private static class WDragPanel extends WVerticalList {
        double dragX;
        double dragY;

        @Override
        protected void onCalculateWidgetPositions() {
            super.onCalculateWidgetPositions();
            if (dragX != 0 || dragY != 0) {
                for (meteordevelopment.meteorclient.gui.utils.Cell<?> cell : cells) {
                    cell.move(dragX, dragY);
                }
            }
        }
    }

    /** Thin drag handle shown only when draggable-panel is on. */
    private class WDragHandle extends WWidget {
        private boolean dragging;

        @Override
        protected void onCalculateSize() {
            width = theme.scale(480);
            double minWidth = theme.scale(this.minWidth);
            if (width < minWidth) width = minWidth;
            height = theme.textHeight() + theme.scale(6);
        }

        @Override
        protected void onRender(meteordevelopment.meteorclient.gui.renderer.GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            String s = "⠿ Better Search — drag me • by Turbo";
            double tw = theme.textWidth(s);
            renderer.text(s, x + width / 2 - tw / 2, y + theme.scale(3), theme.textSecondaryColor(), false);
        }

        @Override
        public boolean onMouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
            if (mouseOver && click.button() == GLFW_MOUSE_BUTTON_LEFT) {
                dragging = true;
                return true;
            }
            return false;
        }

        @Override
        public boolean onMouseReleased(net.minecraft.client.gui.Click click) {
            dragging = false;
            return false;
        }

        @Override
        public void onMouseMoved(double mouseX, double mouseY, double lastMouseX, double lastMouseY) {
            if (!dragging || panel == null) return;
            double dx = mouseX - lastMouseX;
            double dy = mouseY - lastMouseY;
            savedDragX = clamp(savedDragX + dx, -420, 420);
            savedDragY = clamp(savedDragY + dy, -40, 420);
            panel.dragX = savedDragX;
            panel.dragY = savedDragY;
            panel.invalidate();
        }

        private double clamp(double v, double min, double max) {
            return Math.max(min, Math.min(max, v));
        }
    }

    @Override
    public void initWidgets() {
        // Fixed modern panel: centered, customizable width, optionally draggable (no WWindow).
        panel = new WDragPanel();
        panel.spacing = 6;
        add(panel).centerX().marginTop(46).widget();
        panel.minWidth = 500;
        panel.dragX = savedDragX;
        panel.dragY = savedDragY;

        BetterSearchModule cfg = config();
        if (cfg != null && cfg.draggablePanel.get()) {
            panel.add(new WDragHandle()).expandX().widget();
        }

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

        // Cursor first: focus search immediately so typing filters instantly
        searchBox.setFocused(true);
        try {
            searchBox.setCursorMax();
        } catch (Exception ignored) {}
    }

    private BetterSearchModule config() {
        return Modules.get().get(BetterSearchModule.class);
    }

    private int appearancePanelWidth(BetterSearchModule cfg) {
        return cfg != null ? cfg.panelWidth.get() : 500;
    }

    private void refreshResults() {
        if (list == null || searchBox == null) return;

        if (inlineModule != null) {
            showInline();
            return;
        }

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

        // Cursor first: keep the text caret in the search box after every rebuild
        // (mouse clicks on cards would otherwise leave focus nowhere)
        // Harmless when already focused — does not move the caret.
        searchBox.setFocused(true);
    }

    /** Empty query: FULL module list grouped by category (Wurst shows everything with category). */
    private void refreshGrouped(boolean learn) {
        BetterSearchModule cfg = config();
        int columns = cfg != null ? Math.max(1, Math.min(3, cfg.columns.get())) : 1;

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

            List<ModuleSearch.Result> results = new ArrayList<>();
            for (Module m : group) {
                results.add(new ModuleSearch.Result(m, m.title, 0, UsageTracker.getCount(m)));
            }

            if (columns <= 1) {
                for (ModuleSearch.Result r : results) {
                    flat.add(r);
                    addRow(r, flat.size() - 1, false);
                }
            } else {
                int base = flat.size();
                flat.addAll(results);
                addGrid(results, base);
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
        BetterSearchModule cfg = config();
        int columns = cfg != null ? Math.max(1, Math.min(3, cfg.columns.get())) : 1;

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

        if (columns <= 1) {
            for (int i = 0; i < found.size(); i++) {
                addRow(found.get(i), i, true);
            }
        } else {
            addGrid(found, 0);
        }
    }

    private ModuleCard makeCard(ModuleSearch.Result r, int flatIndex, String meta) {
        BetterSearchModule cfg = config();
        ModuleCard card = new ModuleCard(r);
        card.selected = flatIndex == selected;
        card.rounded = cfg == null || cfg.roundedCards.get();
        card.radius = cfg != null ? cfg.cornerRadius.get() : 6;
        card.padExtra = cfg != null ? cfg.cardPadding.get() : 2;
        card.showDot = cfg == null || cfg.showDot.get();
        card.meta = meta;
        int uses = UsageTracker.getCount(r.module());
        boolean active = r.module().isActive();
        String state = active ? "ON" : "OFF";
        card.tooltip = r.matchedText()
            + "  [" + r.module().category.name + "]  (" + state + ")"
            + "\n" + r.module().description
            + (uses > 0 ? "\nUsed " + uses + "x" : "")
            + "\nLeft-click toggle • Right-click settings";
        card.onToggle = () -> {
            r.module().toggle();
            UsageTracker.record(r.module());
            UsageTracker.save();
            refreshResults();
        };
        card.onSettings = () -> openModuleSettings(r.module());
        return card;
    }

    private String rowMeta(ModuleSearch.Result r, boolean showCategory) {
        BetterSearchModule cfg = config();
        boolean showCatSetting = cfg == null || cfg.showCategory.get();
        int uses = UsageTracker.getCount(r.module());
        String state = r.module().isActive() ? "ON" : "OFF";
        boolean showCatHere = showCategory && showCatSetting;
        return showCatHere
            ? r.module().category.name + " • " + state + (uses > 0 ? " • " + uses : "")
            : state + (uses > 0 ? " • " + uses : "");
    }

    private void addRow(ModuleSearch.Result r, int index, boolean showCategory) {
        list.add(makeCard(r, index, rowMeta(r, showCategory))).expandX().widget();
    }

    /** Multiple modules per line: grid rows with N compact cards each. */
    private void addGrid(List<ModuleSearch.Result> results, int baseIndex) {
        BetterSearchModule cfg = config();
        int columns = Math.max(2, Math.min(3, cfg != null ? cfg.columns.get() : 2));
        int innerGap = cfg != null ? cfg.rowInnerGap.get() : 4;

        for (int i = 0; i < results.size(); i += columns) {
            WHorizontalList row = theme.horizontalList();
            row.spacing = innerGap;

            for (int j = 0; j < columns && i + j < results.size(); j++) {
                ModuleSearch.Result r = results.get(i + j);
                // Compact grid cards: no meta text (tooltip carries details)
                row.add(makeCard(r, baseIndex + i + j, null)).expandX().widget();
            }

            list.add(row).expandX().widget();
        }
    }

    // Inline (locked, non-draggable) settings

    private void openModuleSettings(Module m) {
        BetterSearchModule cfg = config();
        boolean inline = cfg == null || cfg.inlineSettings.get();
        if (!inline) {
            UsageTracker.record(m);
            UsageTracker.save();
            meteordevelopment.meteorclient.MeteorClient.mc.setScreen(theme.moduleScreen(m));
            return;
        }
        UsageTracker.record(m);
        UsageTracker.save();
        openInline(m);
    }

    private void openInline(Module m) {
        inlineModule = m;
        searchBox.visible = false;
        if (statusLabel != null) statusLabel.visible = false;
        showInline();
    }

    private void closeInline() {
        inlineModule = null;
        searchBox.visible = true;
        BetterSearchModule cfg = config();
        if (statusLabel != null) statusLabel.visible = cfg == null || cfg.showStatus.get();
        refreshResults();
        searchBox.setFocused(true);
        try {
            searchBox.setCursorMax();
        } catch (Exception ignored) {}
    }

    private void showInline() {
        Module m = inlineModule;
        if (m == null) return;
        list.clear();

        WHorizontalList top = theme.horizontalList();
        top.spacing = 4;
        WButton back = theme.button("Back");
        back.action = this::closeInline;
        top.add(back).widget();
        top.add(theme.label(m.title, true)).expandX().widget();
        WCheckbox activeBox = theme.checkbox(m.isActive());
        activeBox.action = () -> {
            if (m.isActive() != activeBox.checked) {
                m.toggle();
                UsageTracker.record(m);
                UsageTracker.save();
            }
        };
        top.add(activeBox).right().widget();
        list.add(top).expandX().widget();

        WLabel desc = list.add(theme.label(m.description)).expandX().widget();
        try {
            desc.color(theme.textSecondaryColor());
        } catch (Exception ignored) {}

        // Outlined context card for the right-clicked module (toggleable via inline-outline)
        BetterSearchModule cfg = config();
        if (cfg == null || cfg.inlineOutline.get()) {
            ModuleSearch.Result r = new ModuleSearch.Result(m, m.title, 0, UsageTracker.getCount(m));
            ModuleCard context = makeCard(r, -1, rowMeta(r, true));
            context.selected = false;
            context.outline = meteordevelopment.meteorclient.utils.render.color.Color.YELLOW;
            context.onToggle = () -> {
                m.toggle();
                UsageTracker.record(m);
                UsageTracker.save();
                showInline();
            };
            context.onSettings = () -> {};
            list.add(context).expandX().widget();
        }

        list.add(theme.label("Locked in Better Search (non-draggable) • by Turbo")).expandX().widget();
        list.add(theme.settings(m.settings)).expandX().widget();
    }

    @Override
    public void tick() {
        super.tick();
        if (inlineModule != null && list != null) {
            try {
                inlineModule.settings.tick(list, theme);
            } catch (Exception ignored) {}
        }
    }

    private void moveSelection(int delta) {
        if (inlineModule != null || current.isEmpty()) return;
        selected = Math.floorMod(selected + delta, current.size());
        refreshResults();
    }

    private void toggleSelected() {
        if (inlineModule != null || current.isEmpty() || selected < 0 || selected >= current.size()) return;
        ModuleSearch.Result r = current.get(selected);
        r.module().toggle();
        UsageTracker.record(r.module());
        UsageTracker.save();
        refreshResults();
    }

    private void openSelectedSettings() {
        if (inlineModule != null || current.isEmpty() || selected < 0 || selected >= current.size()) return;
        openModuleSettings(current.get(selected).module());
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();

        if (inlineModule != null) {
            if (key == GLFW_KEY_LEFT || key == GLFW_KEY_BACKSPACE) {
                closeInline();
                return true;
            }
            return super.keyPressed(input);
        }

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
