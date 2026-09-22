package net.runelite.client.plugins.microbot.ocbirdhouse;

import java.util.Set;

/**
 * Single source of truth for birdhouse-accepted seeds: every allotment, hop
 * and flower seed with Farming level 35 or below, per OSRS Wiki.
 */
final class BirdHouseSeeds
{
    static final int SEEDS_PER_HOUSE = 10;
    static final int SEEDS_PER_RUN = 40;

    private static final Set<String> NAMES = Set.of(
        "potato seed",
        "onion seed",
        "cabbage seed",
        "tomato seed",
        "sweetcorn seed",
        "strawberry seed",
        "barley seed",
        "hammerstone seed",
        "asgarnian seed",
        "jute seed",
        "yanillian seed",
        "krandorian seed",
        "wildblood seed",
        "marigold seed",
        "rosemary seed",
        "nasturtium seed",
        "woad seed",
        "limpwurt seed"
    );

    private BirdHouseSeeds() {}

    static boolean isAccepted(String itemName)
    {
        return itemName != null && NAMES.contains(itemName.toLowerCase());
    }
}
