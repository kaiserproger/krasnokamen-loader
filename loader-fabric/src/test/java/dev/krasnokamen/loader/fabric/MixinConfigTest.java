package dev.krasnokamen.loader.fabric;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Fabric applies these configs at client startup, and one entry it cannot resolve aborts the
 * launch before the title screen. The shipped configs must therefore name only classes the jar
 * contains, and stream dispatch must stay with Fabric's own sound API hook: a competing redirect
 * of {@code SoundBufferLibrary.getStream} broke Fabric API 0.155.2's required injection
 * (LOADER-SOUND-MIXIN).
 */
class MixinConfigTest {
    private static final List<String> CONFIGS =
            List.of("krasnokamen-loader.mixins.json", "krasnokamen-loader-input.mixins.json");

    @Test
    void everyListedMixinExistsAndNoneRedirectsTheSoundStream() throws IOException {
        for (String config : CONFIGS) {
            JsonObject json = JsonParser.parseString(read(config)).getAsJsonObject();
            String owner = json.get("package").getAsString();
            JsonArray client = json.getAsJsonArray("client");
            assertNotNull(client, config + " has no client mixin list");
            assertFalse(client.isEmpty(), config + " lists no client mixin");
            for (var entry : client) {
                String name = entry.getAsString();
                Class<?> mixin = mixin(config, owner + "." + name);
                String bytecode = new String(bytes(mixin), StandardCharsets.ISO_8859_1);
                assertFalse(
                        bytecode.contains("getStream"),
                        config + " lists " + name
                                + ", which redirects the sound stream that Fabric's sound API owns");
            }
        }
    }

    private static Class<?> mixin(String config, String binary) {
        try {
            return Class.forName(binary, false, MixinConfigTest.class.getClassLoader());
        } catch (ClassNotFoundException error) {
            throw new AssertionError(
                    config + " lists " + binary + ", which this jar does not contain", error);
        }
    }

    private static byte[] bytes(Class<?> type) throws IOException {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream stream = type.getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            return stream.readAllBytes();
        }
    }

    private static String read(String resource) throws IOException {
        try (InputStream stream = MixinConfigTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
