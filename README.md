# AutoPV

Fabric 1.21.11 client mod. Auto-stores items into a server vault (`/pv <n>`) once your
inventory fills past a threshold. Config screen matches your reference image via
YACL (YetAnotherConfigLib) + ModMenu integration. `/asv` command included.

## Build

1. Install a JDK 21.
2. Get the Gradle wrapper (not included here to keep the download small):
   ```
   gradle wrapper --gradle-version 8.8
   ```
   (or open the project in IntelliJ with the Fabric/Loom plugin — it'll fetch one for you)
3. `./gradlew build` → output jar in `build/libs/`.
4. Check `gradle.properties` versions against https://fabricmc.net/develop/ — 1.21.x
   yarn/loader/API build numbers do get bumped; update if the build fails on a
   missing artifact.
5. Drop the jar, plus Fabric API + ModMenu + YACL jars, into your `mods/` folder.

## Config file

`config/autopv.json`, created on first launch. Edit in-game via ModMenu → AutoPV,
or `/asv gui`, or by hand (reload with `/asv reload`).

Fields:

| Field | Meaning |
|---|---|
| `notificationsEnabled` | chat alert at milestone |
| `milestoneAlertSlots` | slot count that triggers the alert |
| `mode` | `OFF` / `ALL` / `CUSTOM` |
| `thresholdSlots` | used-slot count that triggers auto-store |
| `vaultSlots` | which vault numbers to cycle, e.g. `"1-10"` or `"1,2,5,6,7"` |
| `vaultOpenCommand` | server command prefix, e.g. `"pv"` → sends `/pv 3` |
| `ignoredSlots` | slots to never touch/count, survival-inventory numbering (main 9-35, hotbar 36-44), e.g. `"36-44"` |
| `insertDelayTicks` | ticks between each item inserted into the vault (20 = 1s), `0` = instant |

## Commands

- `/asv` or `/asv gui` — open config screen
- `/asv status` — print current settings
- `/asv reload` — reload config from disk
- `/asv mode <OFF\|ALL\|CUSTOM>` — quick switch, saves immediately
- `/asv add hand` — add whatever's in your main hand to the CUSTOM id whitelist
- `/asv add <item>` — add by id, e.g. `minecraft:cod` or just `cod`
- `/asv add name hand` — add the display name of whatever's in hand to the name whitelist
- `/asv add name <name>` — add by display name, e.g. `/asv add name Diamond Sword`
- `/asv remove <item>` — remove by id
- `/asv remove name <name>` — remove by display name
- `/asv list` — print current id + name whitelists

## How auto-store works

`VaultManager` ticks every frame, counts used inventory slots, and once at/above
`thresholdSlots` sends `/<vaultOpenCommand> <n>` picking `n` round-robin from
`vaultSlots`. When the resulting chest-style GUI opens, it builds a queue of
every matching slot and shift-clicks one item every `insertDelayTicks` ticks
(20 ticks = 1s) instead of all at once, then closes the screen once the queue
drains. If the vault comes back full, it moves to the next configured vault
number next cycle. If the screen closes early (you press escape, the server
closes it, etc.) the deposit just stops cleanly where it was.

This assumes your server's vault opens as a plain chest-shaped inventory
(`GenericContainerScreen`) — true for most vault plugins (PlayerVaults and
similar). If yours differs, adjust the screen-type check in
`VaultManager.onScreenOpen`.

## Item whitelist (CUSTOM mode)

`CUSTOM` mode stores an item if it matches **either** whitelist:

- **By id** — `itemWhitelist`, exact registry id like `"minecraft:cod"`.
- **By name** — `itemNameWhitelist`, case-insensitive match against the item's
  shown name (`"Cod"`, `"Diamond Sword"`), including custom/anvil-renamed items.

Manage both two ways:

**In-game GUI** — ModMenu → AutoPV → **Items** tab, two lists side by side.
Add/remove rows directly; works for vanilla ids, custom modded item ids
(`yourmod:magic_gem`), and arbitrary display names since it's all free text.

**Chat commands:**
```
/asv add hand              # id of whatever you're holding
/asv add cod                # short id, auto-prefixed to minecraft:cod
/asv add minecraft:salmon
/asv add yourmod:magic_gem  # custom items work the same way

/asv add name hand           # display name of whatever you're holding
/asv add name Diamond Sword  # match by name instead of id

/asv remove cod
/asv remove name Diamond Sword
/asv list
```

Empty whitelists + CUSTOM mode = nothing gets auto-stored until you add
something. `ALL` mode ignores both whitelists and stores everything; `OFF`
disables auto-store entirely. Matching logic lives in `VaultManager.shouldStore(...)`
if you want tag-based matching instead.

## Ignored slots

`ignoredSlots` (General tab, or edit the json directly) excludes specific
inventory slots from both the used-slot count and the auto-store click pass —
handy for parking a tool or weapon somewhere it never gets swept into the
vault. Uses the same numbering as the vanilla survival inventory screen:
main inventory is `9-35`, hotbar is `36-44`. Accepts ranges, lists, or a mix
(`"36-44"`, `"36,37,44"`, `"9-17,40"`). Armor, offhand, and crafting slots
aren't part of the auto-store scan to begin with, so numbers outside `9-44`
are ignored.

## Troubleshooting

- **`Could not find dev.isxander:yet-another-config-lib-fabric:...`** — fixed:
  YACL's artifact id is `yet-another-config-lib` (no `-fabric`); the `-fabric`
  is only part of the version string, e.g. `3.8.1+1.21.11-fabric`. Already
  corrected in `build.gradle`.
- **`ACQUIRED_PREVIOUS_OWNER_DISOWNED` loom cache lock** — harmless, means a
  prior build got killed mid-way. Gradle rebuilds the cache automatically; if
  it hangs, delete the project's `.gradle/loom-cache` folder and rebuild.
- Version numbers per isXander's own chart: `yacl_version=3.8.1+1.21.11-fabric`.
  Deliberately pinned to 3.8.1, not the newer 3.8.2 — 3.8.2 ships a broken
  access widener that crashes some launchers on startup
  (isXander/YetAnotherConfigLib#313). Recheck
  https://docs.isxander.dev/yet-another-config-lib/installing-yacl if
  targeting a different 1.21.x point release, and check whether that bug is
  fixed before upgrading past 3.8.1.

## Known assumption to double check

Player inventory is assumed to occupy the **last 36 slots** of the vault's
`ScreenHandler` (27 main + 9 hotbar) — standard for vanilla chest-shaped
containers. If your server plugin lays slots out differently, this needs
adjusting.