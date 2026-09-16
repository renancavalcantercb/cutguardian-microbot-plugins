package net.runelite.client.plugins.microbot.ocwalkerprobe;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("ocwalkerprobe")
public interface OcWalkerProbeConfig extends Config
{
    @ConfigItem(
        keyName = "idleTicksToEnd",
        name = "Ticks idle to end a journey",
        description = "How long the player must stand still before the trip counts as finished",
        position = 1
    )
    @Range(min = 2, max = 50)
    default int idleTicksToEnd()
    {
        return 8;
    }

    @ConfigItem(
        keyName = "minTiles",
        name = "Minimum tiles to report",
        description = "Journeys shorter than this are discarded, so shuffling at a bank is not logged",
        position = 2
    )
    @Range(min = 1, max = 200)
    default int minTiles()
    {
        return 15;
    }

    @ConfigItem(
        keyName = "label",
        name = "Run label",
        description = "Written into every log line, so runs from different clients can be told apart",
        position = 3
    )
    default String label()
    {
        return "microbot";
    }
}
