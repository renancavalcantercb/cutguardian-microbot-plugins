package net.runelite.client.plugins.microbot.ocbirdhouse;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** No dependency on the Time Tracking plugin, HTTP, or game interactions. */
final class BirdHouseTracker
{
    private final Map<BirdHouseData, BirdHouseObservation> observations = new EnumMap<>(BirdHouseData.class);
    private String profile;

    synchronized String profile() { return profile; }

    synchronized boolean switchProfile(String next)
    {
        if (Objects.equals(profile, next)) return false;
        observations.clear();
        profile = next;
        return true;
    }

    synchronized Map<BirdHouseData, BirdHouseObservation> snapshot()
    {
        return new EnumMap<>(observations);
    }

    synchronized void restore(BirdHouseData house, String value, long now)
    {
        if (profile == null) return;
        BirdHouseObservation observation = BirdHouseObservation.decode(value, now);
        if (observation != null) observations.put(house, observation);
    }

    synchronized boolean observe(BirdHouseData house, int raw, long now)
    {
        if (profile == null || raw < 0 || raw > BirdHouseObservation.MAX_RAW || now <= 0) return false;
        BirdHouseObservation previous = observations.get(house);
        // Like Time Tracking, only a varp change restarts the 50 minute clock.
        if (previous != null && previous.raw == raw) return false;
        observations.put(house, BirdHouseObservation.observe(raw, now));
        return true;
    }

    /** True when 3+ houses flip from built to 0 at once: varps not synced yet. */
    synchronized boolean syncedOut(Map<BirdHouseData, Integer> raws)
    {
        int removals = 0;
        for (BirdHouseData house : BirdHouseData.values())
        {
            int raw = raws.getOrDefault(house, -1);
            BirdHouseObservation previous = observations.get(house);
            if (raw <= 0 && previous != null && previous.raw > 0) removals++;
        }
        return removals > 2;
    }

    /**
     * Batch observe with Time Tracking's login-sync guard: a synced-out update
     * is ignored instead of wiping seeded history.
     */
    synchronized boolean observeAll(Map<BirdHouseData, Integer> raws, long now)
    {
        if (profile == null || now <= 0) return false;
        for (int raw : raws.values())
        {
            if (raw < 0 || raw > BirdHouseObservation.MAX_RAW) return false;
        }
        if (syncedOut(raws)) return false;
        boolean changed = false;
        for (BirdHouseData house : BirdHouseData.values())
        {
            changed |= observe(house, raws.getOrDefault(house, -1), now);
        }
        return changed;
    }
}
