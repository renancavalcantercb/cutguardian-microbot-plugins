package net.runelite.client.plugins.microbot.ocfarmingew;

import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dynamic bridge to the Efficient Walker plugin.
 *
 * Uses reflection so this plugin compiles cleanly without hard compile-time
 * coupling to a specific versioned/obfuscated Efficient Walker jar, while
 * providing seamless access to Efficient Walker's teleports, fairy rings,
 * shortcuts and multi-floor pathfinding.
 */
public class FarmingEfficientWalkerBridge
{
    private static final Logger log = LoggerFactory.getLogger(FarmingEfficientWalkerBridge.class);
    private static final String EW_PLUGIN_CLASS = "net.runelite.client.plugins.microbot.efficientwalker.EfficientWalkerPlugin";

    private Object walkerInstance;
    private Method walkToMethod;
    private Method walkToNearestBankMethod;
    private Method cancelMethod;
    private Method getStatusMethod;
    private Method getDestinationMethod;
    private Method getPlanningFailureMethod;
    private boolean initialized;

    public boolean isAvailable()
    {
        init();
        return walkerInstance != null;
    }

    private synchronized void init()
    {
        if (initialized && walkerInstance != null)
        {
            return;
        }
        try
        {
            Plugin ewPlugin = Microbot.getPlugin(EW_PLUGIN_CLASS);
            if (ewPlugin == null && Microbot.getPluginManager() != null)
            {
                for (Plugin p : Microbot.getPluginManager().getPlugins())
                {
                    if (p.getClass().getName().equals(EW_PLUGIN_CLASS))
                    {
                        ewPlugin = p;
                        break;
                    }
                }
            }
            if (ewPlugin != null && Microbot.getPluginManager().isPluginEnabled(ewPlugin))
            {
                Method getWalker = ewPlugin.getClass().getMethod("getWalker");
                walkerInstance = getWalker.invoke(ewPlugin);
                if (walkerInstance != null)
                {
                    walkToMethod = walkerInstance.getClass().getMethod("walkTo", WorldPoint.class);
                    walkToNearestBankMethod = walkerInstance.getClass().getMethod("walkToNearestBank");
                    cancelMethod = walkerInstance.getClass().getMethod("cancel");
                    getStatusMethod = walkerInstance.getClass().getMethod("getStatus");
                    getDestinationMethod = walkerInstance.getClass().getMethod("getDestination");
                    getPlanningFailureMethod = walkerInstance.getClass().getMethod("getPlanningFailure");
                    initialized = true;
                    log.info("Farming Runner EW: Successfully connected to Efficient Walker");
                }
            }
        }
        catch (Exception ex)
        {
            log.warn("Farming Runner EW: Failed to connect to Efficient Walker: {}", ex.getMessage());
            walkerInstance = null;
        }
    }

    public boolean walkTo(WorldPoint destination)
    {
        init();
        if (walkerInstance != null && walkToMethod != null)
        {
            try
            {
                return invokeOnClientThread(walkToMethod, destination);
            }
            catch (Exception ex)
            {
                log.warn("Farming Runner EW: Error calling walkTo on Efficient Walker", ex);
            }
        }
        log.info("Farming Runner EW: Efficient Walker unavailable, falling back to Rs2Walker");
        Rs2Walker.walkTo(destination);
        return true;
    }

    public boolean walkToNearestBank()
    {
        init();
        if (walkerInstance != null && walkToNearestBankMethod != null)
        {
            try
            {
                return invokeOnClientThread(walkToNearestBankMethod);
            }
            catch (Exception ex)
            {
                log.warn("Farming Runner EW: Error calling walkToNearestBank on Efficient Walker", ex);
            }
        }
        return false;
    }

    /**
     * Efficient Walker reads live client state while accepting a destination.
     * Farming visits run on their own executor, so marshal only the short request
     * into RuneLite's client thread; route execution remains asynchronous.
     */
    private boolean invokeOnClientThread(Method method, Object... arguments)
    {
        return Microbot.getClientThread().invoke((java.util.function.Supplier<Boolean>) () -> {
            try
            {
                return Boolean.TRUE.equals(method.invoke(walkerInstance, arguments));
            }
            catch (ReflectiveOperationException ex)
            {
                throw new IllegalStateException("Efficient Walker invocation failed", ex);
            }
        });
    }

    public void cancel()
    {
        if (walkerInstance != null && cancelMethod != null)
        {
            try
            {
                cancelMethod.invoke(walkerInstance);
            }
            catch (Exception ex)
            {
                log.warn("Farming Runner EW: Error calling cancel on Efficient Walker", ex);
            }
        }
    }

    public String getStatus()
    {
        if (walkerInstance != null && getStatusMethod != null)
        {
            try
            {
                Object status = getStatusMethod.invoke(walkerInstance);
                return status != null ? status.toString() : "IDLE";
            }
            catch (Exception ex)
            {
                return "ERROR";
            }
        }
        return isAvailable() ? "IDLE" : "UNAVAILABLE";
    }

    public String getPlanningFailure()
    {
        if (walkerInstance != null && getPlanningFailureMethod != null)
        {
            try
            {
                Object failure = getPlanningFailureMethod.invoke(walkerInstance);
                return failure != null ? failure.toString() : "NONE";
            }
            catch (Exception ex)
            {
                return "NONE";
            }
        }
        return "NONE";
    }

    /**
     * Walks to destination using Efficient Walker while checking for arrival,
     * external cancellation, or destination confirmation.
     * Falls back to Rs2Walker if Efficient Walker cannot route or is blocked.
     */
    public boolean walkUntil(WorldPoint destination, int distance, BooleanSupplier finished, long deadlineNanos)
    {
        init();
        if (walkerInstance == null)
        {
            return Rs2Walker.walkUntil(destination, distance, finished);
        }

        try
        {
            boolean started = walkTo(destination);
            if (!started)
            {
                String failure = getPlanningFailure();
                log.warn("Farming Runner EW: Efficient Walker could not start route to {} (failure: {}), falling back to Rs2Walker", destination, failure);
                return Rs2Walker.walkUntil(destination, distance, finished);
            }

            while (System.nanoTime() < deadlineNanos && !finished.getAsBoolean())
            {
                WorldPoint playerLoc = Rs2Player.getWorldLocation();
                if (playerLoc != null && playerLoc.getPlane() == destination.getPlane() && playerLoc.distanceTo(destination) <= distance)
                {
                    cancel();
                    return true;
                }

                String status = getStatus();
                if ("ARRIVED".equalsIgnoreCase(status))
                {
                    return true;
                }
                if ("BLOCKED".equalsIgnoreCase(status))
                {
                    String failure = getPlanningFailure();
                    log.warn("Farming Runner EW: Efficient Walker reported BLOCKED to {} (failure: {}), falling back to Rs2Walker", destination, failure);
                    cancel();
                    return Rs2Walker.walkUntil(destination, distance, finished);
                }

                Global.sleep(200, 300);
            }

            if (finished.getAsBoolean())
            {
                cancel();
                return true;
            }

            cancel();
            log.warn("Farming Runner EW: Efficient Walker did not arrive at {} before deadline, falling back to Rs2Walker", destination);
            return Rs2Walker.walkUntil(destination, distance, finished);
        }
        catch (Exception ex)
        {
            log.warn("Farming Runner EW: Exception during walkUntil with EW, falling back to Rs2Walker", ex);
            cancel();
            return Rs2Walker.walkUntil(destination, distance, finished);
        }
    }
}
