package net.runelite.client.plugins.microbot.ocfarmingew;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Instant;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class OcFarmingEWOverlay extends OverlayPanel
{
    private final OcFarmingEWPlugin plugin;
    private final OcFarmingEWConfig config;

    @Inject
    OcFarmingEWOverlay(OcFarmingEWPlugin plugin, OcFarmingEWConfig config)
    {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        panelComponent.setPreferredSize(new Dimension(280, 0));
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showOverlay()) return null;
        OcFarmingEWPlugin.View view = plugin.getView();
        long now = Instant.now().getEpochSecond();
        panelComponent.getChildren().add(TitleComponent.builder().text("Farming Runner (EW)").color(Color.GREEN).build());
        panelComponent.getChildren().add(LineComponent.builder()
            .left(OcFarmingEWConfig.tending(config) || config.autoVisit() ? plugin.visitStatus() : "Automation off")
            .leftColor(Color.LIGHT_GRAY).build());
        for (FarmingPatchData patch : FarmingPatchData.values())
        {
            if (!patch.enabled(config)) continue;
            FarmingObservation observation = view.patches.get(patch);
            if (observation == null)
            {
                line(patchName(patch), "Not visited", Color.GRAY);
                continue;
            }
            String detail = observation.label();
            Color color = color(observation.state());
            if (observation.state() == FarmingObservation.State.GROWING)
            {
                detail = observation.crop().label + " - " + (observation.isDue(now)
                    ? "Revisit" : "~" + duration(observation.latestReadyAt - now));
                color = observation.isDue(now) ? Color.YELLOW : Color.LIGHT_GRAY;
                if (patch.protect(config) && view.protectedCrops.get(patch) != observation.crop())
                {
                    detail = "Protect " + observation.crop().label;
                    color = Color.YELLOW;
                }
            }
            line(patchName(patch), detail, color);
        }
        return super.render(graphics);
    }

    private void line(String left, String right, Color color)
    {
        panelComponent.getChildren().add(LineComponent.builder().left(left).right(right).rightColor(color).build());
    }

    private static String patchName(FarmingPatchData patch)
    {
        if (patch == FarmingPatchData.GNOME_TREE) return "Gnome (tree)";
        if (patch == FarmingPatchData.GNOME_FRUIT) return "Gnome (fruit)";
        if (patch == FarmingPatchData.VILLAGE_FRUIT) return "Gnome Village";
        return patch.label.replace(" tree", "").replace(" fruit", "");
    }

    private static String duration(long seconds)
    {
        long minutes = (Math.max(0, seconds) + 59) / 60;
        return minutes >= 60 ? minutes / 60 + "h" + minutes % 60 + "m" : minutes + "m";
    }

    private static Color color(FarmingObservation.State state)
    {
        switch (state)
        {
            case CHECK_HEALTH: case CHECKED: case HARVEST: return Color.GREEN;
            case DISEASED: case DEAD: return Color.RED;
            case UNKNOWN: return Color.GRAY;
            default: return Color.WHITE;
        }
    }
}
