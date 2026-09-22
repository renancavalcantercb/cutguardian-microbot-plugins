package net.runelite.client.plugins.microbot.ocbirdhouse;

import java.util.HashMap;
import java.util.Map;

/** Worker-owned policy: oldest due house first, per-house backoff on failure. */
final class BirdHouseVisitPlanner
{
    private final Map<BirdHouseData, Long> retryAfter = new HashMap<>();

    BirdHouseData next(Map<BirdHouseData, BirdHouseObservation> observations, long now)
    {
        BirdHouseData next = null;
        long oldest = Long.MAX_VALUE;
        for (BirdHouseData house : BirdHouseData.values())
        {
            BirdHouseObservation observation = observations.get(house);
            if (now < retryAfter.getOrDefault(house, 0L)) continue;
            // Undiscovered houses are visited immediately so the first run seeds them.
            long priority;
            if (observation == null) priority = 0;
            else if (!observation.needsWork(now)) continue;
            else priority = observation.state() == BirdHouseObservation.State.SEEDED
                ? observation.readyAt : observation.observedAt;
            if (priority < oldest)
            {
                next = house;
                oldest = priority;
            }
        }
        return next;
    }

    void failed(BirdHouseData house, long now)
    {
        retryAfter.put(house, now + 300);
    }

    void reset() { retryAfter.clear(); }

    static boolean confirmed(BirdHouseData house, long previousObservedAt,
        Map<BirdHouseData, BirdHouseObservation> observations)
    {
        BirdHouseObservation latest = observations.get(house);
        return latest != null && latest.observedAt > previousObservedAt;
    }
}
