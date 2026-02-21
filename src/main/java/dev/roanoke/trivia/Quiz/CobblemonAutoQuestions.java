package dev.roanoke.trivia.Quiz;

import com.google.gson.*;
import dev.roanoke.trivia.Trivia;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.resource.Resource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class CobblemonAutoQuestions {

    private CobblemonAutoQuestions() {}

    public static List<Question> generate(MinecraftServer server, int capPerType) {
        if (!FabricLoader.getInstance().isModLoaded("cobblemon")) {
            Trivia.LOGGER.info("[Trivia] Cobblemon not loaded; skipping auto-questions.");
            return List.of();
        }

        // Load name + dex entry text from Cobblemon lang
        LangData lang = loadCobblemonLang();

        // Load species JSON from server ResourceManager (data packs + mod data)
        Map<Identifier, Resource> speciesFiles = server.getResourceManager().findResources(
                "species",
                id -> id.getNamespace().equals("cobblemon") && id.getPath().endsWith(".json")
        );

        List<Question> easy = new ArrayList<>();
        List<Question> medium = new ArrayList<>();
        List<Question> hard = new ArrayList<>();

        for (var entry : speciesFiles.entrySet()) {
            Identifier id = entry.getKey();

            // e.g. cobblemon:species/custom/acideon.json -> speciesId = "acideon"
            String speciesId = filenameNoExt(id.getPath()).toLowerCase(Locale.ROOT);

            JsonObject obj;
            try (var r = new InputStreamReader(entry.getValue().getInputStream(), StandardCharsets.UTF_8)) {
                obj = JsonParser.parseReader(r).getAsJsonObject();
            } catch (Exception e) {
                continue;
            }

            if (obj.has("implemented") && !obj.get("implemented").getAsBoolean()) continue;

            // Prefer lang name; fallback to species json "name"; fallback to id
            String displayName = lang.nameById.getOrDefault(speciesId,
                    obj.has("name") ? obj.get("name").getAsString() : speciesId);

            // --- EASY: primary type (use base name for forms)
            if (obj.has("primaryType")) {
                String primary = obj.get("primaryType").getAsString().toLowerCase(Locale.ROOT);

                // collapse forms: tornadus-therian -> tornadus
                String baseId = speciesId.split("-", 2)[0].toLowerCase(Locale.ROOT);

                // prefer base name from lang, then json name, then baseId
                String baseName = lang.nameById.getOrDefault(baseId,
                        obj.has("name") ? obj.get("name").getAsString() : baseId);

                easy.add(new Question(
                        "What is the primary type of " + baseName + "?",
                        List.of(primary),
                        "easy"
                ));
            }

            // --- EASY: secondary type (use base name for forms)
            if (obj.has("secondaryType")) {
                String secondary = obj.get("secondaryType").getAsString().toLowerCase(Locale.ROOT);

                String baseId = speciesId.split("-", 2)[0].toLowerCase(Locale.ROOT);
                String baseName = lang.nameById.getOrDefault(baseId,
                        obj.has("name") ? obj.get("name").getAsString() : baseId);

                easy.add(new Question(
                        "What is the secondary type of " + baseName + "?",
                        List.of(secondary),
                        "easy"
                ));
            }

            // --- MEDIUM: national dex number
            if (obj.has("nationalPokedexNumber")) {
                String dexNum = String.valueOf(obj.get("nationalPokedexNumber").getAsInt());
                medium.add(new Question(
                        "What is the National Pokedex number of " + displayName + "?",
                        List.of(dexNum),
                        "medium"
                ));
            }

            // --- HARD: ability (accept any valid ability)
            if (obj.has("abilities") && obj.get("abilities").isJsonArray()) {
                List<String> abilities = new ArrayList<>();
                for (JsonElement a : obj.getAsJsonArray("abilities")) {
                    if (a.isJsonPrimitive()) {
                        abilities.add(a.getAsString().toLowerCase(Locale.ROOT));
                    }
                }
                if (!abilities.isEmpty()) {
                    hard.add(new Question(
                            "Name an ability that " + displayName + " can have.",
                            abilities,
                            "hard"
                    ));
                }
            }

            // --- HARD: dex entry (prefer lang desc; fallback to species json "pokedex" if you want)
            String dexEntry = lang.descById.get(speciesId);
            if (dexEntry == null || dexEntry.isBlank()) {
                dexEntry = extractPokedexFromSpeciesJson(obj); // optional fallback
            }
            if (dexEntry != null && !dexEntry.isBlank()) {
                hard.add(new Question(
                        "Whos Dex Entry is this: " + dexEntry,
                        List.of(displayName.toLowerCase(Locale.ROOT)),
                        "hard"
                ));
            }
        }

        // Shuffle and cap so you don’t accidentally add 10k questions
        Collections.shuffle(easy);
        Collections.shuffle(medium);
        Collections.shuffle(hard);

        if (capPerType > 0) {
            if (easy.size() > capPerType) easy = easy.subList(0, capPerType);
            if (medium.size() > capPerType) medium = medium.subList(0, capPerType);
            if (hard.size() > capPerType) hard = hard.subList(0, capPerType);
        }

        List<Question> out = new ArrayList<>(easy.size() + medium.size() + hard.size());
        out.addAll(easy);
        out.addAll(medium);
        out.addAll(hard);

        Trivia.LOGGER.info("[Trivia] Generated Cobblemon questions: easy={} medium={} hard={} total={}",
                easy.size(), medium.size(), hard.size(), out.size());

        return out;
    }

    private static String filenameNoExt(String path) {
        int slash = path.lastIndexOf('/');
        String file = (slash >= 0) ? path.substring(slash + 1) : path;
        return file.endsWith(".json") ? file.substring(0, file.length() - 5) : file;
    }

    private static LangData loadCobblemonLang() {
        LangData data = new LangData();

        ModContainer cobblemon = FabricLoader.getInstance().getModContainer("cobblemon").orElse(null);
        if (cobblemon == null) return data;

        Path langPath = cobblemon.findPath("assets/cobblemon/lang/en_us.json").orElse(null);
        if (langPath == null) return data;

        try (Reader r = Files.newBufferedReader(langPath, StandardCharsets.UTF_8)) {
            JsonObject lang = JsonParser.parseReader(r).getAsJsonObject();

            for (String key : lang.keySet()) {
                if (!key.startsWith("cobblemon.species.")) continue;

                if (key.endsWith(".name")) {
                    String speciesId = key.substring("cobblemon.species.".length(), key.length() - ".name".length())
                            .toLowerCase(Locale.ROOT);
                    data.nameById.put(speciesId, lang.get(key).getAsString());
                } else if (key.endsWith(".desc")) {
                    String speciesId = key.substring("cobblemon.species.".length(), key.length() - ".desc".length())
                            .toLowerCase(Locale.ROOT);
                    data.descById.put(speciesId, lang.get(key).getAsString());
                }
            }

        } catch (Exception e) {
            Trivia.LOGGER.warn("[Trivia] Failed reading Cobblemon en_us.json", e);
        }

        return data;
    }

    // Optional fallback: if custom species don’t have lang entries but do have a pokedex field
    private static String extractPokedexFromSpeciesJson(JsonObject obj) {
        if (!obj.has("pokedex")) return null;
        JsonElement p = obj.get("pokedex");

        // Sometimes it might just be a string
        if (p.isJsonPrimitive() && p.getAsJsonPrimitive().isString()) {
            return p.getAsString();
        }

        if (!p.isJsonObject()) return null;
        JsonObject po = p.getAsJsonObject();

        // common guesses
        if (po.has("entry") && po.get("entry").isJsonPrimitive()) return po.get("entry").getAsString();
        if (po.has("text") && po.get("text").isJsonPrimitive()) return po.get("text").getAsString();

        // try entries array: [{ "text": "..." }, ...]
        if (po.has("entries") && po.get("entries").isJsonArray()) {
            JsonArray arr = po.getAsJsonArray("entries");
            if (!arr.isEmpty() && arr.get(0).isJsonObject()) {
                JsonObject first = arr.get(0).getAsJsonObject();
                if (first.has("text") && first.get("text").isJsonPrimitive()) return first.get("text").getAsString();
            }
        }

        return null;
    }

    private static final class LangData {
        final Map<String, String> nameById = new HashMap<>();
        final Map<String, String> descById = new HashMap<>();
    }
}