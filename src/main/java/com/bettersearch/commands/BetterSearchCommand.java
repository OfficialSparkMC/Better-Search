package com.bettersearch.commands;

import com.bettersearch.tabs.BetterSearchTab;
import com.bettersearch.modules.BetterSearchModule;
import com.bettersearch.search.ModuleSearch;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;

import java.util.List;

/**
 * {@code .better-search} — Wurst-style module finder in chat + tab opener.
 * <ul>
 *   <li>{@code .better-search} opens the Search tab</li>
 *   <li>{@code .better-search <query>} lists matches in chat</li>
 *   <li>{@code .better-search toggle <query>} toggles the best match</li>
 * </ul>
 * Credits: OfficialSparkMC.
 */
public class BetterSearchCommand extends Command {
    public BetterSearchCommand() {
        super("better-search", "Wurst-like Navigator search for modules.", "bs", "bsearch", "navigator", "find");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            Tab tab = Tabs.get(BetterSearchTab.class);
            if (tab != null) tab.openScreen(GuiThemes.get());
            return SINGLE_SUCCESS;
        });

        builder.then(literal("toggle").then(argument("query", StringArgumentType.greedyString()).executes(context -> {
            String query = StringArgumentType.getString(context, "query");
            BetterSearchModule cfg = Modules.get().get(BetterSearchModule.class);
            int max = cfg != null ? cfg.maxResults.get() : 10;
            boolean desc = cfg == null || cfg.searchDescriptions.get();
            boolean sett = cfg == null || cfg.searchSettings.get();
            boolean tags = cfg == null || cfg.searchTags.get();

            List<ModuleSearch.Result> results = ModuleSearch.search(query, desc, sett, tags, max);
            if (results.isEmpty()) {
                error("No modules found for \"%s\".", query);
                return SINGLE_SUCCESS;
            }
            ModuleSearch.Result best = results.get(0);
            best.module().toggle();
            info("Toggled (highlight)%s(default) %s.", best.module().title, best.module().isActive() ? "on" : "off");
            return SINGLE_SUCCESS;
        })));

        builder.then(argument("query", StringArgumentType.greedyString()).executes(context -> {
            String query = StringArgumentType.getString(context, "query");
            BetterSearchModule cfg = Modules.get().get(BetterSearchModule.class);
            int max = cfg != null ? cfg.maxResults.get() : 10;
            boolean desc = cfg == null || cfg.searchDescriptions.get();
            boolean sett = cfg == null || cfg.searchSettings.get();
            boolean tags = cfg == null || cfg.searchTags.get();

            List<ModuleSearch.Result> results = ModuleSearch.search(query, desc, sett, tags, Math.min(max, 8));
            if (results.isEmpty()) {
                info("No modules found for \"%s\".", query);
                return SINGLE_SUCCESS;
            }
            info("Found %d module(s) for \"%s\":", results.size(), query);
            for (ModuleSearch.Result r : results) {
                info("  (highlight)%s(default) [%s] %s — %s", r.module().title, r.module().category.name, r.module().isActive() ? "ON" : "OFF", r.matchedText());
            }
            info("Use (highlight).better-search toggle %s(default) to toggle the best match.", query);
            return SINGLE_SUCCESS;
        }));
    }
}
