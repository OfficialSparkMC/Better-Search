package com.bettersearch;

import com.bettersearch.commands.BetterSearchCommand;
import com.bettersearch.modules.BetterSearchModule;
import com.bettersearch.tabs.BetterSearchTab;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.item.Items;
import org.slf4j.Logger;

public class BetterSearchAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Better Search", Items.COMPASS.getDefaultStack());

    @Override
    public void onInitialize() {
        LOG.info("Initializing Better Search addon by Turbo");

        Tabs.add(new BetterSearchTab());
        Modules.get().add(new BetterSearchModule());
        Commands.add(new BetterSearchCommand());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.bettersearch";
    }
}
