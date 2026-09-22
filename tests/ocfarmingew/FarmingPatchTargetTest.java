package net.runelite.client.plugins.microbot.ocfarmingew;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarmingPatchTargetTest
{
    @Test void skipsInteriorAndDiagonalTilesInFavorOfAdjacentEdge()
    {
        List<WorldPoint> objects = new ArrayList<>();
        // Put the interior first, as an ID-only nearest query can do on a distance tie.
        objects.add(new WorldPoint(2663, 3371, 0));
        for (int x = 2662; x <= 2664; x++) for (int y = 3370; y <= 3372; y++)
            objects.add(new WorldPoint(x, y, 0));
        WorldPoint player = new WorldPoint(2663, 3373, 0);
        FarmingPatchTarget target = FarmingPatchTarget.select(objects, Map.of(player, 0, player.dx(1), 1));
        assertNotNull(target);
        assertEquals(new WorldPoint(2663, 3372, 0), target.objectTile);
        assertEquals(player, target.standingTile);
    }

    @Test void choosesReachableStandingTileEvenWhenOtherEdgeLooksCloser()
    {
        WorldPoint object = new WorldPoint(2663, 3371, 0);
        FarmingPatchTarget target = FarmingPatchTarget.select(List.of(object), Map.of(object.dx(1), 8, object.dy(-1), 3));
        assertEquals(object.dy(-1), target.standingTile);
        assertNull(FarmingPatchTarget.select(List.of(object), Map.of(object.dx(1).dy(1), 0)));
        assertNull(FarmingPatchTarget.select(List.of(object), Map.of()));
    }
}
