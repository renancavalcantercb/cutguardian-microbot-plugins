package net.runelite.client.plugins.microbot.ocfarmingew;

import java.util.*;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static net.runelite.client.plugins.microbot.ocfarmingew.FarmingActionPolicy.*;

class FarmingTreeTest
{
    private FarmingObservation tree(int raw) { return FarmingObservation.observe(raw, 1000, null, false); }
    private Inventory inventory(int level, Map<Integer, Integer> items) { return new Inventory(items, 15, level, 100); }
    private Plan plan(int raw, FarmingTree chosen, int level, Map<Integer, Integer> items)
    { return FarmingTreePolicy.next(tree(raw), inventory(level, items), chosen, true, false); }

    @Test void removedAllotmentRegionsAreNeverTreeDestinations()
    {
        assertEquals(-1, FarmingPatchData.regionAt(new WorldPoint(3053, 3309, 0)));
        assertEquals(-1, FarmingPatchData.regionAt(new WorldPoint(2672, 3372, 0)));
        assertEquals(6, java.util.Arrays.stream(FarmingPatchData.values()).filter(p -> !p.fruit).count());
    }

    @Test void allFiveTreesHaveDistinctGrowingCheckHealthCheckedAndStumpStates()
    {
        for (FarmingTree type : FarmingTree.values())
        {
            for (int raw = type.start; raw < type.check; raw++)
            {
                assertEquals(type.observedCrop, tree(raw).crop());
                assertEquals(FarmingObservation.State.GROWING, tree(raw).state());
                assertEquals(raw - type.start, tree(raw).growthStage());
            }
            assertEquals(FarmingObservation.State.CHECK_HEALTH, tree(type.check).state());
            assertEquals(FarmingObservation.State.CHECKED, tree(type.check + 1).state());
            assertEquals(FarmingObservation.State.STUMP, tree(type.check + 2).state());
            assertEquals(0, tree(type.check).latestReadyAt);
        }
        for (int raw = 192; raw <= 197; raw++) assertEquals(FarmingObservation.State.CHECKED, tree(raw).state());
    }

    @Test void growthPredictionsUseFortyMinuteCyclesAndNeverBecomeCheckedByTime()
    {
        int[] cycles = {4, 6, 8, 10, 12};
        int index = 0;
        for (FarmingTree type : FarmingTree.values())
        {
            FarmingObservation observation = tree(type.start);
            assertEquals(1000 + cycles[index] * 2400L, observation.latestReadyAt);
            assertEquals(1000 + (cycles[index++] - 1) * 2400L, observation.earliestReadyAt);
            assertTrue(observation.isDue(100000));
            assertEquals(FarmingObservation.State.GROWING, observation.state());
        }
    }

    @Test void diseaseDeathAndUnusedGapsNeverBecomeAnEmptyPatch()
    {
        for (FarmingTree type : FarmingTree.values())
        {
            for (int stage = 0; stage <= type.cycles; stage++)
            {
                boolean gap = stage == type.cycles - 1;
                assertEquals(gap ? FarmingObservation.State.UNKNOWN : FarmingObservation.State.DISEASED, tree(type.diseased + stage).state());
                assertEquals(gap ? FarmingObservation.State.UNKNOWN : FarmingObservation.State.DEAD, tree(type.dead + stage).state());
            }
        }
        for (int raw : new int[]{4, 7, 63, 72, 126, 198, 255}) assertEquals(FarmingObservation.State.UNKNOWN, tree(raw).state());
    }

    @Test void treePersistenceRejectsAllotmentDataAndKeepsOriginalEstimate()
    {
        FarmingTracker tracker = new FarmingTracker();
        tracker.switchProfile("account");
        tracker.observe(FarmingPatchData.LUMBRIDGE_TREE, 8, 1000, false);
        FarmingObservation original = tracker.snapshot().get(FarmingPatchData.LUMBRIDGE_TREE);
        assertNotNull(FarmingObservation.decode(original.encode(), 1500));
        assertNull(FarmingObservation.decode("1:8:1000:2800:3400", 1500));
        assertEquals(original.latestReadyAt, FarmingObservation.decode(original.encode(), 1500).latestReadyAt);
        tracker.switchProfile("other");
        assertTrue(tracker.snapshot().isEmpty());
        tracker.restore(FarmingPatchData.LUMBRIDGE_TREE, original.encode(), 1500);
        assertEquals(FarmingObservation.Crop.OAK, tracker.snapshot().get(FarmingPatchData.LUMBRIDGE_TREE).crop());
    }

    @Test void plantingUsesOneUnnotedSaplingAndSpadeInsteadOfSeedsOrDibber()
    {
        Plan oak = plan(3, FarmingTree.OAK, 15, Map.of(952, 1, 5370, 1, 5969, 1));
        assertEquals(Action.PLANT, oak.action);
        assertEquals(5370, oak.item);
        assertEquals(1, oak.amount);
        assertEquals(Action.BLOCKED, plan(3, FarmingTree.OAK, 14, Map.of(952, 1, 5370, 1, 5969, 1)).action);
        assertEquals(Action.BLOCKED, plan(3, FarmingTree.OAK, 15, Map.of(5343, 1, 5370, 1, 5969, 1)).action);
        assertEquals(Action.BLOCKED, plan(3, FarmingTree.OAK, 15, Map.of(952, 1, 5312, 1, 5969, 1)).action);
    }

    @Test void allTreePaymentsAcceptFullItemsOrNotesButRejectLooseOrPartialBaskets()
    {
        for (FarmingTree type : FarmingTree.values())
        {
            for (int payment : new int[]{type.payment, type.paymentNote})
                assertEquals(Action.PLANT, plan(3, type, type.level,
                    Map.of(952, 1, type.sapling, 1, payment, type.paymentAmount)).action);
            assertEquals(Action.BLOCKED, plan(3, type, type.level,
                Map.of(952, 1, type.sapling, 1, type.paymentNote, type.paymentAmount - 1)).action);
        }
        // Loose tomatoes cannot substitute for a full basket.
        assertEquals(Action.BLOCKED, plan(3, FarmingTree.OAK, 15, Map.of(952, 1, 5370, 1, 1982, 5)).action);
        assertEquals(Action.BLOCKED, plan(3, FarmingTree.OAK, 15, Map.of(952, 1, 5370, 1, 1983, 5)).action);
        assertEquals(Action.BLOCKED, plan(3, FarmingTree.OAK, 15, Map.of(952, 1, 5370, 1, 5966, 1)).action);
    }

    @Test void existingTreeIsProtectedWithItsOwnPaymentWhenSelectionChanges()
    {
        Plan protect = plan(8, FarmingTree.MAGIC, 15, Map.of(5969, 1));
        assertEquals(Action.PAY, protect.action);
        assertEquals(5969, protect.item);
        assertEquals(Action.NONE, FarmingTreePolicy.next(tree(8), inventory(15, Map.of()), FarmingTree.MAGIC, true, true).action);
    }

    @Test void checkHealthAlwaysPrecedesPaidRemovalAndNeedsNoSapling()
    {
        assertEquals(Action.CHECK_HEALTH, plan(12, FarmingTree.OAK, 15, Map.of(995, 200)).action);
        assertEquals(Action.REMOVE_TREE, plan(13, FarmingTree.OAK, 15, Map.of(995, 200)).action);
        assertEquals(Action.BLOCKED, plan(13, FarmingTree.OAK, 15, Map.of(995, 199)).action);
        assertEquals(Action.CLEAR, plan(14, FarmingTree.OAK, 15, Map.of(952, 1)).action);
        assertEquals(Action.CLEAR, plan(137, FarmingTree.OAK, 15, Map.of(952, 1)).action);
    }

    @Test void pruningRequiresSecateursAndEmptyPotsAreDiscarded()
    {
        assertEquals(Action.PRUNE, plan(73, FarmingTree.OAK, 15, Map.of(5329, 1)).action);
        assertEquals(Action.PRUNE, plan(73, FarmingTree.OAK, 15, Map.of(7409, 1)).action);
        assertEquals(Action.BLOCKED, plan(73, FarmingTree.OAK, 15, Map.of(6036, 1)).action);
        assertEquals(Action.DROP_POTS, plan(8, FarmingTree.OAK, 15, Map.of(5350, 1)).action);
    }

    @Test void missingOakPaymentNamesTheBasketAndProtectionOffAllowsPlanting()
    {
        Map<Integer, Integer> supplies = Map.of(952, 1, 5370, 1, 995, 1500000);
        Plan blocked = plan(3, FarmingTree.OAK, 15, supplies);
        assertEquals(Action.BLOCKED, blocked.action);
        assertTrue(blocked.reason.contains("1 Tomatoes(5)"));
        assertEquals(Action.PLANT, FarmingTreePolicy.next(tree(3), inventory(15, supplies), FarmingTree.OAK, false, false).action);
    }

    @Test void configurationDoesNotExposeLegacyAllotmentOptions()
    {
        java.util.Set<String> removed = java.util.Set.of("autoFarm", "crop", "payProtection", "soilCompost");
        for (java.lang.reflect.Method method : OcFarmingEWConfig.class.getMethods())
        {
            net.runelite.client.config.ConfigItem item = method.getAnnotation(net.runelite.client.config.ConfigItem.class);
            if (item != null) assertFalse(removed.contains(item.keyName()));
        }
    }

    @Test void receiptsRequireRealTransitionsAndExactConsumption()
    {
        Plan plant = plan(3, FarmingTree.OAK, 15, Map.of(952, 1, 5370, 2, 5969, 1));
        Inventory initial = inventory(15, Map.of(5370, 2, 995, 1000));
        Inventory planted = inventory(15, Map.of(5370, 1, 995, 1000));
        FarmingObservation grown = FarmingObservation.observe(8, 1001, null, false);
        assertFalse(confirmed(plant, tree(3), tree(8), initial, planted, ""));
        assertFalse(confirmed(plant, tree(3), grown, initial, initial, ""));
        assertTrue(confirmed(plant, tree(3), grown, initial, planted, ""));
        Plan check = FarmingActionPolicy.plan(Action.CHECK_HEALTH, 0);
        assertTrue(confirmed(check, tree(12), FarmingObservation.observe(13, 1001, null, false), initial, initial, ""));
        Plan remove = FarmingActionPolicy.plan(Action.REMOVE_TREE, 995);
        FarmingObservation empty = FarmingObservation.observe(3, 1001, null, false);
        assertFalse(confirmed(remove, tree(13), empty, initial, initial, "Yes"));
        assertTrue(confirmed(remove, tree(13), empty, initial, inventory(15, Map.of(995, 800)), ""));
        assertFalse(confirmed(remove, tree(13), empty, initial, inventory(15, Map.of(995, 799)), ""));
    }

    @Test void plannerDiscoversEnabledTreesAndDoesNotVisitDisabledLocations()
    {
        OcFarmingEWConfig config = new OcFarmingEWConfig() {
            @Override public boolean autoTrees() { return true; }
            @Override public boolean lumbridgeTree() { return false; }
        };
        FarmingVisitPlanner planner = new FarmingVisitPlanner();
        assertEquals(FarmingPatchData.VARROCK_TREE, planner.next(Map.of(), 1000, p -> p.tend(config), p -> p.enabled(config), true));
        planner.failed(FarmingPatchData.VARROCK_TREE, 1000);
        assertEquals(FarmingPatchData.FALADOR_TREE, planner.next(Map.of(), 1001, p -> p.tend(config), p -> p.enabled(config), true));
        OcFarmingEWConfig off = new OcFarmingEWConfig() {};
        assertNull(planner.next(Map.of(FarmingPatchData.LUMBRIDGE_TREE, tree(12)), 1000, p -> p.tend(off), p -> p.enabled(off), true));
    }

    @Test void canonicalTransmissionRegionsIncludeAliasesButKeepPatchKeysDistinct()
    {
        Set<String> keys = new HashSet<>();
        int trees = 0;
        for (FarmingPatchData patch : FarmingPatchData.values())
        {
            assertTrue(keys.add(patch.key()));
            trees++;
            WorldPoint point = new WorldPoint(patch.visitX, patch.visitY, 0);
            assertTrue(patch.includes(point.getX(), point.getY(), 0), patch.label);
            assertEquals(patch.region, FarmingPatchData.regionAt(point));
            assertFalse(patch.includes(point.getX(), point.getY(), 1));
        }
        assertEquals(11, trees);
        assertTrue(FarmingPatchData.FALADOR_TREE.acceptsRegion(12084));
        assertTrue(FarmingVisitPlanner.confirmed(FarmingPatchData.NEMUS_TREE, 0, 5427, Map.of(FarmingPatchData.NEMUS_TREE, tree(3))));
    }

    @Test void multiTileTreesCanBeApproachedFromTheFarEdge()
    {
        WorldPoint origin = new WorldPoint(3000, 3370, 0);
        WorldPoint east = origin.dx(3).dy(1);
        FarmingPatchTarget target = FarmingPatchTarget.rectangle(origin, 3, 3, Map.of(east, 0, origin.dx(1), 0));
        assertNotNull(target);
        assertEquals(origin, target.objectTile);
        assertEquals(east, target.standingTile);
    }

    @Test void removalDialogueCannotBeSelectedDuringProtection()
    {
        String chop = "Would you chop my tree down for me?";
        assertEquals(chop, FarmingOwnedDialogue.option(Action.REMOVE_TREE, "Select an Option", List.of(chop, "No.")));
        assertNull(FarmingOwnedDialogue.option(Action.PAY, "Select an Option", List.of(chop, "No.")));
        assertEquals("Yes.", FarmingOwnedDialogue.option(Action.REMOVE_TREE, "Pay 200 coins?", List.of("Yes.", "No.")));
        assertNull(FarmingOwnedDialogue.option(Action.CLEAR, "Remove your bank PIN?", List.of("Cancel")));
    }

    @Test void supportedTreeValuesMatchTheClientsTimeTrackingDecoder() throws Exception
    {
        // Independent reference: the bundled client's actual decoder, not a second
        // copy of our ranges. Time Tracking is not a production dependency.
        Class<?> implementation = Class.forName("net.runelite.client.plugins.timetracking.farming.PatchImplementation");
        Object decoder = Arrays.stream(implementation.getEnumConstants())
            .filter(value -> ((Enum<?>) value).name().equals("TREE")).findFirst().orElseThrow();
        java.lang.reflect.Method decode = decoder.getClass().getDeclaredMethod("forVarbitValue", int.class);
        decode.setAccessible(true);
        for (int raw = 0; raw <= 255; raw++)
        {
            FarmingObservation ours = tree(raw);
            Object reference = decode.invoke(decoder, raw);
            if (ours.crop() == null) continue;
            assertNotNull(reference, "raw " + raw);
            java.lang.reflect.Method produce = reference.getClass().getDeclaredMethod("getProduce");
            java.lang.reflect.Method state = reference.getClass().getDeclaredMethod("getCropState");
            java.lang.reflect.Method stage = reference.getClass().getDeclaredMethod("getStage");
            produce.setAccessible(true); state.setAccessible(true); stage.setAccessible(true);
            assertEquals(ours.crop().name(), ((Enum<?>) produce.invoke(reference)).name(), "raw " + raw);
            String expected = ours.state() == FarmingObservation.State.CHECK_HEALTH ? "GROWING"
                : ours.state() == FarmingObservation.State.CHECKED || ours.state() == FarmingObservation.State.STUMP
                    ? "HARVESTABLE" : ours.state().name();
            assertEquals(expected, ((Enum<?>) state.invoke(reference)).name(), "raw " + raw);
            if (ours.state() == FarmingObservation.State.GROWING)
                assertEquals(ours.growthStage(), stage.invoke(reference), "raw " + raw);
        }
    }
}
