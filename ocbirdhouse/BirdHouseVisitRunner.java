package net.runelite.client.plugins.microbot.ocbirdhouse;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Navigation runs off the client thread; confirmation uses immutable tracker views. */
final class BirdHouseVisitRunner
{
    private static final Logger log = LoggerFactory.getLogger(BirdHouseVisitRunner.class);
    private static final List<Integer> NEST_IDS = Arrays.asList(
        ItemID.BIRD_NEST_EGG_RED,
        ItemID.BIRD_NEST_EGG_GREEN,
        ItemID.BIRD_NEST_EGG_BLUE,
        ItemID.BIRD_NEST_SEEDS,
        ItemID.BIRD_NEST_RING,
        ItemID.BIRD_NEST_SEEDS_JAN2019,
        ItemID.BIRD_NEST_DECENTSEEDS_JAN2019);

    private final OcBirdhousePlugin plugin;
    private final OcBirdhouseConfig config;
    private final BirdHouseVisitPlanner planner = new BirdHouseVisitPlanner();
    private final BirdHouseEfficientWalkerBridge walker = new BirdHouseEfficientWalkerBridge();
    private final BirdHouseTravel travel = new BirdHouseTravel(walker);
    private final BirdHouseActions actions = new BirdHouseActions();
    private final BirdHouseSupplies supplies = new BirdHouseSupplies();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "BirdHouseRunner-visits");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean running = true;
    private volatile String status = "Waiting for due houses";
    private String lastLoggedStatus = "";
    private WorldPoint bankTarget;
    private boolean bankRouting;
    private boolean bankArrived;
    private long bankDeadline;
    private BirdHouseData target;
    private long session = -1;
    private long previousObservedAt;
    private long confirmationDeadline;

    BirdHouseVisitRunner(OcBirdhousePlugin plugin, OcBirdhouseConfig config)
    {
        this.plugin = plugin;
        this.config = config;
    }

    void start() { executor.scheduleWithFixedDelay(this::tick, 1, 1, TimeUnit.SECONDS); }

    void stop()
    {
        running = false;
        walker.cancel();
        executor.shutdownNow();
    }

    String status() { return status; }
    String workStatus() { return supplies.prepared() ? actions.status() : supplies.status(); }

    private boolean cancelled()
    {
        boolean isCancelled = !running || Thread.currentThread().isInterrupted() || !config.autoVisit()
            || Microbot.pauseAllScripts.get() || plugin.getView().session != session;
        if (isCancelled)
        {
            walker.cancel();
        }
        return isCancelled;
    }

    private boolean confirmed()
    {
        OcBirdhousePlugin.View view = plugin.getView();
        return view.session == session && target != null
            && BirdHouseVisitPlanner.confirmed(target, previousObservedAt, view.houses);
    }

    private void tick()
    {
        try
        {
            OcBirdhousePlugin.View view = plugin.getView();
            if (session != view.session)
            {
                session = view.session;
                target = null;
                planner.reset();
                supplies.reset();
                bankTarget = null;
                bankRouting = false;
            }
            if (cancelled())
            {
                target = null;
                status = config.autoVisit() ? "Automatic visits paused" : "Automatic visits off";
                return;
            }
            long now = Instant.now().getEpochSecond();
            if (target != null)
            {
                if (confirmed())
                {
                    walker.cancel();
                    status = "Confirmed: " + target.label;
                    target = null;
                }
                else if (now >= confirmationDeadline) fail(now);
                return;
            }
            if (!view.navigationReady)
            {
                status = "Visits waiting for game";
                return;
            }
            if (!config.autoVisit())
            {
                status = "Automatic visits off";
                return;
            }
            Map<BirdHouseData, BirdHouseObservation> houses = view.houses;
            BirdHouseData next = planner.next(houses, now);
            if (next == null)
            {
                supplies.reset();
                bankTarget = null;
                bankRouting = false;
                status = countdown(houses, now);
                return;
            }
            // Supplies first: island chest when on the island, nearest bank otherwise.
            if (!supplies.prepared())
            {
                if (!supplies.carryingFullRun() && !walkToBank())
                {
                    return;
                }
                if (!supplies.ensure(config.depositAllBeforeRun(), this::cancelled))
                {
                    status = supplies.status();
                    return;
                }
            }
            if (cancelled()) return;
            if (!BirdHouseTravel.onIsland())
            {
                status = "Travelling to Fossil Island";
                if (!travel.goToIsland(this::cancelled))
                {
                    status = "Island travel failed - retry after 5m";
                    planner.failed(next, now);
                    return;
                }
            }
            if (cancelled()) return;
            BirdHouseTier tier = supplies.tier();
            if (tier == null)
            {
                status = "No birdhouse tier selected";
                supplies.reset();
                return;
            }
            target = next;
            BirdHouseObservation previous = houses.get(target);
            previousObservedAt = previous == null ? 0 : previous.observedAt;
            status = "Visiting: " + target.label + (walker.isAvailable() ? " [EW]" : "");
            log.info("Bird House Runner: target={} prevVarp={} prevObservedAt={} (at {})",
                target.label, previous == null ? "none" : previous.raw, previousObservedAt, BirdHouseTravel.pos());
            workHouse(target, tier);
            now = Instant.now().getEpochSecond();
            if (cancelled())
            {
                target = null;
                status = "Visit cancelled";
            }
            else if (confirmed())
            {
                walker.cancel();
                status = "Confirmed: " + target.label;
                target = null;
            }
            else if (!BirdHouseTravel.onIsland())
            {
                fail(now);
            }
            else
            {
                status = "Waiting for house confirmation";
                confirmationDeadline = now + 10;
            }
        }
        catch (Exception ex)
        {
            if (running)
            {
                log.warn("Bird House Runner visit failed", ex);
                fail(Instant.now().getEpochSecond());
            }
        }
        if (!status.equals(lastLoggedStatus))
        {
            log.info("Bird House Runner: {}", status);
            lastLoggedStatus = status;
        }
    }

    private void workHouse(BirdHouseData house, BirdHouseTier tier)
    {
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(3);
        // Meadow houses need the mushtree hop when coming from the Verdant side.
        if (!house.verdant() && !BirdHouseActions.nearHouse(house, 40))
        {
            status = "Mushtree to Meadow";
            log.info("Bird House Runner: mushtree hop to Meadow (at {})", BirdHouseTravel.pos());
            if (!travel.mushtreeToMeadow(this::cancelled))
            {
                log.warn("Bird House Runner: mushtree hop failed");
                return;
            }
        }
        walker.walkUntil(new WorldPoint(house.visitX, house.visitY, 0), 4, () -> cancelled() || confirmed(), deadline);
        if (cancelled() || confirmed()) return;
        if (!actions.dismantle(house, this::cancelled)) return;
        if (!actions.build(house, tier, this::cancelled)) return;
        if (!actions.seed(house, this::cancelled)) return;
        searchNests();
    }

    /**
     * Position at a bank so supplies can open it. The route is issued once per
     * cycle: re-routing every tick drags the player with the bank open and
     * breaks withdrawals. Arrival cancels the walker and waits for full stop.
     */
    private boolean walkToBank()
    {
        if (cancelled()) return false;
        if (bankTarget == null)
        {
            bankTarget = BirdHouseTravel.onIsland()
                ? BirdHouseTravel.ISLAND_BANK
                : Rs2Bank.getNearestBank().getWorldPoint();
            bankRouting = false;
            bankArrived = false;
            log.info("Bird House Runner: bank target={} (at {})", bankTarget, BirdHouseTravel.pos());
        }
        if (Rs2Player.distanceTo(bankTarget) <= 10)
        {
            // bankTarget stays set so later ticks skip the walk without
            // re-resolving the nearest bank; cleared on session change or reset.
            if (!bankArrived)
            {
                log.info("Bird House Runner: at bank, stopping walker (at {})", BirdHouseTravel.pos());
                bankArrived = true;
            }
            walker.cancel();
            BirdHouseTravel.waitFor(() -> !Rs2Player.isMoving(), 5_000, this::cancelled);
            bankRouting = false;
            return true;
        }
        if (bankRouting && System.nanoTime() >= bankDeadline)
        {
            log.warn("Bird House Runner: bank walk timed out, will re-route");
            bankRouting = false;
        }
        status = "Walking to bank";
        if (BirdHouseTravel.onIsland())
        {
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
            walker.walkUntil(bankTarget, 4, this::cancelled, deadline);
            walker.cancel();
            return false;
        }
        if (!bankRouting)
        {
            if (!walker.isAvailable())
            {
                Rs2Walker.walkTo(bankTarget);
            }
            else
            {
                walker.walkToNearestBank();
            }
            bankRouting = true;
            bankDeadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
            log.info("Bird House Runner: bank route issued (at {})", BirdHouseTravel.pos());
        }
        return false;
    }

    private void searchNests()
    {
        Rs2Inventory.items().forEachOrdered(item -> {
            if (NEST_IDS.contains(item.getId()))
            {
                Rs2Inventory.interact(item, "Search");
            }
        });
    }

    private String countdown(Map<BirdHouseData, BirdHouseObservation> houses, long now)
    {
        long nearest = Long.MAX_VALUE;
        for (BirdHouseObservation observation : houses.values())
        {
            if (observation != null && observation.state() == BirdHouseObservation.State.SEEDED && observation.readyAt > now)
            {
                nearest = Math.min(nearest, observation.readyAt);
            }
        }
        if (nearest == Long.MAX_VALUE) return "Waiting for due houses";
        // Minute granularity so the status (and its log line) only changes per minute.
        return "Next run ~" + ((nearest - now) / 60) + "m";
    }

    private void fail(long now)
    {
        walker.cancel();
        if (target != null) planner.failed(target, now);
        target = null;
        status = "Visit failed - retry after 5m";
    }
}
