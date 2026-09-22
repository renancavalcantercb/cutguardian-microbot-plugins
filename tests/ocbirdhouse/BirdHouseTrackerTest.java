package net.runelite.client.plugins.microbot.ocbirdhouse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BirdHouseTrackerTest
{
    @Test void reobservingTheSameVarpDoesNotRestartTheClock()
    {
        BirdHouseTracker tracker = new BirdHouseTracker();
        tracker.switchProfile("p");
        assertTrue(tracker.observe(BirdHouseData.VERDANT_SW, 3, 1000));
        assertFalse(tracker.observe(BirdHouseData.VERDANT_SW, 3, 2000));
        BirdHouseObservation observation = tracker.snapshot().get(BirdHouseData.VERDANT_SW);
        assertEquals(1000, observation.observedAt);
        assertEquals(4000, observation.readyAt);
        assertTrue(tracker.observe(BirdHouseData.VERDANT_SW, 0, 5000));
        assertEquals(0, tracker.snapshot().get(BirdHouseData.VERDANT_SW).raw);
    }
    @Test void plannerVisitsUndiscoveredThenOldestDue()
    {
        BirdHouseVisitPlanner planner = new BirdHouseVisitPlanner();
        BirdHouseTracker tracker = new BirdHouseTracker();
        tracker.switchProfile("p");
        assertEquals(BirdHouseData.VERDANT_SW, planner.next(tracker.snapshot(), 1000));
        tracker.observe(BirdHouseData.VERDANT_SW, 3, 1000);
        tracker.observe(BirdHouseData.VERDANT_NE, 3, 1100);
        tracker.observe(BirdHouseData.MEADOW_N, 3, 1200);
        tracker.observe(BirdHouseData.MEADOW_S, 3, 1300);
        // Growing houses are skipped while nothing is due.
        assertNull(planner.next(tracker.snapshot(), 1500));
        tracker.observe(BirdHouseData.VERDANT_NE, 0, 1500);
        assertEquals(BirdHouseData.VERDANT_NE, planner.next(tracker.snapshot(), 1500));
        tracker.observe(BirdHouseData.VERDANT_NE, 3, 1600);
        // Due houses go by oldest ready time.
        assertEquals(BirdHouseData.VERDANT_SW, planner.next(tracker.snapshot(), 5000));
        planner.failed(BirdHouseData.VERDANT_SW, 5000);
        assertEquals(BirdHouseData.MEADOW_N, planner.next(tracker.snapshot(), 5000));
    }
    @Test void loginSyncGuardKeepsSeededHistory()
    {
        BirdHouseTracker tracker = new BirdHouseTracker();
        tracker.switchProfile("p");
        Map<BirdHouseData, Integer> seeded = new java.util.EnumMap<>(BirdHouseData.class);
        for (BirdHouseData house : BirdHouseData.values()) seeded.put(house, 3);
        assertTrue(tracker.observeAll(seeded, 1000));
        // Varps not transmitted yet after login: all read 0, update ignored.
        Map<BirdHouseData, Integer> blank = new java.util.EnumMap<>(BirdHouseData.class);
        for (BirdHouseData house : BirdHouseData.values()) blank.put(house, 0);
        assertTrue(tracker.syncedOut(blank));
        assertFalse(tracker.observeAll(blank, 2000));
        assertEquals(3, tracker.snapshot().get(BirdHouseData.VERDANT_SW).raw);
        // Sequential single flips (a real run) are still recorded.
        Map<BirdHouseData, Integer> one = new java.util.EnumMap<>(seeded);
        one.put(BirdHouseData.VERDANT_SW, 0);
        assertFalse(tracker.syncedOut(one));
        assertTrue(tracker.observeAll(one, 3000));
        assertEquals(0, tracker.snapshot().get(BirdHouseData.VERDANT_SW).raw);
        assertEquals(3, tracker.snapshot().get(BirdHouseData.VERDANT_NE).raw);
    }
    @Test void confirmationRequiresAFreshObservation()
    {
        BirdHouseTracker tracker = new BirdHouseTracker();
        tracker.switchProfile("p");
        tracker.observe(BirdHouseData.MEADOW_S, 1, 1000);
        Map<BirdHouseData, BirdHouseObservation> view = tracker.snapshot();
        assertFalse(BirdHouseVisitPlanner.confirmed(BirdHouseData.MEADOW_S, 1000, view));
        tracker.observe(BirdHouseData.MEADOW_S, 2, 2000);
        assertTrue(BirdHouseVisitPlanner.confirmed(BirdHouseData.MEADOW_S, 1000, tracker.snapshot()));
    }
    @Test void profilesStayIndependent()
    {
        BirdHouseTracker tracker = new BirdHouseTracker();
        tracker.switchProfile("a");
        tracker.observe(BirdHouseData.VERDANT_SW, 3, 1000);
        tracker.switchProfile("b");
        assertTrue(tracker.snapshot().isEmpty());
        tracker.restore(BirdHouseData.VERDANT_SW, "B1:3:1000:4000", 2000);
        assertEquals(3, tracker.snapshot().get(BirdHouseData.VERDANT_SW).raw);
        assertNull(BirdHouseObservation.decode("B1:3:3000:6000", 2000));
    }
}
