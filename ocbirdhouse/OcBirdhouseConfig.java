package net.runelite.client.plugins.microbot.ocbirdhouse;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(OcBirdhouseConfig.GROUP)
public interface OcBirdhouseConfig extends Config
{
    String GROUP = "ocbirdhouse";

    @ConfigItem(keyName = "showOverlay", name = "Show monitor",
        description = "Show birdhouse observations and time left until the next run.", position = 0)
    default boolean showOverlay() { return true; }

    @ConfigItem(keyName = "autoVisit", name = "Automatically run birdhouses",
        description = "Bank for supplies, travel to Fossil Island via the Digsite barge and empty, build and reseed due houses.", position = 1)
    default boolean autoVisit() { return false; }

    @ConfigItem(keyName = "depositAllBeforeRun", name = "Deposit inventory before run",
        description = "Deposit all inventory items at the bank before withdrawing run supplies.", position = 2)
    default boolean depositAllBeforeRun() { return true; }
}
