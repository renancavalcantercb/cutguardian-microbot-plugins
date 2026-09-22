package net.runelite.client.plugins.microbot.ocfarmingew;

import java.util.Collection;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;

/** Chooses a reachable edge outside the full footprint of a farming patch. */
final class FarmingPatchTarget
{
    final WorldPoint objectTile;
    final WorldPoint standingTile;

    private FarmingPatchTarget(WorldPoint objectTile, WorldPoint standingTile)
    {
        this.objectTile = objectTile;
        this.standingTile = standingTile;
    }

    static FarmingPatchTarget select(Collection<WorldPoint> objects, Map<WorldPoint, Integer> reachable)
    {
        FarmingPatchTarget best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (WorldPoint tile : objects)
        {
            for (WorldPoint neighbor : new WorldPoint[] { tile.dx(1), tile.dx(-1), tile.dy(1), tile.dy(-1) })
            {
                // Exclude interior tiles of the patch footprint.
                // Also require a route to the standing tile, not just clear ground.
                if (objects.contains(neighbor)) continue;
                Integer distance = reachable.get(neighbor);
                if (distance != null && distance < bestDistance)
                {
                    bestDistance = distance;
                    best = new FarmingPatchTarget(tile, neighbor);
                }
            }
        }
        return best;
    }

    static FarmingPatchTarget rectangle(WorldPoint origin, int width, int height, Map<WorldPoint, Integer> reachable)
    {
        java.util.List<WorldPoint> occupied = new java.util.ArrayList<>();
        for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) occupied.add(origin.dx(x).dy(y));
        FarmingPatchTarget edge = select(occupied, reachable);
        return edge == null ? null : new FarmingPatchTarget(origin, edge.standingTile);
    }

    static boolean usesPatch(FarmingActionPolicy.Action action)
    {
        switch (action)
        {
            case RAKE: case CLEAR: case PLANT:
            case CHECK_HEALTH: case PRUNE: case HARVEST: return true;
            default: return false;
        }
    }

    static boolean cannotReach(String message)
    {
        return message != null && message.toLowerCase(java.util.Locale.ROOT)
            .replace('\u2019', '\'').contains("can't reach that");
    }
}
