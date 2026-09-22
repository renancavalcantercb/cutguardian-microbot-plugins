package net.runelite.client.plugins.microbot.ocfarmingew;

import java.lang.reflect.Method;
import java.util.*;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static net.runelite.client.plugins.microbot.ocfarmingew.FarmingActionPolicy.*;

class FarmingFruitTreeTest
{
    private FarmingObservation fruit(int raw) { return FarmingObservation.observe(true, raw, 1000, null, false); }
    private Inventory bag(Map<Integer, Integer> items) { return new Inventory(items, 12, 30, 100); }
    private Plan plan(int raw, Map<Integer, Integer> items)
    { return FarmingTreePolicy.next(fruit(raw), bag(items), FarmingFruitTree.APPLE, true, false); }

    @Test void everyVarbitMatchesTheClientsTimeTrackingFruitDecoder() throws Exception
    {
        Class<?> implementation = Class.forName("net.runelite.client.plugins.timetracking.farming.PatchImplementation");
        Object decoder = Arrays.stream(implementation.getEnumConstants()).filter(e -> ((Enum<?>) e).name().equals("FRUIT_TREE")).findFirst().orElseThrow();
        Method decode = implementation.getDeclaredMethod("forVarbitValue", int.class); decode.setAccessible(true);
        for (int raw = 0; raw <= 255; raw++)
        {
            Object expected = decode.invoke(decoder, raw);
            assertNotNull(expected, "raw=" + raw);
            String crop = ((Enum<?>) get(expected, "getProduce")).name();
            String state = ((Enum<?>) get(expected, "getCropState")).name();
            int stage = (Integer) get(expected, "getStage");
            FarmingObservation actual = fruit(raw);
            if (crop.equals("WEEDS"))
            {
                assertNull(actual.crop());
                assertEquals(stage == 0 ? FarmingObservation.State.EMPTY : FarmingObservation.State.WEEDS, actual.state(), "raw=" + raw);
                continue;
            }
            assertEquals(crop, actual.crop().name(), "raw=" + raw);
            switch (state)
            {
                case "GROWING":
                    assertEquals(stage == 6 ? FarmingObservation.State.CHECK_HEALTH : FarmingObservation.State.GROWING, actual.state());
                    if (stage < 6) assertEquals(stage, actual.growthStage());
                    break;
                case "HARVESTABLE":
                    assertTrue(stage == 0 ? actual.state() == FarmingObservation.State.CHECKED || actual.state() == FarmingObservation.State.STUMP
                        : actual.state() == FarmingObservation.State.HARVEST, "raw=" + raw);
                    break;
                default: assertEquals(state, actual.state().name());
            }
        }
    }
    private Object get(Object target, String name) throws Exception
    { Method method = target.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(target); }

    @Test void sixteenHourEstimateRemainsDueUntilObservedAgain()
    {
        for (FarmingFruitTree kind : FarmingFruitTree.values())
        {
            FarmingObservation planted = fruit(kind.start);
            assertEquals(1000 + 16 * 3600, planted.latestReadyAt);
            assertEquals(1000 + 800 * 60, planted.earliestReadyAt);
            assertTrue(planted.isDue(planted.latestReadyAt));
            assertEquals(FarmingObservation.State.GROWING, planted.state());
            assertEquals(FarmingObservation.State.CHECK_HEALTH, fruit(kind.start + 26).state());
            assertEquals(FarmingObservation.State.CHECKED, fruit(kind.start + 6).state());
            assertEquals(FarmingObservation.State.STUMP, fruit(kind.start + 25).state());
        }
    }

    @Test void sharedRegionKeepsTreeAndFruitVarbitsAndHistorySeparate()
    {
        FarmingTracker tracker = new FarmingTracker(); tracker.switchProfile("test");
        tracker.observe(FarmingPatchData.GNOME_TREE, 8, 1000, false);
        tracker.observe(FarmingPatchData.GNOME_FRUIT, 8, 1000, false);
        assertEquals(FarmingObservation.Crop.OAK, tracker.snapshot().get(FarmingPatchData.GNOME_TREE).crop());
        assertEquals(FarmingObservation.Crop.APPLE, tracker.snapshot().get(FarmingPatchData.GNOME_FRUIT).crop());
        assertNotEquals(FarmingPatchData.GNOME_TREE.key(), FarmingPatchData.GNOME_FRUIT.key());
        String stored = tracker.snapshot().get(FarmingPatchData.GNOME_FRUIT).encode();
        assertTrue(stored.startsWith("F1:"));
        tracker.switchProfile("other");
        tracker.restore(FarmingPatchData.GNOME_TREE, stored, 1001);
        assertTrue(tracker.snapshot().isEmpty());
        tracker.restore(FarmingPatchData.GNOME_FRUIT, stored, 1001);
        assertEquals(58600, tracker.snapshot().get(FarmingPatchData.GNOME_FRUIT).latestReadyAt);
    }

    @Test void catherbyAllotmentTransmissionIsExcludedAndAllDestinationsAreInBounds()
    {
        assertFalse(FarmingPatchData.CATHERBY_FRUIT.includes(2825, 3445, 0));
        assertEquals(-1, FarmingPatchData.regionAt(new WorldPoint(2825, 3445, 0)));
        for (FarmingPatchData patch : FarmingPatchData.values()) if (patch.fruit)
        {
            assertTrue(patch.includes(patch.visitX, patch.visitY, 0));
            assertFalse(patch.includes(patch.visitX, patch.visitY, 1));
        }
        assertEquals(4772, FarmingPatchData.KASTORI_FRUIT.varbit);
        assertEquals(12765, FarmingPatchData.KASTORI_FRUIT.leprechaunId());
    }

    @Test void plantingRequiresSaplingLevelSpadeAndNineSweetcorn()
    {
        assertEquals(Action.PLANT, plan(3, Map.of(5496, 1, 952, 1, 5987, 9)).action);
        assertEquals(Action.BLOCKED, plan(3, Map.of(5496, 1, 952, 1, 5987, 8)).action);
        assertEquals(Action.BLOCKED, plan(3, Map.of(12946, 1, 952, 1, 5987, 9)).action);
        assertEquals(Action.BLOCKED, FarmingTreePolicy.next(fruit(3), new Inventory(Map.of(5496, 1, 952, 1, 5987, 9), 20, 26, 0), FarmingFruitTree.APPLE, true, false).action);
        Plan protection = FarmingTreePolicy.next(fruit(35), bag(Map.of(5387, 4)), FarmingFruitTree.APPLE, true, false);
        assertEquals(Action.PAY, protection.action); // Protect the existing banana, not the configured apple.
        assertEquals(4, protection.amount);
        assertEquals(5387, protection.item);
    }

    @Test void picksAndNotesFruitBeforePayingForRemoval()
    {
        assertEquals(Action.CHECK_HEALTH, plan(34, Map.of()).action);
        assertEquals(Action.HARVEST, plan(20, Map.of()).action);
        assertEquals(Action.NOTE_FRUIT, plan(14, Map.of(1955, 6, 995, 200)).action);
        assertEquals(Action.REMOVE_TREE, plan(14, Map.of(1956, 6, 995, 200)).action);
        assertEquals(Action.CLEAR, plan(33, Map.of(952, 1)).action);
        assertEquals(Action.PRUNE, plan(21, Map.of(5329, 1)).action);
    }

    @Test void harvestingAndNotingRequireMatchingInventoryReceipts()
    {
        Plan pick = plan(20, Map.of());
        FarmingObservation after = FarmingObservation.observe(true, 19, 1001, null, false);
        assertFalse(confirmed(pick, fruit(20), after, bag(Map.of()), bag(Map.of()), ""));
        assertTrue(confirmed(pick, fruit(20), after, bag(Map.of()), bag(Map.of(1955, 1)), ""));
        Plan note = plan(14, Map.of(1955, 6));
        assertFalse(confirmed(note, fruit(14), fruit(14), bag(Map.of(1955, 6)), bag(Map.of()), ""));
        assertTrue(confirmed(note, fruit(14), fruit(14), bag(Map.of(1955, 6)), bag(Map.of(1956, 6)), ""));
    }

    @Test void mixedRunMergesPaymentsAndCountsEachSaplingSeparately()
    {
        OcFarmingEWConfig config = new OcFarmingEWConfig() {
            public boolean autoTrees() { return true; }
            public boolean autoFruitTrees() { return true; }
            public FarmingTree tree() { return FarmingTree.WILLOW; }
            public FarmingFruitTree fruitTree() { return FarmingFruitTree.BANANA; }
        };
        FarmingSupplyPlan supply = new FarmingSupplyPlan(config);
        assertEquals(11, supply.patches);
        assertEquals(33, supply.levelRequired);
        assertEquals(6, required(supply, 5371).amount);
        assertEquals(5, required(supply, 5497).amount);
        assertEquals(26, required(supply, 5386).amount); // 6 willow + 5 * 4 banana.
        assertEquals(55000, required(supply, 995).amount);
        assertEquals(1, supply.required.stream().filter(i -> i.item == 5386).count());
    }

    @Test void fruitOnlyRunDoesNotRequireRegularTreeLevelOrSaplings()
    {
        OcFarmingEWConfig config = new OcFarmingEWConfig() {
            public boolean autoFruitTrees() { return true; }
            public FarmingTree tree() { return FarmingTree.MAGIC; }
        };
        FarmingSupplyPlan supply = new FarmingSupplyPlan(config);
        assertEquals(5, supply.patches);
        assertEquals(27, supply.levelRequired);
        assertEquals(45, required(supply, 5986).amount);
        assertTrue(supply.required.stream().noneMatch(i -> i.item == 5374));
        assertTrue(supply.ready(bag(Map.of(5496, 5, 5987, 45, 952, 1, 5341, 1, 995, 25000))));
    }

    @Test void mixedRunNormalizesFruitSaplingNotesAndDoesNotUnderfundSharedPayment()
    {
        OcFarmingEWConfig config = new OcFarmingEWConfig() {
            public boolean autoTrees() { return true; }
            public boolean autoFruitTrees() { return true; }
            public FarmingTree tree() { return FarmingTree.WILLOW; }
            public FarmingFruitTree fruitTree() { return FarmingFruitTree.BANANA; }
        };
        FarmingSupplyPlan supply = new FarmingSupplyPlan(config);
        Map<Integer,Integer> bag = new HashMap<>(Map.of(5371, 6, 12947, 5, 5387, 6, 952, 1, 5341, 1, 995, 55000));
        assertEquals(FarmingSupplyPlan.Kind.BLOCKED, supply.next(bag(bag), Map.of(5386, 19)).kind);
        FarmingSupplyPlan.Step normalize = supply.next(bag(bag), Map.of(5386, 20));
        assertEquals(FarmingSupplyPlan.Kind.DEPOSIT, normalize.kind);
        assertEquals(12947, normalize.item);
        bag.remove(12947); bag.put(5497, 5);
        FarmingSupplyPlan.Step payment = supply.next(bag(bag), Map.of(5386, 20));
        assertEquals(20, payment.amount);
        assertEquals(5387, payment.received);
    }
    @Test void fruitPreparationLeavesRoomForFirstFruitAndItsNewNoteStack()
    {
        FarmingSupplyPlan plan = new FarmingSupplyPlan(new OcFarmingEWConfig() {
            public boolean autoFruitTrees() { return true; }
        });
        Map<Integer, Integer> items = Map.of(5496, 5, 5987, 45, 952, 1, 5341, 1, 995, 25000, 2347, 1);
        Inventory tight = new Inventory(items, 1, 30, 100);
        assertFalse(plan.ready(tight));
        FarmingSupplyPlan.Step space = plan.next(tight, Map.of());
        assertEquals(FarmingSupplyPlan.Kind.DEPOSIT, space.kind);
        assertEquals(2347, space.item);
        assertTrue(plan.ready(new Inventory(items, 2, 30, 100)));
    }

    private FarmingSupplyPlan.Required required(FarmingSupplyPlan plan, int item)
    { return plan.required.stream().filter(i -> i.item == item).findFirst().orElseThrow(); }
}
