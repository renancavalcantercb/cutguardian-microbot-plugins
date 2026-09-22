package net.runelite.client.plugins.microbot.ocbirdhouse;

/** Canonical birdhouse spaces. Varp mapping matches RuneLite's BirdHouseSpace. */
enum BirdHouseData
{
    VERDANT_SW("Verdant Valley SW", 1629, 3763, 3755),
    VERDANT_NE("Verdant Valley NE", 1628, 3768, 3761),
    MEADOW_N("Mushroom Meadow N", 1626, 3677, 3882),
    MEADOW_S("Mushroom Meadow S", 1627, 3679, 3815);

    /** Regions where birdhouse varps transmit, mirroring Time Tracking's region gate. */
    static final java.util.Set<Integer> FOSSIL_ISLAND_REGIONS = java.util.Set.of(
        14650, 14651, 14652, 14906, 14907, 14908, 15162, 15163);

    final String label;
    final int varp;
    final int visitX;
    final int visitY;

    BirdHouseData(String label, int varp, int x, int y)
    {
        this.label = label;
        this.varp = varp;
        this.visitX = x;
        this.visitY = y;
    }

    String key()
    {
        return "house." + varp;
    }

    /** Verdant houses are worked before the mushtree hop; meadow houses after. */
    boolean verdant()
    {
        return this == VERDANT_SW || this == VERDANT_NE;
    }

    static boolean onFossilIsland(int region)
    {
        return FOSSIL_ISLAND_REGIONS.contains(region);
    }
}
