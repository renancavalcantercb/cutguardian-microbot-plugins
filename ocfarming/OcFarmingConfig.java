package net.runelite.client.plugins.microbot.ocfarming;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(OcFarmingConfig.GROUP)
public interface OcFarmingConfig extends Config
{
    String GROUP = "ocfarming";

    @ConfigItem(keyName = "showOverlay", name = "Show monitor",
        description = "Show tree observations and estimated maturity windows.", position = 0)
    default boolean showOverlay() { return true; }

    @ConfigItem(keyName = "autoVisit", name = "Automatically revisit due patches",
        description = "Use available routes and teleports to visit selected tree patches and confirm expired estimates.",
        position = 1)
    default boolean autoVisit() { return false; }

    @ConfigItem(keyName = "depositAllBeforeRun", name = "Deposit inventory before run",
        description = "Deposit all inventory items at the bank before withdrawing farming supplies.", position = 2)
    default boolean depositAllBeforeRun() { return true; }

    @ConfigItem(keyName = "autoTrees", name = "Automatically tend trees",
        description = "Plant saplings, protect, prune, check health and pay 200 coins to remove checked trees. Enable revisits for travel and first visits.", position = 6)
    default boolean autoTrees() { return false; }

    @ConfigItem(keyName = "tree", name = "Tree to plant",
        description = "Uses one unnoted sapling per patch. Preserves existing growing trees.", position = 7)
    default FarmingTree tree() { return FarmingTree.OAK; }

    @ConfigItem(keyName = "treeProtection", name = "Protect trees",
        description = "Oak: 1 Tomatoes(5). Willow: 1 Apples(5). Maple: 1 Oranges(5). Yew: 10 cactus spines. Magic: 25 coconuts. Noted payments accepted.", position = 8)
    default boolean treeProtection() { return true; }

    @ConfigItem(keyName = "lumbridgeTree", name = "Tree: Lumbridge", description = "West of the castle.", position = 9)
    default boolean lumbridgeTree() { return true; }
    @ConfigItem(keyName = "varrockTree", name = "Tree: Varrock", description = "Castle courtyard.", position = 10)
    default boolean varrockTree() { return true; }
    @ConfigItem(keyName = "faladorTree", name = "Tree: Falador Park", description = "Tree patch in Falador Park.", position = 11)
    default boolean faladorTree() { return true; }
    @ConfigItem(keyName = "taverleyTree", name = "Tree: Taverley", description = "Taverley tree patch.", position = 12)
    default boolean taverleyTree() { return true; }
    @ConfigItem(keyName = "gnomeTree", name = "Tree: Gnome Stronghold", description = "Regular tree patch, not the fruit tree.", position = 13)
    default boolean gnomeTree() { return true; }
    @ConfigItem(keyName = "nemusTree", name = "Tree: Nemus Retreat", description = "Auburnvale patch. Enable only if you have access to Varlamore.", position = 14)
    default boolean nemusTree() { return true; }

    @net.runelite.client.config.Range(min = 0, max = 10000)
    @ConfigItem(keyName = "teleportRuneReserve", name = "Optional rune reserve",
        description = "At the bank, top up each of air/fire/earth/water/law runes to this amount when available. Missing runes never block a run; zero disables top-ups.", position = 15)
    default int teleportRuneReserve() { return 100; }

    @ConfigItem(keyName = "autoFruitTrees", name = "Automatically tend fruit trees",
        description = "Plant fruit saplings, protect, prune, check health, pick and note fruit, then pay to remove and replant.", position = 20)
    default boolean autoFruitTrees() { return false; }
    @ConfigItem(keyName = "fruitTree", name = "Fruit tree to plant",
        description = "One unnoted sapling per selected fruit patch; existing growing crops are preserved.", position = 21)
    default FarmingFruitTree fruitTree() { return FarmingFruitTree.APPLE; }
    @ConfigItem(keyName = "fruitProtection", name = "Protect fruit trees",
        description = "Apple: 9 sweetcorn. Banana: 4 Apples(5). Orange: 3 Strawberries(5). Curry: 5 Bananas(5). Pineapple: 10 watermelons. Papaya: 10 pineapples. Palm: 15 papayas. Dragonfruit: 15 coconuts. Notes accepted.", position = 22)
    default boolean fruitProtection() { return true; }
    @ConfigItem(keyName = "gnomeFruit", name = "Fruit: Gnome Stronghold", description = "Fruit patch east of the agility course.", position = 23)
    default boolean gnomeFruit() { return true; }
    @ConfigItem(keyName = "catherbyFruit", name = "Fruit: Catherby", description = "East of Catherby, near the shore.", position = 24)
    default boolean catherbyFruit() { return true; }
    @ConfigItem(keyName = "villageFruit", name = "Fruit: Tree Gnome Village", description = "West of the Tree Gnome maze.", position = 25)
    default boolean villageFruit() { return true; }
    @ConfigItem(keyName = "brimhavenFruit", name = "Fruit: Brimhaven", description = "North of Brimhaven.", position = 26)
    default boolean brimhavenFruit() { return true; }
    @ConfigItem(keyName = "kastoriFruit", name = "Fruit: Kastori", description = "Kastori fruit patch in Varlamore.", position = 27)
    default boolean kastoriFruit() { return true; }

    static boolean tending(OcFarmingConfig config) { return config.autoTrees() || config.autoFruitTrees(); }
}
