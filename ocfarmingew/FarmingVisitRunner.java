package net.runelite.client.plugins.microbot.ocfarmingew;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Navigation runs off the client thread; confirmation uses immutable tracker views. */
final class FarmingVisitRunner
{
    private static final Logger log = LoggerFactory.getLogger(FarmingVisitRunner.class);
    private final OcFarmingEWPlugin plugin;
    private final OcFarmingEWConfig config;
    private final FarmingVisitPlanner planner = new FarmingVisitPlanner();
    private final FarmingActionExecutor work;
    private final FarmingRunSupplies supplies = new FarmingRunSupplies();
    private final FarmingEfficientWalkerBridge walker = new FarmingEfficientWalkerBridge();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "FarmingRunnerEW-visits");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean running = true;
    private volatile String status = "Waiting for due patches";
    private FarmingPatchData target;
    private long session = -1;
    private long previousObservedAt;
    private long confirmationDeadline;
    private boolean lastAutoTrees, lastAutoFruitTrees;

    FarmingVisitRunner(OcFarmingEWPlugin plugin, OcFarmingEWConfig config)
    {
        this.plugin = plugin;
        this.config = config;
        this.work = new FarmingActionExecutor(plugin, config);
    }

    void start() { executor.scheduleWithFixedDelay(this::tick, 1, 1, TimeUnit.SECONDS); }

    void stop()
    {
        running = false;
        walker.cancel();
        executor.shutdownNow();
    }

    String status() { return status; }
    String workStatus() { return supplies.preparing() ? supplies.status() : work.status(); }

    private boolean cancelled()
    {
        boolean isCancelled = !running || Thread.currentThread().isInterrupted() || (!config.autoVisit() && !OcFarmingEWConfig.tending(config))
            || (target != null && !target.enabled(config))
            || Microbot.pauseAllScripts.get() || plugin.getView().session != session;
        if (isCancelled)
        {
            walker.cancel();
        }
        return isCancelled;
    }

    private boolean confirmed()
    {
        OcFarmingEWPlugin.View view = plugin.getView();
        return view.session == session && target != null
            && FarmingVisitPlanner.confirmed(target, previousObservedAt, view.liveRegion, view.patches);
    }

    private void tick()
    {
        try
        {
            OcFarmingEWPlugin.View view = plugin.getView();
            if (lastAutoTrees != config.autoTrees() || lastAutoFruitTrees != config.autoFruitTrees())
            {
                lastAutoTrees = config.autoTrees();
                lastAutoFruitTrees = config.autoFruitTrees();
                work.reset();
                supplies.reset();
            }
            if (session != view.session)
            {
                session = view.session;
                target = null;
                planner.reset();
                work.reset();
                supplies.reset();
            }
            if (cancelled())
            {
                target = null;
                status = config.autoVisit() ? "Automatic visits paused" : "Automatic visits off";
                return;
            }
            long now = Instant.now().getEpochSecond();
            if (target == null && OcFarmingEWConfig.tending(config) && !work.hasPendingAction())
            {
                if (supplies.preparing() || FarmingRunSupplies.hasWork(view, config))
                {
                    if (!supplies.ensure(config, view, () -> cancelled() || !OcFarmingEWConfig.tending(config)))
                    {
                        status = supplies.status();
                        return;
                    }
                }
                else supplies.reset(); // next due round gets a new pending-work budget
            }
            if (target == null && work.tick(view, () -> cancelled() || !OcFarmingEWConfig.tending(config)))
            {
                status = work.status();
                return;
            }
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
            // snapshot() may wait on the client thread while the tracker publishes
            // another view. Reassess local work next tick before planning a trip.
            if (OcFarmingEWConfig.tending(config) && plugin.getView() != view) return;
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
            Map<FarmingPatchData, FarmingObservation> eligible = new EnumMap<>(FarmingPatchData.class);
            view.patches.forEach((candidate, observation) -> {
                if (candidate.enabled(config)) eligible.put(candidate, observation);
            });
            final long planningTime = now;
            target = planner.next(eligible, now, p -> p.tend(config),
                p -> p.enabled(config) && (!p.tend(config) || !work.blocked(p, planningTime)), OcFarmingEWConfig.tending(config));
            if (target == null)
            {
                status = "Waiting for due patches / retry";
                return;
            }
            FarmingObservation previous = view.patches.get(target);
            previousObservedAt = previous == null ? 0 : previous.observedAt;
            status = "Visiting: " + target.label + (walker.isAvailable() ? " [EW]" : "");
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(3);

            // Use Efficient Walker bridge with fallback to Rs2Walker if not available or blocked
            boolean reached = walker.walkUntil(new WorldPoint(target.visitX, target.visitY, 0), 3,
                () -> cancelled() || !config.autoVisit() || confirmed() || System.nanoTime() >= deadline,
                deadline);

            now = Instant.now().getEpochSecond();
            if (cancelled() || !config.autoVisit())
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
            else if (System.nanoTime() >= deadline || !reached) fail(now);
            else
            {
                status = "Waiting for patch confirmation";
                confirmationDeadline = now + 10;
            }
        }
        catch (Exception ex)
        {
            if (running)
            {
                log.warn("Farming Runner EW visit failed", ex);
                fail(Instant.now().getEpochSecond());
            }
        }
    }

    private void fail(long now)
    {
        walker.cancel();
        if (target != null) planner.failed(target, now);
        target = null;
        status = "Visit failed - retry after 5m";
    }
}
