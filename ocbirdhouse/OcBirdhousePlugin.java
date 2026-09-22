package net.runelite.client.plugins.microbot.ocbirdhouse;

import com.google.inject.Provides;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.WidgetNode;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
    name = "Bird House Runner",
    description = "Fossil Island birdhouses using Efficient Walker: supplies, 50 minute tracking, barge travel and reseeding.",
    tags = {"autonomous", "birdhouse", "hunter", "fossil island", "efficient walker", "monitor"},
    authors = {"cutguardian"},
    version = OcBirdhousePlugin.version,
    minClientVersion = "2.6.22",
    enabledByDefault = PluginConstants.DEFAULT_ENABLED,
    isExternal = PluginConstants.IS_EXTERNAL
)
public class OcBirdhousePlugin extends Plugin
{
    static final String version = "0.1.0";

    @Inject private Client client;
    @Inject private ConfigManager configManager;
    @Inject private OverlayManager overlayManager;
    @Inject private OcBirdhouseOverlay overlay;
    @Inject private OcBirdhouseConfig config;

    private final BirdHouseTracker tracker = new BirdHouseTracker();
    private boolean active;
    private boolean sampledLastTick;
    private int ticksSinceSave;
    private long session;
    private boolean navigationReady;
    private BirdHouseVisitRunner visits;
    private volatile View view = new View("Waiting for login", -1, Collections.emptyMap(), 0, false);

    static final class View
    {
        final String status;
        final int liveRegion;
        final Map<BirdHouseData, BirdHouseObservation> houses;
        final long session;
        final boolean navigationReady;

        View(String status, int liveRegion, Map<BirdHouseData, BirdHouseObservation> houses,
            long session, boolean navigationReady)
        {
            this.status = status;
            this.liveRegion = liveRegion;
            this.houses = Collections.unmodifiableMap(houses);
            this.session = session;
            this.navigationReady = navigationReady;
        }
    }

    @Provides
    OcBirdhouseConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(OcBirdhouseConfig.class);
    }

    @Override
    protected void startUp()
    {
        active = true;
        session++;
        navigationReady = false;
        sampledLastTick = false;
        ticksSinceSave = 0;
        tracker.switchProfile(null);
        publish("Waiting for login", -1);
        overlayManager.add(overlay);
        visits = new BirdHouseVisitRunner(this, config);
        visits.start();
    }

    @Override
    protected void shutDown()
    {
        active = false;
        if (visits != null) visits.stop();
        visits = null;
        session++;
        navigationReady = false;
        persist();
        tracker.switchProfile(null);
        sampledLastTick = false;
        publish("Stopped", -1);
        overlayManager.remove(overlay);
    }

    @Subscribe
    public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
    {
        if (!active) return;
        session++;
        persist(); // Explicit old profile; never write old observations into the new account.
        tracker.switchProfile(null);
        pause("Waiting for profile");
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (active && event.getGameState() != GameState.LOGGED_IN)
        {
            if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING) session++;
            persist();
            pause("Waiting for login / loading");
        }
    }

    @Subscribe
    public void onWidgetClosed(WidgetClosed event)
    {
        if (active && event.getModalMode() != WidgetModalMode.NON_MODAL)
        {
            pause("Waiting for house updates");
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (!active) return;
        navigationReady = false;
        // Game reads occur on the client thread; navigation uses a separate worker.
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
        {
            pause("Waiting for login");
            return;
        }
        long now = Instant.now().getEpochSecond();
        String profile = configManager.getRSProfileKey();
        if (!Objects.equals(profile, tracker.profile()))
        {
            session++;
            persist();
            tracker.switchProfile(profile);
            sampledLastTick = false;
            if (profile != null)
            {
                for (BirdHouseData house : BirdHouseData.values())
                {
                    tracker.restore(house, configManager.getConfiguration(OcBirdhouseConfig.GROUP, profile, house.key()), now);
                }
            }
        }
        if (profile == null)
        {
            pause("Waiting for profile");
            return;
        }
        // Growth rates differ on seasonal worlds. Do not persist those rates as normal play.
        if (client.getWorldType().contains(WorldType.SEASONAL))
        {
            pause("Seasonal world not supported");
            return;
        }
        WorldPoint location = client.getLocalPlayer().getWorldLocation();
        int region = location.getRegionID();
        navigationReady = !client.isInInstancedRegion() && !hasModal();
        // Like Time Tracking, varps only transmit on Fossil Island; off-island
        // the monitor keeps history and the runner travels back when due.
        if (location.getPlane() == 0 && BirdHouseData.onFossilIsland(region))
        {
            Map<BirdHouseData, Integer> raws = new java.util.EnumMap<>(BirdHouseData.class);
            for (BirdHouseData house : BirdHouseData.values())
            {
                raws.put(house, client.getVarbitValue(house.varp));
            }
            if (tracker.syncedOut(raws))
            {
                publish("Syncing birdhouses", region);
                return;
            }
            boolean changed = tracker.observeAll(raws, now);
            sampledLastTick = true;
            if (changed || ++ticksSinceSave >= 50) persist();
            publish("Observing birdhouses", region);
            return;
        }
        if (sampledLastTick) persist();
        sampledLastTick = false;
        publish("History - visit Fossil Island to sync", -1);
    }

    private boolean hasModal()
    {
        Widget welcome = client.getWidget(InterfaceID.WelcomeScreen.MOTW);
        if (welcome != null && !welcome.isHidden()) return true;
        for (WidgetNode node : client.getComponentTable())
        {
            if (node.getModalMode() != WidgetModalMode.NON_MODAL) return true;
        }
        return false;
    }

    private void pause(String status)
    {
        navigationReady = false;
        sampledLastTick = false;
        publish(status, -1);
    }

    private void persist()
    {
        String profile = tracker.profile();
        if (profile == null) return;
        tracker.snapshot().forEach((house, observation) -> {
            configManager.setConfiguration(OcBirdhouseConfig.GROUP, profile, house.key(), observation.encode());
        });
        ticksSinceSave = 0;
    }

    private void publish(String status, int liveRegion)
    {
        view = new View(status, liveRegion, tracker.snapshot(), session, navigationReady);
    }

    View getView() { return view; }
    String visitStatus() { return visits == null ? "Automatic visits off" : visits.status(); }
    String workStatus() { return visits == null ? "Birdhouses stopped" : visits.workStatus(); }
}
