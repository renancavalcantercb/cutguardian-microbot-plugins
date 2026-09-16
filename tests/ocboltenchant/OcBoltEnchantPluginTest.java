package net.runelite.client.plugins.microbot.ocboltenchant;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.awt.Canvas;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.PostClientTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OcBoltEnchantPluginTest
{
    @Test
    void spaceStaysPressedUntilExplicitRelease() throws Exception
    {
        Canvas canvas = new Canvas();
        List<KeyEvent> events = new ArrayList<>();
        canvas.addKeyListener(new KeyAdapter()
        {
            @Override public void keyPressed(KeyEvent e) { events.add(e); }
            @Override public void keyTyped(KeyEvent e) { events.add(e); }
            @Override public void keyReleased(KeyEvent e) { events.add(e); }
        });
        Client client = proxy(Client.class, (m, a) -> m.getName().equals("getCanvas") ? canvas : null);
        Field field = Microbot.class.getDeclaredField("client");
        field.setAccessible(true);
        Object previous = field.get(null);
        try
        {
            field.set(null, client);
            OcBoltEnchantPlugin plugin = new OcBoltEnchantPlugin();
            plugin.holdSpace();
            assertEquals(1, events.size());
            assertEquals(KeyEvent.KEY_PRESSED, events.get(0).getID());
            assertEquals(KeyEvent.VK_SPACE, events.get(0).getKeyCode());
            plugin.releaseSpace();
            assertEquals(2, events.size());
            assertEquals(KeyEvent.KEY_RELEASED, events.get(1).getID());
            assertEquals(KeyEvent.VK_SPACE, events.get(1).getKeyCode());
        }
        finally
        {
            field.set(null, previous);
        }
    }

    @Test
    void holdsSpaceBeforeOpeningSpellAndAcrossMenuRebuilds() throws Exception
    {
        Fixture f = new Fixture();
        f.tick();
        assertEquals(1, f.actions.size());
        assertArrayEquals(new Object[]{-1, InterfaceID.MagicSpellbook.XBOWS_ENCHANT,
            MenuAction.CC_OP, 1, -1, "View", "Crossbow Bolt Enchantments"}, f.actions.get(0));
        f.frame();
        assertEquals(1, f.spaces);
        f.menuOpen = true;
        f.frame();
        f.frame();
        assertEquals(1, f.spaces);
        f.menuOpen = false;
        f.tick();
        assertEquals(2, f.actions.size());
        assertEquals(0, f.releases);
    }

    @Test
    void oneTickKeepsSpaceHeldAndReopensWithoutWaitingForMenuClosure() throws Exception
    {
        Fixture f = new Fixture();
        f.tick();
        f.menuOpen = true;
        f.frame();
        assertEquals(1, f.spaces);
        f.tick();
        assertEquals(2, f.actions.size(), "View must be sent on the very next game tick");
        f.frame();
        assertEquals(1, f.spaces, "Do not confirm the previous menu a second time");
        f.frame();
        assertEquals(1, f.spaces);
        assertEquals(0, f.releases);
        f.endFrame();
        assertEquals(2, f.actions.size(), "Never cast twice within one game tick");
        f.tick();
        assertEquals(3, f.actions.size());
    }

    @Test
    void gameTickOnlyAdvancesClockAndViewWaitsForClientInputProcessing() throws Exception
    {
        Fixture f = new Fixture();
        f.plugin.onGameTick(new GameTick());
        assertTrue(f.actions.isEmpty());
        f.endFrame();
        assertTrue(f.actions.isEmpty(), "First frame presses Space before opening anything");
        f.frame();
        f.endFrame();
        assertEquals(1, f.actions.size());
        f.endFrame();
        assertEquals(1, f.actions.size());
    }

    @Test
    void resolvesTheActualActionSlotAndSupportsCastOnOtherClientRevisions() throws Exception
    {
        for (String action : new String[]{"View", "Cast"})
        {
            Fixture f = new Fixture();
            f.spellActions = new String[]{null, action};
            f.tick();
            assertEquals(1, f.actions.size());
            assertEquals(2, f.actions.get(0)[3]);
            assertEquals(action, f.actions.get(0)[5]);
        }
        Fixture unavailable = new Fixture();
        unavailable.spellActions = new String[]{"Examine"};
        unavailable.tick();
        assertTrue(unavailable.actions.isEmpty());
    }

    @Test
    void existingMenusPreventHoldingAndUnrelatedMenusReleaseSpace() throws Exception
    {
        Fixture existing = new Fixture();
        existing.menuOpen = true;
        existing.tick();
        existing.frame();
        assertTrue(existing.actions.isEmpty());
        assertEquals(0, existing.spaces);

        Fixture unrelated = new Fixture();
        unrelated.tick();
        unrelated.menuOpen = true;
        unrelated.productId = 314;
        unrelated.frame();
        assertEquals(1, unrelated.releases);
        unrelated.endFrame();
        assertEquals(1, unrelated.actions.size());
    }

    @Test
    void pauseLogoutWrongBookConfigChangeAndShutdownReleaseSpaceOnce() throws Exception
    {
        for (int scenario = 0; scenario < 5; scenario++)
        {
            Fixture f = new Fixture();
            f.tick();
            f.menuOpen = true;
            switch (scenario)
            {
                case 0: f.paused = true; break;
                case 1: f.gameState = GameState.LOGIN_SCREEN; break;
                case 2: f.spellbook = 1; break;
                case 3: f.type = BoltType.RUBY; break;
                default: f.plugin.shutDown(); break;
            }
            f.frame();
            assertEquals(1, f.releases, "scenario " + scenario);
            f.paused = false;
            f.gameState = GameState.LOGGED_IN;
            f.spellbook = 0;
            f.frame();
            f.endFrame();
            assertEquals(1, f.spaces, "Do not rehold over an existing menu: " + scenario);
            assertEquals(1, f.releases, "Release only the owned key: " + scenario);
        }
    }

    @Test
    void lowLevelMissingBoltsAndMixedBoltsStopBeforeCasting() throws Exception
    {
        for (int scenario = 0; scenario < 3; scenario++)
        {
            Fixture f = new Fixture();
            switch (scenario)
            {
                case 0: f.level = 1; break;
                case 1: f.items.clear(); break;
                default: f.items.put(BoltType.RUBY.getItemId(), 10); break;
            }
            f.tick();
            assertTrue(f.actions.isEmpty());
            assertNotNull(f.status);
            f.level = 99;
            f.items.clear();
            f.items.put(f.type.getItemId(), 100);
            f.tick();
            assertTrue(f.actions.isEmpty(), "Stopped loop must require restart");
        }
    }

    @Test
    void missingOrHiddenSpellDoesNotCastAndResumesWhenVisible() throws Exception
    {
        Fixture f = new Fixture();
        f.spellHidden = true;
        f.tick();
        assertTrue(f.actions.isEmpty());
        f.spellHidden = false;
        f.tick();
        assertEquals(1, f.actions.size());
    }

    @Test
    void configuredIntervalUsesGameTicksWithoutDependingOnMenuVisibility() throws Exception
    {
        Fixture f = new Fixture();
        f.interval = 3;
        f.tick();
        f.menuOpen = true;
        f.frame();
        f.menuOpen = false;
        f.tick();
        f.tick();
        assertEquals(1, f.actions.size());
        f.tick();
        assertEquals(2, f.actions.size());
        f.tick();
        f.tick();
        f.tick();
        assertEquals(3, f.actions.size(), "Reopen on the configured game tick");
        assertEquals(1, f.spaces);
        assertEquals(0, f.releases);
    }

    @Test
    void noProgressStopsRetriesAndRestartResetsState() throws Exception
    {
        Fixture f = new Fixture();
        for (int i = 0; i < 25; i++) f.tick();
        assertTrue(f.status.contains("No enchanting progress"));
        assertEquals(1, f.releases);
        int attempts = f.actions.size();
        f.tick();
        assertEquals(attempts, f.actions.size());
        f.plugin.shutDown();
        f.plugin.startUp();
        f.tick();
        assertEquals(attempts + 1, f.actions.size());
    }

    @Test
    void changingInventoryKeepsCastingAndDepletionCancelsConfirmation() throws Exception
    {
        Fixture f = new Fixture();
        for (int i = 0; i < 30; i++)
        {
            f.items.put(f.type.getItemId(), 1000 - i * 10);
            f.tick();
            f.menuOpen = true;
            f.frame();
            f.menuOpen = false;
        }
        assertEquals(30, f.actions.size());
        assertEquals(1, f.spaces);
        f.tick();
        f.items.clear();
        f.menuOpen = true;
        f.frame();
        assertEquals(1, f.spaces);
        assertEquals(1, f.releases);
    }

    @Test
    void allBoltVariantsConfirmByProductAndRequireTheirMagicLevel() throws Exception
    {
        for (BoltType type : BoltType.values())
        {
            Fixture f = new Fixture();
            f.type = type;
            f.items.clear();
            f.items.put(type.getItemId(), 1);
            f.productId = type.getEnchantedId();
            f.level = type.getLevel();
            f.tick();
            f.menuOpen = true;
            f.frame();
            assertEquals(1, f.spaces, type.toString());
        }
    }

    private static class Fixture
    {
        BoltType type = BoltType.SAPPHIRE;
        int interval = 1;
        int level = 99;
        int spellbook;
        int spaces;
        int releases;
        int productId = type.getEnchantedId();
        boolean paused;
        boolean menuOpen;
        boolean spellHidden;
        String status;
        String[] spellActions = {"View"};
        GameState gameState = GameState.LOGGED_IN;
        final Map<Integer, Integer> items = new HashMap<>();
        final List<Object[]> actions = new ArrayList<>();
        final Widget product = proxy(Widget.class, (m, a) ->
            m.getName().equals("getItemId") ? productId : null);
        final Widget menu = proxy(Widget.class, (m, a) -> {
            switch (m.getName())
            {
                case "isHidden": return !menuOpen;
                case "getStaticChildren": return new Widget[]{product};
                default: return null;
            }
        });
        final Widget spell = proxy(Widget.class, (m, a) -> {
            switch (m.getName())
            {
                case "isHidden": return spellHidden;
                case "getId": return InterfaceID.MagicSpellbook.XBOWS_ENCHANT;
                case "getActions": return spellActions;
                default: return null;
            }
        });
        final OcBoltEnchantPlugin plugin = new OcBoltEnchantPlugin()
        {
            @Override int quantity(int itemId) { return items.getOrDefault(itemId, 0); }
            @Override boolean paused() { return paused; }
            @Override void holdSpace() { spaces++; }
            @Override void releaseSpace() { releases++; }
            @Override void status(String message) { status = message; }
        };

        Fixture() throws Exception
        {
            Client client = proxy(Client.class, (m, a) -> {
                switch (m.getName())
                {
                    case "getGameState": return gameState;
                    case "getBoostedSkillLevel": return level;
                    case "getVarbitValue": return spellbook;
                    case "getWidget": return (int) a[0] == InterfaceID.Skillmulti.UNIVERSE ? menu : spell;
                    case "menuAction":
                        assertEquals(spaces, releases + 1, "Space must be held when View is sent");
                        actions.add(a.clone()); return null;
                    default: return null;
                }
            });
            set("client", client);
            set("config", new OcBoltEnchantConfig()
            {
                @Override public BoltType boltType() { return type; }
                @Override public int castInterval() { return interval; }
            });
            items.put(type.getItemId(), 100);
            plugin.startUp();
        }

        void set(String name, Object value) throws Exception
        {
            Field field = OcBoltEnchantPlugin.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(plugin, value);
        }

        void tick() { plugin.onGameTick(new GameTick()); frame(); endFrame(); frame(); endFrame(); }
        void frame() { plugin.onClientTick(new ClientTick()); }
        void endFrame() { plugin.onPostClientTick(new PostClientTick()); }
    }

    private interface Answer { Object answer(Method method, Object[] args); }

    private static <T> T proxy(Class<T> type, Answer answer)
    {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
            (self, method, args) -> {
                Object result = answer.answer(method, args);
                if (result != null) return result;
                if (method.getReturnType() == boolean.class) return false;
                if (method.getReturnType() == int.class) return 0;
                return null;
            }));
    }
}
