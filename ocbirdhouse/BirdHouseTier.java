package net.runelite.client.plugins.microbot.ocbirdhouse;

import java.util.Map;
import java.util.Optional;

/**
 * Progressive birdhouse tiers. The runner picks the highest tier the account
 * can build that also has at least 4 logs available, then falls back down
 * the list. Item ids verified against the client cache.
 */
enum BirdHouseTier
{
    BIRD("Bird house", 5, 5, 1511, 21512),
    OAK("Oak bird house", 14, 15, 1521, 21515),
    WILLOW("Willow bird house", 24, 25, 1519, 21518),
    TEAK("Teak bird house", 34, 35, 6333, 21521),
    MAPLE("Maple bird house", 44, 45, 1517, 22192),
    MAHOGANY("Mahogany bird house", 49, 50, 6332, 22195),
    YEW("Yew bird house", 59, 60, 1515, 22198),
    MAGIC("Magic bird house", 74, 75, 1513, 22201),
    REDWOOD("Redwood bird house", 89, 90, 19669, 22204);

    static final int LOGS_PER_RUN = 4;

    final String label;
    final int hunter;
    final int crafting;
    final int logId;
    final int houseId;

    BirdHouseTier(String label, int hunter, int crafting, int logId, int houseId)
    {
        this.label = label;
        this.hunter = hunter;
        this.crafting = crafting;
        this.logId = logId;
        this.houseId = houseId;
    }

    /** Highest buildable tier with enough logs in the given log counts. */
    static Optional<BirdHouseTier> select(int hunterLevel, int craftingLevel, Map<Integer, Integer> logCounts)
    {
        BirdHouseTier[] tiers = values();
        for (int i = tiers.length - 1; i >= 0; i--)
        {
            BirdHouseTier tier = tiers[i];
            if (hunterLevel >= tier.hunter && craftingLevel >= tier.crafting
                && logCounts.getOrDefault(tier.logId, 0) >= LOGS_PER_RUN) return Optional.of(tier);
        }
        return Optional.empty();
    }
}
