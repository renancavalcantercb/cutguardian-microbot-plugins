package net.runelite.client.plugins.microbot.ocwalkerprobe;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The measurement is pure, so it is checked without a client. If these
 * numbers are wrong the comparison between walkers is worthless.
 */
class OcWalkerProbePluginTest
{
    private static WorldPoint at(int x, int y)
    {
        return new WorldPoint(x, y, 0);
    }

    private static WalkerJourney walk(int... xs)
    {
        WalkerJourney journey = new WalkerJourney(at(xs[0], 0));
        for (int i = 1; i < xs.length; i++)
        {
            journey.record(at(xs[i], 0), true);
        }
        return journey;
    }

    @Test
    void straightLineIsPerfectlyEfficient()
    {
        WalkerJourney journey = walk(0, 1, 2, 3, 4);
        assertEquals(4, journey.displacement());
        assertEquals(4, journey.tilesWalked());
        assertEquals(1.0, journey.efficiency(), 0.001);
        assertEquals(0, journey.wrongWaySteps());
    }

    @Test
    void detourCostsEfficiencyButIsNotWrongWay()
    {
        // Sideways around an obstacle: never moves away from the destination.
        WalkerJourney journey = new WalkerJourney(at(0, 0));
        journey.record(at(0, 1), true);
        journey.record(at(1, 1), true);
        journey.record(at(2, 1), true);
        journey.record(at(2, 0), true);
        assertEquals(2, journey.displacement());
        assertEquals(4, journey.tilesWalked());
        assertEquals(2.0, journey.efficiency(), 0.001);
    }

    @Test
    void backtrackingCountsAsWrongWay()
    {
        // Out to 3, back to 1, out to 4: two steps move away from the end tile.
        WalkerJourney journey = walk(0, 1, 2, 3, 2, 1, 2, 3, 4);
        assertEquals(4, journey.displacement());
        assertEquals(8, journey.tilesWalked());
        assertEquals(2, journey.wrongWaySteps());
        assertTrue(journey.efficiency() > 1.9);
    }

    @Test
    void standingStillIsIdleNotAStep()
    {
        WalkerJourney journey = new WalkerJourney(at(0, 0));
        assertTrue(journey.record(at(1, 0), true));
        assertFalse(journey.record(at(1, 0), true));
        assertFalse(journey.record(at(1, 0), true));
        assertTrue(journey.record(at(2, 0), true));
        assertEquals(2, journey.idleTicks());
        assertEquals(2, journey.longestStall());
        assertEquals(2, journey.steps());
        assertEquals(4, journey.ticks());
    }

    @Test
    void longestStallKeepsTheWorstGapNotTheLast()
    {
        WalkerJourney journey = new WalkerJourney(at(0, 0));
        journey.record(at(1, 0), true);
        journey.record(at(1, 0), true);
        journey.record(at(1, 0), true);
        journey.record(at(1, 0), true);
        journey.record(at(2, 0), true);
        journey.record(at(2, 0), true);
        assertEquals(3, journey.longestStall());
    }

    @Test
    void runningPercentTracksPace()
    {
        WalkerJourney journey = new WalkerJourney(at(0, 0));
        journey.record(at(1, 0), true);
        journey.record(at(2, 0), false);
        journey.record(at(3, 0), false);
        journey.record(at(4, 0), false);
        assertEquals(25, journey.runningPercent());
    }

    @Test
    void ordinaryStepsAreWalkSteps()
    {
        assertTrue(WalkerJourney.isWalkStep(at(0, 0), at(1, 0)));
        assertTrue(WalkerJourney.isWalkStep(at(0, 0), at(2, 0)));
        assertTrue(WalkerJourney.isWalkStep(at(0, 0), at(0, 0)));
    }

    @Test
    void aTeleportIsNotAWalkStep()
    {
        // The 1466-tile "trip" that logged 63 of 76 steps as wrong-way.
        assertFalse(WalkerJourney.isWalkStep(at(3200, 3200), at(2800, 3400)));
        assertFalse(WalkerJourney.isWalkStep(at(0, 0), at(6, 0)));
    }

    @Test
    void aPlaneChangeIsNotAWalkStep()
    {
        assertFalse(WalkerJourney.isWalkStep(new WorldPoint(0, 0, 0), new WorldPoint(0, 0, 1)));
    }

    @Test
    void aMissingPositionIsNotAWalkStep()
    {
        assertFalse(WalkerJourney.isWalkStep(null, at(0, 0)));
        assertFalse(WalkerJourney.isWalkStep(at(0, 0), null));
    }

    @Test
    void aJourneyThatEndsWhereItStartedHasNoEfficiency()
    {
        WalkerJourney journey = walk(0, 1, 2, 1, 0);
        assertEquals(0, journey.displacement());
        assertEquals(0.0, journey.efficiency(), 0.001);
    }
}
