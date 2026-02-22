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
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class CobblemonNameScrambleQuestions {

    private CobblemonNameScrambleQuestions() {}

    /**
     * @param cap how many scramble questions to generate (e.g. 300)
     */
    public static List<Question> generate(MinecraftServer server, int cap) {
        if (!FabricLoader.getInstance().isModLoaded("cobblemon")) {
            Trivia.LOGGER.info("[Trivia] Cobblemon not loaded; skipping scramble questions.");
            return List.of();
        }

        // Build a pool of (speciesId -> displayName)
        Map<String, String> names = loadNamesFromLang();
        fillMissingNamesFromSpeciesJson(server, names);

        // Dedupe by normalized name so forms/custom duplicates don’t explode
        LinkedHashMap<String, Entry> uniqueByNorm = new LinkedHashMap<>();
        for (var e : names.entrySet()) {
            String speciesId = e.getKey().toLowerCase(Locale.ROOT);
            String displayName = e.getValue();

            String norm = normalizeForScramble(displayName);
            if (norm.length() < 4) continue; // too short to be fun
            if (uniqueByNorm.containsKey(norm)) continue;

            uniqueByNorm.put(norm, new Entry(speciesId, displayName));
        }

        List<Entry> pool = new ArrayList<>(uniqueByNorm.values());
        Collections.shuffle(pool, new Random());

        List<Question> out = new ArrayList<>();
        Random rng = new Random();

        for (Entry entry : pool) {
            if (cap > 0 && out.size() >= cap) break;

            String answerName = entry.displayName;
            String answerId = entry.speciesId;

            String norm = normalizeForScramble(answerName);          // letters+digits only, lowercase
            String scrambled = scrambleDifferent(norm, rng);
            if (scrambled == null) continue;

            // Accept both pretty name + id (your isRightAnswer normalizer will handle spaces/punct anyway)
            LinkedHashSet<String> answers = new LinkedHashSet<>();
            answers.add(answerName.toLowerCase(Locale.ROOT));
            answers.add(answerId.toLowerCase(Locale.ROOT));

            out.add(new Question(
                    "Unscramble this Pokemon name: " + scrambled,
                    new ArrayList<>(answers),
                    difficultyFromLength(norm.length())
            ));
        }

        Trivia.LOGGER.info("[Trivia] Generated {} Cobblemon name-scramble questions.", out.size());
        return out;
    }

    // ---------------- helpers ----------------

    private static String difficultyFromLength(int len) {
        if (len <= 6) return "easy";
        if (len <= 9) return "medium";
        return "hard";
    }

    // Keep only a-z0-9, lowercase (matches your QuizManager normalize approach)
    private static String normalizeForScramble(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    // Shuffle characters; ensure it differs from original (try a few times)
    private static String scrambleDifferent(String s, Random rng) {
        if (s == null) return null;
        if (s.length() < 4) return null;

        char[] arr = s.toCharArray();
        String original = s;

        for (int attempt = 0; attempt < 15; attempt++) {
            // Fisher–Yates shuffle
            for (int i = arr.length - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                char tmp = arr[i];
                arr[i] = arr[j];
                arr[j] = tmp;
            }
            String scrambled = new String(arr);
            if (!scrambled.equals(original)) return scrambled;
        }
        return null;
    }

    private static Map<String, String> loadNamesFromLang() {
        Map<String, String> nameById = new HashMap<>();

        ModContainer cobblemon = FabricLoader.getInstance().getModContainer("cobblemon").orElse(null);
        if (cobblemon == null) return nameById;

        Path langPath = cobblemon.findPath("assets/cobblemon/lang/en_us.json").orElse(null);
        if (langPath == null) return nameById;

        try (Reader r = Files.newBufferedReader(langPath, StandardCharsets.UTF_8)) {
            JsonObject lang = JsonParser.parseReader(r).getAsJsonObject();

            for (String key : lang.keySet()) {
                if (!key.startsWith("cobblemon.species.") || !key.endsWith(".name")) continue;

                String speciesId = key.substring("cobblemon.species.".length(), key.length() - ".name".length())
                        .toLowerCase(Locale.ROOT);

                nameById.put(speciesId, lang.get(key).getAsString());
            }
        } catch (Exception e) {
            Trivia.LOGGER.warn("[Trivia] Failed reading Cobblemon en_us.json for names.", e);
        }

        return nameById;
    }

    // For custom mons that don’t exist in en_us.json, fallback to species json "name"
    private static void fillMissingNamesFromSpeciesJson(MinecraftServer server, Map<String, String> names) {
        try {
            Map<Identifier, Resource> speciesFiles = server.getResourceManager().findResources(
                    "species",
                    id -> id.getNamespace().equals("cobblemon") && id.getPath().endsWith(".json")
            );

            for (var entry : speciesFiles.entrySet()) {
                Identifier id = entry.getKey();
                String speciesId = filenameNoExt(id.getPath()).toLowerCase(Locale.ROOT);

                if (names.containsKey(speciesId)) continue;

                try (var r = new InputStreamReader(entry.getValue().getInputStream(), StandardCharsets.UTF_8)) {
                    JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
                    if (obj.has("name") && obj.get("name").isJsonPrimitive()) {
                        names.put(speciesId, obj.get("name").getAsString());
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            Trivia.LOGGER.warn("[Trivia] Failed reading species JSON for scramble fallback names.", e);
        }
    }

    private static String filenameNoExt(String path) {
        int slash = path.lastIndexOf('/');
        String file = (slash >= 0) ? path.substring(slash + 1) : path;
        return file.endsWith(".json") ? file.substring(0, file.length() - 5) : file;
    }

    private record Entry(String speciesId, String displayName) {}
}