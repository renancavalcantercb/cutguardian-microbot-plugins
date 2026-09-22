package net.runelite.client.plugins.microbot.ocfarmingew;

/** Wait for two complete stable ticks after login, region change or a modal. */
final class FarmingSamplingGate
{
    private int previousRegion = -1;
    private int stableTicks;

    void reset()
    {
        previousRegion = -1;
        stableTicks = 0;
    }

    boolean accept(int region, boolean eligible)
    {
        if (!eligible)
        {
            reset();
            return false;
        }
        if (previousRegion != region)
        {
            previousRegion = region;
            stableTicks = 0;
        }
        stableTicks = Math.min(3, stableTicks + 1);
        return stableTicks >= 3;
    }
}
