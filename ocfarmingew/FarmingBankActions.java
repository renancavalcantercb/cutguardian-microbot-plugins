package net.runelite.client.plugins.microbot.ocfarmingew;

import java.util.*;
import java.util.function.BooleanSupplier;
import net.runelite.api.*;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Existing Microbot bank/walker APIs, with inventory receipts around mutations. */
class FarmingBankActions
{
    private static final Logger log = LoggerFactory.getLogger(FarmingBankActions.class);
    private final FarmingEfficientWalkerBridge walker = new FarmingEfficientWalkerBridge();
    FarmingGameActions.Snapshot snapshot() { return new FarmingGameActions().snapshot(); }
    FarmingActionPolicy.Inventory inventory() { return snapshot().inventory; }
    boolean ready()
    {
        FarmingGameActions.Snapshot snapshot = snapshot();
        return snapshot.bankReady && (isOpen() || !snapshot.busy);
    }
    boolean isOpen() { return Rs2Bank.isOpen(); }
    boolean open(BooleanSupplier cancelled, boolean allowTravel)
    {
        if (cancelled.getAsBoolean()) return false;
        boolean wasOpen = Rs2Bank.isOpen();
        int epoch = Rs2Bank.getBankLiveEpoch();
        if (!wasOpen && !Rs2Bank.openBank())
        {
            if (!allowTravel || cancelled.getAsBoolean()) return false;
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(3);
            walker.walkUntil(Rs2Bank.getNearestBank().getWorldPoint(), 4,
                () -> cancelled.getAsBoolean() || System.nanoTime() >= deadline || Rs2Bank.isOpen(),
                deadline);
            if (cancelled.getAsBoolean() || System.nanoTime() >= deadline || !Rs2Bank.openBank()) return false;
        }
        return !cancelled.getAsBoolean() && Rs2Bank.verifyBankMirrorAfterOpen(wasOpen, epoch);
    }
    Map<Integer, Integer> bank()
    {
        return Microbot.getClientThread().invoke(() -> {
            Map<Integer, Integer> items = new HashMap<>();
            ItemContainer container = Microbot.getClient().getItemContainer(InventoryID.BANK);
            if (container != null) for (Item item : container.getItems())
                if (item.getId() >= 0 && item.getQuantity() > 0) items.merge(item.getId(), item.getQuantity(), Integer::sum);
            return items;
        });
    }
    boolean execute(FarmingSupplyPlan.Step step, BooleanSupplier cancelled)
    {
        if (cancelled.getAsBoolean() || !isOpen()) return false;
        if (step.kind == FarmingSupplyPlan.Kind.DEPOSIT_ALL) return depositAll(cancelled);
        int before = inventory().count(step.received);
        boolean clicked;
        if (step.kind == FarmingSupplyPlan.Kind.DEPOSIT) clicked = deposit(step.item, step.amount);
        else
        {
            if (!setWithdrawMode(step.noted, cancelled) || cancelled.getAsBoolean()) return false;
            clicked = withdraw(step.item, step.amount);
        }
        if (!clicked) return false;
        int expected = before + (step.kind == FarmingSupplyPlan.Kind.DEPOSIT ? -step.amount : step.amount);
        await(() -> cancelled.getAsBoolean() || inventory().count(step.received) == expected, 5000);
        boolean confirmed = !cancelled.getAsBoolean() && inventory().count(step.received) == expected;
        if (!confirmed) log.warn("Farming Runner bank: {} item={} amount={} not confirmed (before={}, now={})",
            step.kind, step.item, step.amount, before, inventory().count(step.received));
        return confirmed;
    }

    boolean setWithdrawMode(boolean noted, BooleanSupplier cancelled)
    {
        if (cancelled.getAsBoolean() || !isOpen()) return false;
        if (isWithdrawMode(noted)) return true;
        // The current bank has one Item/Note toggle. QUANTITY1_TEXT changes the
        // withdrawal quantity, not the note mode (the core helper uses it for Item).
        if (!clickModeWidget(InterfaceID.Bankmain.NOTE)) return false;
        await(() -> cancelled.getAsBoolean() || !isOpen() || isWithdrawMode(noted), 2000);
        boolean confirmed = !cancelled.getAsBoolean() && isOpen() && isWithdrawMode(noted);
        if (!confirmed) log.warn("Farming Runner bank: could not switch withdrawal mode to {}", noted ? "Note" : "Item");
        return confirmed;
    }

    boolean isWithdrawMode(boolean noted) { return Rs2Bank.isWithdrawAs(noted); }
    boolean clickModeWidget(int widget) { return Rs2Widget.clickWidget(widget); }
    boolean depositAll() { return Rs2Bank.depositAll(); }
    boolean depositAll(BooleanSupplier cancelled)
    {
        if (cancelled.getAsBoolean() || !isOpen()) return false;
        if (inventory().items.isEmpty()) return true;
        if (!depositAll()) return false;
        await(() -> cancelled.getAsBoolean() || inventory().items.isEmpty(), 5000);
        boolean confirmed = !cancelled.getAsBoolean() && inventory().items.isEmpty();
        if (!confirmed) log.warn("Farming Runner bank: deposit all inventory not confirmed");
        return confirmed;
    }
    boolean deposit(int item, int amount) { return Rs2Bank.depositX(item, amount); }
    boolean withdraw(int item, int amount) { return Rs2Bank.withdrawX(item, amount); }
    void await(BooleanSupplier finished, int timeout) { Global.sleepUntil(finished, timeout); }
    void closeInterface() { Rs2Bank.closeBank(); }

    boolean close(BooleanSupplier cancelled)
    {
        if (!isOpen()) return true;
        if (cancelled.getAsBoolean()) return false;
        // Restoring Item mode is convenient, but must not prevent bank closure.
        setWithdrawMode(false, cancelled);
        if (cancelled.getAsBoolean()) return false;
        closeInterface();
        await(() -> cancelled.getAsBoolean() || !isOpen(), 3000);
        boolean closed = !cancelled.getAsBoolean() && !isOpen();
        if (!closed) log.warn("Farming Runner bank: bank closure not confirmed");
        return closed;
    }
}
