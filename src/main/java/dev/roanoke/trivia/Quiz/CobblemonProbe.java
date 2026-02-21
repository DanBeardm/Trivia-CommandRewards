package dev.roanoke.trivia.Quiz;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.roanoke.trivia.Trivia;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.resource.Resource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class CobblemonProbe {

    public static void run(MinecraftServer server) {
        if (!FabricLoader.getInstance().isModLoaded("cobblemon")) {
            Trivia.LOGGER.info("[Trivia] Cobblemon not installed; skipping probe.");
            return;
        }

        // A) Read a couple species files via ResourceManager (data/)
        try {
            Map<Identifier, Resource> speciesFiles =
                    server.getResourceManager().findResources(
                            "species",
                            id -> id.getNamespace().equals("cobblemon") && id.getPath().endsWith(".json")
                    );

            Trivia.LOGGER.info("[Trivia] Found {} Cobblemon species JSON files.", speciesFiles.size());
            Trivia.LOGGER.info("[Trivia] First species id example: {}",
                    speciesFiles.keySet().stream().findFirst().orElse(null));

            // log just the first one to prove parsing works
            var first = speciesFiles.entrySet().stream().findFirst().orElse(null);
            if (first != null) {
                try (var r = new InputStreamReader(first.getValue().getInputStream(), StandardCharsets.UTF_8)) {
                    JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
                    Trivia.LOGGER.info("[Trivia] Example species file: {} has keys: {}", first.getKey(), obj.keySet());
                }
            }
        } catch (Exception e) {
            Trivia.LOGGER.error("[Trivia] Failed reading Cobblemon species resources.", e);
        }

        // B) Read en_us.json from Cobblemon mod jar (assets/)
        try {
            ModContainer cobblemon = FabricLoader.getInstance().getModContainer("cobblemon").orElse(null);
            if (cobblemon == null) return;

            Path langPath = cobblemon.findPath("assets/cobblemon/lang/en_us.json").orElse(null);
            if (langPath == null) {
                Trivia.LOGGER.warn("[Trivia] Could not find assets/cobblemon/lang/en_us.json inside Cobblemon jar.");
                return;
            }

            try (var reader = Files.newBufferedReader(langPath, StandardCharsets.UTF_8)) {
                JsonObject lang = JsonParser.parseReader(reader).getAsJsonObject();
                Trivia.LOGGER.info("[Trivia] Cobblemon en_us.json keys: {}", lang.size());

                // log one dex entry key if present
                String testKey = "cobblemon.species.espeon.desc";
                if (lang.has(testKey)) {
                    Trivia.LOGGER.info("[Trivia] Example dex entry: {} = {}", testKey, lang.get(testKey).getAsString());
                } else {
                    Trivia.LOGGER.info("[Trivia] Example key '{}' not found (that’s ok).", testKey);
                }
            }
        } catch (Exception e) {
            Trivia.LOGGER.error("[Trivia] Failed reading Cobblemon lang file.", e);
        }
    }
}