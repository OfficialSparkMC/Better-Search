package com.bettersearch.tabs;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import net.minecraft.client.gui.screen.Screen;

/**
 * Separate Meteor tab for Better Search (by OfficialSparkMC).
 * Shows up in the ClickGUI top bar next to Modules/Config/HUD.
 */
public class BetterSearchTab extends Tab {
    public BetterSearchTab() {
        super("Search");
    }

    @Override
    public TabScreen createScreen(GuiTheme theme) {
        return new BetterSearchTabScreen(theme, this);
    }

    @Override
    public boolean isScreen(Screen screen) {
        return screen instanceof BetterSearchTabScreen;
    }
}
