# Better Search — by Turbo

A Meteor Client addon for **Minecraft 1.21.11** that adds a **Wurst-client-like Navigator search** for modules.

Meteor's built-in Modules search only sorts by Levenshtein distance and shows a handful of results.
Better Search gives you what makes Wurst's Navigator fast: a full searchable list, fuzzy matching,
synonyms, usage learning, and keyboard-first navigation.

Credits: **Turbo**.

## Features (Wurst-style)

- **Navigator GUI**: search bar on top + big scrollable list of every module.
  Empty query shows everything, most-used first.
- **Fuzzy matching** (lower score = better):
  1. exact (`kill aura` → `Kill Aura`)
  2. prefix (`kill` → `Kill Aura`)
  3. word-prefix, order independent (`aura kill` → `Kill Aura`)
  4. all-words-contained
  5. substring
  6. subsequence (`kla` → `Kill Aura`)
  7. typo-tolerant Levenshtein (`kil aura` still finds it)
- **Searches everything**: name, title, description, category (e.g. `combat`),
  Meteor aliases, setting names (e.g. `range` finds KillAura), plus custom synonyms.
- **Synonyms via `@SearchTags`** (Wurst equivalent):
  ```java
  @SearchTags({"speedy-gonzales", "haste"})
  public class FastBreak extends Module { ... }
  ```
- **Learns your preferences**: every module toggle is counted via mixin and saved to
  `meteor-client/better-search-usage.json`. Frequent modules rank higher, like Wurst Navigator.
- **Keyboard-first**: `Up/Down` move, `Enter` toggle, `Right/Tab` open settings, `Esc` close.
- **Mouse**: left-click toggles, right-click opens module settings (same as Meteor + Wurst).
- **Chat command**: `.better-search` / `.bs` / `.navigator` / `.find`.
- **Tooltips**: each row shows matched text, `[category]`, `ON/OFF`, description, and use count.

## Requirements

- Minecraft **1.21.11**
- Fabric Loader **0.18.2**
- Meteor Client **1.21.11-SNAPSHOT** (from https://meteorclient.com or GitHub builds)
- Java **21**

## Install

1. Install Fabric Loader 0.18.2 for Minecraft 1.21.11.
2. Put Meteor Client (1.21.11 build) + this mod's JAR into your `mods/` folder.
3. Launch the game.
4. Press `N` or run `.better-search`.

## Usage

1. Open Better Search: press `N` (default bind of the `Better Search` module, rebindable in
   ClickGUI → Better Search category → module settings) or run `.better-search` in chat.
2. Type, e.g. `kill`, `fly`, `esp`, `range`, `combat`, `speedy`.
3. Navigate and toggle.

### Controls

| Input | Action |
|-------|--------|
| Type | Filter modules live |
| `Up` / `Down` | Move selection (`>` arrow) |
| `Enter` | Toggle selected module |
| `Right` / `Tab` | Open selected module's settings |
| Left-click row | Toggle module |
| Right-click row | Open module settings |
| `Esc` | Close |

### Commands

| Command | What it does |
|---------|--------------|
| `.better-search` | Open the Navigator GUI |
| `.better-search <query>` | List up to 8 matches in chat |
| `.better-search toggle <query>` | Toggle the best match |

### Module settings (`Better Search` module)

| Setting | Default | Meaning |
|---------|---------|---------|
| `max-results` | 30 | Max rows in the list |
| `learn-usage` | true | Boost frequently used modules (Wurst learning) |
| `search-descriptions` | true | Match descriptions |
| `search-settings` | true | Match setting names |
| `search-tags` | true | Match aliases + `@SearchTags` |

Usage counts live in `meteor-client/better-search-usage.json`. Delete the file to reset learning.

## For addon developers

Add synonyms to your own modules so they are easy to find:

```java
import com.bettersearch.SearchTags;
import meteordevelopment.meteorclient.systems.modules.Module;

@SearchTags({"speedy-gonzales", "fast break", "haste"})
public class MyModule extends Module {
    public MyModule() {
        super(CATEGORY, "my-module", "Does something cool.");
    }
}
```

## Project structure

```text
src/main/java/com/bettersearch/
  BetterSearchAddon.java      — Meteor entrypoint (getPackage, category, credits to Turbo)
  SearchTags.java             — @SearchTags annotation (Wurst-style synonyms)
  search/
    FuzzyMatcher.java         — exact/prefix/words/substring/subsequence/levenshtein scoring
    UsageTracker.java         — toggle counting + JSON persistence, no extra deps
    ModuleSearch.java         — combines name/title/desc/category/aliases/settings/tags + ranking
  gui/
    BetterSearchScreen.java   — WindowScreen Navigator (search box + scrollable results + keyboard nav)
  modules/
    BetterSearchModule.java   — momentary module (press N), opens GUI, exposes search settings
  commands/
    BetterSearchCommand.java  — .better-search / .bs / .navigator / .find (open, list, toggle)
  mixin/
    ModuleToggleMixin.java    — @Mixin(Module) toggle() counter for learning
src/main/resources/
  fabric.mod.json             — id: better-search, entrypoint com.bettersearch.BetterSearchAddon
  better-search.mixins.json   — mixin config
  assets/better-search/icon.png
```

## How to build (1.21.11)

### Prerequisites

- Java 21 (Gradle toolchain will use it; `java -version` should show 21)
- Internet access (Gradle downloads Minecraft, Yarn, Meteor from `https://maven.meteordev.org`)
- This repo cloned with git

Versions are pinned in `gradle/libs.versions.toml`:

```toml
minecraft = "1.21.11"
yarn-mappings = "1.21.11+build.3"
fabric-loader = "0.18.2"
loom = "1.14-SNAPSHOT"
meteor = "1.21.11-SNAPSHOT"
```

### Build steps

```bash
# 1. Check Java
java -version

# 2. Build (Linux/macOS)
./gradlew build

# Windows
gradlew.bat build
```

Output:

- `build/libs/better-search-0.1.0.jar` — remapped (ready for `mods/`)
- Build log ends with `BUILD SUCCESSFUL`

### Run from source (dev client)

- IntelliJ IDEA: open the project, let Gradle sync, run the `Minecraft Client` run configuration.
  This starts Minecraft 1.21.11 with Meteor + this addon loaded.
- Or: `./gradlew runClient`

### Install the built JAR

Copy `build/libs/better-search-*.jar` into your Minecraft `mods/` folder alongside:

- `fabric-loader-0.18.2`
- `meteor-client-1.21.11-*.jar`

then launch Minecraft 1.21.11.

### Troubleshooting

- `401 / 404 from maven.meteordev.org`: Meteor snapshots require the `releases` + `snapshots`
  repos declared in `build.gradle.kts` (already configured). Retry; snapshots can be briefly unavailable.
- `Unsupported class file major version`: you are not on Java 21. Set `JAVA_HOME` to JDK 21.
- `Mixin apply failed`: you are running the wrong Minecraft/Meteor combo. This branch only supports 1.21.11.
- No `N` bind: open ClickGUI (`Right Shift`), go to `Better Search` category, open `better-search`
  settings, set the keybind manually.

## Git tracking

This project uses git:

```bash
git status
git log --oneline
git add -A
git commit -m "message"
```

## Credits

- Addon idea, design, and implementation: **Turbo**
- Built on [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) + Fabric
- Inspired by [Wurst Client Navigator](https://wurst.wiki/navigator) (search bar, synonyms, usage learning, keyboard nav)

## License

CC0 — do whatever you want. Please keep the credit to **Turbo**.
