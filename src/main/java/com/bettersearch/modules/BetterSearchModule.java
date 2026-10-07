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
 * <p>Uses Meteor's shared GUI renderer (theme widgets) for a fancy themed UI. Credits: Turbo</p>
 */
@SearchTags({"navigator", "find", "lookup", "wurst", "search gui", "module finder"})
public class BetterSearchModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSearch = settings.createGroup("Search");

    public final Setting<Integer> maxResults = sgGeneral.add(new IntSetting.Builder()
        .name("max-results")
        .description("Max results shown in the Better Search list. Empty query shows most-used first (Wurst-style).")
        .defaultValue(30)
        .min(5)
        .sliderMax(100)
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

    public BetterSearchModule() {
        super(BetterSearchAddon.CATEGORY, "better-search", "Wurst-like Navigator search tab for all modules. Right-Ctrl opens it. By Turbo.", "navigator", "search", "find", "bs");
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
