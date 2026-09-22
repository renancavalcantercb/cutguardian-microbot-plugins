package net.runelite.client.plugins.microbot.ocbirdhouse;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Empty / Build / Seed interactions for one house, confirmed through varp
 * changes. Arrival walking is owned by the visit runner.
 */
final class BirdHouseActions
{
    private static final Logger log = LoggerFactory.getLogger(BirdHouseActions.class);

    private final java.util.function.IntUnaryOperator reader;
    private String status = "Idle";

    BirdHouseActions()
    {
        this(null);
    }

    BirdHouseActions(java.util.function.IntUnaryOperator reader)
    {
        this.reader = reader;
    }

    String status() { return status; }

    private int varp(BirdHouseData house)
    {
        return reader == null ? Microbot.getVarbitPlayerValue(house.varp) : reader.applyAsInt(house.varp);
    }

    static boolean isEmpty(int varp) { return varp == 0; }
    static boolean isBuilt(int varp) { return varp > 0 && varp % 3 != 0; }
    static boolean isSeeded(int varp) { return varp > 0 && varp % 3 == 0; }

    /** Empty a mature house. Already-harvested spaces are skipped. */
    boolean dismantle(BirdHouseData house, BooleanSupplier cancelled)
    {
        int value = varp(house);
        if (!isSeeded(value))
        {
            status = "Skip dismantle " + house.label + " (not seeded)";
            log.info("Bird House Runner: dismantle[{}] skip, varp={} (at {})", house.label, value, BirdHouseTravel.pos());
            return true;
        }
        status = "Emptying " + house.label;
        log.info("Bird House Runner: dismantle[{}] varp={} -> Empty (at {})", house.label, value, BirdHouseTravel.pos());
        BirdHouseTravel.waitForStop(cancelled);
        if (!Rs2GameObject.interact(point(house), "Empty"))
        {
            log.warn("Bird House Runner: Empty target missing at {} (at {})", house.label, BirdHouseTravel.pos());
            return false;
        }
        if (!BirdHouseTravel.waitFor(() -> isEmpty(varp(house)), 10_000, cancelled))
        {
            log.warn("Bird House Runner: {} did not empty in time, varp={} (at {})",
                house.label, varp(house), BirdHouseTravel.pos());
            return false;
        }
        log.info("Bird House Runner: dismantle[{}] success, varp=0", house.label);
        status = "Emptied " + house.label;
        return true;
    }

    /** Build on an empty space. Consumes one clockwork and the run's logs. */
    boolean build(BirdHouseData house, BirdHouseTier tier, BooleanSupplier cancelled)
    {
        int value = varp(house);
        if (!isEmpty(value))
        {
            status = "Skip build " + house.label + " (not empty)";
            log.info("Bird House Runner: build[{}] skip, varp={} (at {})", house.label, value, BirdHouseTravel.pos());
            return true;
        }
        int logs = Rs2Inventory.count(tier.logId);
        if (logs < 1)
        {
            status = "Missing " + tier.label.toLowerCase() + " logs";
            log.warn("Bird House Runner: build[{}] no {} logs (at {})", house.label, tier.label, BirdHouseTravel.pos());
            return false;
        }
        status = "Building " + house.label;
        log.info("Bird House Runner: build[{}] varp=0 -> Build with {} ({} in inv, at {})",
            house.label, tier.label, logs, BirdHouseTravel.pos());
        BirdHouseTravel.waitForStop(cancelled);
        if (!Rs2GameObject.interact(point(house), "Build"))
        {
            log.warn("Bird House Runner: Build target missing at {} (at {})", house.label, BirdHouseTravel.pos());
            return false;
        }
        if (!BirdHouseTravel.waitFor(() -> !isEmpty(varp(house)), 15_000, cancelled))
        {
            log.warn("Bird House Runner: {} did not build in time, varp={} (at {})",
                house.label, varp(house), BirdHouseTravel.pos());
            return false;
        }
        log.info("Bird House Runner: build[{}] success, varp={}", house.label, varp(house));
        status = "Built " + house.label;
        return true;
    }

    /** Seed a built house with any accepted 10-seed stack. */
    boolean seed(BirdHouseData house, BooleanSupplier cancelled)
    {
        int value = varp(house);
        if (isEmpty(value))
        {
            status = "Cannot seed empty " + house.label;
            return false;
        }
        if (isSeeded(value))
        {
            status = "Skip seeding " + house.label + " (already seeded)";
            return true;
        }
        Rs2ItemModel seed = findSeed(BirdHouseSeeds.SEEDS_PER_HOUSE).orElse(null);
        if (seed == null)
        {
            status = "Missing a 10-seed stack";
            log.warn("Bird House Runner: seed[{}] no 10-seed stack (at {})", house.label, BirdHouseTravel.pos());
            return false;
        }
        int seedId = seed.getId();
        int before = seedTotal();
        status = "Seeding " + house.label + " (" + seed.getName() + ")";
        log.info("Bird House Runner: seed[{}] using {} id={} (total seeds={}, at {})",
            house.label, seed.getName(), seedId, before, BirdHouseTravel.pos());
        if (!Rs2Inventory.use(seedId))
        {
            log.warn("Bird House Runner: seed[{}] use({}) returned false", house.label, seedId);
            return false;
        }
        if (!BirdHouseTravel.waitFor(() -> Rs2Inventory.getSelectedItemId() == seedId, 2_000, cancelled))
        {
            log.warn("Bird House Runner: seed[{}] not selected in time", house.label);
            return false;
        }
        BirdHouseTravel.waitForStop(cancelled);
        if (!Rs2GameObject.interact(point(house)))
        {
            log.warn("Bird House Runner: seed target missing at {} (at {})", house.label, BirdHouseTravel.pos());
            return false;
        }
        if (!BirdHouseTravel.waitFor(() -> seedTotal() < before, 10_000, cancelled))
        {
            log.warn("Bird House Runner: seeding {} consumed no seeds (total={}, at {})",
                house.label, seedTotal(), BirdHouseTravel.pos());
            return false;
        }
        log.info("Bird House Runner: seed[{}] success, varp={}, seeds left={}",
            house.label, varp(house), seedTotal());
        status = "Seeded " + house.label;
        return true;
    }

    private static WorldPoint point(BirdHouseData house)
    {
        return new WorldPoint(house.visitX, house.visitY, 0);
    }

    private static int seedTotal()
    {
        return Rs2Inventory.items()
            .filter(item -> BirdHouseSeeds.isAccepted(item.getName()))
            .mapToInt(Rs2ItemModel::getQuantity)
            .sum();
    }

    private static Optional<Rs2ItemModel> findSeed(int minQty)
    {
        return Rs2Inventory.items()
            .filter(item -> BirdHouseSeeds.isAccepted(item.getName()))
            .filter(item -> item.getQuantity() >= minQty)
            .max(Comparator.comparingInt(Rs2ItemModel::getQuantity));
    }

    static boolean nearHouse(BirdHouseData house, int radius)
    {
        WorldPoint location = Rs2Player.getWorldLocation();
        return location != null && location.distanceTo(point(house)) <= radius;
    }
}
