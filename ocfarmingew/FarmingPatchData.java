package net.runelite.client.plugins.microbot.ocfarmingew;

/** Canonical farming transmission regions, not a radius around the object. */
enum FarmingPatchData
{
    LUMBRIDGE_TREE("Lumbridge tree", 12594, 8391, 3195, 3228, 2681, 12850),
    VARROCK_TREE("Varrock tree", 12854, 8390, 3226, 3458, 11957, 12853),
    FALADOR_TREE("Falador Park tree", 11828, 8389, 3001, 3374, 2679, 12084),
    TAVERLEY_TREE("Taverley tree", 11573, 8388, 2936, 3440, 2678, 11829),
    GNOME_TREE("Gnome Stronghold tree", 9781, 19147, 2437, 3417, 2687, 9782, 9526, 9525),
    NEMUS_TREE("Nemus Retreat tree", 5427, 56953, 1365, 3320, 14514, 5428, 5684),
    GNOME_FRUIT(true, "Gnome Stronghold fruit", 9781, 4772, 7962, 2473, 3446, 2682, 9782, 9526, 9525),
    CATHERBY_FRUIT(true, "Catherby fruit", 11317, 4771, 7965, 2858, 3432, 2670),
    VILLAGE_FRUIT(true, "Tree Gnome Village fruit", 9777, 4771, 7963, 2490, 3181, 2683, 10033),
    BRIMHAVEN_FRUIT(true, "Brimhaven fruit", 11058, 4771, 7964, 2765, 3213, 2669, 11057),
    KASTORI_FRUIT(true, "Kastori fruit", 5423, 4772, 56955, 1349, 3058, 14516, 5167, 5424);

    final String label;
    final boolean fruit;
    final int region;
    final int varbit;
    final int objectId;
    final int visitX;
    final int visitY;
    private final int gardener;
    private final int[] aliases;

    FarmingPatchData(String label, int region, int objectId, int x, int y, int gardener, int... aliases)
    { this(false, label, region, 4771, objectId, x, y, gardener, aliases); }

    FarmingPatchData(boolean fruit, String label, int region, int varbit, int objectId, int x, int y, int gardener, int... aliases)
    {
        this.fruit = fruit;
        this.label = label;
        this.region = region;
        this.varbit = varbit;
        this.objectId = objectId;
        this.visitX = x;
        this.visitY = y;
        this.gardener = gardener;
        this.aliases = aliases;
    }

    String key()
    {
        return "patch." + region + "." + varbit;
    }

    int gardenerId() { return gardener; }

    boolean enabled(OcFarmingEWConfig config)
    {
        if (fruit ? !config.autoFruitTrees() : !config.autoTrees()) return false;
        switch (this)
        {
            case LUMBRIDGE_TREE: return config.lumbridgeTree();
            case VARROCK_TREE: return config.varrockTree();
            case FALADOR_TREE: return config.faladorTree();
            case TAVERLEY_TREE: return config.taverleyTree();
            case GNOME_TREE: return config.gnomeTree();
            case NEMUS_TREE: return config.nemusTree();
            case GNOME_FRUIT: return config.gnomeFruit();
            case CATHERBY_FRUIT: return config.catherbyFruit();
            case VILLAGE_FRUIT: return config.villageFruit();
            case BRIMHAVEN_FRUIT: return config.brimhavenFruit();
            case KASTORI_FRUIT: return config.kastoriFruit();
            default: return false;
        }
    }

    boolean tend(OcFarmingEWConfig config) { return enabled(config); }
    FarmingTreeKind chosen(OcFarmingEWConfig config) { return fruit ? config.fruitTree() : config.tree(); }
    boolean protect(OcFarmingEWConfig config) { return fruit ? config.fruitProtection() : config.treeProtection(); }
    int leprechaunId() { return this == KASTORI_FRUIT ? 12765 : 0; }

    boolean acceptsRegion(int actual)
    {
        if (actual == region) return true;
        for (int alias : aliases) if (alias == actual) return true;
        return false;
    }

    static int regionAt(net.runelite.api.coords.WorldPoint point)
    {
        if (point == null) return -1;
        for (FarmingPatchData patch : values())
            if (patch.includes(point.getX(), point.getY(), point.getPlane())) return patch.region;
        return -1;
    }

    String paymentAction() { return "Pay"; }

    boolean includes(int x, int y, int plane)
    {
        // This corner of Catherby transmits allotments through the same varbit.
        if (this == CATHERBY_FRUIT && x < 2840 && y >= 3440) return false;
        return plane == 0 && acceptsRegion(((x >> 6) << 8 | (y >> 6)));
    }
}
