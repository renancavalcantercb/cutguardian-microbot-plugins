package net.runelite.client.plugins.microbot.ocdarts;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("ocdarts")
public interface OcDartsConfig extends Config
{
    @ConfigItem(
        keyName = "dartType",
        name = "Dart type",
        description = "Dart tips to combine with feathers on each click. Hold Shift for normal clicks.",
        position = 0
    )
    default DartType dartType()
    {
        return DartType.BRONZE;
    }
}
