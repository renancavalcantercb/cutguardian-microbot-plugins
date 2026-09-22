package net.runelite.client.plugins.microbot.ocbirdhouse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BirdHouseTierTest
{
    private static Map<Integer, Integer> logs(int... pairs)
    {
        Map<Integer, Integer> counts = new java.util.HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) counts.put(pairs[i], pairs[i + 1]);
        return counts;
    }
    @Test void picksHighestBuildableTierWithEnoughLogs()
    {
        assertEquals(BirdHouseTier.YEW,
            BirdHouseTier.select(70, 70, logs(1515, 4, 1517, 40)).orElseThrow());
        // Magic needs 74 Hunter; falls back to yew.
        assertEquals(BirdHouseTier.YEW,
            BirdHouseTier.select(70, 99, logs(1513, 40, 1515, 4)).orElseThrow());
        // Only 3 yew logs; falls back to maple.
        assertEquals(BirdHouseTier.MAPLE,
            BirdHouseTier.select(70, 70, logs(1515, 3, 1517, 4)).orElseThrow());
    }
    @Test void lowLevelsStartAtBirdHouse()
    {
        assertEquals(BirdHouseTier.BIRD,
            BirdHouseTier.select(5, 5, logs(1511, 4, 1521, 40)).orElseThrow());
        assertTrue(BirdHouseTier.select(4, 5, logs(1511, 40)).isEmpty());
        assertTrue(BirdHouseTier.select(99, 99, logs(1511, 3)).isEmpty());
    }
    @Test void seedNamesAreAcceptedCaseInsensitively()
    {
        assertTrue(BirdHouseSeeds.isAccepted("Potato seed"));
        assertTrue(BirdHouseSeeds.isAccepted("HAMMERSTONE SEED"));
        assertTrue(!BirdHouseSeeds.isAccepted("Magic seed"));
        assertTrue(!BirdHouseSeeds.isAccepted(null));
        assertEquals(10, BirdHouseSeeds.SEEDS_PER_HOUSE);
        assertEquals(40, BirdHouseSeeds.SEEDS_PER_RUN);
    }
}
