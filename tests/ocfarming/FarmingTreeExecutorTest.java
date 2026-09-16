package net.runelite.client.plugins.microbot.ocfarming;

import java.util.*;
import java.util.function.BooleanSupplier;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static net.runelite.client.plugins.microbot.ocfarming.FarmingActionPolicy.Action;

class FarmingTreeExecutorTest
{
    private long now = 1000;
    private final FarmingPatchData patch = FarmingPatchData.LUMBRIDGE_TREE;
    private final FakePlugin plugin = new FakePlugin();
    private final FakeGame game = new FakeGame();
    private boolean enabled = true;
    private final OcFarmingConfig config = new OcFarmingConfig() {
        @Override public boolean autoTrees() { return enabled; }
    };
    private final FarmingActionExecutor executor = new FarmingActionExecutor(plugin, config, game, () -> now);

    @Test void completeTreeCycleWaitsForCheckRemovalPlantAndProtectionReceipts()
    {
        game.items.putAll(Map.of(995, 1000, 952, 1, 5370, 1, 5969, 1));
        tick(12);
        assertEquals(List.of(Action.CHECK_HEALTH), game.actions);
        tick(12);
        assertEquals(1, game.actions.size());
        tick(13); // health receipt
        tick(13); // request removal
        assertEquals(List.of(Action.CHECK_HEALTH, Action.REMOVE_TREE), game.actions);
        tick(13);
        assertEquals(2, game.actions.size());
        game.items.put(995, 800);
        tick(3); // removal receipt
        tick(3); // sapling
        assertEquals(Action.PLANT, game.actions.get(2));
        game.items.remove(5370);
        tick(8);
        tick(8); // protection
        assertEquals(Action.PAY, game.actions.get(3));
        game.items.remove(5969);
        plugin.receipt = "You pay the gardener a basket of tomatoes to protect the patch.";
        tick(8);
        assertTrue(plugin.paid);
        assertFalse(tick(8)); // local work complete; navigation released
        assertEquals(4, game.actions.size());
    }

    @Test void missingSpadeOrBasketBlocksPlantingAndDoesNotReportSuccess()
    {
        game.items.put(5370, 1);
        tick(3);
        assertTrue(executor.blocked(patch, now));
        assertTrue(game.actions.isEmpty());
        assertFalse(plugin.paid);
    }

    @Test void treeModeCanBeStoppedMidAction()
    {
        tick(12);
        assertEquals(List.of(Action.CHECK_HEALTH), game.actions);
        enabled = false;
        assertFalse(tick(13));
        assertEquals(1, game.actions.size());
    }

    @Test void staleReadingsAfterDialogueHoldTheRegionUntilFreshDataArrives()
    {
        assertTrue(executor.tick(new OcFarmingPlugin.View("waiting", -1,
            Map.of(patch, FarmingObservation.observe(3, 990, null, false)), 1, true, Map.of()), () -> false));
        assertTrue(game.actions.isEmpty());
    }

    @Test void wrongGardenerCannotConfirmTreeProtection()
    {
        game.items.put(5969, 1);
        tick(8);
        game.text = "That'll do nicely. Leave it with me - I'll make sure that patch grows for you.";
        game.npc = 2665;
        tick(8);
        assertFalse(plugin.paid);
        game.npc = 2681;
        tick(8);
        assertTrue(plugin.paid);
    }

    private boolean tick(int raw)
    {
        now++;
        return executor.tick(new OcFarmingPlugin.View("test", patch.region,
            Map.of(patch, FarmingObservation.observe(raw, now, null, false)), 1, true,
            plugin.paid ? Map.of(patch, FarmingObservation.Crop.OAK) : Map.of()), () -> false);
    }

    private static class FakePlugin extends OcFarmingPlugin
    {
        boolean paid;
        String receipt = "";
        @Override String actionReceipt() { return receipt; }
        @Override void clearActionReceipt() { receipt = ""; }
        @Override void selectProtection(FarmingPatchData patch) { }
        @Override void recordProtection(FarmingPatchData patch, FarmingObservation observation, long session) { paid = true; }
    }

    private static class FakeGame extends FarmingGameActions
    {
        final Map<Integer, Integer> items = new HashMap<>();
        final List<Action> actions = new ArrayList<>();
        String text = "";
        int npc = -1;
        @Override Snapshot snapshot()
        {
            // Lumbridge alias region 12850 must still count as canonical 12594.
            return new Snapshot(new FarmingActionPolicy.Inventory(items, 15, 15, 100), true, false,
                false, text, new WorldPoint(3200, 3230, 0), npc);
        }
        @Override boolean near(Snapshot snapshot, FarmingPatchData patch, Action action) { return true; }
        @Override boolean execute(FarmingPatchData patch, FarmingActionPolicy.Plan plan, BooleanSupplier cancelled)
        { actions.add(plan.action); return true; }
        @Override void continueOwnedDialogue(Action action, boolean completed) { }
    }
}
