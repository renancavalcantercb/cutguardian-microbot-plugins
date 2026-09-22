package net.runelite.client.plugins.microbot.ocfarmingew;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FarmingEfficientWalkerBridgeTest
{
    @Test
    void dispatchesWalkRequestToClientThread() throws Exception
    {
        FarmingEfficientWalkerBridge bridge = new FarmingEfficientWalkerBridge();
        AtomicBoolean onClientThread = new AtomicBoolean();
        FakeWalker walker = new FakeWalker(onClientThread);
        set(bridge, "walkerInstance", walker);
        set(bridge, "walkToMethod", FakeWalker.class.getMethod("walkTo", WorldPoint.class));
        set(bridge, "initialized", true);

        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.invoke(org.mockito.ArgumentMatchers.<Supplier<Boolean>>any()))
            .thenAnswer(call -> {
                onClientThread.set(true);
                try
                {
                    return ((Supplier<?>) call.getArgument(0)).get();
                }
                finally
                {
                    onClientThread.set(false);
                }
            });

        WorldPoint destination = new WorldPoint(3226, 3458, 0);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class))
        {
            microbot.when(Microbot::getClientThread).thenReturn(clientThread);
            assertTrue(bridge.walkTo(destination));
        }

        assertTrue(walker.calledOnClientThread);
        verify(clientThread).invoke(org.mockito.ArgumentMatchers.<Supplier<Boolean>>any());
    }

    private static void set(Object target, String name, Object value) throws Exception
    {
        Field field = FarmingEfficientWalkerBridge.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    public static final class FakeWalker
    {
        private final AtomicBoolean onClientThread;
        private boolean calledOnClientThread;

        FakeWalker(AtomicBoolean onClientThread)
        {
            this.onClientThread = onClientThread;
        }

        public boolean walkTo(WorldPoint destination)
        {
            calledOnClientThread = onClientThread.get();
            return calledOnClientThread && destination != null;
        }
    }
}
