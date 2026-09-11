package net.runelite.client.plugins.microbot.ocdarts;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OcDartsPluginTest
{
    @Test
    void menuCreationDoesNotActAndEachClickCombinesExactlyOnce() throws Exception
    {
        Fixture f = new Fixture();
        f.buildMenu();
        assertNotNull(f.callback);
        assertTrue(f.actions.isEmpty());
        f.click();
        assertEquals(2, f.actions.size());
        assertArrayEquals(new Object[]{3, ComponentID.INVENTORY_CONTAINER, MenuAction.WIDGET_TARGET,
            0, 314, "Use", "Feather"}, f.actions.get(0));
        assertArrayEquals(new Object[]{19, ComponentID.INVENTORY_CONTAINER, MenuAction.WIDGET_TARGET_ON_WIDGET,
            0, 819, "Use", "Feather -> Bronze (level 10)"}, f.actions.get(1));
        f.click();
        assertEquals(4, f.actions.size());
    }

    @Test
    void clickResolvesNewSlotsInsteadOfUsingMenuTimeSlots() throws Exception
    {
        Fixture f = new Fixture();
        f.buildMenu();
        f.put(314, 27);
        f.put(819, 0);
        f.click();
        assertEquals(27, f.actions.get(0)[0]);
        assertEquals(0, f.actions.get(1)[0]);
    }

    @Test
    void depletedMaterialsAndStaleWidgetsDoNotDispatch() throws Exception
    {
        for (int missing : new int[]{314, 819})
        {
            Fixture f = new Fixture();
            f.buildMenu();
            f.items.remove(missing);
            f.click();
            assertTrue(f.actions.isEmpty());
        }
        Fixture f = new Fixture();
        f.buildMenu();
        f.widgets.remove(19);
        f.click();
        assertTrue(f.actions.isEmpty());
    }

    @Test
    void allTypesCheckLevelAndOnlyUseConfiguredTips() throws Exception
    {
        for (DartType type : DartType.values())
        {
            Fixture f = new Fixture();
            f.type = type;
            f.put(type.getTipId(), 19);
            f.level = type.getLevel() - 1;
            f.buildMenu();
            f.click();
            assertTrue(f.actions.isEmpty(), type.toString());
            f.level++;
            f.click();
            assertEquals(2, f.actions.size());
            assertEquals(type.getTipId(), f.actions.get(1)[4]);
        }
    }

    @Test
    void configurationChangesInvalidateOldMenuCallback() throws Exception
    {
        Fixture f = new Fixture();
        f.buildMenu();
        f.type = DartType.DRAGON;
        f.put(11232, 20);
        f.click();
        assertTrue(f.actions.isEmpty());
    }

    @Test
    void shiftLogoutHiddenInventoryAndShutdownInvalidateClicks() throws Exception
    {
        for (int scenario = 0; scenario < 4; scenario++)
        {
            Fixture f = new Fixture();
            f.buildMenu();
            switch (scenario)
            {
                case 0: f.shift = true; break;
                case 1: f.gameState = GameState.LOGIN_SCREEN; break;
                case 2: f.hidden = true; break;
                default: f.plugin.shutDown(); break;
            }
            f.click();
            assertTrue(f.actions.isEmpty());
        }
    }

    @Test
    void interfacesOutsideViewportShiftAndOpenMenusKeepNormalInput() throws Exception
    {
        for (int scenario = 0; scenario < 4; scenario++)
        {
            Fixture f = new Fixture();
            switch (scenario)
            {
                case 0:
                    f.entries = new MenuEntry[]{proxy(MenuEntry.class, (m, a) ->
                        m.getName().equals("getWidget") ? f.inventory : null)};
                    break;
                case 1: f.mouse = new Point(700, 50); break;
                case 2: f.shift = true; break;
                default: f.menuOpen = true; break;
            }
            f.buildMenu();
            assertNull(f.callback);
            assertTrue(f.actions.isEmpty());
        }
    }

    private static class Fixture
    {
        DartType type = DartType.BRONZE;
        int level = 99;
        boolean shift;
        boolean hidden;
        boolean menuOpen;
        GameState gameState = GameState.LOGGED_IN;
        Point mouse = new Point(100, 100);
        MenuEntry[] entries = new MenuEntry[0];
        Consumer<MenuEntry> callback;
        final Map<Integer, Rs2ItemModel> items = new HashMap<>();
        final Map<Integer, Widget> widgets = new HashMap<>();
        final List<Object[]> actions = new ArrayList<>();
        final Widget inventory = proxy(Widget.class, (method, args) -> {
            switch (method.getName())
            {
                case "getId": return ComponentID.INVENTORY_CONTAINER;
                case "isHidden": return hidden;
                case "getChild": return widgets.get((int) args[0]);
                default: return null;
            }
        });
        final OcDartsPlugin plugin = new OcDartsPlugin()
        {
            @Override
            Rs2ItemModel inventoryItem(int id)
            {
                return items.get(id);
            }
        };

        @SuppressWarnings("unchecked")
        Fixture() throws Exception
        {
            MenuEntry entry = proxy(MenuEntry.class, (method, args) -> {
                if (method.getName().equals("onClick") && args != null)
                {
                    callback = (Consumer<MenuEntry>) args[0];
                }
                return null;
            });
            Menu menu = proxy(Menu.class, (method, args) ->
                method.getName().equals("createMenuEntry") ? entry : entries);
            Client client = proxy(Client.class, (method, args) -> {
                switch (method.getName())
                {
                    case "getGameState": return gameState;
                    case "isKeyPressed": return shift;
                    case "isMenuOpen": return menuOpen;
                    case "getMenu": return menu;
                    case "getMouseCanvasPosition": return mouse;
                    case "getViewportWidth": return 512;
                    case "getViewportHeight": return 334;
                    case "getBoostedSkillLevel": return level;
                    case "getWidget": return inventory;
                    case "menuAction": actions.add(args.clone()); return null;
                    default: return null;
                }
            });
            set("client", client);
            set("config", new OcDartsConfig()
            {
                @Override
                public DartType dartType()
                {
                    return type;
                }
            });
            put(314, 3);
            put(819, 19);
            plugin.startUp();
        }

        void put(int id, int slot)
        {
            items.put(id, Rs2ItemModel.createFromCache(id, 100, slot));
            widgets.put(slot, proxy(Widget.class, (method, args) -> {
                switch (method.getName())
                {
                    case "getIndex": return slot;
                    case "getItemId": return id;
                    case "getItemQuantity": return 100;
                    default: return null;
                }
            }));
        }

        void set(String name, Object value) throws Exception
        {
            Field field = OcDartsPlugin.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(plugin, value);
        }

        void buildMenu()
        {
            plugin.onPostMenuSort(new PostMenuSort());
        }

        void click()
        {
            callback.accept(null);
        }
    }

    private interface Answer
    {
        Object answer(Method method, Object[] args);
    }

    private static <T> T proxy(Class<T> type, Answer answer)
    {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
            (self, method, args) -> {
                Object result = answer.answer(method, args);
                if (result != null) return result;
                if (method.getReturnType() == type) return self;
                if (method.getReturnType() == boolean.class) return false;
                if (method.getReturnType() == int.class) return 0;
                return null;
            }));
    }
}
