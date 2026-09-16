package net.runelite.client.plugins.microbot.ocwalkerprobe;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Measures whoever is moving the player.
 *
 * Deliberately blind to the walker: it reads position only, so the numbers
 * are comparable across clients. Run the same route under each walker and the
 * difference stops being an impression.
 */
@PluginDescriptor(
    name = "[OC] Walker Probe",
    description = "Measures movement quality: efficiency, stalls and wrong-way steps",
    tags = {"walking", "pathing", "debug", "oc"},
    authors = {"cutguardian"},
    version = OcWalkerProbePlugin.version,
    minClientVersion = "2.6.22",
    enabledByDefault = PluginConstants.DEFAULT_ENABLED,
    isExternal = PluginConstants.IS_EXTERNAL
)
public class OcWalkerProbePlugin extends Plugin
{
    static final String version = "1.0.0";
    private static final Logger log = LoggerFactory.getLogger(OcWalkerProbePlugin.class);

    @Inject private Client client;
    @Inject private OcWalkerProbeConfig config;
    @Inject private OverlayManager overlayManager;
    @Inject private OcWalkerProbeOverlay overlay;

    private final List<WalkerJourney> finished = new ArrayList<>();
    private volatile WalkerJourney current;
    private WorldPoint lastPoint;
    private int idleSinceMove;
    private volatile int jumps;

    @Provides
    OcWalkerProbeConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(OcWalkerProbeConfig.class);
    }

    @Override
    protected void startUp()
    {
        overlayManager.add(overlay);
        reset();
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(overlay);
        if (!finished.isEmpty())
        {
            log.info("Walker Probe [{}] session: {}", config.label(), sessionSummary());
        }
        reset();
        finished.clear();
    }

    private void reset()
    {
        current = null;
        lastPoint = null;
        idleSinceMove = 0;
    }

    int jumps() { return jumps; }

    WalkerJourney current() { return current; }
    List<WalkerJourney> finished() { return Collections.unmodifiableList(finished); }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            reset();
            return;
        }
        Player player = client.getLocalPlayer();
        WorldPoint point = player == null ? null : player.getWorldLocation();
        if (point == null)
        {
            return;
        }
        if (lastPoint == null)
        {
            lastPoint = point;
            return;
        }

        boolean moved = !point.equals(lastPoint);
        boolean jumped = moved && !WalkerJourney.isWalkStep(lastPoint, point);
        lastPoint = point;

        // A teleport ends the leg and starts a new one where it landed. Folding
        // it into one journey destroys both efficiency and wrong-way.
        if (jumped)
        {
            jumps++;
            closeLeg();
            current = new WalkerJourney(point);
            idleSinceMove = 0;
            return;
        }
        // Varp 173 is the classic run-enabled flag. Pace does not affect
        // efficiency or wrong-way steps, but it is worth logging so a slow
        // trip is not mistaken for a bad route.
        boolean running = client.getVarpValue(173) == 1;

        // A journey starts on the first step, not on a click the probe cannot see.
        if (current == null)
        {
            if (moved)
            {
                current = new WalkerJourney(point);
                idleSinceMove = 0;
            }
            return;
        }

        if (current.record(point, running))
        {
            idleSinceMove = 0;
            return;
        }

        idleSinceMove++;
        if (idleSinceMove < config.idleTicksToEnd())
        {
            return;
        }

        closeLeg();
        reset();
    }

    /** Records the leg in progress when it is long enough to mean anything. */
    private void closeLeg()
    {
        WalkerJourney done = current;
        current = null;
        if (done == null || done.displacement() < config.minTiles())
        {
            return;
        }
        finished.add(done);
        log.info("Walker Probe [{}]: {}", config.label(), done.summary());
    }

    /** Averages across the session, so one bad trip does not read as a verdict. */
    String sessionSummary()
    {
        if (finished.isEmpty())
        {
            return "no journeys yet";
        }
        double efficiency = 0;
        int wrongWay = 0;
        int idle = 0;
        int ticks = 0;
        for (WalkerJourney journey : finished)
        {
            efficiency += journey.efficiency();
            wrongWay += journey.wrongWaySteps();
            idle += journey.idleTicks();
            ticks += journey.ticks();
        }
        return String.format("%d legs | avg eff %.2f | wrong-way %d | idle %d/%dt | %d jumps",
            finished.size(), efficiency / finished.size(), wrongWay, idle, ticks, jumps);
    }
}
