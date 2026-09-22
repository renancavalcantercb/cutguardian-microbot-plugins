package net.runelite.client.plugins.microbot.ocfarmingew;

import java.util.*;

/** Supplies for the pending work in the current tracker view. */
final class FarmingSupplyPlan
{
    static final int[] RUNES = {556, 554, 557, 555, 563};
    // Reserve 25 times the removal fee per patch for removal and paid travel.
    static final int COINS_PER_PATCH = 200 * 25;
    enum Kind { DEPOSIT, DEPOSIT_ALL, WITHDRAW, READY, BLOCKED }
    static final class Step
    {
        final Kind kind;
        final int item, amount, received;
        final boolean noted;
        final String message;
        Step(Kind kind, int item, int amount, int received, boolean noted, String message)
        { this.kind = kind; this.item = item; this.amount = amount; this.received = received; this.noted = noted; this.message = message; }
    }
    static final class Required
    {
        final int item, note, amount;
        final String name;
        final boolean withdrawNoted;
        Required(int item, int note, int amount, String name, boolean noted)
        { this.item = item; this.note = note; this.amount = amount; this.name = name; this.withdrawNoted = noted; }
    }
    final int patches, runeTarget, levelRequired, spaceReserve;
    final List<Required> required = new ArrayList<>();
    final List<Required> saplings = new ArrayList<>();
    final String key, configKey;
    final Map<FarmingPatchData, Work> work = new EnumMap<>(FarmingPatchData.class);
    final boolean pruning;

    static final class Work
    {
        final FarmingTreeKind planting, payment;
        final boolean prune;
        Work(FarmingTreeKind planting, FarmingTreeKind payment, boolean prune)
        { this.planting = planting; this.payment = payment; this.prune = prune; }

        boolean covers(Work next)
        {
            return (next.planting == null || planting == next.planting)
                && (next.payment == null || payment == next.payment) && (!next.prune || prune);
        }
    }

    FarmingSupplyPlan(OcFarmingEWConfig config)
    { this(config, null, 0); }

    FarmingSupplyPlan(OcFarmingEWConfig config, OcFarmingEWPlugin.View view, long now)
    {
        StringBuilder sites = new StringBuilder();
        int count = 0, level = 0;
        boolean fruitWork = false, prune = false;
        Map<FarmingTreeKind, Integer> crops = new LinkedHashMap<>();
        for (FarmingPatchData patch : FarmingPatchData.values()) if (patch.enabled(config))
        {
            sites.append(patch.name());
            Work pending = pending(patch, config, view, now);
            if (pending == null) continue;
            work.put(patch, pending);
            count++;
            fruitWork |= patch.fruit;
            prune |= pending.prune;
            if (pending.planting != null)
            {
                crops.merge(pending.planting, 1, Integer::sum);
                level = Math.max(level, pending.planting.level());
            }
            FarmingTreeKind payment = pending.payment;
            if (payment != null) addRequired(new Required(payment.payment(), payment.paymentNote(),
                payment.paymentAmount(), payment.paymentLabel(), true));
        }
        patches = count;
        levelRequired = level;
        pruning = prune;
        // The first fruit note creates a new stack. Starting with only one free
        // slot could pick one fruit, note it, and still leave no room to resume.
        spaceReserve = fruitWork ? 2 : 1;
        runeTarget = Math.max(0, Math.min(10000, config.teleportRuneReserve()));
        configKey = sites + ":" + config.tree().name() + ":" + config.treeProtection() + ":" + runeTarget
            + ":" + config.fruitTree().name() + ":" + config.fruitProtection() + ":" + config.autoVisit();
        StringBuilder tasks = new StringBuilder(configKey);
        work.forEach((patch, pending) -> tasks.append(':').append(patch).append('/').append(pending.planting)
            .append('/').append(pending.payment).append('/').append(pending.prune));
        key = tasks.toString();
        crops.forEach((crop, amount) -> saplings.add(new Required(crop.sapling(), crop.saplingNote(), amount, crop.label() + " sapling", false)));
        required.addAll(0, saplings);
        if (!saplings.isEmpty())
        {
            required.add(saplings.size(), new Required(952, 953, 1, "Spade", false));
            required.add(saplings.size() + 1, new Required(5341, 5342, 1, "Rake", false));
        }
        if (patches > 0) required.add(new Required(995, -1, patches * COINS_PER_PATCH, "Coins for travel and tree removal", false));
    }

    static Work pending(FarmingPatchData patch, OcFarmingEWConfig config, OcFarmingEWPlugin.View view, long now)
    {
        if (!patch.enabled(config) || (view != null && !config.autoVisit() && patch.region != view.liveRegion)) return null;
        FarmingObservation observation = view == null ? null : view.patches.get(patch);
        FarmingTreeKind chosen = patch.chosen(config);
        // Unvisited patches and expired estimates still need a conservative replant budget.
        if (observation == null || observation.isDue(now))
            return new Work(chosen, patch.protect(config) ? chosen : null, false);
        boolean unpaid = patch.protect(config) && observation.crop() != null
            && view.protectedCrops.get(patch) != observation.crop();
        if (observation.state() == FarmingObservation.State.GROWING)
            return unpaid ? new Work(null, FarmingTreeKind.from(observation), false) : null;
        if (observation.state() == FarmingObservation.State.DISEASED)
            return new Work(null, unpaid ? FarmingTreeKind.from(observation) : null, true);
        return FarmingVisitPlanner.needsWork(observation)
            ? new Work(chosen, patch.protect(config) ? chosen : null, false) : null;
    }

    boolean covers(FarmingSupplyPlan next)
    {
        return configKey.equals(next.configKey) && next.work.entrySet().stream()
            .allMatch(entry -> work.containsKey(entry.getKey()) && work.get(entry.getKey()).covers(entry.getValue()));
    }

    String readyMessage()
    {
        return "Run supplies ready: " + saplings.stream().mapToInt(item -> item.amount).sum()
            + " saplings for " + patches + " patches";
    }

    private void addRequired(Required added)
    {
        for (int i = 0; i < required.size(); i++)
        {
            Required existing = required.get(i);
            if (existing.item == added.item && existing.withdrawNoted == added.withdrawNoted)
            {
                required.set(i, new Required(existing.item, existing.note, existing.amount + added.amount, existing.name, existing.withdrawNoted));
                return;
            }
        }
        required.add(added);
    }

    Set<Integer> keep()
    {
        Set<Integer> keep = new HashSet<>(Arrays.asList(995, 5329, 7409));
        for (Required item : required) { keep.add(item.item); keep.add(item.note); }
        for (int rune : RUNES) keep.add(rune);
        return keep;
    }

    boolean hasUnused(FarmingActionPolicy.Inventory inventory)
    {
        Set<Integer> keep = keep();
        for (int id : inventory.items.keySet())
        {
            if (!keep.contains(id)) return true;
        }
        return false;
    }

    boolean ready(FarmingActionPolicy.Inventory inventory)
    {
        if (pruning && inventory.count(5329) == 0 && inventory.count(7409) == 0) return false;
        for (Required sapling : saplings)
            if (inventory.count(sapling.item) != sapling.amount || inventory.count(sapling.note) > 0) return false;
        for (Required item : required)
        {
            int have = item.withdrawNoted ? Math.max(inventory.count(item.item), inventory.count(item.note)) : inventory.count(item.item);
            if (have < item.amount) return false;
        }
        return inventory.free >= spaceReserve;
    }

    Step next(FarmingActionPolicy.Inventory inventory, Map<Integer, Integer> bank)
    {
        // Check all mandatory stock before changing inventory. A cached bank list
        // must never be supplied here: the caller waits for a live bank container.
        for (Required item : required)
        {
            int total = inventory.count(item.item) + (item.note >= 0 ? inventory.count(item.note) : 0) + bank.getOrDefault(item.item, 0);
            if (total < item.amount) return result(Kind.BLOCKED, "Missing " + (item.amount - total) + " " + item.name + " in bag + bank");
        }
        if (pruning && inventory.count(5329) == 0 && inventory.count(7409) == 0)
        {
            int secateurs = bank.getOrDefault(7409, 0) > 0 ? 7409 : 5329;
            if (bank.getOrDefault(secateurs, 0) == 0) return result(Kind.BLOCKED, "Missing secateurs to prune tree");
            if (inventory.free <= spaceReserve) return makeSpace(inventory);
            return new Step(Kind.WITHDRAW, secateurs, 1, secateurs, false, "Withdraw secateurs");
        }
        for (Required sapling : saplings)
        {
            int excess = inventory.count(sapling.item) - sapling.amount;
            if (excess > 0) return deposit(sapling.item, excess, "Store excess saplings");
        }
        for (Required item : required)
        {
            // Normalize noted saplings/tools and unnoted payments before withdrawal.
            int normalize = item.withdrawNoted ? item.item : item.note;
            if (normalize >= 0 && inventory.count(normalize) > 0) return deposit(normalize, inventory.count(normalize), "Prepare " + item.name);
        }
        // Bank unused items before withdrawing run supplies so the backpack stays clean.
        Set<Integer> keep = keep();
        for (int id : new TreeSet<>(inventory.items.keySet()))
        {
            if (!keep.contains(id)) return deposit(id, inventory.count(id), "Bank unused item before withdrawing supplies");
        }
        for (Required item : required)
        {
            int received = item.withdrawNoted ? item.note : item.item;
            int missing = item.amount - inventory.count(received);
            if (missing <= 0) continue;
            int slots = item.withdrawNoted || item.item == 995 ? (inventory.count(received) > 0 ? 0 : 1) : missing;
            if (inventory.free < slots + spaceReserve) return makeSpace(inventory);
            return new Step(Kind.WITHDRAW, item.item, missing, received, item.withdrawNoted, "Withdraw " + missing + " " + item.name);
        }
        // Optional reserves never prevent the run. Keep existing excess runes/coins.
        for (int rune : RUNES)
        {
            int amount = Math.min(Math.max(0, runeTarget - inventory.count(rune)), bank.getOrDefault(rune, 0));
            if (amount > 0 && (inventory.count(rune) > 0 || inventory.free > spaceReserve))
                return new Step(Kind.WITHDRAW, rune, amount, rune, false, "Optional teleport runes");
        }
        return inventory.free >= spaceReserve ? result(Kind.READY, readyMessage()) : makeSpace(inventory);
    }

    private Step makeSpace(FarmingActionPolicy.Inventory inventory)
    {
        Set<Integer> keep = keep();
        for (int id : new TreeSet<>(inventory.items.keySet())) if (!keep.contains(id))
            return deposit(id, inventory.count(id), "Bank unused item to make room");
        return result(Kind.BLOCKED, "Need inventory space for run supplies and weeds");
    }

    private static Step deposit(int id, int amount, String message) { return new Step(Kind.DEPOSIT, id, amount, id, false, message); }
    private static Step result(Kind kind, String message) { return new Step(kind, 0, 0, 0, false, message); }
}
