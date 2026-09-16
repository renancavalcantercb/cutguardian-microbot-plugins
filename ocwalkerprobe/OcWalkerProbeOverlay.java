package net.runelite.client.plugins.microbot.ocwalkerprobe;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class OcWalkerProbeOverlay extends OverlayPanel
{
    private final OcWalkerProbePlugin plugin;
    private final OcWalkerProbeConfig config;

    @Inject
    OcWalkerProbeOverlay(OcWalkerProbePlugin plugin, OcWalkerProbeConfig config)
    {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        panelComponent.setPreferredSize(new Dimension(240, 0));
    }

    private void line(String left, String right)
    {
        panelComponent.getChildren().add(LineComponent.builder().left(left).right(right).build());
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        panelComponent.getChildren().clear();
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("Walker Probe: " + config.label())
            .build());

        WalkerJourney current = plugin.current();
        if (current == null)
        {
            line("moving", "no");
        }
        else
        {
            line("tiles", current.displacement() + " / " + current.tilesWalked());
            line("ticks", String.valueOf(current.ticks()));
            line("efficiency", String.format("%.2f", current.efficiency()));
            line("idle", current.idleTicks() + "t (max " + current.longestStall() + ")");
        }

        List<WalkerJourney> finished = plugin.finished();
        if (!finished.isEmpty())
        {
            WalkerJourney last = finished.get(finished.size() - 1);
            panelComponent.getChildren().add(TitleComponent.builder().text("last trip").build());
            line("tiles", last.displacement() + " in " + last.ticks() + "t");
            line("efficiency", String.format("%.2f", last.efficiency()));
            line("wrong-way", last.wrongWaySteps() + " / " + last.steps());
            line("longest stall", last.longestStall() + "t");
            line("running", last.runningPercent() + "%");
            line("teleports", String.valueOf(plugin.jumps()));
        }

        panelComponent.getChildren().add(LineComponent.builder()
            .left(plugin.sessionSummary())
            .build());

        return super.render(graphics);
    }
}
