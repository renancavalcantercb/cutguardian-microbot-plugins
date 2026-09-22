package net.runelite.client.plugins.microbot.ocfarmingew;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Single worker owns both actions and navigation; a click is never a receipt. */
final class FarmingActionExecutor
{
    private static final Logger log = LoggerFactory.getLogger(FarmingActionExecutor.class);
    private final OcFarmingEWPlugin plugin;
    private final OcFarmingEWConfig config;
    private final FarmingGameActions game;
    private final LongSupplier clock;
    private final Map<FarmingPatchData, Long> blockedUntil = new EnumMap<>(FarmingPatchData.class);
    private final Map<FarmingPatchData, Integer> reachRetries = new EnumMap<>(FarmingPatchData.class);
    private FarmingPatchData patch;
    private FarmingActionPolicy.Plan pending;
    private FarmingObservation before;
    private FarmingActionPolicy.Inventory initial, lastInventory;
    private int lastRaw;
    private long lastProgress, started;
    private boolean received;
    private volatile String status = "Waiting for local work";

    FarmingActionExecutor(OcFarmingEWPlugin plugin, OcFarmingEWConfig config)
    {
        this(plugin, config, new FarmingGameActions(), () -> Instant.now().getEpochSecond());
    }

    FarmingActionExecutor(OcFarmingEWPlugin plugin, OcFarmingEWConfig config, FarmingGameActions game, LongSupplier clock)
    {
        this.plugin = plugin;
        this.config = config;
        this.game = game;
        this.clock = clock;
    }

    void reset()
    {
        pending = null;
        patch = null;
        blockedUntil.clear();
        reachRetries.clear();
        status = "Waiting for local work";
    }

    String status() { return status; }
    boolean hasPendingAction() { return pending != null; }
    boolean blocked(FarmingPatchData candidate, long now) { return now < blockedUntil.getOrDefault(candidate, 0L); }

    /** False releases navigation only when local work has been assessed or we are outside a patch area. */
    boolean tick(OcFarmingEWPlugin.View view, BooleanSupplier cancelled)
    {
        if (!OcFarmingEWConfig.tending(config)) { pending = null; return false; }
        if (pending != null && !patch.tend(config)) pending = null;
        long now = clock.getAsLong();
        FarmingGameActions.Snapshot snapshot = game.snapshot();
        if (!snapshot.ready) return true;
        int currentRegion = FarmingPatchData.regionAt(snapshot.location);
        if (pending != null)
        {
            if (snapshot.location == null || !patch.includes(snapshot.location.getX(), snapshot.location.getY(), snapshot.location.getPlane()))
            {
                block("Left patch during action", now);
                return true;
            }
            FarmingObservation after = view.liveRegion == patch.region ? view.patches.get(patch) : null;
            String receipt = plugin.actionReceipt() + " "
                + ((pending.action != FarmingActionPolicy.Action.PAY && pending.action != FarmingActionPolicy.Action.REMOVE_TREE)
                    || snapshot.dialogueNpc == patch.gardenerId()
                    ? snapshot.dialogueText : "");
            received |= FarmingActionPolicy.confirmed(pending, before, after, initial, snapshot.inventory, receipt);
            if (received)
            {
                if (snapshot.dialogue && now - started < 30)
                {
                    game.continueOwnedDialogue(pending.action, true);
                    return true;
                }
                if (pending.action == FarmingActionPolicy.Action.PAY)
                    plugin.recordProtection(patch, before, view.session);
                status = patch.label + ": " + pending.reason + " confirmed";
                log.info("Farming Runner: {}", status);
                reachRetries.remove(patch);
                pending = null;
                return true;
            }
            if (FarmingPatchTarget.usesPatch(pending.action) && FarmingPatchTarget.cannotReach(plugin.actionReceipt()))
            {
                int attempts = reachRetries.getOrDefault(patch, 0);
                if (attempts >= 2)
                {
                    block("Cannot reach patch after repositioning", now);
                    return true;
                }
                reachRetries.put(patch, attempts + 1);
                FarmingActionPolicy.Action action = pending.action;
                pending = null;
                plugin.clearActionReceipt();
                status = "Repositioning at " + patch.label + " after reach failure";
                log.info("Farming Runner: {}", status);
                game.approach(patch, action, cancelled);
                // Re-read the patch and supplies before issuing the next action.
                return true;
            }
            if ((pending.action == FarmingActionPolicy.Action.RAKE || pending.action == FarmingActionPolicy.Action.HARVEST)
                && snapshot.inventory.free == 0)
            {
                pending = null; // Discard weeds on the next tick before resuming.
                return true;
            }
            int raw = after == null ? lastRaw : after.raw;
            if (raw != lastRaw || !snapshot.inventory.items.equals(lastInventory.items)) lastProgress = now;
            lastRaw = raw;
            lastInventory = snapshot.inventory;
            if (now - lastProgress >= 25 || now - started >= 180)
            {
                block(pending.reason + " not confirmed; check supplies/target", now);
                return true;
            }
            if (snapshot.dialogue) game.continueOwnedDialogue(pending.action, false);
            return true;
        }
        if (snapshot.busy) return true;
        if (!view.navigationReady || snapshot.location == null) return true;
        boolean localArea = false;
        for (FarmingPatchData candidate : FarmingPatchData.values())
        {
            if (!candidate.tend(config)) continue;
            if (!candidate.includes(snapshot.location.getX(), snapshot.location.getY(), snapshot.location.getPlane())) continue;
            localArea = true;
            FarmingObservation observation = view.patches.get(candidate);
            // Closing a gardener dialogue invalidates the live view for
            // two ticks. That is a wait, not completion of this region's work.
            // Check every local patch before allowing the planner to leave.
            if (view.liveRegion != currentRegion || observation == null || now - observation.observedAt > 3)
            {
                status = "Waiting for fresh local patch readings";
                return true;
            }
        }
        if (!localArea) return false;
        for (FarmingPatchData candidate : FarmingPatchData.values())
        {
            if (!candidate.tend(config) || candidate.region != currentRegion || blocked(candidate, now)) continue;
            FarmingObservation observation = view.patches.get(candidate);
            boolean paid = view.protectedCrops.get(candidate) == observation.crop() && observation.crop() != null;
            FarmingActionPolicy.Plan plan = FarmingTreePolicy.next(observation, snapshot.inventory,
                candidate.chosen(config), candidate.protect(config), paid);
            if (plan.action == FarmingActionPolicy.Action.NONE) continue;
            patch = candidate;
            if (plan.action == FarmingActionPolicy.Action.BLOCKED)
            {
                block(plan.reason, now);
                continue;
            }
            if (!game.near(snapshot, candidate, plan.action))
            {
                status = "Approaching " + candidate.label;
                BooleanSupplier stopped = () -> cancelled.getAsBoolean() || !candidate.tend(config);
                game.approach(candidate, plan.action, stopped);
                if (!stopped.getAsBoolean() && !game.near(game.snapshot(), candidate, plan.action)) block("Cannot reach patch", now);
                return true;
            }
            pending = plan;
            before = observation;
            initial = snapshot.inventory;
            lastInventory = initial;
            lastRaw = observation.raw;
            started = lastProgress = now;
            received = false;
            plugin.clearActionReceipt();
            if (plan.action == FarmingActionPolicy.Action.PAY) plugin.selectProtection(candidate);
            status = candidate.label + ": " + plan.reason;
            log.info("Farming Runner: {}", status);
            if (cancelled.getAsBoolean() || !candidate.tend(config)
                || !game.execute(candidate, plan, () -> cancelled.getAsBoolean() || !candidate.tend(config)))
                block("Could not start " + plan.reason, now);
            return true;
        }
        return false;
    }

    private void block(String reason, long now)
    {
        if (patch != null) blockedUntil.put(patch, now + 60);
        reachRetries.remove(patch);
        status = (patch == null ? "" : patch.label + ": ") + reason;
        log.warn("Farming Runner: {} (retry after 60s)", status);
        pending = null;
    }
}
