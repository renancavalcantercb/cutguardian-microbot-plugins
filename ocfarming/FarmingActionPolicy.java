package net.runelite.client.plugins.microbot.ocfarming;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Decisions and receipts are independent of clicks and the client API. */
final class FarmingActionPolicy
{
    enum Action { NONE, BLOCKED, RAKE, CLEAR, DROP_WEEDS, PLANT, PAY,
        CHECK_HEALTH, REMOVE_TREE, PRUNE, DROP_POTS, HARVEST, NOTE_FRUIT }

    static final class Inventory
    {
        final Map<Integer, Integer> items;
        final int free, level, xp;
        Inventory(Map<Integer, Integer> items, int free, int level, int xp)
        {
            this.items = Collections.unmodifiableMap(new HashMap<>(items));
            this.free = free;
            this.level = level;
            this.xp = xp;
        }
        int count(int item) { return items.getOrDefault(item, 0); }
    }

    static final class Plan
    {
        final Action action;
        final int item, pairedItem, amount;
        final String reason;
        Plan(Action action, int item, int pairedItem, int amount, String reason)
        {
            this.action = action;
            this.item = item;
            this.pairedItem = pairedItem;
            this.amount = amount;
            this.reason = reason;
        }
    }

    static Plan plan(Action action, int item) { return new Plan(action, item, 0, 1, action.toString()); }
    static Plan blocked(String reason) { return new Plan(Action.BLOCKED, 0, 0, 0, reason); }

    static boolean confirmed(Plan plan, FarmingObservation before, FarmingObservation after,
        Inventory initial, Inventory current, String receipt)
    {
        String text = receipt == null ? "" : receipt.toLowerCase(java.util.Locale.ROOT);
        boolean fresh = after != null && before.fruit == after.fruit && after.observedAt > before.observedAt;
        switch (plan.action)
        {
            case DROP_WEEDS: return current.count(6055) < initial.count(6055);
            case DROP_POTS: return current.count(5350) < initial.count(5350);
            case CHECK_HEALTH:
                return fresh && before.crop() == after.crop()
                    && (after.state() == FarmingObservation.State.CHECKED || after.state() == FarmingObservation.State.HARVEST);
            case HARVEST:
                return fresh && before.crop() == after.crop() && after.raw < before.raw
                    && (after.state() == FarmingObservation.State.HARVEST || after.state() == FarmingObservation.State.CHECKED)
                    && current.count(plan.item) > initial.count(plan.item);
            case NOTE_FRUIT:
                return initial.count(plan.item) - current.count(plan.item) == plan.amount
                    && current.count(plan.pairedItem) - initial.count(plan.pairedItem) == plan.amount;
            case REMOVE_TREE:
                return fresh
                    && (after.state() == FarmingObservation.State.EMPTY || after.state() == FarmingObservation.State.WEEDS)
                    && initial.count(995) - current.count(995) == 200;
            case PRUNE:
                return fresh && before.crop() == after.crop()
                    && (after.state() == FarmingObservation.State.GROWING || after.state() == FarmingObservation.State.CHECK_HEALTH);
            case PAY:
                return FarmingProtectionTracker.isConfirmation(receipt)
                    || (initial.count(plan.item) - current.count(plan.item) == plan.amount
                        && text.contains("you pay the gardener") && text.contains("protect the patch"));
            case RAKE: return fresh && after.state() == FarmingObservation.State.EMPTY;
            case CLEAR: return fresh && (after.state() == FarmingObservation.State.EMPTY || after.state() == FarmingObservation.State.WEEDS);
            case PLANT:
                FarmingTreeKind tree = FarmingTreeKind.from(after);
                return fresh && after.state() == FarmingObservation.State.GROWING && tree != null
                    && tree.sapling() == plan.item && initial.count(plan.item) - current.count(plan.item) == 1;
            default: return false;
        }
    }
}
