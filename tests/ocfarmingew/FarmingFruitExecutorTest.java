package net.runelite.client.plugins.microbot.ocfarmingew;

import java.util.*;
import java.util.function.BooleanSupplier;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static net.runelite.client.plugins.microbot.ocfarmingew.FarmingActionPolicy.Action;

class FarmingFruitExecutorTest
{
    private long now = 1000;
    private boolean enabled = true;
    private final FarmingPatchData patch = FarmingPatchData.GNOME_FRUIT;
    private final FakePlugin plugin = new FakePlugin();
    private final FakeGame game = new FakeGame();
    private final OcFarmingEWConfig config = new OcFarmingEWConfig() {
        public boolean autoTrees() { return true; }
        public boolean autoFruitTrees() { return enabled; }
        public FarmingTree tree() { return FarmingTree.WILLOW; }
    };
    private final FarmingActionExecutor executor = new FarmingActionExecutor(plugin, config, game, () -> now);

    @Test void completeFruitCycleWaitsForHealthHarvestNotesRemovalPlantAndProtection()
    {
        game.items.putAll(Map.of(995, 1000, 952, 1, 5496, 1, 5987, 9));
        tick(34); tick(34);
        assertEquals(List.of(Action.CHECK_HEALTH), game.actions);
        tick(20); tick(20);
        assertEquals(Action.HARVEST, last());
        game.items.put(1955, 6);
        tick(14); tick(14);
        assertEquals(Action.NOTE_FRUIT, last());
        game.items.remove(1955); // A disappearing fruit is insufficient without notes.
        tick(14);
        assertEquals(Action.NOTE_FRUIT, last());
        assertTrue(executor.hasPendingAction());
        game.items.put(1956, 6);
        tick(14); tick(14);
        assertEquals(Action.REMOVE_TREE, last());
        game.items.put(995, 800);
        tick(3); tick(3);
        assertEquals(Action.PLANT, last());
        assertEquals(5496, game.lastItem);
        game.items.remove(5496);
        tick(8); tick(8);
        assertEquals(Action.PAY, last());
        assertEquals(5987, game.lastItem);
        game.items.remove(5987);
        plugin.receipt = "You pay the gardener sweetcorn to protect the patch.";
        tick(8);
        assertEquals(FarmingObservation.Crop.APPLE, plugin.paid.get(patch));
        assertFalse(tick(8));
        assertEquals(List.of(Action.CHECK_HEALTH, Action.HARVEST, Action.NOTE_FRUIT, Action.REMOVE_TREE, Action.PLANT, Action.PAY), game.actions);
        assertTrue(game.targets.stream().allMatch(p -> p == patch));
    }

    @Test void partialHarvestWithFullInventoryNotesFruitBeforePickingAgain()
    {
        tick(20);
        game.items.put(1955, 1); game.free = 0;
        tick(19); tick(19);
        assertEquals(Action.NOTE_FRUIT, last());
        game.items.remove(1955); game.items.put(1956, 1); game.free = 1;
        tick(19); tick(19);
        assertEquals(Action.HARVEST, last());
        assertFalse(executor.blocked(patch, now));
    }

    @Test void disablingFruitMidActionKeepsRegularModeButStopsFruitActions()
    {
        tick(34);
        enabled = false;
        assertFalse(tick(20));
        assertFalse(executor.hasPendingAction());
        assertEquals(List.of(Action.CHECK_HEALTH), game.actions);
    }

    @Test void staleFruitReadInSharedRegionCannotReleaseNavigation()
    {
        now++;
        Map<FarmingPatchData, FarmingObservation> observations = new EnumMap<>(FarmingPatchData.class);
        observations.put(FarmingPatchData.GNOME_TREE, FarmingObservation.observe(15, now, null, false));
        observations.put(patch, FarmingObservation.observe(true, 34, now - 10, null, false));
        assertTrue(executor.tick(new OcFarmingEWPlugin.View("test", 9781, observations, 1, true, plugin.paid), () -> false));
        assertTrue(game.actions.isEmpty());
    }

    @Test void weedsFromFruitPatchAreDroppedWithoutWalkingToRegularTree()
    {
        game.items.putAll(Map.of(5341, 1, 952, 1, 5496, 1, 5987, 9));
        tick(0);
        assertEquals(Action.RAKE, last());
        game.items.put(6055, 3);
        tick(3); // Rake receipt, standing at the fruit patch.
        for (int weeds = 3; weeds > 0; weeds--)
        {
            tick(3);
            assertEquals(Action.DROP_WEEDS, last());
            assertEquals(FarmingPatchData.GNOME_TREE, game.targets.get(game.targets.size() - 1));
            game.items.put(6055, weeds - 1);
            tick(3);
        }
        tick(3);
        assertEquals(Action.PLANT, last());
        assertEquals(0, game.approaches);
    }

    @Test void emptyPotIsDroppedAtFruitPatchBeforeProtectingApple()
    {
        game.items.putAll(Map.of(952, 1, 5496, 1, 5987, 9));
        tick(3);
        assertEquals(Action.PLANT, last());
        game.items.remove(5496); game.items.put(5350, 1);
        tick(8); tick(8);
        assertEquals(Action.DROP_POTS, last());
        game.items.remove(5350);
        tick(8); tick(8);
        assertEquals(Action.PAY, last());
        assertEquals(patch, game.targets.get(game.targets.size() - 1));
        assertEquals(0, game.approaches);
    }

    private Action last() { return game.actions.get(game.actions.size() - 1); }
    private boolean tick(int raw)
    {
        now++;
        return executor.tick(new OcFarmingEWPlugin.View("test", patch.region, Map.of(
            FarmingPatchData.GNOME_TREE, FarmingObservation.observe(15, now, null, false),
            patch, FarmingObservation.observe(true, raw, now, null, false)), 1, true, plugin.paid), () -> false);
    }
    private static class FakePlugin extends OcFarmingEWPlugin
    {
        final Map<FarmingPatchData, FarmingObservation.Crop> paid = new EnumMap<>(FarmingPatchData.class);
        String receipt = "";
        FakePlugin() { paid.put(FarmingPatchData.GNOME_TREE, FarmingObservation.Crop.WILLOW); }
        String actionReceipt() { return receipt; }
        void clearActionReceipt() { receipt = ""; }
        void selectProtection(FarmingPatchData patch) { }
        void recordProtection(FarmingPatchData patch, FarmingObservation observation, long session) { paid.put(patch, observation.crop()); }
    }
    private static class FakeGame extends FarmingGameActions
    {
        final Map<Integer,Integer> items = new HashMap<>();
        final List<Action> actions = new ArrayList<>();
        final List<FarmingPatchData> targets = new ArrayList<>();
        int free = 15, lastItem, approaches;
        Snapshot snapshot() { return new Snapshot(new FarmingActionPolicy.Inventory(items, free, 30, 100), true, false, false, "", new WorldPoint(2473, 3446, 0), -1); }
        boolean near(Snapshot snapshot, FarmingPatchData patch, Action action)
        {
            // Exercise the real distance gate for inventory-only actions. The
            // normal tree is 36 tiles away, outside the old 20-tile gate.
            if (action == Action.DROP_WEEDS || action == Action.DROP_POTS) return super.near(snapshot, patch, action);
            return true;
        }
        void approach(FarmingPatchData patch, Action action, BooleanSupplier cancelled) { approaches++; }
        boolean execute(FarmingPatchData patch, FarmingActionPolicy.Plan plan, BooleanSupplier cancelled)
        { actions.add(plan.action); targets.add(patch); lastItem = plan.item; return true; }
        void continueOwnedDialogue(Action action, boolean completed) { }
    }
}
