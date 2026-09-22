package net.runelite.client.plugins.microbot.ocfarmingew;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Prepare pending work; replenish only when new work exceeds the remaining budget. */
final class FarmingRunSupplies
{
    private final FarmingBankActions bank;
    private final LongSupplier clock;
    private String key = "";
    private FarmingSupplyPlan preparedPlan;
    private boolean prepared, banking, depositedAll;
    private long retryAfter;
    private String status = "Waiting to prepare run";
    private final java.util.Set<Integer> skippedOptional = new java.util.HashSet<>();
    FarmingRunSupplies() { this(new FarmingBankActions(), () -> java.time.Instant.now().getEpochSecond()); }
    FarmingRunSupplies(FarmingBankActions bank, LongSupplier clock) { this.bank = bank; this.clock = clock; }
    void reset() { key = ""; preparedPlan = null; prepared = banking = depositedAll = false; retryAfter = 0; skippedOptional.clear(); status = "Waiting to prepare run"; }
    boolean prepared() { return prepared; }
    boolean preparing() { return !key.isEmpty() && !prepared; }
    String status() { return status; }
    boolean ensure(OcFarmingEWConfig config, BooleanSupplier cancelled)
    { return ensure(config, null, cancelled); }

    boolean ensure(OcFarmingEWConfig config, OcFarmingEWPlugin.View view, BooleanSupplier cancelled)
    {
        FarmingSupplyPlan plan = new FarmingSupplyPlan(config, view, clock.getAsLong());
        if (cancelled.getAsBoolean() || !bank.ready()) return false;
        if (plan.patches == 0)
        {
            if (banking && !bank.close(cancelled)) return false;
            reset();
            return true;
        }
        // Forget completed work so another patch becoming due starts a new budget.
        // Shrinking the current budget must not replenish already consumed items.
        if (prepared && preparedPlan.covers(plan))
        { preparedPlan = plan; key = plan.key; return true; }
        if (!plan.key.equals(key)) { reset(); key = plan.key; }
        if (clock.getAsLong() < retryAfter) return false;
        if (bank.inventory().level < plan.levelRequired) return block("Need Farming level " + plan.levelRequired);
        if (!banking && !plan.hasUnused(bank.inventory()) && plan.ready(bank.inventory()) && !bank.isOpen())
        { prepared = true; preparedPlan = plan; status = plan.readyMessage(); return true; }
        status = "Preparing run at bank";
        BooleanSupplier stopped = () -> cancelled.getAsBoolean() || !plan.configKey.equals(new FarmingSupplyPlan(config).configKey);
        if (!bank.open(stopped, config.autoVisit()))
            return block(config.autoVisit() ? "Could not open a live bank; retry in 60s" : "Open a bank or enable automatic revisits to fetch supplies");
        banking = true;
        if (stopped.getAsBoolean()) return false;
        if (config.depositAllBeforeRun() && !depositedAll)
        {
            if (plan.hasUnused(bank.inventory()))
            {
                status = "Depositing inventory items";
                if (!bank.depositAll(stopped))
                    return block("Deposit inventory not confirmed; retry in 60s");
                depositedAll = true;
                return false;
            }
            depositedAll = true;
        }
        java.util.Map<Integer, Integer> available = new java.util.HashMap<>(bank.bank());
        skippedOptional.forEach(available::remove);
        FarmingSupplyPlan.Step step = plan.next(bank.inventory(), available);
        status = step.message;
        switch (step.kind)
        {
            case BLOCKED: return block(step.message);
            case READY:
                if (bank.close(stopped)) { prepared = true; preparedPlan = plan; banking = false; return true; }
                return block("Could not close bank; retry in 60s");
            default:
                if (!bank.execute(step, stopped))
                {
                    if (step.kind == FarmingSupplyPlan.Kind.WITHDRAW
                        && !(plan.pruning && (step.item == 5329 || step.item == 7409))
                        && plan.required.stream().noneMatch(item -> item.item == step.item))
                    { skippedOptional.add(step.item); return false; }
                    return block(step.message + " not confirmed; retry in 60s");
                }
                return false;
        }
    }
    private boolean block(String message) { status = message; retryAfter = clock.getAsLong() + 60; return false; }

    static boolean hasWork(OcFarmingEWPlugin.View view, OcFarmingEWConfig config)
    {
        long now = java.time.Instant.now().getEpochSecond();
        for (FarmingPatchData patch : FarmingPatchData.values())
        {
            if (FarmingSupplyPlan.pending(patch, config, view, now) != null) return true;
        }
        return false;
    }
}
