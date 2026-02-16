package dev.roanoke.trivia.Reward;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.roanoke.trivia.Quiz.Question;
import dev.roanoke.trivia.Trivia;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.HashMap;

public class RewardManager {

    private final HashMap<String, ArrayList<Reward>> rewardPools = new HashMap<>();

    public RewardManager(JsonObject rewardsObj) {
        Trivia.LOGGER.info("Loading rewards...");

        for (String difficulty : rewardsObj.keySet()) {
            JsonArray rewardsArr = rewardsObj.get(difficulty).getAsJsonArray();

            for (JsonElement rewardElem : rewardsArr) {
                JsonObject rewardObj = rewardElem.getAsJsonObject();

                // Backwards-compatible: old format still works, new fields are optional
                String itemName = rewardObj.has("item_name") ? rewardObj.get("item_name").getAsString() : "";
                String displayName = rewardObj.has("display_name") ? rewardObj.get("display_name").getAsString() : "";
                Integer quantity = rewardObj.has("quantity") ? rewardObj.get("quantity").getAsInt() : 1;

                String command = rewardObj.has("command") ? rewardObj.get("command").getAsString() : "";

                Reward reward = new Reward(itemName, displayName, quantity, command);

                // Only skip if it's neither an item nor a command
                if (!reward.isValid()) {
                    Trivia.LOGGER.warn("Skipping invalid reward entry in '{}': {}", difficulty, rewardObj);
                    continue;
                }

                rewardPools.computeIfAbsent(difficulty, k -> new ArrayList<>()).add(reward);
            }
        }

        for (String difficulty : rewardPools.keySet()) {
            System.out.println("Loaded " + rewardPools.get(difficulty).size() + " rewards for difficulty " + difficulty);
        }
    }

    public Reward giveReward(ServerPlayerEntity player, Question question) {
        if (!rewardPools.containsKey(question.difficulty)) return null;

        ArrayList<Reward> rewards = rewardPools.get(question.difficulty);
        Reward reward = rewards.get((int) (Math.random() * rewards.size()));

        MinecraftServer server = player.getServer();

        // 1) Execute command reward (if present)
        if (server != null && reward.hasCommand()) {
            // Give commands player context (@s etc) + keep server permission level
            ServerCommandSource source = server.getCommandSource()
                    .withLevel(4)                 // method exists :contentReference[oaicite:1]{index=1}
                    .withEntity(player)           // method exists :contentReference[oaicite:2]{index=2}
                    .withWorld(player.getServerWorld()) // method exists :contentReference[oaicite:3]{index=3}
                    .withPosition(player.getPos())      // method exists :contentReference[oaicite:4]{index=4}
                    .withSilent();               // method exists :contentReference[oaicite:5]{index=5}

            String cmd = applyPlaceholders(reward.command, player);

            // executeWithPrefix allows commands with or without a leading "/" :contentReference[oaicite:6]{index=6}
            server.getCommandManager().executeWithPrefix(source, cmd);
        }

        // 2) Give item reward (if present)
        if (reward.hasItem()) {
            if (!player.giveItemStack(reward.itemStack.copy())) {
                player.dropItem(reward.itemStack.copy(), false);
            }
        }

        return reward;
    }

    private static String applyPlaceholders(String cmd, ServerPlayerEntity player) {
        if (cmd == null) return "";
        String name = player.getName().getString();
        String uuid = player.getUuidAsString();

        return cmd
                .replace("%player%", name)
                .replace("{player}", name)
                .replace("@p", name)     // keep your old style working
                .replace("%uuid%", uuid);
    }
}
