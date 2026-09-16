package net.runelite.client.plugins.microbot.ocfarming;

/** Regular tree patches only; saplings and payments verified against gameval/Hub. */
public enum FarmingTree implements FarmingTreeKind
{
    OAK("Oak", FarmingObservation.Crop.OAK, 5370, 15, 5968, 1, 8, 12, 73, 137),
    WILLOW("Willow", FarmingObservation.Crop.WILLOW, 5371, 30, 5386, 1, 15, 21, 80, 144),
    MAPLE("Maple", FarmingObservation.Crop.MAPLE, 5372, 45, 5396, 1, 24, 32, 89, 153),
    YEW("Yew", FarmingObservation.Crop.YEW, 5373, 60, 6016, 10, 35, 45, 100, 164),
    MAGIC("Magic", FarmingObservation.Crop.MAGIC, 5374, 75, 5974, 25, 48, 60, 113, 177);

    final String label;
    final FarmingObservation.Crop observedCrop;
    final int sapling, level, payment, paymentNote, paymentAmount;
    final int start, check, diseased, dead, cycles;

    FarmingTree(String label, FarmingObservation.Crop crop, int sapling, int level,
        int payment, int amount, int start, int check, int diseased, int dead)
    {
        this.label = label;
        this.observedCrop = crop;
        this.sapling = sapling;
        this.level = level;
        this.payment = payment;
        this.paymentNote = payment + 1;
        this.paymentAmount = amount;
        this.start = start;
        this.check = check;
        this.diseased = diseased;
        this.dead = dead;
        this.cycles = check - start;
    }

    boolean sick(int raw, int base)
    {
        // The stage immediately before the final diseased/dead value is unused.
        return (raw >= base && raw < base + cycles - 1) || raw == base + cycles;
    }

    static FarmingTree fromRaw(int raw)
    {
        for (FarmingTree tree : values())
            if ((raw >= tree.start && raw <= tree.check + 2)
                || tree.sick(raw, tree.diseased) || tree.sick(raw, tree.dead)
                || (tree == WILLOW && raw >= 192 && raw <= 197)) return tree;
        return null;
    }

    static FarmingTree from(FarmingObservation observation)
    {
        return observation != null && !observation.fruit ? fromRaw(observation.raw) : null;
    }

    public String paymentLabel()
    {
        switch (this)
        {
            case OAK: return "Tomatoes(5)";
            case WILLOW: return "Apples(5)";
            case MAPLE: return "Oranges(5)";
            case YEW: return "cactus spines";
            default: return "coconuts";
        }
    }

    public String label() { return label; }
    public int sapling() { return sapling; }
    public int saplingNote() { return 12941 + ordinal(); }
    public int level() { return level; }
    public int payment() { return payment; }
    public int paymentNote() { return paymentNote; }
    public int paymentAmount() { return paymentAmount; }
    public int cycles() { return cycles; }
    @Override public String toString() { return label; }
}
