package net.runelite.client.plugins.microbot.ocfarmingew;

/** A regional tree observation. Maturity remains an estimate until revisited. */
final class FarmingObservation
{
    static final long CYCLE_SECONDS = 2400;
    enum Crop
    {
        OAK("Oak"), WILLOW("Willow"), MAPLE("Maple"), YEW("Yew"), MAGIC("Magic"),
        APPLE("Apple"), BANANA("Banana"), ORANGE("Orange"), CURRY("Curry"),
        PINEAPPLE("Pineapple"), PAPAYA("Papaya"), PALM("Palm"), DRAGONFRUIT("Dragonfruit");
        final String label;
        Crop(String label) { this.label = label; }
    }
    enum State
    {
        WEEDS("Weeds"), EMPTY("Empty"), GROWING("growing"), DISEASED("diseased"),
        DEAD("dead"), CHECK_HEALTH("check health"), CHECKED("health checked"),
        STUMP("stump"), HARVEST("fruit ready"), UNKNOWN("Unsupported state");
        final String label;
        State(String label) { this.label = label; }
    }
    final int raw;
    final boolean fruit;
    final long observedAt, earliestReadyAt, latestReadyAt;

    FarmingObservation(int raw, long observedAt, long earliestReadyAt, long latestReadyAt)
    { this(false, raw, observedAt, earliestReadyAt, latestReadyAt); }

    FarmingObservation(boolean fruit, int raw, long observedAt, long earliestReadyAt, long latestReadyAt)
    {
        this.fruit = fruit;
        this.raw = raw;
        this.observedAt = observedAt;
        this.earliestReadyAt = earliestReadyAt;
        this.latestReadyAt = latestReadyAt;
    }

    State state()
    {
        if (fruit) return FarmingFruitTree.state(raw);
        if (raw >= 0 && raw <= 2) return State.WEEDS;
        if (raw == 3) return State.EMPTY;
        FarmingTree kind = FarmingTree.fromRaw(raw);
        if (kind == null) return State.UNKNOWN;
        if (raw < kind.check && raw >= kind.start) return State.GROWING;
        if (raw == kind.check) return State.CHECK_HEALTH;
        if (raw == kind.check + 2) return State.STUMP;
        if (raw == kind.check + 1 || (kind == FarmingTree.WILLOW && raw >= 192 && raw <= 197)) return State.CHECKED;
        return kind.sick(raw, kind.diseased) ? State.DISEASED : State.DEAD;
    }

    Crop crop()
    {
        if (fruit)
        {
            FarmingFruitTree kind = FarmingFruitTree.fromRaw(raw);
            return kind == null ? null : Crop.valueOf(kind.name());
        }
        FarmingTree kind = FarmingTree.fromRaw(raw);
        return kind == null ? null : kind.observedCrop;
    }

    int growthStage()
    {
        if (fruit)
        {
            FarmingFruitTree kind = FarmingFruitTree.fromRaw(raw);
            return kind != null && state() == State.GROWING ? raw - kind.start : -1;
        }
        FarmingTree kind = FarmingTree.fromRaw(raw);
        return kind != null && raw >= kind.start && raw < kind.check ? raw - kind.start : -1;
    }

    String label() { return crop() == null ? state().label : crop().label + " " + state().label; }
    boolean isDue(long now) { return state() == State.GROWING && latestReadyAt > 0 && now >= latestReadyAt; }

    static FarmingObservation observe(int raw, long now, FarmingObservation previous, boolean continuous)
    { return observe(false, raw, now, previous, continuous); }

    static FarmingObservation observe(boolean fruit, int raw, long now, FarmingObservation previous, boolean continuous)
    {
        long earliest = 0, latest = 0;
        FarmingObservation current = new FarmingObservation(fruit, raw, now, 0, 0);
        if (current.state() == State.GROWING)
        {
            FarmingTreeKind kind = FarmingTreeKind.from(current);
            int remaining = kind.cycles() - current.growthStage();
            earliest = now + (remaining - 1) * kind.cycleSeconds();
            latest = now + remaining * kind.cycleSeconds();
            if (continuous && previous != null && previous.state() == State.GROWING
                && current.crop() == previous.crop() && now >= previous.observedAt
                && now - previous.observedAt <= 2 && (current.growthStage() == previous.growthStage()
                    || current.growthStage() == previous.growthStage() + 1))
            {
                long lower = Math.max(earliest, previous.earliestReadyAt);
                long upper = Math.min(latest, previous.latestReadyAt);
                if (lower <= upper) { earliest = lower; latest = upper; }
            }
        }
        return new FarmingObservation(fruit, raw, now, earliest, latest);
    }

    String encode() { return (fruit ? "F1:" : "T1:") + raw + ":" + observedAt + ":" + earliestReadyAt + ":" + latestReadyAt; }

    static FarmingObservation decode(String stored, long now)
    {
        if (stored == null) return null;
        try
        {
            String[] fields = stored.split(":", -1);
            // Retain 0.6 tree history; never reinterpret legacy allotment values.
            if (fields.length != 5 || !(fields[0].equals("T1") || fields[0].equals("F1"))) return null;
            FarmingObservation result = new FarmingObservation(fields[0].equals("F1"), Integer.parseInt(fields[1]),
                Long.parseLong(fields[2]), Long.parseLong(fields[3]), Long.parseLong(fields[4]));
            if (result.raw < 0 || result.raw > 255 || result.observedAt <= 0 || result.observedAt > now
                || result.earliestReadyAt < 0 || result.latestReadyAt < result.earliestReadyAt) return null;
            if (result.state() == State.GROWING)
            {
                if (result.earliestReadyAt < result.observedAt
                    || result.latestReadyAt - result.observedAt > FarmingTreeKind.from(result).cycles() * FarmingTreeKind.from(result).cycleSeconds()) return null;
            }
            else if (result.earliestReadyAt != 0 || result.latestReadyAt != 0) return null;
            return result;
        }
        catch (NumberFormatException ex) { return null; }
    }
}
