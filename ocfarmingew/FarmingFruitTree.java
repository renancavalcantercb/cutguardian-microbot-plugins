package net.runelite.client.plugins.microbot.ocfarmingew;

/** Hub saplings/payments and Time Tracking FRUIT_TREE's 27-value crop blocks. */
public enum FarmingFruitTree implements FarmingTreeKind
{
    APPLE("Apple", 5496, 12946, 27, 5986, 9, "Sweetcorn", 1955, "Pick-apple", 8),
    BANANA("Banana", 5497, 12947, 33, 5386, 4, "Apples(5)", 1963, "Pick-banana", 35),
    ORANGE("Orange", 5498, 12948, 39, 5406, 3, "Strawberries(5)", 2108, "Pick-orange", 72),
    CURRY("Curry", 5499, 12949, 42, 5416, 5, "Bananas(5)", 5970, "Pick-leaf", 99),
    PINEAPPLE("Pineapple", 5500, 12950, 51, 5982, 10, "Watermelon", 2114, "Pick-pineapple", 136),
    PAPAYA("Papaya", 5501, 12951, 57, 2114, 10, "Pineapple", 5972, "Pick-fruit", 163),
    PALM("Palm", 5502, 12952, 68, 5972, 15, "Papaya fruit", 5974, "Pick-coconut", 200),
    DRAGONFRUIT("Dragonfruit", 22866, 22867, 81, 5974, 15, "Coconut", 22929, "Pick-dragonfruit", 227);

    final String label, paymentLabel, pickAction;
    final int sapling, saplingNote, level, payment, paymentAmount, produce, start;
    FarmingFruitTree(String label, int sapling, int note, int level, int payment, int amount,
        String paymentLabel, int produce, String pickAction, int start)
    {
        this.label = label; this.sapling = sapling; this.saplingNote = note; this.level = level;
        this.payment = payment; this.paymentAmount = amount; this.paymentLabel = paymentLabel;
        this.produce = produce; this.pickAction = pickAction; this.start = start;
    }
    static FarmingFruitTree fromRaw(int raw)
    {
        for (FarmingFruitTree tree : values()) if (raw >= tree.start && raw <= tree.start + 26) return tree;
        return null;
    }
    static FarmingObservation.State state(int raw)
    {
        if (raw >= 0 && raw <= 2) return FarmingObservation.State.WEEDS;
        FarmingFruitTree tree = fromRaw(raw);
        if (tree == null) return raw == 3 ? FarmingObservation.State.EMPTY
            : raw >= 0 && raw <= 255 ? FarmingObservation.State.WEEDS : FarmingObservation.State.UNKNOWN;
        int stage = raw - tree.start;
        if (stage < 6) return FarmingObservation.State.GROWING;
        if (stage == 6) return FarmingObservation.State.CHECKED;
        if (stage <= 12) return FarmingObservation.State.HARVEST;
        if (stage <= 18) return FarmingObservation.State.DISEASED;
        if (stage <= 24) return FarmingObservation.State.DEAD;
        return stage == 25 ? FarmingObservation.State.STUMP : FarmingObservation.State.CHECK_HEALTH;
    }
    public String label() { return label; }
    public int sapling() { return sapling; }
    public int saplingNote() { return saplingNote; }
    public int level() { return level; }
    public int payment() { return payment; }
    public int paymentNote() { return payment + 1; }
    public int paymentAmount() { return paymentAmount; }
    public String paymentLabel() { return paymentLabel; }
    public int cycles() { return 6; }
    public long cycleSeconds() { return 9600; }
    @Override public String toString() { return label; }
}
