package net.runelite.client.plugins.microbot.ocbirdhouse;

/**
 * A birdhouse observation. Decoding mirrors RuneLite Time Tracking's
 * BirdHouseState: 0 is empty, multiples of 3 are seeded (growing), any other
 * positive value is built but unseeded. Seeded houses mature
 * {@link #DURATION_SECONDS} after the seeding was observed.
 */
final class BirdHouseObservation
{
    static final long DURATION_SECONDS = 3000;
    static final int MAX_RAW = 12;

    enum State
    {
        EMPTY("Empty"), BUILT("built, needs seeds"), SEEDED("growing"), UNKNOWN("Unknown");
        final String label;
        State(String label) { this.label = label; }
    }

    final int raw;
    final long observedAt;
    final long readyAt;

    BirdHouseObservation(int raw, long observedAt, long readyAt)
    {
        this.raw = raw;
        this.observedAt = observedAt;
        this.readyAt = readyAt;
    }

    State state()
    {
        if (raw == 0) return State.EMPTY;
        if (raw < 0 || raw > MAX_RAW) return State.UNKNOWN;
        return raw % 3 == 0 ? State.SEEDED : State.BUILT;
    }

    /** Empty and unseeded houses need a visit now; seeded houses when mature. */
    boolean needsWork(long now)
    {
        switch (state())
        {
            case EMPTY:
            case BUILT: return true;
            case SEEDED: return readyAt > 0 && now >= readyAt;
            default: return false;
        }
    }

    String label(long now)
    {
        if (state() == State.SEEDED && !needsWork(now))
        {
            long left = readyAt - now;
            return "growing ~" + (left / 60) + "m" + (left % 60) + "s";
        }
        return state().label;
    }

    static BirdHouseObservation observe(int raw, long now)
    {
        long ready = raw > 0 && raw <= MAX_RAW && raw % 3 == 0 ? now + DURATION_SECONDS : 0;
        return new BirdHouseObservation(raw, now, ready);
    }

    String encode() { return "B1:" + raw + ":" + observedAt + ":" + readyAt; }

    static BirdHouseObservation decode(String stored, long now)
    {
        if (stored == null) return null;
        try
        {
            String[] fields = stored.split(":", -1);
            if (fields.length != 4 || !fields[0].equals("B1")) return null;
            BirdHouseObservation result = new BirdHouseObservation(
                Integer.parseInt(fields[1]), Long.parseLong(fields[2]), Long.parseLong(fields[3]));
            if (result.raw < 0 || result.raw > MAX_RAW || result.observedAt <= 0 || result.observedAt > now
                || result.readyAt < 0) return null;
            if (result.state() == State.SEEDED)
            {
                if (result.readyAt < result.observedAt || result.readyAt - result.observedAt > DURATION_SECONDS) return null;
            }
            else if (result.readyAt != 0) return null;
            return result;
        }
        catch (NumberFormatException ex) { return null; }
    }
}
