package net.runelite.client.plugins.microbot.ocfarmingew;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;
import java.util.function.BooleanSupplier;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;

/** Hub interaction patterns, using the client's existing caches and utilities. */
class FarmingGameActions
{
    static final class Snapshot
    {
        final FarmingActionPolicy.Inventory inventory;
        final boolean ready, bankReady, busy, dialogue;
        final String dialogueText;
        final WorldPoint location;
        final int dialogueNpc;
        Snapshot(FarmingActionPolicy.Inventory inventory, boolean ready, boolean busy,
            boolean dialogue, String dialogueText, WorldPoint location, int dialogueNpc)
        {
            this(inventory, ready, true, busy, dialogue, dialogueText, location, dialogueNpc);
        }
        Snapshot(FarmingActionPolicy.Inventory inventory, boolean playerReady, boolean inventoryKnown,
            boolean busy, boolean dialogue, String dialogueText, WorldPoint location, int dialogueNpc)
        {
            this.inventory = inventory;
            // An empty inventory can have no container after login. Banking must
            // be able to populate it; patch actions still require a known container.
            this.bankReady = playerReady;
            this.ready = playerReady && inventoryKnown;
            this.busy = busy;
            this.dialogue = dialogue;
            this.dialogueText = dialogueText;
            this.location = location;
            this.dialogueNpc = dialogueNpc;
        }
    }

    Snapshot snapshot()
    {
        return Microbot.getClientThread().invoke(() -> {
            Client client = Microbot.getClient();
            Map<Integer, Integer> items = new HashMap<>();
            ItemContainer container = client.getItemContainer(InventoryID.INVENTORY);
            int occupied = 0;
            if (container != null) for (Item item : container.getItems())
            {
                if (item.getId() < 0 || item.getQuantity() <= 0) continue;
                occupied++;
                items.merge(item.getId(), item.getQuantity(), Integer::sum);
            }
            Player player = client.getLocalPlayer();
            net.runelite.api.widgets.Widget head = client.getWidget(net.runelite.api.gameval.InterfaceID.ChatLeft.HEAD);
            int dialogueNpc = head != null && !head.isHidden()
                && head.getModelType() == net.runelite.api.widgets.WidgetModelType.NPC_CHATHEAD ? head.getModelId() : -1;
            boolean playerReady = client.getGameState() == GameState.LOGGED_IN && player != null
                && !client.isInInstancedRegion() && !client.getWorldType().contains(WorldType.SEASONAL)
                && !Rs2Player.isInCombat();
            return new Snapshot(new FarmingActionPolicy.Inventory(items, 28 - occupied,
                client.getBoostedSkillLevel(Skill.FARMING), client.getSkillExperience(Skill.FARMING)), playerReady, container != null,
                player != null && (player.getAnimation() != -1 || Rs2Player.isMoving()),
                Rs2Dialogue.isInDialogue(), Rs2Dialogue.getDialogueText(),
                player == null ? null : player.getWorldLocation(), dialogueNpc);
        });
    }

    boolean near(Snapshot snapshot, FarmingPatchData patch, FarmingActionPolicy.Action action)
    {
        // These actions only touch the bag. In a shared region, the first patch
        // may own the plan while the player is beside another patch.
        if (action == FarmingActionPolicy.Action.DROP_WEEDS || action == FarmingActionPolicy.Action.DROP_POTS) return true;
        if (action == FarmingActionPolicy.Action.NOTE_FRUIT)
        {
            WorldPoint npc = leprechaunLocation(patch);
            return npc != null && snapshot.location != null && snapshot.location.distanceTo(npc) <= 4;
        }
        if (FarmingPatchTarget.usesPatch(action))
        {
            FarmingPatchTarget target = patchTarget(patch);
            return target != null && target.standingTile.equals(snapshot.location);
        }
        return snapshot.location != null && snapshot.location.getPlane() == 0
            && snapshot.location.distanceTo(new WorldPoint(patch.visitX, patch.visitY, 0)) <= 20;
    }

    void approach(FarmingPatchData patch, FarmingActionPolicy.Action action, BooleanSupplier cancelled)
    {
        WorldPoint destination = new WorldPoint(patch.visitX, patch.visitY, 0);
        int distance = 3;
        if (action == FarmingActionPolicy.Action.NOTE_FRUIT)
        {
            long started = now();
            if (leprechaunLocation(patch) == null)
                walk(destination, 3, () -> cancelled.getAsBoolean() || now() - started >= 45 || leprechaunLocation(patch) != null);
            WorldPoint npc = leprechaunLocation(patch);
            if (npc != null && !cancelled.getAsBoolean())
                walk(npc, 3, () -> cancelled.getAsBoolean() || now() - started >= 45 || near(snapshot(), patch, action));
            return;
        }
        if (FarmingPatchTarget.usesPatch(action))
        {
            FarmingPatchTarget target = patchTarget(patch);
            if (target == null)
            {
                // Transmission regions can extend beyond the scene (especially
                // Gnome/Nemus). A fresh varbit does not imply the object is loaded.
                long loadingStarted = now();
                walk(destination, 3, () -> cancelled.getAsBoolean() || now() - loadingStarted >= 45
                    || patchTarget(patch) != null);
                if (cancelled.getAsBoolean()) return;
                target = patchTarget(patch);
            }
            if (target == null) return;
            destination = target.standingTile;
            distance = 0;
        }
        long started = now();
        FarmingApproachProgress progress = new FarmingApproachProgress(destination, snapshot().location, started);
        walk(destination, distance, () -> {
            if (cancelled.getAsBoolean() || now() - started >= 45) return true;
            if (!FarmingPatchTarget.usesPatch(action)) return false;
            Snapshot current = snapshot();
            // The chosen standing tile is a route hint. Arriving at ANY usable
            // edge completes approach, even if the walker missed that exact tile.
            return near(current, patch, action) || progress.stalled(current.location, now());
        });
    }

    long now() { return Instant.now().getEpochSecond(); }

    void walk(WorldPoint destination, int distance, BooleanSupplier finished)
    {
        if (finished.getAsBoolean()) return;
        if (distance == 0 && !withinCanvasRange(destination))
        {
            // Re-evaluate the walking mode on arrival near the patch. A route
            // which began far away must not keep using the minimap for the last tile.
            walkRoute(destination, distance, () -> finished.getAsBoolean() || withinCanvasRange(destination));
            if (finished.getAsBoolean()) return;
        }
        if (distance == 0 && withinCanvasRange(destination) && walkCanvas(destination))
        {
            // A minimap pixel covers multiple tiles. One canvas walk is precise
            // enough for the last steps and avoids repeatedly redirecting movement.
            awaitWalk(finished);
            return;
        }
        walkRoute(destination, distance, finished);
    }

    private boolean withinCanvasRange(WorldPoint destination)
    {
        WorldPoint current = snapshot().location;
        return current != null && current.distanceTo(destination) <= 6;
    }

    void walkRoute(WorldPoint destination, int distance, BooleanSupplier finished)
    {
        Rs2Walker.walkUntil(destination, distance, finished);
    }

    boolean walkCanvas(WorldPoint destination)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> Rs2Walker.walkFastCanvas(destination, false)).orElse(false);
    }

    void awaitWalk(BooleanSupplier finished) { Global.sleepUntil(finished, 6000); }

    FarmingPatchTarget patchTarget(FarmingPatchData patch)
    {
        return Microbot.getClientThread().invoke(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            if (player == null) return (FarmingPatchTarget) null;
            Rs2TileObjectModel object = Microbot.getRs2TileObjectCache().query().withIds(patch.objectId).toList().stream()
                .filter(o -> patch.includes(o.getWorldLocation().getX(), o.getWorldLocation().getY(), o.getPlane()))
                .findFirst().orElse(null);
            if (object == null) return (FarmingPatchTarget) null;
            ObjectComposition definition = Microbot.getClient().getObjectDefinition(patch.objectId);
            return FarmingPatchTarget.rectangle(object.getWorldLocation(), Math.max(1, definition.getSizeX()),
                Math.max(1, definition.getSizeY()), Rs2Tile.getReachableTilesFromTile(player.getWorldLocation(), 40));
        });
    }

    boolean execute(FarmingPatchData patch, FarmingActionPolicy.Plan plan, BooleanSupplier cancelled)
    {
        if (cancelled.getAsBoolean()) return false;
        Snapshot current = snapshot();
        if (!current.ready || current.location == null
            || !patch.includes(current.location.getX(), current.location.getY(), current.location.getPlane())) return false;
        if (plan.action == FarmingActionPolicy.Action.DROP_WEEDS) return Rs2Inventory.drop(6055);
        if (plan.action == FarmingActionPolicy.Action.DROP_POTS) return Rs2Inventory.drop(5350);
        if (plan.action == FarmingActionPolicy.Action.NOTE_FRUIT)
        {
            Rs2NpcModel npc = localNpc(patch.leprechaunId(), patch);
            return npc != null && Rs2Inventory.useItemOnNpc(plan.item, npc);
        }
        if (plan.action == FarmingActionPolicy.Action.PAY || plan.action == FarmingActionPolicy.Action.REMOVE_TREE)
        {
            Rs2NpcModel npc = localNpc(patch.gardenerId(), patch);
            return npc != null && Rs2Npc.interact(npc, patch.paymentAction());
        }
        FarmingPatchTarget target = patchTarget(patch);
        if (target == null || !target.standingTile.equals(current.location)) return false;
        Rs2TileObjectModel object = Microbot.getClientThread().invoke(() ->
            Microbot.getRs2TileObjectCache().query().withIds(patch.objectId).toList().stream()
                .filter(o -> target.objectTile.equals(o.getWorldLocation())).findFirst().orElse(null));
        if (object == null) return false;
        boolean correctRegion = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            WorldPoint point = object.getWorldLocation();
            return patch.includes(point.getX(), point.getY(), point.getPlane());
        }).orElse(false);
        if (!correctRegion) return false;
        String option;
        switch (plan.action)
        {
            case RAKE: option = "Rake"; break;
            case CLEAR: option = "Clear"; break;
            case CHECK_HEALTH: option = "Check-health"; break;
            case PRUNE: option = "Prune"; break;
            case HARVEST:
                FarmingFruitTree fruit = java.util.Arrays.stream(FarmingFruitTree.values())
                    .filter(tree -> tree.produce == plan.item).findFirst().orElse(null);
                if (fruit == null) return false;
                option = fruit.pickAction; break;
            case PLANT: option = "Plant"; break;
            default: return false;
        }
        if (plan.action == FarmingActionPolicy.Action.PLANT)
        {
            if (!Rs2Inventory.use(plan.item)) return false;
            Global.sleepUntil(() -> cancelled.getAsBoolean() || Rs2Inventory.isItemSelected(), 1000);
            if (cancelled.getAsBoolean() || !Rs2Inventory.isItemSelected()) return false;
        }
        else if (!Microbot.getClientThread().runOnClientThreadOptional(() -> Rs2GameObject.hasAction(object, option)).orElse(false)) return false;
        return !cancelled.getAsBoolean() && object.click(option);
    }

    private Rs2NpcModel localNpc(int id, FarmingPatchData patch)
    {
        WorldPoint center = new WorldPoint(patch.visitX, patch.visitY, 0);
        return Microbot.getClientThread().invoke(() -> Rs2Npc.getNpcs(npc -> {
            WorldPoint point = npc.getWorldLocation();
            return npc.getId() == id && point != null && point.distanceTo(center) <= 32
                && patch.includes(point.getX(), point.getY(), point.getPlane());
        }).min(java.util.Comparator.comparingInt(npc -> npc.getWorldLocation().distanceTo(center))).orElse(null));
    }

    private WorldPoint leprechaunLocation(FarmingPatchData patch)
    {
        return Microbot.getClientThread().invoke(() -> {
            Rs2NpcModel npc = localNpc(patch.leprechaunId(), patch);
            return npc == null ? null : npc.getWorldLocation();
        });
    }

    void continueOwnedDialogue(FarmingActionPolicy.Action action, boolean completed)
    {
        // Only invoked while waiting for the result of our own pending action.
        boolean options = Microbot.getClientThread().runOnClientThreadOptional(Rs2Dialogue::hasSelectAnOption).orElse(false);
        if (options && !completed)
        {
            String question = Microbot.getClientThread().invoke(Rs2Dialogue::getQuestion);
            Microbot.getClientThread().runOnClientThreadOptional(() -> {
                List<String> optionsText = Rs2Dialogue.getDialogueOptions().stream()
                    .map(net.runelite.api.widgets.Widget::getText).collect(Collectors.toList());
                String choice = FarmingOwnedDialogue.option(action, question, optionsText);
                return choice != null && Rs2Dialogue.clickOption(choice, true);
            });
        }
        else if (Microbot.getClientThread().runOnClientThreadOptional(Rs2Dialogue::hasContinue).orElse(false)) Rs2Dialogue.clickContinue();
    }
}
