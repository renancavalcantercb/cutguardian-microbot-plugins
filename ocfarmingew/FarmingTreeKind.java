package net.runelite.client.plugins.microbot.ocfarmingew;

/** Shared supplies and action contract; each patch retains its own decoder. */
interface FarmingTreeKind
{
    String label();
    int sapling();
    int saplingNote();
    int level();
    int payment();
    int paymentNote();
    int paymentAmount();
    String paymentLabel();
    int cycles();
    default long cycleSeconds() { return 2400; }

    static FarmingTreeKind from(FarmingObservation observation)
    {
        if (observation == null) return null;
        return observation.fruit ? FarmingFruitTree.fromRaw(observation.raw) : FarmingTree.fromRaw(observation.raw);
    }
}
