package net.runelite.client.plugins.microbot.ocfarmingew;

import com.google.inject.Provides;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.MenuAction;
import net.runelite.api.GameState;
import net.runelite.api.WidgetNode;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.api.widgets.WidgetModelType;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
    name = "Farming Runner (Efficient Walker)",
    description = "Trees and fruit trees using Efficient Walker: supplies, tracking, visits, protection, fruit picking/noting and replanting.",
    tags = {"autonomous", "farming", "trees", "saplings", "efficient walker", "monitor"},
    authors = {"cutguardian"},
    version = OcFarmingEWPlugin.version,
    minClientVersion = "2.6.22",
    enabledByDefault = PluginConstants.DEFAULT_ENABLED,
    isExternal = PluginConstants.IS_EXTERNAL
)
public class OcFarmingEWPlugin extends Plugin
{
    static final String version = "0.9.5-ew";

    @Inject private Client client;
    @Inject private ConfigManager configManager;
    @Inject private OverlayManager overlayManager;
    @Inject private OcFarmingEWOverlay overlay;
    @Inject private OcFarmingEWConfig config;

    private final FarmingTracker tracker = new FarmingTracker();
    private final FarmingProtectionTracker protection = new FarmingProtectionTracker();
    private final FarmingSamplingGate gate = new FarmingSamplingGate();
    private boolean active;
    private boolean sampledLastTick;
    private int ticksSinceSave;
    private long session;
    private boolean navigationReady;
    private FarmingVisitRunner visits;
    private volatile String actionReceipt = "";
    private volatile View view = new View("Waiting for login", -1, Collections.emptyMap(), 0, false, Collections.emptyMap());

    static final class View
    {
        final String status;
        final int liveRegion;
        final Map<FarmingPatchData, FarmingObservation> patches;
        final long session;
        final boolean navigationReady;
        final Map<FarmingPatchData, FarmingObservation.Crop> protectedCrops;

        View(String status, int liveRegion, Map<FarmingPatchData, FarmingObservation> patches,
            long session, boolean navigationReady, Map<FarmingPatchData, FarmingObservation.Crop> protectedCrops)
        {
            this.status = status;
            this.liveRegion = liveRegion;
            this.patches = Collections.unmodifiableMap(patches);
            this.session = session;
            this.navigationReady = navigationReady;
            this.protectedCrops = Collections.unmodifiableMap(protectedCrops);
        }
    }

    @Provides
    OcFarmingEWConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(OcFarmingEWConfig.class);
    }

    @Override
    protected void startUp()
    {
        active = true;
        session++;
        navigationReady = false;
        gate.reset();
        sampledLastTick = false;
        ticksSinceSave = 0;
        tracker.switchProfile(null);
        protection.clear();
        publish("Waiting for login", -1);
        overlayManager.add(overlay);
        visits = new FarmingVisitRunner(this, config);
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
        protection.clear();
        gate.reset();
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
        protection.clear();
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
            pause("Waiting for patch updates");
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (!active || client.getLocalPlayer() == null
            || (event.getMenuAction() != MenuAction.NPC_THIRD_OPTION && event.getMenuAction() != MenuAction.NPC_FOURTH_OPTION)) return;
        WorldPoint location = client.getLocalPlayer().getWorldLocation();
        for (FarmingPatchData patch : FarmingPatchData.values())
            if (patch.includes(location.getX(), location.getY(), location.getPlane())
                && patch.paymentAction().equalsIgnoreCase(event.getMenuOption()))
                protection.select(patch, Instant.now().getEpochSecond());
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (!active || (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)) return;
        String message = event.getMessage().replaceAll("<[^>]*>", "").toLowerCase(java.util.Locale.ROOT);
        if (message.contains("you pay the gardener") || FarmingPatchTarget.cannotReach(message)) actionReceipt = message;
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
            protection.clear();
            gate.reset();
            sampledLastTick = false;
            if (profile != null)
            {
                for (FarmingPatchData patch : FarmingPatchData.values())
                {
                    tracker.restore(patch, configManager.getConfiguration(OcFarmingEWConfig.GROUP, profile, patch.key()), now);
                    protection.restore(patch, configManager.getConfiguration(OcFarmingEWConfig.GROUP, profile,
                        patch.key() + ".protected"), tracker.snapshot().get(patch));
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
        // Like Time Tracking's PaymentTracker, inspect the gardener's actual
        // confirmation and NPC chathead even while a dialogue suspends varbit reads.
        Widget text = client.getWidget(InterfaceID.ChatLeft.TEXT);
        Widget head = client.getWidget(InterfaceID.ChatLeft.HEAD);
        if (text != null && !text.isHidden() && head != null && head.getModelType() == WidgetModelType.NPC_CHATHEAD
            && protection.confirm(FarmingPatchData.regionAt(location), head.getModelId(), text.getText(), tracker.snapshot(), now)) persist();
        boolean inBounds = false;
        for (FarmingPatchData patch : FarmingPatchData.values())
        {
            inBounds |= patch.includes(location.getX(), location.getY(), location.getPlane());
        }
        navigationReady = !client.isInInstancedRegion() && !hasModal();
        boolean eligible = inBounds && navigationReady;
        if (!gate.accept(FarmingPatchData.regionAt(location), eligible))
        {
            if (sampledLastTick) persist();
            sampledLastTick = false;
            publish(!inBounds ? "History - visit a supported patch" : "Waiting for patch updates", -1);
            return;
        }
        boolean changed = false;
        for (FarmingPatchData patch : FarmingPatchData.values())
        {
            if (!patch.includes(location.getX(), location.getY(), location.getPlane())) continue;
            FarmingObservation previous = tracker.snapshot().get(patch);
            changed |= tracker.observe(patch, client.getVarbitValue(patch.varbit), now, sampledLastTick);
            String oldProtection = protection.encode(patch);
            protection.observe(patch, previous, tracker.snapshot().get(patch));
            changed |= !oldProtection.equals(protection.encode(patch));
        }
        sampledLastTick = true;
        if (changed || ++ticksSinceSave >= 50) persist();
        publish("Observing farming patches", FarmingPatchData.regionAt(location));
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
        gate.reset();
        sampledLastTick = false;
        publish(status, -1);
    }

    private void persist()
    {
        String profile = tracker.profile();
        if (profile == null) return;
        tracker.snapshot().forEach((patch, observation) -> {
            configManager.setConfiguration(OcFarmingEWConfig.GROUP, profile, patch.key(), observation.encode());
            configManager.setConfiguration(OcFarmingEWConfig.GROUP, profile, patch.key() + ".protected", protection.encode(patch));
        });
        ticksSinceSave = 0;
    }

    private void publish(String status, int liveRegion)
    {
        view = new View(status, liveRegion, tracker.snapshot(), session, navigationReady, protection.snapshot());
    }

    View getView() { return view; }
    String visitStatus() { return visits == null ? "Automatic visits off" : visits.status(); }
    String workStatus() { return visits == null ? "Farming stopped" : visits.workStatus(); }
    String actionReceipt() { return actionReceipt; }
    void clearActionReceipt() { actionReceipt = ""; }
    void selectProtection(FarmingPatchData patch)
    {
        protection.select(patch, Instant.now().getEpochSecond());
    }
    void recordProtection(FarmingPatchData patch, FarmingObservation observation, long expectedSession)
    {
        Microbot.getClientThread().invoke((Runnable) () -> {
            if (!active || session != expectedSession) return;
            protection.record(patch, observation);
            persist();
            publish(view.status, view.liveRegion);
        });
    }
}
