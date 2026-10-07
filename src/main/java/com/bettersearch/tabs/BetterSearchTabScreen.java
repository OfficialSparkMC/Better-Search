package com.bettersearch.tabs;

import com.bettersearch.BetterSearchAddon;
import com.bettersearch.modules.BetterSearchModule;
import com.bettersearch.search.ModuleSearch;
import com.bettersearch.search.UsageTracker;
import meteordevelopment.meteorclient.events.meteor.ActiveModulesChangedEvent;
import meteordevelopment.meteorclient.events.meteor.ModuleBindChangedEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.WKeybind;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WView;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
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

    /** Live card widgets by module for in-place updates (no rebuild on toggle). */
    private final java.util.Map<Module, ModuleCard> cardMap = new java.util.HashMap<>();

    /** Last query that was fully sorted; same-query rebuilds keep order stable. */
    private String lastQuery = null;

    /** Non-null while inline (locked) settings are open. */
    private Module inlineModule = null;

    /** Dedicated holder for inline settings so Settings.tick() can only rebuild settings, never our view. */
    private WVerticalList inlineSettingsHolder = null;

    /** Inline bind editor + active box, kept live via Meteor's change events (like its own screen). */
    private WKeybind inlineKeybind = null;
    private WCheckbox inlineActiveBox = null;

    /** Last right-clicked module — stays outlined in the list. */
    private Module outlinedModule = null;

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

    /** Thin drag handle shown only when draggable-panel is on (visual only, drag handled by screen). */
    private WDragHandle dragHandle = null;
    private boolean draggingPanel = false;
    private double lastMoveX, lastMoveY;
    private boolean hasLastMove = false;

    private static class WDragHandle extends WWidget {
        @Override
        protected void onCalculateSize() {
            width = theme.scale(480);
            double minWidth = theme.scale(this.minWidth);
            if (width < minWidth) width = minWidth;
            height = theme.textHeight() + theme.scale(10);
        }

        @Override
        protected void onRender(meteordevelopment.meteorclient.gui.renderer.GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            String s = mouseOver ? "⠿ Better Search — dragging" : "⠿ Better Search — drag me";
            double tw = theme.textWidth(s);
            renderer.text(s, x + width / 2 - tw / 2, y + theme.scale(5),
                mouseOver ? theme.textColor() : theme.textSecondaryColor(), false);
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
            mouseOver = isOver(click.x(), click.y());
            return super.mouseClicked(click, doubled);
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
            dragHandle = new WDragHandle();
            panel.add(dragHandle).expandX().widget();
        } else {
            dragHandle = null;
        }

        searchBox = panel.add(theme.textBox("", "Search modules... (Right-Ctrl to open)")).expandX().widget();
        searchBox.setFocused(true);
        searchBox.minWidth = 480;
        searchBox.action = () -> {
            selected = 0;
            // Typing a new search while inline leaves the settings and filters the list
            if (inlineModule != null) closeInline();
            else refreshResults();
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

        BetterSearchModule cfg = config();

        // Customizable module/row sizes (also applied in inline mode so layout stays stable)
        int panelWidth = appearancePanelWidth(cfg);
        int rowGap = cfg != null ? cfg.rowGap.get() : 2;
        boolean showStatus = cfg == null || cfg.showStatus.get();
        if (panel != null) panel.minWidth = panelWidth;
        searchBox.minWidth = Math.max(200, panelWidth - 20);
        list.spacing = rowGap;
        if (statusLabel != null) statusLabel.visible = showStatus;

        if (inlineModule != null) {
            showInline();
            return;
        }

        int max = cfg != null ? cfg.maxResults.get() : 100;
        boolean desc = cfg == null || cfg.searchDescriptions.get();
        boolean sett = cfg == null || cfg.searchSettings.get();
        boolean tags = cfg == null || cfg.searchTags.get();
        boolean learn = cfg == null || cfg.learnUsage.get();

        String query = searchBox.get().trim();
        // Remember the selected module so rebuilds keep selection on it (not the index)
        Module keepSel = (selected >= 0 && selected < current.size()) ? current.get(selected).module() : null;
        boolean sameQuery = query.equals(lastQuery);
        java.util.Map<Module, Integer> prevOrder = null;
        if (sameQuery) {
            prevOrder = new java.util.HashMap<>();
            for (int i = 0; i < current.size(); i++) prevOrder.put(current.get(i).module(), i);
        }
        lastQuery = query;

        list.clear();
        cardMap.clear();

        if (query.isEmpty()) {
            refreshGrouped(learn, sameQuery ? prevOrder : null);
        } else {
            refreshFiltered(query, max, desc, sett, tags, learn, sameQuery ? prevOrder : null);
        }

        if (selected >= current.size()) selected = Math.max(0, current.size() - 1);
        if (current.isEmpty()) selected = 0;
        // Restore selection onto the same module when it is still shown
        if (sameQuery && keepSel != null) {
            for (int i = 0; i < current.size(); i++) {
                if (current.get(i).module() == keepSel) {
                    selected = i;
                    break;
                }
            }
        }

        // Cursor first: keep the text caret in the search box after every rebuild
        // (mouse clicks on cards would otherwise leave focus nowhere)
        // Harmless when already focused — does not move the caret.
        searchBox.setFocused(true);

        // Rebuilds create brand-new card widgets. Their bounds are only computed
        // on the next render, so refresh hover after that (same pattern Meteor
        // uses itself) — clicks then work without moving the mouse first.
        taskAfterRender = () -> refreshHover();
    }

    private void refreshHover() {
        try {
            var mc = meteordevelopment.meteorclient.MeteorClient.mc;
            double s = mc.getWindow().getScaleFactor();
            double mx = mc.mouse.getX() * s;
            double my = mc.mouse.getY() * s;
            if (list != null) list.mouseMoved(mx, my, mx, my);
        } catch (Exception ignored) {}
    }

    /** Empty query: FULL module list grouped by category (Wurst shows everything with category). */
    private void refreshGrouped(boolean learn, java.util.Map<Module, Integer> prevOrder) {
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

            if (prevOrder != null) {
                // Same query: keep previous order (toggles must not reshuffle the list)
                group.sort(Comparator
                    .comparingInt((Module m) -> prevOrder.getOrDefault(m, Integer.MAX_VALUE))
                    .thenComparing(m -> m.title, String.CASE_INSENSITIVE_ORDER));
            } else {
                group.sort(Comparator
                    .comparingInt((Module m) -> learn ? -UsageTracker.getCount(m) : 0)
                    .thenComparing(m -> m.title, String.CASE_INSENSITIVE_ORDER));
            }

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
            statusLabel.set(flat.size() + " modules • grouped by category");
        }

        WLabel footer = list.add(theme.label("Right-Ctrl to reopen • left toggle • right settings")).expandX().widget();
        try {
            footer.color(theme.textSecondaryColor());
        } catch (Exception ignored) {}
    }

    /** Non-empty query: flat fuzzy-ranked list with category shown per row (Wurst-style). */
    private void refreshFiltered(String query, int max, boolean desc, boolean sett, boolean tags, boolean learn, java.util.Map<Module, Integer> prevOrder) {
        BetterSearchModule cfg = config();
        int columns = cfg != null ? Math.max(1, Math.min(3, cfg.columns.get())) : 1;

        List<ModuleSearch.Result> found = ModuleSearch.search(query, desc, sett, tags, max);
        if (prevOrder != null) {
            // Same query: keep previous order (toggles must not reshuffle the list)
            found = found.stream()
                .sorted((a, b) -> {
                    int c = Integer.compare(a.score(), b.score());
                    if (c != 0) return c;
                    int po = Integer.compare(
                        prevOrder.getOrDefault(a.module(), Integer.MAX_VALUE),
                        prevOrder.getOrDefault(b.module(), Integer.MAX_VALUE));
                    if (po != 0) return po;
                    return a.module().title.compareToIgnoreCase(b.module().title);
                })
                .toList();
        } else if (!learn) {
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

    private ModuleCard makeCard(ModuleSearch.Result r, int flatIndex, boolean showCategory, boolean showMeta) {
        BetterSearchModule cfg = config();
        ModuleCard card = new ModuleCard(r);
        card.selected = flatIndex == selected;
        card.rounded = cfg == null || cfg.roundedCards.get();
        card.radius = cfg != null ? cfg.cornerRadius.get() : 6;
        card.padExtra = cfg != null ? cfg.cardPadding.get() : 2;
        card.showDot = cfg == null || cfg.showDot.get();
        card.showMeta = showMeta;
        card.showCategory = showCategory && (cfg == null || cfg.showCategory.get());
        // Outline the right-clicked module (toggleable via inline-outline)
        if ((cfg == null || cfg.inlineOutline.get()) && r.module() == outlinedModule) {
            card.outline = meteordevelopment.meteorclient.utils.render.color.Color.YELLOW;
        }
        card.tooltip = cardTooltip(r);
        card.onToggle = () -> {
            // In-place update: no list rebuild, so nothing moves, scroll and
            // selection stay exactly where they are.
            r.module().toggle();
            UsageTracker.record(r.module());
            UsageTracker.save();
            card.tooltip = cardTooltip(r);
            card.invalidate();
        };
        card.onSettings = () -> openModuleSettings(r.module());
        cardMap.put(r.module(), card);
        return card;
    }

    private String cardTooltip(ModuleSearch.Result r) {
        int uses = UsageTracker.getCount(r.module());
        String state = r.module().isActive() ? "ON" : "OFF";
        return r.matchedText()
            + "  [" + r.module().category.name + "]  (" + state + ")"
            + "\n" + r.module().description
            + (uses > 0 ? "\nUsed " + uses + "x" : "")
            + "\nLeft-click toggle • Right-click settings";
    }

    private void addRow(ModuleSearch.Result r, int index, boolean showCategory) {
        list.add(makeCard(r, index, showCategory, true)).expandX().widget();
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
                row.add(makeCard(r, baseIndex + i + j, false, false)).expandX().widget();
            }

            list.add(row).expandX().widget();
        }
    }

    // Inline (locked, non-draggable) settings

    /**
     * Classic draggable Meteor window + a yellow marker on top identifying the
     * module picked from Better Search. Used when inline-settings is OFF
     * (Meteor's own window has no outline API, so we mark it instead).
     */
    public static class OutlinedModuleScreen extends meteordevelopment.meteorclient.gui.screens.ModuleScreen {
        private final Module mod;

        public OutlinedModuleScreen(GuiTheme theme, Module module) {
            super(theme, module);
            this.mod = module;
        }

        @Override
        public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
            super.render(context, mouseX, mouseY, delta);
            // Yellow outline around Meteor's outer window.
            // Widget coords are raw pixels; DrawContext works in scaled units.
            try {
                double s = Math.max(1, meteordevelopment.meteorclient.MeteorClient.mc.getWindow().getScaleFactor());
                int pad = 3;
                int x = (int) Math.floor(window.x / s) - pad;
                int y = (int) Math.floor(window.y / s) - pad;
                int w = (int) Math.ceil(window.width / s) + pad * 2;
                int h = (int) Math.ceil(window.height / s) + pad * 2;
                int yellow = 0xFFFFFF00;
                int t = 2;
                // Outer 2px ring
                context.fill(x - 2, y - 2, x + w + 2, y, yellow);
                context.fill(x - 2, y + h, x + w + 2, y + h + 2, yellow);
                context.fill(x - 2, y, x, y + h, yellow);
                context.fill(x + w, y, x + w + 2, y + h, yellow);
                // Inner 2px ring
                context.fill(x, y, x + w, y + t, yellow);
                context.fill(x, y + h - t, x + w, y + h, yellow);
                context.fill(x, y, x + t, y + h, yellow);
                context.fill(x + w - t, y, x + w, y + h, yellow);
            } catch (Exception ignored) {}
        }

        @Override
        public void initWidgets() {
            super.initWidgets();
            // Prepend marker so it sits at the very top without disturbing Meteor's layout.
            // (Content cells live in window.view, not window itself.)
            WLabel marker = theme.label("◉ " + mod.title + " — from Better Search");
            try {
                marker.color(meteordevelopment.meteorclient.utils.render.color.Color.YELLOW);
            } catch (Exception ignored) {}
            java.util.List<meteordevelopment.meteorclient.gui.utils.Cell<?>> cells =
                new java.util.ArrayList<>(window.view.cells);
            window.clear();
            add(marker).expandX().widget();
            for (meteordevelopment.meteorclient.gui.utils.Cell<?> cell : cells) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                meteordevelopment.meteorclient.gui.utils.Cell raw = cell;
                window.view.cells.add(raw);
            }
            window.invalidate();
        }
    }

    private void openModuleSettings(Module m) {
        BetterSearchModule cfg = config();
        boolean inline = cfg == null || cfg.inlineSettings.get();
        outlinedModule = m;
        BetterSearchAddon.LOG.info("[BetterSearch] right-click on {} (inline={}, outline={})",
            m.name, inline, cfg == null || cfg.inlineOutline.get());
        if (!inline) {
            UsageTracker.record(m);
            UsageTracker.save();
            meteordevelopment.meteorclient.MeteorClient.mc.setScreen(new OutlinedModuleScreen(theme, m));
            return;
        }
        UsageTracker.record(m);
        UsageTracker.save();
        openInline(m);
    }

    private void openInline(Module m) {
        inlineModule = m;
        inlineSettingsHolder = null;
        BetterSearchAddon.LOG.info("[BetterSearch] inline OPEN for {}", m.name);
        showInline();
    }

    private void closeInline() {
        BetterSearchAddon.LOG.info("[BetterSearch] inline CLOSE");
        inlineModule = null;
        inlineSettingsHolder = null;
        inlineKeybind = null;
        inlineActiveBox = null;
        refreshResults();
        searchBox.setFocused(true);
    }

    private void showInline() {
        Module m = inlineModule;
        if (m == null) return;
        list.clear();

        if (statusLabel != null) {
            statusLabel.set("Settings: " + m.title + " — Left/Backspace for list, or type to search");
        }

        BetterSearchModule cfg = config();
        MenuPanel menu = new MenuPanel();
        menu.spacing = 4;
        menu.drawBg = cfg == null || cfg.menuBackground.get();
        try {
            menu.bg = new meteordevelopment.meteorclient.utils.render.color.Color(cfg != null ? cfg.menuBgColor.get() : new meteordevelopment.meteorclient.utils.render.color.SettingColor(12, 12, 18, 210));
        } catch (Exception ignored) {}
        menu.outline = cfg == null || cfg.menuOutline.get();
        menu.radius = cfg != null ? cfg.menuCornerRadius.get() : 8;
        menu.pad = cfg != null ? cfg.menuPadding.get() : 6;
        list.add(menu).expandX().widget();

        WHorizontalList top = theme.horizontalList();
        top.spacing = 4;
        WButton back = theme.button("Back");
        back.action = this::closeInline;
        top.add(back).widget();
        top.add(theme.label(m.title, true)).expandX().widget();
        inlineActiveBox = theme.checkbox(m.isActive());
        inlineActiveBox.action = () -> {
            if (m.isActive() != inlineActiveBox.checked) {
                m.toggle();
                UsageTracker.record(m);
                UsageTracker.save();
            }
        };
        top.add(inlineActiveBox).right().widget();
        menu.add(top).expandX().widget();

        WLabel desc = menu.add(theme.label(m.description)).expandX().widget();
        try {
            desc.color(theme.textSecondaryColor());
        } catch (Exception ignored) {}

        // Context card for the right-clicked module.
        // ALWAYS outlined + tinted (no toggle) so the open menu visibly marks its module.
        {
            ModuleSearch.Result r = new ModuleSearch.Result(m, m.title, 0, UsageTracker.getCount(m));
            ModuleCard context = makeCard(r, -1, true, true);
            context.selected = false;
            context.outline = meteordevelopment.meteorclient.utils.render.color.Color.YELLOW;
            context.onToggle = () -> {
                m.toggle();
                UsageTracker.record(m);
                UsageTracker.save();
                showInline();
            };
            context.onSettings = () -> {};
            menu.add(context).expandX().widget();
            BetterSearchAddon.LOG.info("[BetterSearch] inline SHOW menu for {} (outlined context card added)", m.name);
        }

        menu.add(theme.label("Locked in Better Search (non-draggable)")).expandX().widget();
        // Dedicated holder: Settings.tick() clears + rebuilds its container on the first
        // tick (visibility pass), so it must never be our shared list.
        inlineSettingsHolder = theme.verticalList();
        inlineSettingsHolder.add(theme.settings(m.settings)).expandX().widget();
        menu.add(inlineSettingsHolder).expandX().widget();

        // Bind section (mirrors Meteor's module screen so binds can be edited inline).
        WSection bindSection = theme.section("Bind", true);
        menu.add(bindSection).expandX().widget();

        WHorizontalList bind = bindSection.add(theme.horizontalList()).expandX().widget();
        bind.add(theme.label("Bind: "));
        inlineKeybind = bind.add(theme.keybind(m.keybind)).expandX().widget();
        inlineKeybind.actionOnSet = () -> Modules.get().setModuleToBind(m);
        WButton bindReset = bind.add(theme.button(GuiRenderer.RESET)).expandCellX().right().widget();
        bindReset.action = inlineKeybind::resetBind;
        bindReset.tooltip = "Reset";

        WHorizontalList tobr = bindSection.add(theme.horizontalList()).widget();
        tobr.add(theme.label("Toggle on bind release: "));
        WCheckbox tobrC = tobr.add(theme.checkbox(m.toggleOnBindRelease)).widget();
        tobrC.action = () -> m.toggleOnBindRelease = tobrC.checked;

        WHorizontalList cf = bindSection.add(theme.horizontalList()).widget();
        cf.add(theme.label("Chat Feedback: "));
        WCheckbox cfC = cf.add(theme.checkbox(m.chatFeedback)).widget();
        cfC.action = () -> m.chatFeedback = cfC.checked;

        taskAfterRender = () -> refreshHover();
    }

    @Override
    public void tick() {
        super.tick();
        if (inlineModule != null && inlineSettingsHolder != null) {
            try {
                inlineModule.settings.tick(inlineSettingsHolder, theme);
            } catch (Exception ignored) {}
        }
    }

    // Same live refresh Meteor's own module screen does: the bind value is set
    // globally on capture, and these events tell the open view to repaint.
    @EventHandler
    private void onModuleBindChanged(ModuleBindChangedEvent event) {
        if (inlineModule != null && event.module == inlineModule && inlineKeybind != null) {
            inlineKeybind.reset();
        }
    }

    @EventHandler
    private void onActiveModulesChanged(ActiveModulesChangedEvent event) {
        if (inlineModule != null && inlineActiveBox != null) {
            inlineActiveBox.checked = inlineModule.isActive();
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
        // In-place like mouse toggles: no rebuild, selection and scroll stay put
        ModuleCard card = cardMap.get(r.module());
        if (card != null) {
            card.tooltip = cardTooltip(r);
            card.invalidate();
        } else {
            refreshResults();
        }
    }

    private void openSelectedSettings() {
        if (inlineModule != null || current.isEmpty() || selected < 0 || selected >= current.size()) return;
        openModuleSettings(current.get(selected).module());
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();

        if (inlineModule != null) {
            // While capturing a new bind, let the keybind widget receive every key
            boolean binding = false;
            try {
                binding = Modules.get().isBinding();
            } catch (Exception ignored) {}
            if (!binding && (key == GLFW_KEY_LEFT || key == GLFW_KEY_BACKSPACE)) {
                closeInline();
                return true;
            }
            // Esc goes back to the Better Search list instead of exiting to game
            if (!binding && key == GLFW_KEY_ESCAPE) {
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

    // Screen-level panel dragging (robust: direct move, no relayout fight)

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        if (dragHandle != null && dragHandle.mouseOver && click.button() == GLFW_MOUSE_BUTTON_LEFT) {
            draggingPanel = true;
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        draggingPanel = false;
        return super.mouseReleased(click);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);
        if (draggingPanel && panel != null && hasLastMove) {
            // super() works in scaled widget units, so scale deltas the same way
            double s = meteordevelopment.meteorclient.MeteorClient.mc.getWindow().getScaleFactor();
            double nx = clamp(savedDragX + (mouseX - lastMoveX) * s, -420, 420);
            double ny = clamp(savedDragY + (mouseY - lastMoveY) * s, -40, 420);
            panel.move(nx - savedDragX, ny - savedDragY);
            savedDragX = nx;
            savedDragY = ny;
            panel.dragX = nx;
            panel.dragY = ny;
        }
        lastMoveX = mouseX;
        lastMoveY = mouseY;
        hasLastMove = true;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
