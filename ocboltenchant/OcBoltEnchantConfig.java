package net.runelite.client.plugins.microbot.ocboltenchant;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("ocboltenchant")
public interface OcBoltEnchantConfig extends Config
{
    @ConfigItem(keyName = "boltType", name = "Bolt type",
        description = "Carry only this type of unenchanted gem-tipped bolt. Space confirms the game's selection.", position = 0)
    default BoltType boltType() { return BoltType.SAPPHIRE; }

    @Range(min = 1, max = 10)
    @ConfigItem(keyName = "castInterval", name = "Ticks between casts",
        description = "1 attempts a new enchantment each game tick. Hold Shift to pause.", position = 1)
    default int castInterval() { return 1; }
}
