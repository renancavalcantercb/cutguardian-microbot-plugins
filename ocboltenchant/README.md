# [OC] Bolt Enchant

Version **1.0.2**, author **cutguardian**, minimum Microbot **2.6.22**.

Automatically opens **Crossbow Bolt Enchantments** in the normal spellbook
with **Space held down before the first click and across subsequent menus**.
The default is one attempt per game tick; actual speed depends on the client,
server and menu response. The plugin does not wait for the enchanting animation
or for the automatic batch to finish, and does not block while moving.

Unlike OC Darts, this plugin repeats automatically while enabled.

## Usage

1. Select **Bolt type** in the plugin settings. Normal and dragon variants are supported.
2. Carry that type of unenchanted gem-tipped bolt and the required runes or staff.
   Carry only one type of unenchanted gem-tipped bolt so Space cannot select another type.
3. Open the normal Magic tab and make **Crossbow Bolt Enchantments** visible in the filters.
4. If necessary, enchant once manually to establish the game's Space selection.
   Close the production menu before enabling the plugin.
5. Enable **[OC] Bolt Enchant**. Hold **Shift** or use Microbot's global pause to pause.
   Disable the plugin to stop. Adjust **Ticks between casts** to slow the attempts.

The plugin refuses to start over an existing production menu. After its own spell
click it keeps Space held while reopening the spell on the configured game ticks,
without waiting for the previous menu to close. An unexpected product in the
production menu releases Space. Pausing, changing spellbooks, hiding the spell,
changing bolt type or disabling the plugin also releases the key. Inventory depletion, insufficient
Magic level, mixed bolt types and errors stop the loop. Lack of inventory progress
for 20 active game ticks after a cast attempt also stops it (for example, missing
runes or an unconfirmed Space selection). Restock/correct the issue and toggle
the plugin off and on to restart. Status messages use `Microbot.status`.

Keep the spell visible to continue casting. Existing production menus are left
for you to close. There is no banking, buying, walking or spellbook switching.
The game determines how Space selects the product and batch quantity.

## Build

From the repository root, build, test and save the JAR in `dist/`:

```powershell
.\build.cmd -Plugin ocboltenchant -BuildOnly -Test -Offline `
    -ClientJar '..\Microbot\runelite-client\build\libs\microbot-2.6.22.jar' `
    -ClientVersion '2.6.22'
```

Omit `-BuildOnly` to also install as
`%USERPROFILE%\.runelite\microbot-plugins\OcBoltEnchantPlugin.jar`.
Restart the client after installation to load the new JAR.

## In-game validation checklist

Version 1.0.2 keeps Space pressed across menu rebuilds instead of pressing and
releasing it after observing each menu. The supplied manual recording shows
37 Magic XP and 10 enchanted emerald bolts on every game tick from ticks 58–66;
the animation continues across those casts and is not a readiness signal.
Thirteen regression tests cover the cadence, action dispatch, actual keyboard
hold/release events, interruption cleanup and inventory checks. Live validation
of this version's one-tick cadence is pending restocking unenchanted bolts.

Additional in-game validation is still required:

- Confirm the cadence with one tick and a slower interval, while standing and running.
- Confirm normal and dragon bolts, the last partial stack, rune pouch and elemental staff.
- Confirm missing runes stop after the progress timeout.
- Confirm Shift, global pause, logout, spellbook changes and disabling during menu opening.
- Confirm Space is released when an unrelated production menu appears.
