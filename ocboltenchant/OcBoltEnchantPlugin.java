package net.runelite.client.plugins.microbot.ocboltenchant;

import com.google.inject.Provides;
import java.awt.event.KeyEvent;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.Skill;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.PostClientTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
    name = "[OC] Bolt Enchant",
    description = "Repeatedly open Crossbow Bolt Enchantments and confirm with Space. Hold Shift to pause.",
    tags = {"oc", "magic", "bolts", "enchanting"},
    authors = {"cutguardian"},
    version = OcBoltEnchantPlugin.version,
    minClientVersion = "2.6.22",
    enabledByDefault = PluginConstants.DEFAULT_ENABLED,
    isExternal = PluginConstants.IS_EXTERNAL
)
public class OcBoltEnchantPlugin extends Plugin
{
    static final String version = "1.0.2";
    private static final Logger log = LoggerFactory.getLogger(OcBoltEnchantPlugin.class);
    private static final int PROGRESS_TIMEOUT_TICKS = 20;

    @Inject private Client client;
    @Inject private OcBoltEnchantConfig config;

    private boolean active;
    private BoltType ownedMenuType;
    private boolean spaceHeld;
    private BoltType currentType;
    private int tick;
    private int nextCastTick;
    private int lastProgressTick;
    private int previousQuantity = -1;
    private boolean attempted;

    @Provides
    OcBoltEnchantConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(OcBoltEnchantConfig.class);
    }

    @Override
    protected void startUp()
    {
        active = true;
        ownedMenuType = null;
        currentType = null;
        tick = nextCastTick = lastProgressTick = 0;
        previousQuantity = -1;
        attempted = false;
        status("Open the normal spellbook; hold Shift to pause");
    }

    @Override
    protected void shutDown()
    {
        active = false;
        releaseInput();
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        tick++;
    }

    // Space remains held across menu rebuilds. Waiting to press it after seeing the
    // menu misses the client's first shortcut-processing opportunity.
    @Subscribe
    public void onPostClientTick(PostClientTick event)
    {
        try
        {
            if (!ready()) return;
            BoltType type = config.boltType();
            if (currentType != type)
            {
                currentType = type;
                releaseInput();
                previousQuantity = -1;
                attempted = false;
            }
            int quantity = quantity(type.getItemId());
            if (quantity <= 0)
            {
                stop("No " + type + " left; restock and restart the plugin");
                return;
            }
            if (client.getBoostedSkillLevel(Skill.MAGIC) < type.getLevel())
            {
                stop("Requires Magic " + type.getLevel());
                return;
            }
            for (BoltType other : BoltType.values())
            {
                if (other != type && quantity(other.getItemId()) > 0)
                {
                    stop("Carry only one type of unenchanted gem-tipped bolt");
                    return;
                }
            }
            if (quantity != previousQuantity)
            {
                previousQuantity = quantity;
                lastProgressTick = tick;
            }
            if (attempted && tick - lastProgressTick >= PROGRESS_TIMEOUT_TICKS)
            {
                stop("No enchanting progress; check runes and Space selection, then restart");
                return;
            }
            Widget menu = client.getWidget(InterfaceID.Skillmulti.UNIVERSE);
            boolean menuVisible = visible(menu);
            if (menuVisible && (ownedMenuType != type || !containsProduct(menu, type)))
            {
                releaseInput();
                lastProgressTick = tick;
                status("Close the existing production menu to resume");
                return;
            }
            Widget spell = client.getWidget(InterfaceID.MagicSpellbook.XBOWS_ENCHANT);
            if (!visible(spell))
            {
                releaseInput();
                lastProgressTick = tick;
                status("Open Magic and show Crossbow Bolt Enchantments in the spell filters");
                return;
            }
            int action = openAction(spell);
            if (action == 0)
            {
                stop("Crossbow Bolt Enchantments has no View or Cast action");
                return;
            }
            if (!spaceHeld)
            {
                spaceHeld = true;
                holdSpace();
                // Let the client consume KEY_PRESSED before the first View.
                return;
            }
            if (tick < nextCastTick) return;
            ownedMenuType = type;
            nextCastTick = tick + Math.max(1, config.castInterval());
            if (!attempted) lastProgressTick = tick;
            attempted = true;
            client.menuAction(-1, spell.getId(), MenuAction.CC_OP, action, -1,
                spell.getActions()[action - 1], "Crossbow Bolt Enchantments");
            status("Enchanting " + type + " (Space held)");
        }
        catch (Exception ex)
        {
            log.error("Bolt enchanting stopped", ex);
            stop("Unexpected error; check the client log");
        }
    }

    @Subscribe
    public void onClientTick(ClientTick event)
    {
        try
        {
            if (!ready()) return;
            if (currentType != config.boltType())
            {
                releaseInput();
                return;
            }
            Widget menu = client.getWidget(InterfaceID.Skillmulti.UNIVERSE);
            if (spaceHeld && (quantity(currentType.getItemId()) <= 0
                || (visible(menu) && (ownedMenuType != currentType || !containsProduct(menu, currentType)))))
            {
                releaseInput();
            }
        }
        catch (Exception ex)
        {
            log.error("Bolt confirmation stopped", ex);
            stop("Unexpected error; check the client log");
        }
    }

    private boolean ready()
    {
        if (!active) return false;
        if (client.getGameState() != GameState.LOGGED_IN || paused())
        {
            releaseInput();
            lastProgressTick = tick;
            return false;
        }
        if (client.getVarbitValue(VarbitID.SPELLBOOK) != 0)
        {
            releaseInput();
            lastProgressTick = tick;
            status("Switch to the normal spellbook");
            return false;
        }
        return true;
    }

    private void stop(String message)
    {
        active = false;
        releaseInput();
        status(message);
        log.info("[OC] Bolt Enchant: {}", message);
    }

    private static boolean visible(Widget widget)
    {
        return widget != null && !widget.isHidden();
    }

    private void releaseInput()
    {
        ownedMenuType = null;
        if (spaceHeld)
        {
            spaceHeld = false;
            releaseSpace();
        }
    }

    private static int openAction(Widget spell)
    {
        String[] actions = spell.getActions();
        if (actions == null) return 0;
        for (int i = 0; i < actions.length; i++)
        {
            // This spell opens a selection interface: the live client exposes View.
            // Retain Cast for client revisions that expose the spell directly.
            if ("View".equalsIgnoreCase(actions[i]) || "Cast".equalsIgnoreCase(actions[i])) return i + 1;
        }
        return 0;
    }

    private static boolean containsProduct(Widget widget, BoltType type)
    {
        if (!visible(widget)) return false;
        if (widget.getItemId() == type.getEnchantedId()
            || type.getEnchantedName().equalsIgnoreCase(stripTags(widget.getName()))
            || type.getEnchantedName().equalsIgnoreCase(stripTags(widget.getText()))) return true;
        return containsProduct(widget.getStaticChildren(), type)
            || containsProduct(widget.getDynamicChildren(), type)
            || containsProduct(widget.getNestedChildren(), type);
    }

    private static boolean containsProduct(Widget[] children, BoltType type)
    {
        if (children == null) return false;
        for (Widget child : children)
        {
            if (containsProduct(child, type)) return true;
        }
        return false;
    }

    private static String stripTags(String text)
    {
        return text == null ? "" : text.replaceAll("<[^>]*>", "").trim();
    }

    int quantity(int itemId) { return Rs2Inventory.itemQuantity(itemId); }
    boolean paused() { return Microbot.pauseAllScripts.get() || client.isKeyPressed(KeyCode.KC_SHIFT); }
    void holdSpace() { Rs2Keyboard.keyHold(KeyEvent.VK_SPACE); }
    void releaseSpace() { Rs2Keyboard.keyRelease(KeyEvent.VK_SPACE); }
    void status(String message) { Microbot.status = "[OC] Bolt Enchant: " + message; }
}
