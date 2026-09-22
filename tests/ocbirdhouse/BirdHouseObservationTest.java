package net.runelite.client.plugins.microbot.ocbirdhouse;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BirdHouseObservationTest
{
    @Test void stateDecodingMatchesTimeTracking()
    {
        assertEquals(BirdHouseObservation.State.EMPTY, BirdHouseObservation.observe(0, 1000).state());
        assertEquals(BirdHouseObservation.State.BUILT, BirdHouseObservation.observe(1, 1000).state());
        assertEquals(BirdHouseObservation.State.BUILT, BirdHouseObservation.observe(2, 1000).state());
        assertEquals(BirdHouseObservation.State.SEEDED, BirdHouseObservation.observe(3, 1000).state());
        assertEquals(BirdHouseObservation.State.SEEDED, BirdHouseObservation.observe(12, 1000).state());
        assertEquals(BirdHouseObservation.State.UNKNOWN, BirdHouseObservation.observe(13, 1000).state());
    }
    @Test void seededHousesMatureAfterFiftyMinutes()
    {
        BirdHouseObservation seeded = BirdHouseObservation.observe(3, 1000);
        assertEquals(4000, seeded.readyAt);
        assertFalse(seeded.needsWork(3999));
        assertTrue(seeded.needsWork(4000));
        assertTrue(BirdHouseObservation.observe(0, 1000).needsWork(1000));
        assertTrue(BirdHouseObservation.observe(1, 1000).needsWork(1000));
    }
    @Test void encodeDecodeRoundTrip()
    {
        BirdHouseObservation seeded = BirdHouseObservation.observe(6, 1000);
        BirdHouseObservation restored = BirdHouseObservation.decode(seeded.encode(), 5000);
        assertNotNull(restored);
        assertEquals(6, restored.raw);
        assertEquals(1000, restored.observedAt);
        assertEquals(4000, restored.readyAt);
        assertTrue(restored.needsWork(5000));
    }
    @Test void invalidAndLegacyDataAreRejected()
    {
        for (String value : new String[]{"", "bad", "1:3:1000:4000", "B1:3:1000", "B1:13:1000:0",
            "B1:-1:1000:0", "B1:3:0:3000", "B1:3:1000:0", "B1:3:1000:9999", "B1:0:1000:1000",
            "B1:1:1000:1000", "T1:8:1000:8200:10600"})
            assertNull(BirdHouseObservation.decode(value, 2000), value);
    }
}
