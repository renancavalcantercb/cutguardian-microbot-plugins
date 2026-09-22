package net.runelite.client.plugins.microbot.ocbirdhouse;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Instant;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class OcBirdhouseOverlay extends OverlayPanel
{
    private final OcBirdhousePlugin plugin;
    private final OcBirdhouseConfig config;

    @Inject
    OcBirdhouseOverlay(OcBirdhousePlugin plugin, OcBirdhouseConfig config)
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
        OcBirdhousePlugin.View view = plugin.getView();
        long now = Instant.now().getEpochSecond();
        panelComponent.getChildren().add(TitleComponent.builder().text("Bird House Runner").color(Color.GREEN).build());
        panelComponent.getChildren().add(LineComponent.builder()
            .left(config.autoVisit() ? plugin.visitStatus() : "Automation off")
            .leftColor(Color.LIGHT_GRAY).build());
        String work = plugin.workStatus();
        if (work != null && !work.isEmpty() && !work.equals("Waiting to prepare run"))
        {
            panelComponent.getChildren().add(LineComponent.builder().left(work).leftColor(Color.LIGHT_GRAY).build());
        }
        for (BirdHouseData house : BirdHouseData.values())
        {
            BirdHouseObservation observation = view.houses.get(house);
            if (observation == null)
            {
                line(house.label, "Not visited", Color.GRAY);
                continue;
            }
            boolean due = observation.needsWork(now);
            line(house.label, due ? observation.state().label + " - due" : observation.label(now),
                due ? Color.YELLOW : color(observation.state()));
        }
        return super.render(graphics);
    }

    private void line(String left, String right, Color color)
    {
        panelComponent.getChildren().add(LineComponent.builder().left(left).right(right).rightColor(color).build());
    }

    private static Color color(BirdHouseObservation.State state)
    {
        switch (state)
        {
            case SEEDED: return Color.LIGHT_GRAY;
            case BUILT: return Color.WHITE;
            case UNKNOWN: return Color.GRAY;
            default: return Color.WHITE;
        }
    }
}
