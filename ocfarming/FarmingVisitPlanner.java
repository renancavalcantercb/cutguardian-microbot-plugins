package net.runelite.client.plugins.microbot.ocfarming;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/** Worker-owned policy: visit the oldest expired estimate, back off per region. */
final class FarmingVisitPlanner
{
    private final Map<Integer, Long> retryAfter = new HashMap<>();

    FarmingPatchData next(Map<FarmingPatchData, FarmingObservation> observations, long now)
    {
        return next(observations, now, false);
    }

    FarmingPatchData next(Map<FarmingPatchData, FarmingObservation> observations, long now, boolean tend)
    {
        return next(observations, now, p -> tend, p -> true, false);
    }

    FarmingPatchData next(Map<FarmingPatchData, FarmingObservation> observations, long now,
        Predicate<FarmingPatchData> tend, Predicate<FarmingPatchData> enabled, boolean discoverTrees)
    {
        FarmingPatchData next = null;
        long oldest = Long.MAX_VALUE;
        for (FarmingPatchData patch : FarmingPatchData.values())
        {
            FarmingObservation observation = observations.get(patch);
            if (!enabled.test(patch)) continue;
            boolean discover = observation == null && discoverTrees && tend.test(patch);
            if ((!discover && (observation == null || !(observation.isDue(now) || (tend.test(patch) && needsWork(observation)))))
                || now < retryAfter.getOrDefault(patch.region, 0L)) continue;
            long priority = discover ? 0 : observation.isDue(now) ? observation.latestReadyAt : observation.observedAt;
            if (priority < oldest)
            {
                next = patch;
                oldest = priority;
            }
        }
        return next;
    }

    static boolean needsWork(FarmingObservation observation)
    {
        switch (observation.state())
        {
            case WEEDS: case EMPTY: case DISEASED: case DEAD:
            case CHECK_HEALTH: case CHECKED: case STUMP: case HARVEST: return true;
            default: return false;
        }
    }

    void failed(FarmingPatchData patch, long now)
    {
        retryAfter.put(patch.region, now + 300);
    }

    void reset() { retryAfter.clear(); }

    static boolean confirmed(FarmingPatchData patch, long previousObservedAt,
        int liveRegion, Map<FarmingPatchData, FarmingObservation> observations)
    {
        FarmingObservation latest = observations.get(patch);
        return liveRegion == patch.region && latest != null && latest.observedAt > previousObservedAt;
    }
}
