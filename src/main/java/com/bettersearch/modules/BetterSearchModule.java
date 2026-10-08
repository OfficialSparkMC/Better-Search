package com.bettersearch.modules;

import com.bettersearch.BetterSearchAddon;
import com.bettersearch.SearchTags;
import com.bettersearch.tabs.BetterSearchTab;
import com.bettersearch.search.UsageTracker;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL;

/**
 * Wurst-Navigator-style module search.
 * Opens the separate "Search" tab in the Meteor menu (scrollable, clean UI).
 * Press Right-Ctrl (default bind) to jump straight to it.
 * Left-click toggles, right-click opens settings,
 * Up/Down + Enter work from the keyboard.
 *
 * <p>Uses Meteor's shared GUI renderer (theme widgets) for a fancy themed UI. Credits: OfficialSparkMC</p>
 */
@SearchTags({"navigator", "find", "lookup", "wurst", "search gui", "module finder"})
public class BetterSearchModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSearch = settings.createGroup("Search");
    private final SettingGroup sgAppearance = settings.createGroup("Appearance");

    public final Setting<Integer> maxResults = sgGeneral.add(new IntSetting.Builder()
        .name("max-results")
        .description("Max results when filtering. Empty query always shows the FULL list grouped by category.")
        .defaultValue(100)
        .min(10)
        .sliderMax(200)
        .build()
    );

    public final Setting<Boolean> learnUsage = sgGeneral.add(new BoolSetting.Builder()
        .name("learn-usage")
        .description("Learn your preferences: frequently toggled modules rank higher, like Wurst Navigator.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> searchDescriptions = sgSearch.add(new BoolSetting.Builder()
        .name("search-descriptions")
        .description("Also match module descriptions.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> searchSettings = sgSearch.add(new BoolSetting.Builder()
        .name("search-settings")
        .description("Also match setting names, e.g. 'range' finds KillAura.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> searchTags = sgSearch.add(new BoolSetting.Builder()
        .name("search-tags")
        .description("Also match aliases and @SearchTags synonyms (Wurst-style).")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> panelWidth = sgAppearance.add(new IntSetting.Builder()
        .name("panel-width")
        .description("Width of the Search tab panel.")
        .defaultValue(500)
        .min(300)
        .sliderMax(800)
        .build()
    );

    public final Setting<Integer> columns = sgAppearance.add(new IntSetting.Builder()
        .name("columns")
        .description("Modules per line (grid). 1 = classic list.")
        .defaultValue(1)
        .min(1)
        .sliderMax(3)
        .build()
    );

    public final Setting<Integer> rowGap = sgAppearance.add(new IntSetting.Builder()
        .name("row-gap")
        .description("Vertical gap between rows (row size / density).")
        .defaultValue(2)
        .min(0)
        .sliderMax(12)
        .build()
    );

    public final Setting<Integer> rowInnerGap = sgAppearance.add(new IntSetting.Builder()
        .name("row-inner-gap")
        .description("Horizontal gap inside a row.")
        .defaultValue(4)
        .min(0)
        .sliderMax(12)
        .build()
    );

    public final Setting<Boolean> showCategory = sgAppearance.add(new BoolSetting.Builder()
        .name("show-category")
        .description("Show the category on each row (Wurst-style).")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> showStatus = sgAppearance.add(new BoolSetting.Builder()
        .name("show-status")
        .description("Show the status line under the search bar.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> cardPadding = sgAppearance.add(new IntSetting.Builder()
        .name("card-padding")
        .description("Inner padding of module cards (= module size).")
        .defaultValue(2)
        .min(0)
        .sliderMax(8)
        .build()
    );

    public final Setting<Integer> cornerRadius = sgAppearance.add(new IntSetting.Builder()
        .name("corner-radius")
        .description("Corner radius of module cards.")
        .defaultValue(10)
        .min(0)
        .sliderMax(12)
        .build()
    );

    public final Setting<Boolean> inlineSettings = sgAppearance.add(new BoolSetting.Builder()
        .name("inline-settings")
        .description("Open module settings INSIDE Better Search (locked, non-draggable). Off = classic draggable Meteor window.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> inlineOutline = sgAppearance.add(new BoolSetting.Builder()
        .name("inline-outline")
        .description("Keep a yellow outline on the right-clicked module back in the search list.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> draggablePanel = sgAppearance.add(new BoolSetting.Builder()
        .name("draggable-panel")
        .description("Show a drag handle to move the Search panel. Off = fixed centered (clean).")
        .defaultValue(false)
        .build()
    );

    public BetterSearchModule() {
        super(BetterSearchAddon.CATEGORY, "better-search", "Wurst-like Navigator search tab for all modules. Right-Ctrl opens it.", "navigator", "search", "find", "bs");
        keybind.set(true, GLFW_KEY_RIGHT_CONTROL, 0);
        chatFeedback = false;
        UsageTracker.load();
    }

    @Override
    public void onActivate() {
        try {
            Tab tab = Tabs.get(BetterSearchTab.class);
            if (tab != null) tab.openScreen(GuiThemes.get());
            else mc.setScreen(new com.bettersearch.tabs.BetterSearchTabScreen(GuiThemes.get(), new BetterSearchTab()));
        } finally {
            // Momentary button like Wurst Navigator: stay unbound-toggled
            if (isActive()) toggle();
        }
    }
}
