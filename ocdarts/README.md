# [OC] Darts

Author: **cutguardian**. Version: **1.0.1**.

Select a dart type, open your inventory, and click in the game view to use a feather
on the selected dart tips. Each click issues one item combination. There is no
timer, automatic repetition, banking, or queued backlog of clicks.

Hold **Shift** for normal game clicks. Inventory, chat, minimap and interface
controls keep their normal actions. Disable the plugin to restore all clicks.

The OC menu entry shows what is missing when the selected tips, feathers, or
Fletching level are unavailable. Clicking an unavailable entry does nothing.
Keep the normal inventory tab open and close banks and other interfaces.
Items can occupy any inventory slots, and only the configured tips are used.
Temporary Fletching boosts count toward the level check.

IDs and required levels follow the supplied table:

| Dart tips | Item ID | Fletching |
|---|---:|---:|
| Bronze | 819 | 10 |
| Iron | 820 | 22 |
| Steel | 821 | 37 |
| Mithril | 822 | 52 |
| Adamant | 823 | 67 |
| Rune | 824 | 81 |
| Amethyst | 25853 | 90 |
| Dragon | 11232 | 95 |

All types use feathers (item ID **314**).

Run `./build.cmd` from the repository root to build through Microbot-Hub and
install `OcDartsPlugin.jar` into `%USERPROFILE%/.runelite/microbot-plugins`.
See the [repository README](../README.md) for build options.

Manual validation: enable with feathers and selected tips; click the game view
once and verify material consumption, then wait and verify no further plugin
actions. Repeat after moving both stacks, with missing materials, below the
required level, while holding Shift, with a bank open, and after disabling.
Verify inventory, chat, minimap and right-click menus remain usable.
