package net.runelite.client.plugins.microbot.ocbirdhouse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

/**
 * Run setup: hammer, chisel, clockworks (topped up to 4; Empty returns them,
 * so only the first run withdraws a full set), 4 logs of the selected
 * progressive tier and 40 seeds. The caller walks to a bank first; the island
 * bank chest works for every run after the first island trip.
 */
final class BirdHouseSupplies
{
    static final int HAMMER = 2347;
    static final int CHISEL = 1755;
    static final int CLOCKWORK = 8792;
    static final int CLOCKWORKS_PER_RUN = 4;
    /** Pendant ids 1-5 then full: withdrawal burns the lowest charges first. */
    static final int[] PENDANT_IDS = {11190, 11191, 11192, 11193, 11194, 11195};
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BirdHouseSupplies.class);

    static final class ItemEntry
    {
        final String name;
        final int id;
        final int qty;
        ItemEntry(String name, int id, int qty) { this.name = name; this.id = id; this.qty = qty; }
    }

    interface BankOps
    {
        boolean isOpen();
        boolean open();
        void close();
        boolean depositAll();
        int invCount(int id);
        int bankCount(int id);
        boolean withdraw(int id, int qty);
        List<ItemEntry> bankSeedStacks();
        int invSeedTotal();
        void waitForChanges(int ms);
    }

    private final BankOps bank;
    private BirdHouseTier tier;
    private boolean prepared;
    private long retryAfter;
    private String status = "Waiting to prepare run";

    BirdHouseSupplies() { this(new MicrobotBankOps()); }
    BirdHouseSupplies(BankOps bank) { this.bank = bank; }

    void reset() { tier = null; prepared = false; retryAfter = 0; status = "Waiting to prepare run"; }
    boolean prepared() { return prepared; }
    String status() { return status; }
    BirdHouseTier tier() { return tier; }

    /** Inventory-only check so the runner skips the bank walk when already set. */
    boolean carryingFullRun()
    {
        Optional<BirdHouseTier> carried = BirdHouseTier.select(hunterLevel(), craftingLevel(), invLogCounts());
        if (carried.isEmpty()) return false;
        return hasPendant() && bank.invCount(HAMMER) >= 1 && bank.invCount(CHISEL) >= 1
            && bank.invCount(CLOCKWORK) >= CLOCKWORKS_PER_RUN
            && bank.invSeedTotal() >= BirdHouseSeeds.SEEDS_PER_RUN;
    }

    private boolean hasPendant()
    {
        for (int id : PENDANT_IDS)
        {
            if (bank.invCount(id) >= 1) return true;
        }
        return false;
    }

    private static String dumpInv(BankOps bank)
    {
        return "hammer=" + bank.invCount(HAMMER) + " chisel=" + bank.invCount(CHISEL)
            + " clockwork=" + bank.invCount(CLOCKWORK) + " seeds=" + bank.invSeedTotal();
    }

    static int hunterLevel()
    {
        return Microbot.getClient().getRealSkillLevel(Skill.HUNTER);
    }

    static int craftingLevel()
    {
        return Microbot.getClient().getRealSkillLevel(Skill.CRAFTING);
    }

    boolean ensure(boolean depositAllBeforeRun, BooleanSupplier cancelled)
    {
        long now = Instant.now().getEpochSecond();
        if (now < retryAfter) return false;
        if (cancelled.getAsBoolean()) return false;
        // Fast path: inventory already carries a full run, no bank trip needed.
        Optional<BirdHouseTier> carried = BirdHouseTier.select(hunterLevel(), craftingLevel(), invLogCounts());
        if (hasPendant() && bank.invCount(HAMMER) >= 1 && bank.invCount(CHISEL) >= 1
            && bank.invCount(CLOCKWORK) >= CLOCKWORKS_PER_RUN
            && bank.invSeedTotal() >= BirdHouseSeeds.SEEDS_PER_RUN && carried.isPresent())
        {
            tier = carried.get();
            prepared = true;
            status = "Supplies ready (" + tier.label + ")";
            log.info("Bird House Runner: supplies already carried, tier={} ({})", tier.label, dumpInv(bank));
            return true;
        }
        status = "Preparing run at bank";
        log.info("Bird House Runner: preparing at bank ({}), hunter={} crafting={}",
            dumpInv(bank), hunterLevel(), craftingLevel());
        if (!bank.isOpen() && !bank.open()) return block("Could not open bank; retry in 60s");
        if (cancelled.getAsBoolean()) return false;
        if (depositAllBeforeRun)
        {
            log.info("Bird House Runner: depositing inventory");
            bank.depositAll();
            bank.waitForChanges(3000);
        }
        Map<Integer, Integer> logs = combinedLogCounts();
        Optional<BirdHouseTier> selected = BirdHouseTier.select(hunterLevel(), craftingLevel(), logs);
        if (selected.isEmpty()) return block("No buildable tier with 4+ logs (check levels and bank)");
        tier = selected.get();
        log.info("Bird House Runner: tier selected={} (logId={})", tier.label, tier.logId);
        if (!topUpPendant(cancelled)) return false;
        if (!topUp(HAMMER, 1, "hammer", cancelled)) return false;
        if (!topUp(CHISEL, 1, "chisel", cancelled)) return false;
        if (!topUp(CLOCKWORK, CLOCKWORKS_PER_RUN, "clockworks", cancelled)) return false;
        if (!topUp(tier.logId, BirdHouseTier.LOGS_PER_RUN, tier.label.toLowerCase() + " logs", cancelled)) return false;
        if (!topUpSeeds(cancelled)) return false;
        bank.close();
        prepared = true;
        status = "Supplies ready (" + tier.label + ")";
        log.info("Bird House Runner: supplies ready, tier={} ({})", tier.label, dumpInv(bank));
        return true;
    }

    private boolean topUpPendant(BooleanSupplier cancelled)
    {
        if (hasPendant())
        {
            log.info("Bird House Runner: pendant already in inventory");
            return true;
        }
        for (int id : PENDANT_IDS)
        {
            if (bank.bankCount(id) < 1) continue;
            status = "Withdrawing digsite pendant";
            log.info("Bird House Runner: withdrawing digsite pendant id={}", id);
            if (!bank.withdraw(id, 1)) return block("Withdraw digsite pendant not confirmed; retry in 60s");
            bank.waitForChanges(2000);
            if (cancelled.getAsBoolean()) return false;
            if (hasPendant()) return true;
        }
        return block("Need a digsite pendant in bank");
    }

    private boolean topUp(int id, int want, String label, BooleanSupplier cancelled)
    {
        int have = bank.invCount(id);
        if (have >= want)
        {
            log.info("Bird House Runner: {} already stocked (have={})", label, have);
            return true;
        }
        int need = want - have;
        if (bank.bankCount(id) < need) return block("Need " + need + " more " + label);
        status = "Withdrawing " + label;
        log.info("Bird House Runner: withdrawing {}x{} (have={}, need={})", need, label, have, need);
        if (!bank.withdraw(id, need)) return block("Withdraw " + label + " not confirmed; retry in 60s");
        bank.waitForChanges(2000);
        if (cancelled.getAsBoolean()) return false;
        if (bank.invCount(id) < want) return block("Withdraw " + label + " not confirmed; retry in 60s");
        log.info("Bird House Runner: {} stocked (have={})", label, bank.invCount(id));
        return true;
    }

    private boolean topUpSeeds(BooleanSupplier cancelled)
    {
        int have = bank.invSeedTotal();
        if (have >= BirdHouseSeeds.SEEDS_PER_RUN) return true;
        int need = BirdHouseSeeds.SEEDS_PER_RUN - have;
        List<ItemEntry> stacks = new ArrayList<>(bank.bankSeedStacks());
        stacks.sort(Comparator.comparingInt((ItemEntry e) -> e.qty).reversed());
        log.info("Bird House Runner: seeds have={} need={}, bank stacks={}", have, need,
            stacks.stream().map(e -> e.name + "x" + e.qty).reduce((a, b) -> a + ", " + b).orElse("none"));
        int remaining = need;
        for (ItemEntry stack : stacks)
        {
            if (remaining <= 0) break;
            int take = Math.min(stack.qty, remaining);
            status = "Withdrawing seeds (" + stack.name + ")";
            log.info("Bird House Runner: withdrawing {}x {} (id={})", take, stack.name, stack.id);
            if (!bank.withdraw(stack.id, take)) return block("Withdraw seeds not confirmed; retry in 60s");
            bank.waitForChanges(2000);
            if (cancelled.getAsBoolean()) return false;
            remaining -= take;
        }
        if (bank.invSeedTotal() < BirdHouseSeeds.SEEDS_PER_RUN)
            return block("Need " + need + " seeds but bank is short");
        return true;
    }

    private Map<Integer, Integer> invLogCounts()
    {
        Map<Integer, Integer> counts = new HashMap<>();
        for (BirdHouseTier tier : BirdHouseTier.values()) counts.put(tier.logId, bank.invCount(tier.logId));
        return counts;
    }

    private Map<Integer, Integer> combinedLogCounts()
    {
        Map<Integer, Integer> counts = invLogCounts();
        for (BirdHouseTier tier : BirdHouseTier.values())
            counts.put(tier.logId, counts.getOrDefault(tier.logId, 0) + bank.bankCount(tier.logId));
        return counts;
    }

    private boolean block(String message)
    {
        status = message;
        log.warn("Bird House Runner: supplies blocked: {}", message);
        retryAfter = Instant.now().getEpochSecond() + 60;
        return false;
    }

    static final class MicrobotBankOps implements BankOps
    {
        @Override public boolean isOpen() { return Rs2Bank.isOpen(); }
        @Override public boolean open() { return Rs2Bank.openBank(); }
        @Override public void close()
        {
            Rs2Bank.closeBank();
            Rs2Inventory.waitForInventoryChanges(1000);
        }
        @Override public boolean depositAll()
        {
            Rs2Bank.depositAll();
            return true;
        }
        @Override public int invCount(int id) { return Rs2Inventory.count(id); }
        @Override public int bankCount(int id) { return Rs2Bank.count(id); }
        @Override public boolean withdraw(int id, int qty) { return Rs2Bank.withdrawX(id, qty); }
        @Override public List<ItemEntry> bankSeedStacks()
        {
            List<ItemEntry> stacks = new ArrayList<>();
            Rs2Bank.bankItems().stream()
                .filter(item -> BirdHouseSeeds.isAccepted(item.getName()))
                .forEach(item -> stacks.add(new ItemEntry(item.getName(), item.getId(), item.getQuantity())));
            return stacks;
        }
        @Override public int invSeedTotal()
        {
            return Rs2Inventory.items()
                .filter(item -> BirdHouseSeeds.isAccepted(item.getName()))
                .mapToInt(Rs2ItemModel::getQuantity)
                .sum();
        }
        @Override public void waitForChanges(int ms) { Rs2Inventory.waitForInventoryChanges(ms); }
    }
}
