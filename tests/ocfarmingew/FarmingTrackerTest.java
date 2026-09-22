package net.runelite.client.plugins.microbot.ocfarmingew;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarmingTrackerTest
{
    private FarmingObservation observe(int raw, long time) { return FarmingObservation.observe(raw, time, null, false); }
    @Test void observingGrowthNarrowsTheWindowButGapsResetCalibration()
    {
        FarmingObservation a = observe(8, 1000);
        FarmingObservation b = FarmingObservation.observe(9, 1001, a, true);
        assertEquals(8200, b.earliestReadyAt);
        assertEquals(8201, b.latestReadyAt);
        FarmingObservation gap = FarmingObservation.observe(9, 1500, b, true);
        assertEquals(6300, gap.earliestReadyAt);
        assertEquals(8700, gap.latestReadyAt);
    }
    @Test void replantingAndPruningStartFreshEstimates()
    {
        FarmingObservation replanted = FarmingObservation.observe(8, 1001, observe(11, 1000), true);
        assertEquals(10601, replanted.latestReadyAt);
        FarmingObservation pruned = FarmingObservation.observe(9, 1001, observe(73, 1000), true);
        assertEquals(8201, pruned.latestReadyAt);
    }
    @Test void changingTreeDiscardsThePreviousCalibration()
    {
        FarmingObservation oak = FarmingObservation.observe(9, 1001, observe(8, 1000), true);
        FarmingObservation willow = FarmingObservation.observe(16, 1002, oak, true);
        assertEquals(10602, willow.earliestReadyAt);
        assertEquals(13002, willow.latestReadyAt);
    }
    @Test void invalidAndLegacyDataAreRejected()
    {
        for (String value : new String[]{"", "bad", "1:8:1000:8200:10600", "T1:8:1001:8200:10600",
            "T1:256:1000:0:0", "T1:3:0:0:0", "T1:8:1000:0:10600", "T1:8:1000:10600:8200",
            "T1:8:1000:8200:999999", "T1:73:1000:2000:3000"})
            assertNull(FarmingObservation.decode(value, 1000), value);
    }
    @Test void expiredHistoryPreservesObservedGrowth()
    {
        FarmingObservation restored = FarmingObservation.decode(observe(8, 1000).encode(), 20000);
        assertNotNull(restored);
        assertEquals(10600, restored.latestReadyAt);
        assertTrue(restored.isDue(20000));
        assertEquals(FarmingObservation.State.GROWING, restored.state());
    }
    @Test void profilesAndPatchSnapshotsStayIndependent()
    {
        FarmingTracker tracker = new FarmingTracker();
        FarmingPatchData lumbridge = FarmingPatchData.LUMBRIDGE_TREE, varrock = FarmingPatchData.VARROCK_TREE;
        assertFalse(tracker.observe(lumbridge, 8, 1000, false));
        tracker.switchProfile("one");
        tracker.observe(lumbridge, 8, 1000, false);
        tracker.observe(varrock, 12, 1000, false);
        Map<FarmingPatchData, FarmingObservation> previous = tracker.snapshot();
        tracker.observe(varrock, 3, 1001, true);
        assertEquals(12, previous.get(varrock).raw);
        assertEquals(8, tracker.snapshot().get(lumbridge).raw);
        String saved = previous.get(lumbridge).encode();
        tracker.switchProfile("two");
        assertTrue(tracker.snapshot().isEmpty());
        tracker.restore(lumbridge, saved, 2000);
        assertEquals(8, tracker.snapshot().get(lumbridge).raw);
    }
    @Test void samplingWaitsForStableRegionAndResetsAfterModalOrLoading()
    {
        FarmingSamplingGate gate = new FarmingSamplingGate();
        assertFalse(gate.accept(12594, true));
        assertFalse(gate.accept(12594, true));
        assertTrue(gate.accept(12594, true));
        assertFalse(gate.accept(12854, true));
        assertFalse(gate.accept(12854, true));
        assertTrue(gate.accept(12854, true));
        assertFalse(gate.accept(12854, false));
        assertFalse(gate.accept(12854, true));
        assertFalse(gate.accept(12854, true));
        assertTrue(gate.accept(12854, true));
        gate.reset();
        assertFalse(gate.accept(12854, true));
    }
}
