package net.runelite.client.plugins.microbot.ocfarming;

import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarmingApproachTest
{
    private static final WorldPoint OBJECT = new WorldPoint(3051, 3307, 0);
    private static final WorldPoint TARGET = new WorldPoint(3051, 3306, 0);

    @Test void alternateEdgeEndsActualApproachBeforeWalkerReversesDirection()
    {
        FakeGame game = new FakeGame();
        game.approach(FarmingPatchData.FALADOR_TREE, FarmingActionPolicy.Action.PLANT, () -> false);
        assertTrue(game.finishedAtOtherEdge);
    }

    @Test void movingBackAndForthDoesNotResetProgressTimeout()
    {
        FarmingApproachProgress progress = new FarmingApproachProgress(TARGET, TARGET.dx(1), 1000);
        assertFalse(progress.stalled(TARGET.dx(1).dy(1), 1002));
        assertFalse(progress.stalled(TARGET.dx(1), 1004));
        assertTrue(progress.stalled(TARGET.dx(1).dy(1), 1006));
    }

    @Test void lastStepUsesOneCanvasWalkInsteadOfWebWalker()
    {
        FakeGame game = new FakeGame();
        game.localWalk();
        assertEquals(1, game.canvasClicks);
    }

    @Test void gettingCloserResetsTheShortApproachTimeout()
    {
        FarmingApproachProgress progress = new FarmingApproachProgress(TARGET, TARGET.dx(10), 1000);
        assertFalse(progress.stalled(TARGET.dx(12), 1010)); // a distant detour
        assertFalse(progress.stalled(TARGET.dx(2), 1011));
        assertFalse(progress.stalled(TARGET.dx(1), 1016));
        assertFalse(progress.stalled(TARGET.dx(1), 1021));
        assertTrue(progress.stalled(TARGET.dx(1), 1022));
    }

    @Test void distantApproachHandsOverToCanvasBeforeLastTile()
    {
        HandoffGame game = new HandoffGame();
        game.walk(game.destination, 0, () -> game.cancelled);
        assertEquals(1, game.routes);
        assertEquals(1, game.canvasClicks);
        assertEquals(game.destination, game.position);
    }

    @Test void cancellationDuringRouteCannotIssueCanvasClick()
    {
        HandoffGame game = new HandoffGame();
        game.cancelOnArrival = true;
        game.walk(game.destination, 0, () -> game.cancelled);
        assertEquals(1, game.routes);
        assertEquals(0, game.canvasClicks);
    }

    @Test void regionalObservationWithoutLoadedTreeWalksToLoadTheObjectThenItsEdge()
    {
        LoadingTreeGame game = new LoadingTreeGame();
        game.approach(FarmingPatchData.GNOME_TREE, FarmingActionPolicy.Action.PLANT, () -> game.cancelled);
        assertEquals(List.of(3, 0), game.distances);
        assertTrue(game.near(game.snapshot(), FarmingPatchData.GNOME_TREE, FarmingActionPolicy.Action.PLANT));
    }

    @Test void cancellationWhileLoadingTreeCannotStartTheFinalApproach()
    {
        LoadingTreeGame game = new LoadingTreeGame();
        game.cancelOnArrival = true;
        game.approach(FarmingPatchData.GNOME_TREE, FarmingActionPolicy.Action.PLANT, () -> game.cancelled);
        assertEquals(List.of(3), game.distances);
    }

    private static class LoadingTreeGame extends FarmingGameActions
    {
        final WorldPoint edge = new WorldPoint(2435, 3415, 0);
        WorldPoint position = new WorldPoint(2465, 3490, 0);
        boolean loaded, cancelled, cancelOnArrival;
        final java.util.List<Integer> distances = new java.util.ArrayList<>();
        @Override long now() { return 1000; }
        @Override Snapshot snapshot()
        {
            return new Snapshot(new FarmingActionPolicy.Inventory(Map.of(), 28, 15, 100),
                true, false, false, "", position, -1);
        }
        @Override FarmingPatchTarget patchTarget(FarmingPatchData patch)
        {
            return loaded ? FarmingPatchTarget.rectangle(edge.dx(1), 3, 3, Map.of(edge, 0)) : null;
        }
        @Override void walk(WorldPoint destination, int distance, BooleanSupplier finished)
        {
            distances.add(distance);
            assertFalse(finished.getAsBoolean());
            if (distance == 3)
            {
                loaded = true;
                position = edge.dy(6);
                cancelled = cancelOnArrival;
            }
            else position = destination;
            assertTrue(finished.getAsBoolean());
        }
    }

    private static class HandoffGame extends FarmingGameActions
    {
        final WorldPoint destination = new WorldPoint(2671, 3372, 0);
        WorldPoint position = new WorldPoint(2672, 3380, 0);
        int routes, canvasClicks;
        boolean cancelled, cancelOnArrival;
        @Override Snapshot snapshot()
        {
            return new Snapshot(new FarmingActionPolicy.Inventory(Map.of(), 28, 12, 100),
                true, false, false, "", position, -1);
        }
        @Override void walkRoute(WorldPoint target, int distance, BooleanSupplier finished)
        {
            routes++;
            assertFalse(finished.getAsBoolean());
            position = new WorldPoint(2671, 3373, 0);
            cancelled = cancelOnArrival;
            assertTrue(finished.getAsBoolean(), "Web walker must stop before insisting on the final tile");
        }
        @Override boolean walkCanvas(WorldPoint target) { canvasClicks++; position = target; return true; }
        @Override void awaitWalk(BooleanSupplier finished) { }
    }

    private static class FakeGame extends FarmingGameActions
    {
        WorldPoint position = new WorldPoint(3052, 3305, 0);
        boolean finishedAtOtherEdge;
        int canvasClicks;
        void localWalk() { super.walk(TARGET, 0, () -> canvasClicks > 0); }
        @Override boolean walkCanvas(WorldPoint destination) { canvasClicks++; return true; }
        @Override void awaitWalk(BooleanSupplier finished) { assertTrue(finished.getAsBoolean()); }
        @Override long now() { return 1000; }
        @Override Snapshot snapshot()
        {
            return new Snapshot(new FarmingActionPolicy.Inventory(Map.of(), 28, 7, 100),
                true, false, false, "", position, -1);
        }
        @Override FarmingPatchTarget patchTarget(FarmingPatchData patch)
        {
            return FarmingPatchTarget.select(List.of(OBJECT),
                Map.of(TARGET, position.equals(TARGET) ? 0 : 1, position, 0));
        }
        @Override void walk(WorldPoint destination, int distance, BooleanSupplier finished)
        {
            assertEquals(TARGET, destination);
            assertFalse(finished.getAsBoolean());
            position = new WorldPoint(3052, 3307, 0); // adjacent east edge, not selected south edge
            finishedAtOtherEdge = finished.getAsBoolean();
        }
    }
}
