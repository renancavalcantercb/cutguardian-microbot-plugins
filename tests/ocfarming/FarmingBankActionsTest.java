package net.runelite.client.plugins.microbot.ocfarming;

import java.util.*;
import java.util.function.BooleanSupplier;
import net.runelite.api.gameval.InterfaceID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarmingBankActionsTest
{
    @Test void emptyInventoryWithoutContainerCanStartBankPreparation()
    {
        FakeBank bank = new FakeBank(); bank.open = false; bank.inventoryKnown = false;
        assertTrue(bank.ready());
        assertFalse(bank.snapshot().ready, "Patch actions still require an inventory container");
    }

    @Test void unavailablePlayerCannotBankEvenWithAnInventoryContainer()
    {
        FakeBank bank = new FakeBank(); bank.playerReady = false;
        assertFalse(bank.ready());
        assertFalse(bank.snapshot().ready);
    }

    @Test void movementStillDelaysBankPreparationUntilStoppedOrBankOpen()
    {
        FakeBank bank = new FakeBank(); bank.open = false; bank.busy = true; bank.inventoryKnown = false;
        assertFalse(bank.ready());
        bank.busy = false;
        assertTrue(bank.ready());
        bank.busy = true; bank.open = true;
        assertTrue(bank.ready());
    }

    @Test void paymentInNotesThenRunesAsItemsUsesTheSameToggleBothWays()
    {
        FakeBank bank = new FakeBank();
        assertTrue(bank.execute(withdraw(5968, 6, 5969, true), () -> false));
        assertEquals(6, bank.items.get(5969));
        assertTrue(bank.execute(withdraw(556, 100, 556, false), () -> false));
        assertEquals(100, bank.items.get(556));
        assertEquals(List.of(InterfaceID.Bankmain.NOTE, InterfaceID.Bankmain.NOTE), bank.widgets);
        assertFalse(bank.noted);
        assertTrue(bank.close(() -> false));
        assertEquals(1, bank.closes);
    }

    @Test void aClickWithoutModeChangeCannotWithdrawRunes()
    {
        FakeBank bank = new FakeBank(); bank.noted = true; bank.toggleWorks = false;
        assertFalse(bank.execute(withdraw(556, 100, 556, false), () -> false));
        assertEquals(0, bank.withdrawals);
        assertTrue(bank.items.isEmpty());
    }

    @Test void failedModeRestorationCannotPreventClosingBank()
    {
        FakeBank bank = new FakeBank(); bank.noted = true; bank.toggleWorks = false;
        assertTrue(bank.close(() -> false));
        assertEquals(1, bank.closes);
        assertFalse(bank.open);
    }

    @Test void failedModeClickCannotPreventClosingBank()
    {
        FakeBank bank = new FakeBank(); bank.noted = true; bank.clickWorks = false;
        assertTrue(bank.close(() -> false));
        assertEquals(1, bank.closes);
    }

    @Test void alreadyCorrectModeDoesNotToggle()
    {
        FakeBank bank = new FakeBank();
        assertTrue(bank.execute(withdraw(563, 20, 563, false), () -> false));
        assertTrue(bank.widgets.isEmpty());
    }

    @Test void cancellationPreventsClicksWithdrawalsAndClosure()
    {
        FakeBank bank = new FakeBank();
        assertFalse(bank.execute(withdraw(556, 100, 556, false), () -> true));
        assertFalse(bank.close(() -> true));
        assertEquals(0, bank.closes);
        assertEquals(0, bank.withdrawals);
        assertTrue(bank.widgets.isEmpty());
    }

    @Test void cancelledWhileSwitchingModeCannotCloseOrWithdraw()
    {
        FakeBank bank = new FakeBank(); bank.noted = true; bank.cancelAfterClick = true;
        assertFalse(bank.close(() -> bank.cancelled));
        assertEquals(0, bank.closes);
    }

    @Test void closeClickWithoutInterfaceDisappearingIsNotSuccess()
    {
        FakeBank bank = new FakeBank(); bank.closeWorks = false;
        assertFalse(bank.close(() -> false));
        assertEquals(1, bank.closes);
    }

    @Test void depositAllEmptyInventorySucceedsWithoutClick()
    {
        FakeBank bank = new FakeBank();
        assertTrue(bank.depositAll(() -> false));
        assertEquals(0, bank.depositAllClicks);
    }

    @Test void depositAllWithItemsClearsInventoryAndConfirms()
    {
        FakeBank bank = new FakeBank();
        bank.items.put(5370, 2);
        bank.items.put(995, 5000);
        assertTrue(bank.depositAll(() -> false));
        assertEquals(1, bank.depositAllClicks);
        assertTrue(bank.items.isEmpty());
    }

    @Test void depositAllFailsWhenClickFailsOrCancelled()
    {
        FakeBank bank = new FakeBank();
        bank.items.put(5370, 2);
        assertFalse(bank.depositAll(() -> true));
        assertEquals(0, bank.depositAllClicks);

        bank.depositAllWorks = false;
        assertFalse(bank.depositAll(() -> false));
        assertEquals(1, bank.depositAllClicks);
        assertEquals(2, bank.items.get(5370));
    }

    @Test void depositAllStepExecutesViaBankAction()
    {
        FakeBank bank = new FakeBank();
        bank.items.put(952, 1);
        FarmingSupplyPlan.Step step = new FarmingSupplyPlan.Step(FarmingSupplyPlan.Kind.DEPOSIT_ALL, 0, 0, 0, false, "Deposit all");
        assertTrue(bank.execute(step, () -> false));
        assertEquals(1, bank.depositAllClicks);
        assertTrue(bank.items.isEmpty());
    }

    private static FarmingSupplyPlan.Step withdraw(int item, int amount, int received, boolean noted)
    { return new FarmingSupplyPlan.Step(FarmingSupplyPlan.Kind.WITHDRAW, item, amount, received, noted, "test"); }

    private static class FakeBank extends FarmingBankActions
    {
        boolean open = true, noted, toggleWorks = true, clickWorks = true, closeWorks = true;
        boolean cancelAfterClick, cancelled;
        boolean playerReady = true, inventoryKnown = true, busy;
        int withdrawals, closes, depositAllClicks;
        boolean depositAllWorks = true;
        final List<Integer> widgets = new ArrayList<>();
        final Map<Integer, Integer> items = new HashMap<>();
        @Override boolean isOpen() { return open; }
        @Override FarmingGameActions.Snapshot snapshot()
        { return new FarmingGameActions.Snapshot(inventory(), playerReady, inventoryKnown, busy, false, "", null, -1); }
        @Override boolean isWithdrawMode(boolean expected) { return noted == expected; }
        @Override boolean clickModeWidget(int widget)
        {
            widgets.add(widget);
            if (clickWorks && toggleWorks && widget == InterfaceID.Bankmain.NOTE) noted = !noted;
            if (cancelAfterClick) cancelled = true;
            return clickWorks;
        }
        @Override boolean depositAll()
        {
            depositAllClicks++;
            if (depositAllWorks && clickWorks) items.clear();
            return depositAllWorks && clickWorks;
        }
        @Override FarmingActionPolicy.Inventory inventory() { return new FarmingActionPolicy.Inventory(items, 20, 15, 100); }
        @Override boolean withdraw(int item, int amount)
        {
            withdrawals++;
            // Notes for stackable runes would be wrong; only baskets use note mode.
            assertEquals(item == 5968, noted);
            items.merge(noted ? 5969 : item, amount, Integer::sum);
            return true;
        }
        @Override void await(BooleanSupplier finished, int timeout) { finished.getAsBoolean(); }
        @Override void closeInterface() { closes++; if (closeWorks) open = false; }
    }
}
