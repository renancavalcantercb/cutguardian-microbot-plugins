package net.runelite.client.plugins.microbot.ocbirdhouse;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Island access without a digsite pendant: Efficient Walker to the Digsite
 * barge dock, then the guard's Quick-travel to the Museum Camp. Afterwards
 * the bot lives on the island and resupplies at the built bank chest.
 */
final class BirdHouseTravel
{
    private static final Logger log = LoggerFactory.getLogger(BirdHouseTravel.class);

    /** Digsite pendant ids 1-5 (lowest charges first) plus the full pendant. */
    static final int[] PENDANT_IDS = {11190, 11191, 11192, 11193, 11194, 11195};
    static final String PENDANT_RUB = "Rub";
    static final String PENDANT_FOSSIL = "Fossil Island";
    static final String PENDANT_DIGSITE = "Digsite";
    /** Mushtree tile in Verdant Valley, used for hops to other island sectors. */
    static final WorldPoint VERDANT_MUSHTREE = new WorldPoint(3757, 3757, 0);

    /** Digsite pendant teleport tile, right by the canal barge dock. */
    static final WorldPoint BARGE_APPROACH = new WorldPoint(3343, 3666, 0);
    static final int BARGE_GUARD_BARGE = 7768;
    static final int BARGE_GUARD_POSTQUEST = 8012;
    static final String BARGE_GUARD_NAME = "Barge guard";
    static final String QUICK_TRAVEL = "Quick-travel";

    static final int MUSHTREE_OBJECT_ID = 30924;
    static final String MUSHTREE_WIDGET = "Mycelium Transportation System";
    static final String MUSHTREE_MEADOW = "Mushroom Meadow";

    /** Built bank chest on Fossil Island (21 Construction). */
    static final WorldPoint ISLAND_BANK = new WorldPoint(3766, 3898, 0);

    private final BirdHouseEfficientWalkerBridge walker;

    BirdHouseTravel(BirdHouseEfficientWalkerBridge walker)
    {
        this.walker = walker;
    }

    static boolean onIsland()
    {
        WorldPoint location = Rs2Player.getWorldLocation();
        return location != null && BirdHouseData.onFossilIsland(location.getRegionID());
    }

    static String pos()
    {
        WorldPoint location = Rs2Player.getWorldLocation();
        return location == null ? "unknown" : location.toString();
    }

    /** Wait until the player fully stops; interactions miss while walking. */
    static boolean waitForStop(BooleanSupplier cancelled)
    {
        if (!Rs2Player.isMoving()) return true;
        log.info("Bird House Runner: waiting to stop walking (at {})", pos());
        return waitFor(() -> !Rs2Player.isMoving(), 5_000, cancelled);
    }

    static Optional<Rs2ItemModel> pendant()
    {
        return Rs2Inventory.items()
            .filter(item -> {
                for (int id : PENDANT_IDS)
                {
                    if (item.getId() == id) return true;
                }
                return false;
            })
            .findFirst();
    }

    /**
     * Pendant straight to Fossil Island (House on the Hill, mushtree in
     * front); Digsite + barge is the fallback. No-op when already there.
     */
    boolean goToIsland(BooleanSupplier cancelled)
    {
        log.info("Bird House Runner: goToIsland start (at {})", pos());
        if (onIsland())
        {
            log.info("Bird House Runner: already on Fossil Island");
            return true;
        }
        if (!nearDock())
        {
            Optional<Rs2ItemModel> pendant = pendant();
            if (pendant.isPresent())
            {
                int pendantId = pendant.get().getId();
                log.info("Bird House Runner: rubbing digsite pendant id={} (at {})", pendantId, pos());
                if (Rs2Inventory.interact(pendantId, PENDANT_RUB)
                    && waitFor(Rs2Dialogue::isInDialogue, 5_000, cancelled))
                {
                    if (Rs2Dialogue.hasDialogueOption(PENDANT_FOSSIL)
                        && Rs2Dialogue.clickOption(PENDANT_FOSSIL))
                    {
                        log.info("Bird House Runner: pendant Fossil Island clicked, waiting to land");
                        if (waitFor(BirdHouseTravel::onIsland, 30_000, cancelled))
                        {
                            log.info("Bird House Runner: landed on Fossil Island (at {})", pos());
                            return true;
                        }
                        log.warn("Bird House Runner: direct teleport did not land, trying Digsite+barge");
                    }
                    if (Rs2Dialogue.hasDialogueOption(PENDANT_DIGSITE)
                        && Rs2Dialogue.clickOption(PENDANT_DIGSITE))
                    {
                        log.info("Bird House Runner: pendant Digsite option clicked, waiting to land");
                        waitFor(() -> nearDock() || onIsland(), 15_000, cancelled);
                    }
                    else
                    {
                        log.warn("Bird House Runner: pendant teleport failed, falling back to walking");
                    }
                }
                else
                {
                    log.warn("Bird House Runner: pendant rub gave no dialogue, falling back to walking");
                }
            }
            else
            {
                log.info("Bird House Runner: no digsite pendant, walking to the dock");
            }
        }
        if (!onIsland() && !nearDock())
        {
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(10);
            log.info("Bird House Runner: walking to barge dock {} (at {})", BARGE_APPROACH, pos());
            if (!walker.walkUntil(BARGE_APPROACH, 8, cancelled, deadline) && !nearDock())
            {
                log.warn("Bird House Runner: could not reach the Digsite barge dock (at {})", pos());
                return false;
            }
            log.info("Bird House Runner: reached the dock (at {})", pos());
        }
        if (cancelled.getAsBoolean()) return false;
        waitForStop(cancelled);
        log.info("Bird House Runner: clicking barge guard Quick-travel (at {})", pos());
        if (Rs2Npc.interact(BARGE_GUARD_BARGE, QUICK_TRAVEL)
            || Rs2Npc.interact(BARGE_GUARD_POSTQUEST, QUICK_TRAVEL)
            || Rs2Npc.interact(BARGE_GUARD_NAME, QUICK_TRAVEL))
        {
            log.info("Bird House Runner: barge Quick-travel clicked, waiting for arrival");
        }
        else
        {
            log.warn("Bird House Runner: barge guard not found at the dock (at {})", pos());
            return false;
        }
        if (!waitFor(BirdHouseTravel::onIsland, 30_000, cancelled))
        {
            log.warn("Bird House Runner: barge ride did not land on Fossil Island (at {})", pos());
            return false;
        }
        log.info("Bird House Runner: landed on Fossil Island (at {})", pos());
        return true;
    }

    /**
     * Mushtree hop to Mushroom Meadow. Walks to the Verdant mushtree first so
     * the hop also works straight from the House on the Hill landing.
     */
    boolean mushtreeToMeadow(BooleanSupplier cancelled)
    {
        WorldPoint meadow = new WorldPoint(BirdHouseData.MEADOW_N.visitX, BirdHouseData.MEADOW_N.visitY, 0);
        if (Rs2Player.distanceTo(meadow) < 25) return true;
        if (Rs2Player.distanceTo(VERDANT_MUSHTREE) > 8)
        {
            log.info("Bird House Runner: walking to Verdant mushtree (at {})", pos());
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(3);
            walker.walkUntil(VERDANT_MUSHTREE, 6, cancelled, deadline);
            walker.cancel();
            if (cancelled.getAsBoolean()) return false;
        }
        log.info("Bird House Runner: using mushtree (at {})", pos());
        waitForStop(cancelled);
        Rs2GameObject.interact(MUSHTREE_OBJECT_ID, "Use");
        if (!waitFor(() -> Rs2Widget.findWidget(MUSHTREE_WIDGET) != null, 5_000, cancelled))
        {
            log.warn("Bird House Runner: mushtree widget never opened (at {})", pos());
            return false;
        }
        Rs2Widget.clickWidget(MUSHTREE_MEADOW);
        boolean landed = waitFor(() -> Rs2Player.distanceTo(meadow) < 25, 15_000, cancelled);
        log.info("Bird House Runner: mushtree hop {} (at {})", landed ? "landed" : "failed", pos());
        return landed;
    }

    private boolean nearDock()
    {
        WorldPoint location = Rs2Player.getWorldLocation();
        return location != null && location.distanceTo(BARGE_APPROACH) <= 12;
    }

    static boolean waitFor(BooleanSupplier condition, long timeoutMs, BooleanSupplier cancelled)
    {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline)
        {
            if (cancelled != null && cancelled.getAsBoolean()) return false;
            try
            {
                if (condition.getAsBoolean()) return true;
            }
            catch (Exception ex)
            {
                log.debug("Bird House Runner: wait condition failed", ex);
            }
            Global.sleep(200, 300);
        }
        try
        {
            return condition.getAsBoolean();
        }
        catch (Exception ex)
        {
            return false;
        }
    }
}
