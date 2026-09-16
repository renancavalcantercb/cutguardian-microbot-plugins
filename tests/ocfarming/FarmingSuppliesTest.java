package net.runelite.client.plugins.microbot.ocfarming;

import java.util.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarmingSuppliesTest
{
    private final OcFarmingConfig config = new OcFarmingConfig() {
        @Override public boolean autoTrees() { return true; }
        @Override public boolean autoVisit() { return true; }
    };
    private FarmingActionPolicy.Inventory bag(Map<Integer, Integer> items) { return new FarmingActionPolicy.Inventory(items, 20, 15, 100); }
    private Map<Integer, Integer> complete() { return new HashMap<>(Map.of(5370, 6, 5969, 6, 952, 1, 5341, 1, 995, 30000)); }

    @Test void selectedLocationsDetermineExactSaplingsAndPayment()
    {
        FarmingSupplyPlan all = new FarmingSupplyPlan(config);
        assertEquals(6, all.patches);
        OcFarmingConfig fewer = new OcFarmingConfig() {
            @Override public boolean autoTrees() { return true; }
            @Override public boolean nemusTree() { return false; }
            @Override public boolean gnomeTree() { return false; }
        };
        FarmingSupplyPlan four = new FarmingSupplyPlan(fewer);
        assertEquals(4, four.patches);
        assertTrue(four.ready(bag(Map.of(5370, 4, 5968, 4, 952, 1, 5341, 1, 995, 20000))));
        assertFalse(four.ready(bag(complete())));
    }

    @Test void mandatoryStockIsCheckedBeforeDepositingOrWithdrawing()
    {
        FarmingSupplyPlan plan = new FarmingSupplyPlan(config);
        FarmingSupplyPlan.Step step = plan.next(bag(Map.of(5370, 10, 952, 1, 5341, 1)), Map.of());
        assertEquals(FarmingSupplyPlan.Kind.BLOCKED, step.kind);
        assertTrue(step.message.contains("6 Tomatoes(5)"));
    }

    @Test void withdrawsOnlyDeficitAndBanksExcessSaplings()
    {
        FarmingSupplyPlan plan = new FarmingSupplyPlan(config);
        Map<Integer, Integer> inventory = complete();
        inventory.put(5370, 2);
        FarmingSupplyPlan.Step missing = plan.next(bag(inventory), Map.of(5370, 100));
        assertEquals(FarmingSupplyPlan.Kind.WITHDRAW, missing.kind);
        assertEquals(4, missing.amount);
        assertFalse(missing.noted);
        inventory.put(5370, 10);
        FarmingSupplyPlan.Step excess = plan.next(bag(inventory), Map.of());
        assertEquals(FarmingSupplyPlan.Kind.DEPOSIT, excess.kind);
        assertEquals(4, excess.amount);
    }

    @Test void notedSaplingsAreDepositedBeforeUnnotedWithdrawal()
    {
        Map<Integer, Integer> inventory = complete();
        inventory.remove(5370); inventory.put(12941, 6);
        FarmingSupplyPlan plan = new FarmingSupplyPlan(config);
        assertFalse(plan.ready(bag(inventory)));
        FarmingSupplyPlan.Step step = plan.next(bag(inventory), Map.of());
        assertEquals(FarmingSupplyPlan.Kind.DEPOSIT, step.kind);
        assertEquals(12941, step.item);
    }

    @Test void mixedPaymentsAreNormalizedAndOnlyMissingNotesWithdrawn()
    {
        Map<Integer, Integer> inventory = complete();
        inventory.put(5969, 2); inventory.put(5968, 2);
        FarmingSupplyPlan plan = new FarmingSupplyPlan(config);
        FarmingSupplyPlan.Step normalize = plan.next(bag(inventory), Map.of(5968, 2));
        assertEquals(FarmingSupplyPlan.Kind.DEPOSIT, normalize.kind);
        assertEquals(5968, normalize.item);
        inventory.remove(5968);
        FarmingSupplyPlan.Step withdraw = plan.next(bag(inventory), Map.of(5968, 4));
        assertEquals(4, withdraw.amount);
        assertEquals(5968, withdraw.item);
        assertEquals(5969, withdraw.received);
        assertTrue(withdraw.noted);
    }

    @Test void runesAreOptionalAndPartialBankStockIsEnough()
    {
        FarmingSupplyPlan plan = new FarmingSupplyPlan(config);
        assertEquals(FarmingSupplyPlan.Kind.READY, plan.next(bag(complete()), Map.of()).kind);
        FarmingSupplyPlan.Step runes = plan.next(bag(complete()), Map.of(556, 17));
        assertEquals(17, runes.amount);
        assertEquals(556, runes.item);
        Map<Integer, Integer> inventory = complete(); inventory.put(556, 90);
        assertEquals(10, plan.next(bag(inventory), Map.of(556, 500)).amount);
    }

    @Test void fullInventoryBanksUnusedItemsBeforeRequiredWithdrawals()
    {
        Map<Integer, Integer> inventory = complete(); inventory.put(5370, 1); inventory.put(5343, 1);
        FarmingSupplyPlan.Step step = new FarmingSupplyPlan(config).next(new FarmingActionPolicy.Inventory(inventory, 0, 15, 100), Map.of(5370, 5));
        assertEquals(FarmingSupplyPlan.Kind.DEPOSIT, step.kind);
        assertEquals(5343, step.item);
    }

    @Test void completeBagDoesNotVisitBankOrRestockAfterEachPlant()
    {
        FakeBank bank = new FakeBank(); bank.items.putAll(complete());
        FarmingRunSupplies supplies = new FarmingRunSupplies(bank, () -> 1000);
        assertTrue(supplies.ensure(config, () -> false));
        bank.items.put(5370, 5); bank.items.put(5969, 5);
        assertTrue(supplies.ensure(config, () -> false));
        assertEquals(0, bank.opens);
        supplies.reset();
        bank.stock.put(5370, 1); bank.stock.put(5968, 1);
        assertFalse(supplies.ensure(config, () -> false));
        assertEquals(1, bank.opens);
    }

    @Test void preparationCompletesOnlyAfterInventoryReceiptsAndBankClosure()
    {
        FakeBank bank = new FakeBank();
        bank.items.put(5370, 2);
        bank.stock.putAll(Map.of(5370, 4, 952, 1, 5341, 1, 5968, 6, 995, 30000));
        FarmingRunSupplies supplies = new FarmingRunSupplies(bank, () -> 1000);
        for (int i = 0; i < 10 && !supplies.prepared(); i++) supplies.ensure(config, () -> false);
        assertTrue(supplies.prepared());
        assertEquals(6, bank.items.get(5370));
        assertEquals(6, bank.items.get(5969));
        assertFalse(bank.open);
    }

    @Test void emptyBagWithoutContainerOpensBankAndPreparesFullRun()
    {
        FakeBank bank = new FakeBank();
        bank.stock.putAll(Map.of(5370, 6, 952, 1, 5341, 1, 5968, 6, 556, 100, 563, 100, 995, 30000));
        assertFalse(bank.snapshot().ready);
        FarmingRunSupplies supplies = new FarmingRunSupplies(bank, () -> 1000);
        for (int i = 0; i < 10 && !supplies.prepared(); i++) supplies.ensure(config, () -> false);
        assertTrue(bank.opens > 0);
        assertTrue(supplies.prepared());
        assertEquals(6, bank.items.get(5370));
        assertEquals(6, bank.items.get(5969));
        assertEquals(100, bank.items.get(556));
        assertEquals(100, bank.items.get(563));
        assertFalse(bank.open);
    }

    @Test void completeToolsAndSaplingsStillVisitBankToTopUpTravelMoney()
    {
        FakeBank bank = new FakeBank(); bank.items.putAll(complete()); bank.items.put(995, 1200);
        bank.stock.put(995, 100000);
        FarmingRunSupplies supplies = new FarmingRunSupplies(bank, () -> 1000);
        assertFalse(supplies.ensure(config, () -> false));
        assertEquals(1, bank.opens);
        assertEquals(30000, bank.items.get(995));
        assertEquals(71200, bank.stock.get(995));
        assertTrue(supplies.ensure(config, () -> false));
        assertFalse(bank.open);
        bank.items.put(995, 28000); // Spending during this run must not trigger restocking.
        assertTrue(supplies.ensure(config, () -> false));
        assertEquals(28000, bank.items.get(995));
    }

    @Test void insufficientBankMoneyBlocksTravelWithMissingAmount()
    {
        FakeBank bank = new FakeBank(); bank.items.putAll(complete()); bank.items.put(995, 1200);
        bank.stock.put(995, 1000);
        FarmingRunSupplies supplies = new FarmingRunSupplies(bank, () -> 1000);
        assertFalse(supplies.ensure(config, () -> false));
        assertFalse(supplies.prepared());
        assertEquals(0, bank.executions);
        assertTrue(supplies.status().contains("27800 Coins for travel and tree removal"));
    }

    @Test void coinWithdrawalFailureIsMandatoryAndDoesNotReleaseRun()
    {
        FakeBank bank = new FakeBank(); bank.items.putAll(complete()); bank.items.put(995, 1200);
        bank.stock.put(995, 100000); bank.fail = true;
        FarmingRunSupplies supplies = new FarmingRunSupplies(bank, () -> 1000);
        assertFalse(supplies.ensure(config, () -> false));
        assertFalse(supplies.prepared());
        assertTrue(supplies.status().contains("not confirmed"));
    }

    @Test void coinStackUsesOnlyOneSlotAndExcessIsKept()
    {
        FarmingSupplyPlan plan = new FarmingSupplyPlan(config);
        Map<Integer, Integer> inventory = complete(); inventory.remove(995);
        FarmingSupplyPlan.Step step = plan.next(new FarmingActionPolicy.Inventory(inventory, 2, 15, 100), Map.of(995, 100000));
        assertEquals(FarmingSupplyPlan.Kind.WITHDRAW, step.kind);
        assertEquals(995, step.item);
        assertEquals(30000, step.amount);
        assertFalse(step.noted);
        inventory.put(995, 50000);
        assertTrue(plan.ready(bag(inventory)));
        assertEquals(FarmingSupplyPlan.Kind.READY, plan.next(bag(inventory), Map.of(995, 100000)).kind);
    }

    @Test void failedMandatoryWithdrawalCannotReleaseRun()
    {
        FakeBank bank = new FakeBank(); bank.items.putAll(complete()); bank.items.put(5370, 2);
        bank.stock.put(5370, 4); bank.fail = true;
        FarmingRunSupplies supplies = new FarmingRunSupplies(bank, () -> 1000);
        assertFalse(supplies.ensure(config, () -> false));
        assertFalse(supplies.prepared());
        assertTrue(supplies.status().contains("not confirmed"));
    }

    @Test void failedOptionalRuneWithdrawalDoesNotBlockRun()
    {
        FakeBank bank = new FakeBank(); bank.items.putAll(complete()); bank.stock.put(556, 100); bank.open = true; bank.fail = true;
        FarmingRunSupplies supplies = new FarmingRunSupplies(bank, () -> 1000);
        assertFalse(supplies.ensure(config, () -> false));
        assertTrue(supplies.ensure(config, () -> false));
        assertTrue(supplies.prepared());
    }

    @Test void cancellationAndLiveBankFailureDoNotReadStockOrWithdraw()
    {
        FakeBank bank = new FakeBank();
        FarmingRunSupplies supplies = new FarmingRunSupplies(bank, () -> 1000);
        assertFalse(supplies.ensure(config, () -> true));
        assertEquals(0, bank.opens);
        bank.allowOpen = false;
        assertFalse(supplies.ensure(config, () -> false));
        assertEquals(0, bank.stockReads);
        assertEquals(0, bank.executions);
    }

    private final OcFarmingConfig mixed = new OcFarmingConfig() {
        @Override public boolean autoTrees() { return true; }
        @Override public boolean autoFruitTrees() { return true; }
        @Override public boolean autoVisit() { return true; }
        @Override public FarmingTree tree() { return FarmingTree.WILLOW; }
        @Override public FarmingFruitTree fruitTree() { return FarmingFruitTree.CURRY; }
    };

    private OcFarmingPlugin.View growingView()
    {
        Map<FarmingPatchData, FarmingObservation> observations = new EnumMap<>(FarmingPatchData.class);
        Map<FarmingPatchData, FarmingObservation.Crop> protectedCrops = new EnumMap<>(FarmingPatchData.class);
        for (FarmingPatchData patch : FarmingPatchData.values())
        {
            observations.put(patch, new FarmingObservation(patch.fruit,
                patch.fruit ? FarmingFruitTree.CURRY.start : FarmingTree.WILLOW.start, 900, 5000, 6000));
            protectedCrops.put(patch, observations.get(patch).crop());
        }
        return new OcFarmingPlugin.View("", FarmingPatchData.LUMBRIDGE_TREE.region, observations, 1, true, protectedCrops);
    }

    private OcFarmingPlugin.View with(OcFarmingPlugin.View view, FarmingPatchData patch, FarmingObservation observation, boolean paid)
    {
        Map<FarmingPatchData, FarmingObservation> observations = new EnumMap<>(FarmingPatchData.class);
        observations.putAll(view.patches);
        if (observation == null) observations.remove(patch); else observations.put(patch, observation);
        Map<FarmingPatchData, FarmingObservation.Crop> protection = new EnumMap<>(FarmingPatchData.class);
        protection.putAll(view.protectedCrops);
        if (paid && observation != null) protection.put(patch, observation.crop()); else protection.remove(patch);
        return new OcFarmingPlugin.View("", view.liveRegion, observations, 1, true, protection);
    }

    private OcFarmingPlugin.View empty(OcFarmingPlugin.View view, FarmingPatchData patch)
    { return with(view, patch, new FarmingObservation(patch.fruit, 3, 1000, 0, 0), false); }

    @Test void oneWillowDoesNotRequireBananasFruitSaplingsOrFruitLevel()
    {
        OcFarmingPlugin.View view = empty(growingView(), FarmingPatchData.LUMBRIDGE_TREE);
        FarmingSupplyPlan plan = new FarmingSupplyPlan(mixed, view, 1000);
        assertEquals(1, plan.patches);
        assertEquals(30, plan.levelRequired);
        assertEquals(1, plan.saplings.get(0).amount);
        FakeBank bank = new FakeBank(); bank.level = 30;
        bank.stock.putAll(Map.of(FarmingTree.WILLOW.sapling(), 1, FarmingTree.WILLOW.payment(), 1,
            952, 1, 5341, 1, 995, 5000));
        FarmingRunSupplies supplies = new FarmingRunSupplies(bank, () -> 1000);
        for (int i = 0; i < 10 && !supplies.prepared(); i++) supplies.ensure(mixed, view, () -> false);
        assertTrue(supplies.prepared(), supplies.status());
        assertEquals(0, bank.items.getOrDefault(FarmingFruitTree.CURRY.paymentNote(), 0));
        assertEquals(0, bank.items.getOrDefault(FarmingFruitTree.CURRY.sapling(), 0));
        assertFalse(bank.open);
    }

    @Test void localModeOnlyBudgetsWorkInCurrentRegion()
    {
        OcFarmingConfig local = new OcFarmingConfig() {
            @Override public boolean autoTrees() { return true; }
            @Override public boolean autoFruitTrees() { return true; }
            @Override public boolean autoVisit() { return false; }
        };
        OcFarmingPlugin.View view = empty(empty(growingView(), FarmingPatchData.LUMBRIDGE_TREE), FarmingPatchData.CATHERBY_FRUIT);
        FarmingSupplyPlan plan = new FarmingSupplyPlan(local, view, 1000);
        assertEquals(1, plan.patches);
        assertEquals(FarmingTree.OAK.sapling(), plan.saplings.get(0).item);
    }

    @Test void protectionOnlyUsesExistingCropWithoutSaplingOrPlantingLevel()
    {
        OcFarmingPlugin.View view = with(growingView(), FarmingPatchData.CATHERBY_FRUIT,
            new FarmingObservation(true, FarmingFruitTree.APPLE.start, 900, 5000, 6000), false);
        FarmingSupplyPlan plan = new FarmingSupplyPlan(mixed, view, 1000);
        assertEquals(1, plan.patches);
        assertTrue(plan.saplings.isEmpty());
        assertEquals(0, plan.levelRequired);
        assertTrue(plan.ready(bag(Map.of(FarmingFruitTree.APPLE.paymentNote(), 9, 995, 5000))));
        assertTrue(plan.required.stream().noneMatch(item -> item.item == FarmingFruitTree.CURRY.payment()));
    }

    @Test void diseasedTreeNeedsSecateursInsteadOfReplacementSapling()
    {
        OcFarmingPlugin.View view = with(growingView(), FarmingPatchData.LUMBRIDGE_TREE,
            new FarmingObservation(FarmingTree.WILLOW.diseased, 1000, 0, 0), true);
        FarmingSupplyPlan plan = new FarmingSupplyPlan(mixed, view, 1000);
        assertTrue(plan.saplings.isEmpty());
        assertFalse(plan.ready(bag(Map.of(995, 5000))));
        assertTrue(plan.ready(bag(Map.of(995, 5000, 7409, 1))));
        assertEquals(5329, plan.next(bag(Map.of(995, 5000)), Map.of(5329, 1)).item);
    }

    @Test void unknownAndDuePatchesStillReserveReplantSupplies()
    {
        OcFarmingPlugin.View view = with(growingView(), FarmingPatchData.LUMBRIDGE_TREE, null, false);
        view = with(view, FarmingPatchData.CATHERBY_FRUIT,
            new FarmingObservation(true, FarmingFruitTree.CURRY.start, 1, 900, 1000), true);
        FarmingSupplyPlan plan = new FarmingSupplyPlan(mixed, view, 1000);
        assertEquals(2, plan.patches);
        assertEquals(2, plan.saplings.size());
        assertTrue(plan.required.stream().anyMatch(item -> item.item == FarmingFruitTree.CURRY.payment() && item.amount == 5));
    }

    @Test void onlyPendingLocationsContributeToSaplingAndPaymentQuantities()
    {
        OcFarmingPlugin.View view = empty(empty(growingView(), FarmingPatchData.LUMBRIDGE_TREE), FarmingPatchData.VARROCK_TREE);
        FarmingSupplyPlan plan = new FarmingSupplyPlan(mixed, view, 1000);
        assertEquals(2, plan.patches);
        assertEquals(1, plan.saplings.size());
        assertEquals(2, plan.saplings.get(0).amount);
        assertTrue(plan.required.stream().anyMatch(item -> item.item == FarmingTree.WILLOW.payment() && item.amount == 2));
        assertTrue(plan.required.stream().anyMatch(item -> item.item == 995 && item.amount == 10000));
    }

    @Test void pruningSuppliesAreMandatoryAndOtherMissingStockPreventsWithdrawal()
    {
        OcFarmingPlugin.View view = with(growingView(), FarmingPatchData.LUMBRIDGE_TREE,
            new FarmingObservation(FarmingTree.WILLOW.diseased, 1000, 0, 0), true);
        FakeBank bank = new FakeBank(); bank.stock.put(5329, 1);
        FarmingRunSupplies supplies = new FarmingRunSupplies(bank, () -> 1000);
        assertFalse(supplies.ensure(mixed, view, () -> false));
        assertEquals(0, bank.executions);
        supplies.reset(); bank.items.put(995, 5000); bank.fail = true;
        assertFalse(supplies.ensure(mixed, view, () -> false));
        assertFalse(supplies.prepared());
        assertTrue(supplies.status().contains("not confirmed"));
    }

    @Test void consumedSuppliesAreNotReplenishedAndNewDuePatchGetsFreshBudget()
    {
        OcFarmingPlugin.View view = empty(growingView(), FarmingPatchData.LUMBRIDGE_TREE);
        FakeBank bank = new FakeBank(); bank.level = 30;
        bank.items.putAll(Map.of(FarmingTree.WILLOW.sapling(), 1, FarmingTree.WILLOW.paymentNote(), 1,
            952, 1, 5341, 1, 995, 5000));
        FarmingRunSupplies supplies = new FarmingRunSupplies(bank, () -> 1000);
        assertTrue(supplies.ensure(mixed, view, () -> false));
        bank.items.remove(FarmingTree.WILLOW.sapling());
        bank.items.put(995, 4800);
        view = with(view, FarmingPatchData.LUMBRIDGE_TREE, growingView().patches.get(FarmingPatchData.LUMBRIDGE_TREE), false);
        assertTrue(supplies.ensure(mixed, view, () -> false));
        assertEquals(0, bank.opens);
        // Another willow becomes ready while the first still needs payment.
        view = empty(view, FarmingPatchData.VARROCK_TREE);
        assertFalse(supplies.ensure(mixed, view, () -> false));
        assertFalse(supplies.prepared());
        assertEquals(1, bank.opens);
        assertTrue(supplies.status().contains("Willow sapling"));
    }

    @Test void changedWorkClearsMissingStockBackoff()
    {
        FakeBank bank = new FakeBank(); bank.level = 30;
        bank.items.putAll(Map.of(FarmingTree.WILLOW.sapling(), 1, FarmingTree.WILLOW.paymentNote(), 1,
            952, 1, 5341, 1, 995, 10000));
        FarmingRunSupplies supplies = new FarmingRunSupplies(bank, () -> 1000);
        OcFarmingPlugin.View willow = empty(growingView(), FarmingPatchData.LUMBRIDGE_TREE);
        OcFarmingPlugin.View both = empty(willow, FarmingPatchData.CATHERBY_FRUIT);
        bank.level = 99;
        assertFalse(supplies.ensure(mixed, both, () -> false));
        assertTrue(supplies.ensure(mixed, willow, () -> false));
        assertTrue(supplies.prepared());
    }

    @Test void allGrowingProtectedPatchesNeedNoBankOrSupplies()
    {
        FakeBank bank = new FakeBank();
        FarmingRunSupplies supplies = new FarmingRunSupplies(bank, () -> 1000);
        assertTrue(supplies.ensure(mixed, growingView(), () -> false));
        assertEquals(0, bank.opens);
        assertEquals(0, new FarmingSupplyPlan(mixed, growingView(), 1000).patches);
    }

    @Test void bagWithUnusedItemsIsNotReadyEvenWithToolsAndSaplings()
    {
        Map<Integer, Integer> inventory = complete();
        inventory.put(1519, 10); // 10 willow logs in backpack
        FarmingSupplyPlan plan = new FarmingSupplyPlan(config);
        assertTrue(plan.hasUnused(bag(inventory)));
    }

    @Test void nextBanksUnusedItemsBeforeWithdrawingSupplies()
    {
        Map<Integer, Integer> inventory = complete();
        inventory.remove(5370); // missing saplings
        inventory.put(1519, 5); // 5 willow logs (unused item)
        FarmingSupplyPlan plan = new FarmingSupplyPlan(config);
        FarmingSupplyPlan.Step step = plan.next(bag(inventory), Map.of(5370, 10));
        assertEquals(FarmingSupplyPlan.Kind.DEPOSIT, step.kind);
        assertEquals(1519, step.item);
        assertEquals(5, step.amount);
    }

    @Test void runSuppliesDepositsAllWhenInventoryContainsUnusedItems()
    {
        FakeBank bank = new FakeBank();
        bank.items.put(1519, 5); // 5 willow logs in backpack
        bank.stock.putAll(Map.of(5370, 6, 952, 1, 5341, 1, 5968, 6, 995, 30000));
        FarmingRunSupplies supplies = new FarmingRunSupplies(bank, () -> 1000);
        assertFalse(supplies.ensure(config, () -> false));
        assertEquals(1, bank.opens);
        assertEquals(1, bank.depositAllCalls);
        assertTrue(bank.items.isEmpty());
        for (int i = 0; i < 10 && !supplies.prepared(); i++) supplies.ensure(config, () -> false);
        assertTrue(supplies.prepared());
        assertEquals(6, bank.items.get(5370));
        assertFalse(bank.items.containsKey(1519));
        assertFalse(bank.open);
    }

    private static class FakeBank extends FarmingBankActions
    {
        final Map<Integer, Integer> items = new HashMap<>(), stock = new HashMap<>();
        boolean open, fail, allowOpen = true;
        int opens, stockReads, executions, depositAllCalls, level = 15;
        @Override FarmingActionPolicy.Inventory inventory() { return new FarmingActionPolicy.Inventory(items, 20, level, 100); }
        @Override FarmingGameActions.Snapshot snapshot()
        { return new FarmingGameActions.Snapshot(inventory(), true, !items.isEmpty(), false, false, "", null, -1); }
        @Override boolean isOpen() { return open; }
        @Override boolean open(BooleanSupplier cancelled, boolean travel) { opens++; open = allowOpen; return open; }
        @Override Map<Integer, Integer> bank() { stockReads++; return stock; }
        @Override boolean depositAll()
        {
            depositAllCalls++;
            if (fail) return false;
            items.forEach((id, count) -> stock.merge(id, count, Integer::sum));
            items.clear();
            return true;
        }
        @Override boolean execute(FarmingSupplyPlan.Step step, BooleanSupplier cancelled)
        {
            executions++;
            if (fail || cancelled.getAsBoolean()) return false;
            if (step.kind == FarmingSupplyPlan.Kind.DEPOSIT_ALL) return depositAll();
            if (step.kind == FarmingSupplyPlan.Kind.WITHDRAW)
            { stock.merge(step.item, -step.amount, Integer::sum); items.merge(step.received, step.amount, Integer::sum); }
            else { items.merge(step.item, -step.amount, Integer::sum); stock.merge(step.item, step.amount, Integer::sum); }
            return true;
        }
        @Override void await(BooleanSupplier finished, int timeout) { finished.getAsBoolean(); }
        @Override boolean depositAll(BooleanSupplier cancelled)
        {
            if (cancelled.getAsBoolean()) return false;
            return depositAll();
        }
        @Override boolean close(BooleanSupplier cancelled) { open = false; return true; }
    }
}
