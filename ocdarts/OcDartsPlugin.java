package net.runelite.client.plugins.microbot.ocdarts;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

@PluginDescriptor(
    name = "[OC] Darts",
    description = "Combine feathers and selected dart tips once per click in the game view. Hold Shift for normal clicks.",
    tags = {"one click", "oc", "fletching", "darts"},
    authors = {"cutguardian"},
    version = OcDartsPlugin.version,
    minClientVersion = "2.6.22",
    enabledByDefault = PluginConstants.DEFAULT_ENABLED,
    isExternal = PluginConstants.IS_EXTERNAL
)
public class OcDartsPlugin extends Plugin
{
    static final String version = "1.0.1";
    static final int FEATHER_ID = 314;

    @Inject
    private Client client;

    @Inject
    private OcDartsConfig config;

    private boolean active;
    private boolean dispatching;

    @Provides
    OcDartsConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(OcDartsConfig.class);
    }

    @Override
    protected void startUp()
    {
        active = true;
    }

    @Override
    protected void shutDown()
    {
        active = false;
    }

    @Subscribe
    public void onPostMenuSort(PostMenuSort event)
    {
        if (!canHandleClick() || client.isMenuOpen() || !isGameView())
        {
            return;
        }

        // Leave interface controls (including inventory, chat and minimap) accessible.
        for (MenuEntry entry : client.getMenu().getMenuEntries())
        {
            if (entry.getWidget() != null || "[OC] Darts".equals(entry.getOption()))
            {
                return;
            }
        }

        DartType type = config.dartType();
        String unavailable = unavailableReason(type);
        client.getMenu().createMenuEntry(-1)
            .setOption("[OC] Darts")
            .setTarget(unavailable == null ? type.toString() : unavailable)
            .setType(MenuAction.RUNELITE)
            .setForceLeftClick(true)
            .onClick(entry -> combine(type));
    }

    private boolean canHandleClick()
    {
        return active && !dispatching && client.getGameState() == GameState.LOGGED_IN
            && !client.isKeyPressed(KeyCode.KC_SHIFT);
    }

    private boolean isGameView()
    {
        Point mouse = client.getMouseCanvasPosition();
        int x = mouse.getX() - client.getViewportXOffset();
        int y = mouse.getY() - client.getViewportYOffset();
        return x >= 0 && y >= 0 && x < client.getViewportWidth() && y < client.getViewportHeight();
    }

    private String unavailableReason(DartType type)
    {
        if (client.getBoostedSkillLevel(Skill.FLETCHING) < type.getLevel())
        {
            return "Requires Fletching " + type.getLevel();
        }
        Widget inventory = client.getWidget(ComponentID.INVENTORY_CONTAINER);
        if (inventory == null || inventory.isHidden())
        {
            return "Open inventory and close other interfaces";
        }
        if (itemWidget(inventory, FEATHER_ID) == null)
        {
            return "Missing feathers";
        }
        if (itemWidget(inventory, type.getTipId()) == null)
        {
            return "Missing selected dart tips";
        }
        return null;
    }

    // RuneLite menu callbacks execute on the client thread. Re-resolve slots at click time:
    // the inventory may have changed since the menu was built.
    void combine(DartType type)
    {
        if (!canHandleClick() || config.dartType() != type || unavailableReason(type) != null)
        {
            return;
        }
        Widget inventory = client.getWidget(ComponentID.INVENTORY_CONTAINER);
        Widget feather = itemWidget(inventory, FEATHER_ID);
        Widget tips = itemWidget(inventory, type.getTipId());
        if (feather == null || tips == null)
        {
            return;
        }

        dispatching = true;
        try
        {
            // Deliberately dispatch synchronously: Rs2Inventory.combine sleeps and simulates
            // mouse input. An OC callback must not block or generate another physical click.
            client.menuAction(feather.getIndex(), inventory.getId(), MenuAction.WIDGET_TARGET,
                0, FEATHER_ID, "Use", "Feather");
            client.menuAction(tips.getIndex(), inventory.getId(), MenuAction.WIDGET_TARGET_ON_WIDGET,
                0, type.getTipId(), "Use", "Feather -> " + type);
        }
        finally
        {
            dispatching = false;
        }
    }

    private Widget itemWidget(Widget inventory, int id)
    {
        Rs2ItemModel item = inventoryItem(id);
        if (item == null || item.getQuantity() <= 0 || item.getSlot() < 0)
        {
            return null;
        }
        Widget child = inventory.getChild(item.getSlot());
        return child != null && child.getItemId() == id && child.getItemQuantity() > 0 ? child : null;
    }

    Rs2ItemModel inventoryItem(int id)
    {
        return Rs2Inventory.get(id);
    }
}
