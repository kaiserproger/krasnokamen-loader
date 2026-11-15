package dev.krasnokamen.agent.javaagent;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;

final class VanillaFrameCaptureTest {
    @TempDir Path directory;

    @Test
    void screenshotKeepsExtractedGaitWhenRenderStateIsClearedBeforeIoCompletion() throws Exception {
        Path path = directory.resolve("screenshots/frame.png");
        JsonArray captured = JsonParser.parseString(
            "[{\"entity_type\":\"minecraft:spider\",\"walk_position\":1.5,\"walk_speed\":0.4}]"
        ).getAsJsonArray();
        AtomicReference<JsonArray> liveState = new AtomicReference<>(captured);
        String previous = System.getProperty("krasnokamen.client.vanillaEvents");
        System.setProperty("krasnokamen.client.vanillaEvents", "true");
        try (var minecraft = mockStatic(Minecraft.class);
             var gui = mockStatic(MinecraftGuiAccess.class);
             var facade = mockStatic(MinecraftClientFacade.class, CALLS_REAL_METHODS);
             var capture = mockStatic(VanillaFrameCapture.class);
             var screenshot = mockStatic(Screenshot.class);
             var executors = mockConstruction(MinecraftClientExecutor.class, (executor, context) ->
                 doAnswer(call -> ((Callable<?>) call.getArgument(0)).call())
                     .when(executor).callOnClientThread(any()))) {
            facade.when(() -> MinecraftClientFacade.renderedGait(null)).thenAnswer(call -> liveState.get());
            capture.when(() -> VanillaFrameCapture.afterExtract(null)).thenCallRealMethod();
            capture.when(() -> VanillaFrameCapture.afterRender(null)).thenCallRealMethod();
            capture.when(() -> VanillaFrameCapture.request(directory, path)).thenAnswer(call -> {
                Object pending = call.callRealMethod();
                // Drive the real capture hooks as the renderer does, without a GPU or sleeps.
                VanillaFrameCapture.afterExtract(null);
                VanillaFrameCapture.afterRender(null);
                return pending;
            });
            screenshot.when(() -> Screenshot.grab(any(), anyString(), any(), anyInt(), any()))
                .thenAnswer(call -> {
                    Files.write(path, new byte[] {1});
                    // Vanilla clears/reuses render state before the asynchronous PNG writer completes.
                    liveState.set(new JsonArray());
                    Consumer<Component> complete = call.getArgument(4);
                    complete.accept(null);
                    return null;
                });
            assertEquals(path, new MinecraftClientFacade().screenshot(path));
            assertEquals(captured, JsonParser.parseString(Files.readString(
                path.resolveSibling("frame.png.gait.json"))));
        } finally {
            if (previous == null) System.clearProperty("krasnokamen.client.vanillaEvents");
            else System.setProperty("krasnokamen.client.vanillaEvents", previous);
        }
    }
}
