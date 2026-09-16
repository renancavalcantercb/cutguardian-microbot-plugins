package net.runelite.client.plugins.microbot.ocfarming;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarmingVisitPlannerTest
{
    private final FarmingVisitPlanner planner = new FarmingVisitPlanner();
    private final Map<FarmingPatchData, FarmingObservation> patches = new EnumMap<>(FarmingPatchData.class);
    private final FarmingPatchData patch = FarmingPatchData.LUMBRIDGE_TREE;
    private FarmingObservation observe(int raw, long time) { return FarmingObservation.observe(raw, time, null, false); }
    @Test void tendingVisitsKnownWorkWithoutTreatingUnknownValuesAsEmpty()
    {
        assertNull(planner.next(patches, 2000, true));
        for (int raw : new int[]{0, 3, 12, 13, 14, 73, 137})
        {
            patches.put(patch, observe(raw, 1000));
            assertEquals(patch, planner.next(patches, 2000, true));
            assertNull(planner.next(patches, 2000, false));
        }
        patches.put(patch, observe(255, 1000));
        assertNull(planner.next(patches, 2000, true));
    }
    @Test void onlyExpiredGrowingObservationsRequestMonitorVisits()
    {
        patches.put(patch, observe(8, 1000));
        assertNull(planner.next(patches, 10599));
        assertEquals(patch, planner.next(patches, 10600));
        assertEquals(FarmingObservation.State.GROWING, patches.get(patch).state());
    }
    @Test void oldestEstimateWinsAndFailuresBackOff()
    {
        patches.put(patch, observe(8, 1000));
        patches.put(FarmingPatchData.VARROCK_TREE, observe(8, 1100));
        assertEquals(patch, planner.next(patches, 11000));
        planner.failed(patch, 11000);
        assertEquals(FarmingPatchData.VARROCK_TREE, planner.next(patches, 11299));
        assertEquals(patch, planner.next(patches, 11300));
        planner.reset();
        assertEquals(patch, planner.next(patches, 11001));
    }
    @Test void confirmationRequiresFreshObservationInTheCorrectRegion()
    {
        patches.put(patch, observe(8, 1000));
        assertFalse(FarmingVisitPlanner.confirmed(patch, 1000, patch.region, patches));
        patches.put(patch, observe(12, 11000));
        assertFalse(FarmingVisitPlanner.confirmed(patch, 1000, 12854, patches));
        assertTrue(FarmingVisitPlanner.confirmed(patch, 1000, patch.region, patches));
    }
    @Test void revisitingGrowingTreeRecalculatesTheEstimate()
    {
        patches.put(patch, observe(8, 1000));
        assertEquals(patch, planner.next(patches, 11000));
        patches.put(patch, observe(11, 11000));
        assertNull(planner.next(patches, 11001));
        assertEquals(patch, planner.next(patches, 13400));
    }
}
