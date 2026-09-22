package net.runelite.client.plugins.microbot.ocbirdhouse;

import java.util.function.BooleanSupplier;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Locomotion facade. The web walker (Rs2Walker) owns every route; this class
 * only adds arrival deadlines and keeps a single seam for the runner, travel
 * and future walker swaps. All calls are synchronous and abort through the
 * given cancellation conditions.
 */
public class BirdHouseEfficientWalkerBridge
{
    private static final Logger log = LoggerFactory.getLogger(BirdHouseEfficientWalkerBridge.class);

    public boolean isAvailable()
    {
        return true;
    }

    public boolean walkTo(WorldPoint destination)
    {
        log.info("Bird House Runner: web walker to {}", destination);
        return Rs2Walker.walkTo(destination);
    }

    public boolean walkToNearestBank()
    {
        WorldPoint bank = Rs2Bank.getNearestBank().getWorldPoint();
        log.info("Bird House Runner: web walker to nearest bank {}", bank);
        return Rs2Walker.walkTo(bank);
    }

    public void cancel()
    {
        try
        {
            Rs2Walker.setTarget(null, "Bird House Runner cancel");
        }
        catch (Exception ex)
        {
            log.debug("Bird House Runner: walker cancel ignored", ex);
        }
    }

    public String getStatus()
    {
        return "WEBWALKER";
    }

    public String getPlanningFailure()
    {
        return "NONE";
    }

    /**
     * Walks to destination, stopping on arrival, external cancellation or the
     * deadline. Returns true when the destination was reached.
     */
    public boolean walkUntil(WorldPoint destination, int distance, BooleanSupplier finished, long deadlineNanos)
    {
        log.info("Bird House Runner: web walker to {} (dist {})", destination, distance);
        return Rs2Walker.walkUntil(destination, distance,
            () -> finished.getAsBoolean() || System.nanoTime() >= deadlineNanos);
    }
}
