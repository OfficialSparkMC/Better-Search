# Better Search — by Turbo

A Meteor Client addon for **Minecraft 1.21.11** that adds a **separate `Search` tab**
in the Meteor ClickGUI menu — a Wurst-Navigator-style module finder with a clean,
scrollable UI.

Open Meteor (`Right Shift`), click the **`Search`** tab in the top bar (next to
Modules / Config / HUD). You get a search bar on top and a scrollable list of every
module below. Empty query shows everything, most-used first.

Credits: **Turbo**.

## Features

- **Separate tab**: `Search` tab in the Meteor menu top bar (`Tabs.add`), not a popup.
  Scrollable centered window (`WindowTabScreen`), clean rows.
- **Fancy clean UI via shared renderer**: all rows use Meteor theme widgets (shared GuiRenderer, so it matches your Meteor theme), one search box, status line, fancy rows with `●/○` active dot (green/gray) + module toggle + `category • ON/OFF • uses`,
  footer credit (`by Turbo`). Details live in tooltips.
- **Scrollable**: results live in the window's scrollable view with scrollbar.
  Mouse wheel + drag work; search box stays at the top.
- **Fuzzy matching**: exact → prefix → word-prefix (`aura kill` → `Kill Aura`) →
  all-words → substring → subsequence (`kla`) → typo-tolerant Levenshtein.
- **Searches everything**: name, title, description, category (`combat`),
  aliases, setting names (`range` → KillAura), `@SearchTags` synonyms.
- **Synonyms** (Wurst `@SearchTags` equivalent):
  ```java
  @SearchTags({"speedy-gonzales", "haste"})
  public class FastBreak extends Module { ... }
  ```
- **Learns preferences**: every toggle counted via `ModuleToggleMixin`,
  saved to `meteor-client/better-search-usage.json`, most-used ranks first.
- **Keyboard**: `Up/Down` move, `Enter` toggle, `Right` open settings, `Esc` close.
- **Mouse**: left-click toggles, right-click opens settings.
- **Shortcuts**: `Right-Ctrl` opens the Search tab (momentary module bind, rebindable) + `.better-search` / `.bs` chat command.

## Requirements

- Minecraft **1.21.11**
- Fabric Loader **0.18.2**
- Meteor Client **1.21.11-SNAPSHOT**
- Java **21**

## Install

1. Install Fabric Loader 0.18.2 for Minecraft 1.21.11.
2. Put Meteor Client (1.21.11) + this mod's JAR into `mods/`.
3. Launch, press `Right-Ctrl` — the `Search` tab appears (or `Right Shift` → click `Search`).

## Usage

1. Press `Right-Ctrl` (default bind, rebindable in ClickGUI → Better Search) or Meteor menu → `Search` tab, or `.better-search`.
2. Type, e.g. `kill`, `fly`, `esp`, `range`, `combat`.
3. `Enter` toggles, `Right` opens settings. Mouse: left toggle, right settings.

### Controls

| Input | Action |
|-------|--------|
| Type | Live filter, selection resets to best match |
| `Up` / `Down` | Move selection |
| `Enter` | Toggle selected |
| `Right` | Open selected settings |
| Left-click | Toggle |
| Right-click | Open settings |
| `Esc` | Close |
| Wheel / drag | Scroll list |

### Commands

| Command | Action |
|---------|--------|
| `.better-search` | Open the Search tab |
| `.better-search <query>` | List matches in chat |
| `.better-search toggle <query>` | Toggle best match |

Aliases: `.bs`, `.bsearch`, `.navigator`, `.find`.

### Settings (`Better Search` module)

| Setting | Default | Meaning |
|---------|---------|---------|
| `max-results` | 30 | Rows in tab list |
| `learn-usage` | true | Boost frequent modules |
| `search-descriptions` | true | Match descriptions |
| `search-settings` | true | Match setting names |
| `search-tags` | true | Match aliases + `@SearchTags` |

Delete `meteor-client/better-search-usage.json` to reset learning.

## For addon developers

```java
import com.bettersearch.SearchTags;

@SearchTags({"speedy-gonzales", "fast break", "haste"})
public class MyModule extends Module { ... }
```

Custom tab integration used here:

```java
Tabs.add(new BetterSearchTab()); // Tab{name="Search", screen=WindowTabScreen}
```

## Project structure

```text
src/main/java/com/bettersearch/
  BetterSearchAddon.java      — entrypoint, Tabs.add(new BetterSearchTab()), by Turbo
  SearchTags.java             — synonyms annotation
  search/
    FuzzyMatcher.java         — scoring
    UsageTracker.java         — counts + JSON, no extra deps
    ModuleSearch.java         — ranking across fields
  tabs/
    BetterSearchTab.java      — Tab("Search") in top bar
    BetterSearchTabScreen.java— WindowTabScreen, clean scrollable UI + keyboard nav
  modules/
    BetterSearchModule.java   — Right-Ctrl keybind, opens Search tab, search tuning settings
  commands/
    BetterSearchCommand.java  — .better-search (open/list/toggle)
  mixin/
    ModuleToggleMixin.java    — toggle counter
src/main/resources/
  fabric.mod.json, better-search.mixins.json, assets/better-search/icon.png
```

## How to build (1.21.11)

Pinned in `gradle/libs.versions.toml`:

```toml
minecraft = "1.21.11"
yarn-mappings = "1.21.11+build.3"
fabric-loader = "0.18.2"
loom = "1.14-SNAPSHOT"
meteor = "1.21.11-SNAPSHOT"
```

```bash
java -version        # must be 21
./gradlew build      # Linux/macOS
gradlew.bat build    # Windows
```

Output: `build/libs/better-search-0.1.0.jar` (`BUILD SUCCESSFUL`).
Dev client: run `Minecraft Client` config in IDEA, or `./gradlew runClient`.
Install: copy JAR to `mods/` next to Meteor 1.21.11.

Troubleshooting: use Java 21; keep the 1.21.11 combo (this branch is not for 26.x);
if Meteor snapshots 401/404, retry (repos already in `build.gradle.kts`).

## Git

```bash
git status
git log --oneline
```

## Credits

- **Turbo** — idea, design, implementation
- Meteor Client + Fabric; inspired by Wurst Navigator

## License

CC0. Keep the credit to **Turbo**.
