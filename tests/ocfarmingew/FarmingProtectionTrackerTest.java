package net.runelite.client.plugins.microbot.ocfarmingew;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarmingProtectionTrackerTest
{
    private final FarmingProtectionTracker tracker = new FarmingProtectionTracker();
    private final FarmingPatchData patch = FarmingPatchData.FALADOR_TREE;
    private final String receipt = "That'll do nicely, sir. Leave it with me - I'll make sure<br>that patch grows for you.";

    @Test void paymentMenuAloneDoesNotSetProtection()
    {
        tracker.select(patch, 1000);
        assertTrue(tracker.snapshot().isEmpty());
        assertFalse(tracker.confirm(patch.region, patch.gardenerId(), "You need a basket of tomatoes", Map.of(patch, observe(8)), 1001));
    }

    @Test void confirmationIsBoundToNpcRegionAndSelectedPatch()
    {
        tracker.select(patch, 1000);
        Map<FarmingPatchData, FarmingObservation> observations = Map.of(patch, observe(8), FarmingPatchData.VARROCK_TREE, observe(8));
        assertFalse(tracker.confirm(10548, 2679, receipt, observations, 1001));
        assertFalse(tracker.confirm(patch.region, 2665, receipt, observations, 1001));
        assertTrue(tracker.confirm(patch.region, 2679, receipt, observations, 1001));
        assertEquals(FarmingObservation.Crop.OAK, tracker.snapshot().get(patch));
        assertNull(tracker.snapshot().get(FarmingPatchData.VARROCK_TREE));
    }

    @Test void expiredSelectionAndEmptyPatchCannotReceiveProtection()
    {
        tracker.select(patch, 1000);
        assertFalse(tracker.confirm(patch.region, 2679, receipt, Map.of(patch, observe(8)), 1091));
        tracker.select(patch, 1100);
        assertFalse(tracker.confirm(patch.region, 2679, receipt, Map.of(patch, observe(3)), 1101));
    }

    @Test void growthKeepsProtectionButNewCycleAndDeathInvalidateIt()
    {
        for (int raw : new int[]{3, 0, 12, 73, 137, 15, 24, 8})
        {
            tracker.record(patch, observe(10));
            tracker.observe(patch, observe(10), observe(raw));
            assertNull(tracker.snapshot().get(patch), "raw=" + raw);
        }
        tracker.record(patch, observe(9));
        tracker.observe(patch, observe(9), observe(10));
        assertEquals(FarmingObservation.Crop.OAK, tracker.snapshot().get(patch));
    }

    @Test void restoreMustMatchTheSavedCropAndProfileClearRemovesProtection()
    {
        tracker.record(patch, observe(8));
        String stored = tracker.encode(patch);
        tracker.clear();
        tracker.restore(patch, stored, observe(15));
        assertTrue(tracker.snapshot().isEmpty());
        tracker.restore(patch, stored, observe(8));
        assertEquals(FarmingObservation.Crop.OAK, tracker.snapshot().get(patch));
        tracker.clear();
        assertTrue(tracker.snapshot().isEmpty());
    }

    private FarmingObservation observe(int raw) { return FarmingObservation.observe(raw, 1000, null, false); }
}
