package net.runelite.client.plugins.microbot.ocfarmingew;

import static net.runelite.client.plugins.microbot.ocfarmingew.FarmingActionPolicy.*;

/** Tree actions require a sapling/spade, and health must be checked before removal. */
final class FarmingTreePolicy
{
    static Plan next(FarmingObservation observation, Inventory inventory, FarmingTreeKind chosen, boolean protect, boolean paid)
    {
        if (observation == null || observation.state() == FarmingObservation.State.UNKNOWN)
            return blocked("Tree state not supported");
        if (observation.fruit != (chosen instanceof FarmingFruitTree)) return blocked("Wrong tree type for patch");
        if (inventory.count(6055) > 0) return plan(Action.DROP_WEEDS, 6055);
        if (inventory.count(5350) > 0) return plan(Action.DROP_POTS, 5350);
        // Banknotes avoid filling the bag and also remain valid protection payments.
        if (observation.fruit) for (FarmingFruitTree fruit : FarmingFruitTree.values())
            if (inventory.count(fruit.produce) > 0)
                return new Plan(Action.NOTE_FRUIT, fruit.produce, fruit.produce + 1,
                    inventory.count(fruit.produce), "Note " + fruit.label + " produce");
        switch (observation.state())
        {
            case WEEDS:
                if (inventory.count(5341) == 0) return blocked("Need rake");
                return inventory.free > 0 ? plan(Action.RAKE, 0) : blocked("Need space for weeds");
            case EMPTY:
                if (inventory.level < chosen.level()) return blocked("Need Farming level " + chosen.level());
                if (inventory.count(952) == 0) return blocked("Need spade");
                if (inventory.count(chosen.sapling()) == 0) return blocked("Need unnoted " + chosen.label() + " sapling");
                if (protect && paymentItem(chosen, inventory) == 0) return blocked("Need " + chosen.paymentAmount() + " " + chosen.paymentLabel() + " to protect " + chosen.label() + " (notes accepted)");
                return new Plan(Action.PLANT, chosen.sapling(), 0, 1, "Plant " + chosen.label() + " sapling");
            case GROWING:
                if (!protect || paid) return plan(Action.NONE, 0);
                FarmingTreeKind growing = FarmingTreeKind.from(observation);
                int item = paymentItem(growing, inventory);
                return new Plan(Action.PAY, item == 0 ? growing.paymentNote() : item, 0, growing.paymentAmount(), "Protect " + growing.label());
            case CHECK_HEALTH: return plan(Action.CHECK_HEALTH, 0);
            case HARVEST:
                FarmingFruitTree fruit = FarmingFruitTree.fromRaw(observation.raw);
                return inventory.free > 0 ? new Plan(Action.HARVEST, fruit.produce, 0, 1, "Pick " + fruit.label)
                    : blocked("Need inventory space to pick fruit");
            case CHECKED:
                return inventory.count(995) >= 200 ? new Plan(Action.REMOVE_TREE, 995, 0, 200, "Pay to remove tree")
                    : blocked("Need 200 coins to remove checked tree");
            case STUMP: case DEAD:
                return inventory.count(952) > 0 ? plan(Action.CLEAR, 0) : blocked("Need spade");
            case DISEASED:
                return inventory.count(5329) > 0 || inventory.count(7409) > 0
                    ? plan(Action.PRUNE, 0) : blocked("Need secateurs to prune tree");
            default: return plan(Action.NONE, 0);
        }
    }

    private static int paymentItem(FarmingTreeKind tree, Inventory inventory)
    {
        if (inventory.count(tree.paymentNote()) >= tree.paymentAmount()) return tree.paymentNote();
        if (inventory.count(tree.payment()) >= tree.paymentAmount()) return tree.payment();
        return 0;
    }
}
