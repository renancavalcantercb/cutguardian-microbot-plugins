package net.runelite.client.plugins.microbot.ocwalkerprobe;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.coords.WorldPoint;

/**
 * One continuous stretch of movement, measured after the fact.
 *
 * The probe never knows where the player was heading — it does not read the
 * walker's destination, on purpose, so the same numbers come out whichever
 * walker (or human) is moving the player. It does not need the destination:
 * once the journey ends, the last tile IS the destination, so efficiency and
 * wrong-way steps are computed backwards over the recorded positions.
 *
 * Pure: no client access, so the maths is unit tested without a game.
 */
public final class WalkerJourney
{
    /**
     * Running covers at most 2 tiles per tick. Anything beyond this is a
     * teleport, a plane change or a fresh login — not a step.
     *
     * Measuring across one of those is meaningless: the route went through a
     * teleport, so every walking step before it reads as "moving away" from
     * the final tile in straight-line terms. A 1466-tile trip logged 63 of 76
     * steps as wrong-way for exactly that reason.
     */
    public static final int MAX_WALK_STEP = 5;

    /** False when the pair cannot be two consecutive walking positions. */
    public static boolean isWalkStep(WorldPoint from, WorldPoint to)
    {
        if (from == null || to == null || from.getPlane() != to.getPlane())
        {
            return false;
        }
        int distance = from.distanceTo(to);
        return distance >= 0 && distance <= MAX_WALK_STEP;
    }

    private final List<WorldPoint> positions = new ArrayList<>();
    private int ticks;
    private int idleTicks;
    private int currentStall;
    private int longestStall;
    private int tilesWalked;
    private int runningTicks;

    public WalkerJourney(WorldPoint start)
    {
        positions.add(start);
    }

    /** Returns true when the player actually changed tile this tick. */
    public boolean record(WorldPoint point, boolean running)
    {
        ticks++;
        if (running)
        {
            runningTicks++;
        }
        WorldPoint previous = last();
        if (point.equals(previous))
        {
            idleTicks++;
            currentStall++;
            if (currentStall > longestStall)
            {
                longestStall = currentStall;
            }
            return false;
        }
        tilesWalked += previous.distanceTo(point);
        positions.add(point);
        currentStall = 0;
        return true;
    }

    public WorldPoint last() { return positions.get(positions.size() - 1); }
    public WorldPoint start() { return positions.get(0); }
    public int ticks() { return ticks; }
    public int idleTicks() { return idleTicks; }
    public int longestStall() { return longestStall; }
    public int tilesWalked() { return tilesWalked; }
    public int steps() { return positions.size() - 1; }
    public int displacement() { return start().distanceTo(last()); }

    /** Share of the journey spent with run enabled, so pace never skews a comparison. */
    public int runningPercent()
    {
        return ticks == 0 ? 0 : (runningTicks * 100) / ticks;
    }

    /**
     * Tiles walked per tile of progress. 1.00 is a straight line; 1.60 means
     * 60% of the distance was detour. Obstacles make some detour legitimate,
     * so compare runs of the same route rather than reading it as absolute.
     */
    public double efficiency()
    {
        int displacement = displacement();
        return displacement == 0 ? 0 : (double) tilesWalked / displacement;
    }

    /**
     * Steps that ended further from the final tile than they started.
     *
     * This is the bounce FarmingApproachProgress exists to bound. A walker
     * that never reverses does not need that code.
     */
    public int wrongWaySteps()
    {
        WorldPoint destination = last();
        int wrong = 0;
        for (int i = 1; i < positions.size(); i++)
        {
            int before = positions.get(i - 1).distanceTo(destination);
            int after = positions.get(i).distanceTo(destination);
            if (after > before)
            {
                wrong++;
            }
        }
        return wrong;
    }

    public String summary()
    {
        return String.format("%d tiles in %dt | eff %.2f | idle %dt (longest %dt) | wrong-way %d/%d | run %d%%",
            displacement(), ticks, efficiency(), idleTicks, longestStall, wrongWaySteps(), steps(), runningPercent());
    }
}
