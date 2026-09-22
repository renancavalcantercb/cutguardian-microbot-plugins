package net.runelite.client.plugins.microbot.ocfarmingew;

import net.runelite.api.coords.WorldPoint;

/** Bounds repeated short clicks that move the player without getting closer. */
final class FarmingApproachProgress
{
    private final WorldPoint destination;
    private int bestDistance;
    private long lastProgress;

    FarmingApproachProgress(WorldPoint destination, WorldPoint initial, long now)
    {
        this.destination = destination;
        bestDistance = initial == null ? Integer.MAX_VALUE : initial.distanceTo(destination);
        lastProgress = now;
    }

    boolean stalled(WorldPoint current, long now)
    {
        if (current == null) return true;
        int distance = current.distanceTo(destination);
        if (distance < bestDistance)
        {
            bestDistance = distance;
            lastProgress = now;
        }
        // Longer routes can legitimately detour. This guard concerns the final
        // few tiles, where toggling between positions is not forward progress.
        return distance <= 3 && now - lastProgress >= 6;
    }
}
