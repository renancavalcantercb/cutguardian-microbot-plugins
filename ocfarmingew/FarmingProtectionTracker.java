package net.runelite.client.plugins.microbot.ocfarmingew;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** Remembers observed gardener confirmations, following Time Tracking's approach. */
final class FarmingProtectionTracker
{
    private final Map<FarmingPatchData, FarmingObservation.Crop> protectedCrops = new EnumMap<>(FarmingPatchData.class);
    private FarmingPatchData selected;
    private long selectedAt;

    synchronized void clear() { protectedCrops.clear(); selected = null; }
    synchronized void select(FarmingPatchData patch, long now) { selected = patch; selectedAt = now; }
    synchronized Map<FarmingPatchData, FarmingObservation.Crop> snapshot() { return new EnumMap<>(protectedCrops); }

    static boolean isConfirmation(String text)
    {
        if (text == null) return false;
        String normalized = text.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        return (normalized.startsWith("that'll do nicely") && normalized.contains("leave it with me")
            && normalized.contains("patch grows for you")) || normalized.contains("already looking after that patch");
    }

    synchronized boolean confirm(int region, int npc, String text, Map<FarmingPatchData, FarmingObservation> observations, long now)
    {
        if (selected == null || selected.region != region || selected.gardenerId() != npc
            || now < selectedAt || now - selectedAt > 90 || !isConfirmation(text)) return false;
        FarmingObservation observation = observations.get(selected);
        if (observation == null || observation.state() != FarmingObservation.State.GROWING) return false;
        boolean changed = protectedCrops.put(selected, observation.crop()) != observation.crop();
        selected = null;
        return changed;
    }

    synchronized void record(FarmingPatchData patch, FarmingObservation observation)
    {
        if (observation != null && observation.state() == FarmingObservation.State.GROWING)
            protectedCrops.put(patch, observation.crop());
    }

    synchronized void observe(FarmingPatchData patch, FarmingObservation previous, FarmingObservation current)
    {
        FarmingObservation.Crop crop = protectedCrops.get(patch);
        if (crop == null) return;
        if (current.state() != FarmingObservation.State.GROWING || current.crop() != crop
            || (previous != null && previous.crop() == crop && previous.growthStage() > current.growthStage()))
            protectedCrops.remove(patch);
    }

    synchronized void restore(FarmingPatchData patch, String stored, FarmingObservation observation)
    {
        if (observation != null && observation.state() == FarmingObservation.State.GROWING
            && observation.crop() != null && ("1:" + observation.crop().name()).equals(stored))
            protectedCrops.put(patch, observation.crop());
    }

    synchronized String encode(FarmingPatchData patch)
    {
        FarmingObservation.Crop crop = protectedCrops.get(patch);
        return crop == null ? "" : "1:" + crop.name();
    }
}
