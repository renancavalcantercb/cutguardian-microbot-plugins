package net.runelite.client.plugins.microbot.ocfarming;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** No dependency on the Time Tracking plugin, HTTP, or game interactions. */
final class FarmingTracker
{
    private final Map<FarmingPatchData, FarmingObservation> observations = new EnumMap<>(FarmingPatchData.class);
    private String profile;

    synchronized String profile() { return profile; }

    synchronized boolean switchProfile(String next)
    {
        if (Objects.equals(profile, next)) return false;
        observations.clear();
        profile = next;
        return true;
    }

    synchronized Map<FarmingPatchData, FarmingObservation> snapshot()
    {
        return new EnumMap<>(observations);
    }

    synchronized void restore(FarmingPatchData patch, String value, long now)
    {
        if (profile == null) return;
        FarmingObservation observation = FarmingObservation.decode(value, now);
        if (observation != null && observation.fruit == patch.fruit) observations.put(patch, observation);
    }

    synchronized boolean observe(FarmingPatchData patch, int raw, long now, boolean continuous)
    {
        if (profile == null || raw < 0 || raw > 255 || now <= 0) return false;
        FarmingObservation previous = observations.get(patch);
        observations.put(patch, FarmingObservation.observe(patch.fruit, raw, now, previous, continuous));
        return previous == null || previous.raw != raw;
    }
}
