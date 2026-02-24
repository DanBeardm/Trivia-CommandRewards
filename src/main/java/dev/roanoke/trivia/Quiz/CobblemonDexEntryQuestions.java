package dev.roanoke.trivia.Quiz;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.roanoke.trivia.Trivia;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.server.MinecraftServer;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CobblemonDexEntryQuestions {

    private CobblemonDexEntryQuestions() {}

    public static List<Question> generate(MinecraftServer server, int cap) {
        if (!FabricLoader.getInstance().isModLoaded("cobblemon")) {
            Trivia.LOGGER.info("[Trivia] Cobblemon not loaded; skipping dex-entry questions.");
            return List.of();
        }

        LangData lang = loadLang();
        List<Question> out = new ArrayList<>();

        for (String speciesId : lang.descById.keySet()) {
            String desc = lang.descById.get(speciesId);
            if (desc == null || desc.isBlank()) continue;
            if (isEcologyUnderResearch(desc)) continue;
            // Base id = cut off everything after first '-'
            String fullId = speciesId.toLowerCase(Locale.ROOT);
            String baseId = speciesId.split("-", 2)[0].toLowerCase(Locale.ROOT);

            String baseName = lang.nameById.getOrDefault(baseId, baseId);

            String maskedDesc = maskNameVariants(desc, baseName, baseId, fullId);

            // Answers: baseName and (optionally) baseId if different
            String nameNorm = normKey(baseName);
            String idNorm = normKey(baseId);

            List<String> answers = new ArrayList<>();

// ALWAYS include baseId so players can type flabebe / tornadus
            answers.add(baseId.toLowerCase(Locale.ROOT));

// Only include baseName if it’s genuinely different after normalising
            if (!nameNorm.equals(idNorm)) {
                answers.add(baseName.toLowerCase(Locale.ROOT));
            }

            out.add(new Question(
                    "Whos Dex Entry is this: " + maskedDesc,
                    answers,
                    "hard"
            ));

        }

        Collections.shuffle(out);
        if (cap > 0 && out.size() > cap) {
            out = out.subList(0, cap);
        }

        Trivia.LOGGER.info("[Trivia] Generated {} Cobblemon dex-entry questions.", out.size());
        return out;
    }

    private static boolean isEcologyUnderResearch(String desc) {
        if (desc == null) return true;

        // Convert full-width characters to normal ASCII, normalize punctuation, etc.
        String s = Normalizer.normalize(desc, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .trim();

        // collapse whitespace
        s = s.replaceAll("\\s+", " ");

        // remove punctuation so "research." matches too
        s = s.replaceAll("[^a-z0-9 ]", "");

        return s.contains("ecology under research");
    }

    private static String maskNameVariants(String desc, String baseName, String baseId) {
        if (desc == null) return "";

        // we’ll mask using underscores based on the base id length (stable, no accents)
        String underscores = "_".repeat(Math.max(3, baseId.length()));

        // variants to mask (case-insensitive, unicode-aware)
        //  - baseName (may contain accents)
        //  - de-accented baseName (Flabebe)
        //  - baseId (flabebe)
        List<String> variants = new ArrayList<>();
        variants.add(baseName);
        variants.add(stripAccents(baseName));
        variants.add(baseId);

        String out = desc;
        for (String v : variants) {
            if (v == null || v.isBlank()) continue;

            // (?iu) = case-insensitive + unicode-aware so Flabébé matches properly
            out = out.replaceAll("(?iu)\\b" + java.util.regex.Pattern.quote(v) + "\\b", underscores);
        }
        return out;
    }
    private static String stripAccents(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    private static String stripGenderSymbols(String s) {
        if (s == null) return "";
        return s.replace("♂", "").replace("♀", "");
    }

    private static String maskNameVariants(String desc, String baseName, String baseId, String fullId) {
        if (desc == null) return "";

        String underscores = "_".repeat(Math.max(3, baseId.length()));

        List<String> variants = new ArrayList<>();

        // add the most specific variants first
        variants.add(baseName);
        variants.add(stripAccents(baseName));
        variants.add(stripGenderSymbols(baseName));
        variants.add(stripGenderSymbols(stripAccents(baseName)));

        variants.add(fullId);
        variants.add(baseId);

        // remove blanks + sort longest-first so "Nidoran♂" gets masked before "Nidoran"
        variants.removeIf(v -> v == null || v.isBlank());
        variants.sort(Comparator.comparingInt(String::length).reversed());

        String out = desc;
        for (String v : variants) {
            out = out.replaceAll("(?iu)" + Pattern.quote(v), underscores);
        }
        return out;
    }

    // used only for comparing/deduping answers
    private static String normKey(String s) {
        return stripAccents(s).toLowerCase(Locale.ROOT).trim();
    }
    private static String maskFirstName(String desc, String name) {
        if (name == null || name.isBlank()) return desc;

        // Word-boundary match, replace first hit only (case-insensitive)
        Pattern p = Pattern.compile("\\b" + Pattern.quote(name) + "\\b", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(desc);
        if (!m.find()) return desc;

        String underscores = "_".repeat(name.length());
        return m.replaceFirst(underscores);
    }

    private static LangData loadLang() {
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
            Trivia.LOGGER.error("[Trivia] Failed to read Cobblemon en_us.json", e);
        }

        return data;
    }

    private static final class LangData {
        final Map<String, String> nameById = new HashMap<>();
        final Map<String, String> descById = new HashMap<>();
    }
}