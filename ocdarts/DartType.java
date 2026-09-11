package net.runelite.client.plugins.microbot.ocdarts;

public enum DartType
{
    BRONZE("Bronze", 819, 10),
    IRON("Iron", 820, 22),
    STEEL("Steel", 821, 37),
    MITHRIL("Mithril", 822, 52),
    ADAMANT("Adamant", 823, 67),
    RUNE("Rune", 824, 81),
    AMETHYST("Amethyst", 25853, 90),
    DRAGON("Dragon", 11232, 95);

    private final String displayName;
    private final int tipId;
    private final int level;

    DartType(String displayName, int tipId, int level)
    {
        this.displayName = displayName;
        this.tipId = tipId;
        this.level = level;
    }

    public int getTipId()
    {
        return tipId;
    }

    public int getLevel()
    {
        return level;
    }

    @Override
    public String toString()
    {
        return displayName + " (level " + level + ")";
    }
}
