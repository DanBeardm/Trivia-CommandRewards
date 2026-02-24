package dev.roanoke.trivia;

import dev.roanoke.trivia.Quiz.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.kyori.adventure.platform.fabric.FabricServerAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import dev.roanoke.trivia.Commands.QuizCommands;
import dev.roanoke.trivia.Utils.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class Trivia implements ModInitializer {
    /**
     * Runs the mod initializer.
     */
    public static final Logger LOGGER = LoggerFactory.getLogger("Trivia");
    public static FabricServerAudiences adventure;
    public static MiniMessage mm = MiniMessage.miniMessage();

    public static Messages messages = new Messages(FabricLoader.getInstance().getConfigDir().resolve("Trivia/messages.json"));
    public static Trivia instance;
    public QuizManager quiz = new QuizManager();
    public Config config = new Config();
    public Integer quizIntervalCounter = 0;
    public Integer quizTimeOutCounter = 0;

    @Override
    public void onInitialize() {

        instance = this;

        new QuizCommands();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            adventure = FabricServerAudiences.of(server);

            // your generators
            quiz.addQuestions(CobblemonDexEntryQuestions.generate(server, 600));
            quiz.addQuestions(CobblemonNameScrambleQuestions.generate(server, 600));
            quiz.addQuestions(CobblemonAutoQuestions.generate(server, 600));

            // ✅ make the next tick start a quiz as soon as players are online
            quizTimeOutCounter = 0;
            quizIntervalCounter = config.getQuizInterval();
        });

        ServerTickEvents.START_SERVER_TICK.register(server -> {

            // If nobody is online, do nothing (don't tick interval or timeout)
            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                return;
            }

            // No quiz running -> tick the interval timer only
            if (!quiz.quizInProgress()) {
                if (quizIntervalCounter >= config.getQuizInterval()) {
                    quizIntervalCounter = 0;

                    // IMPORTANT: reset timeout counter when starting a quiz
                    quizTimeOutCounter = 0;

                    quiz.startQuiz(server);
                } else {
                    quizIntervalCounter++;
                }
                return;
            }

            // Quiz running -> tick the timeout timer only
            if (quizTimeOutCounter >= config.getQuizTimeOut()) {
                quizTimeOutCounter = 0;
                quizIntervalCounter = 0;
                quiz.timeOutQuiz(server);
            } else {
                quizTimeOutCounter++;
            }
        });


        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            if (quiz.quizInProgress()) {
                if (quiz.isRightAnswer(message.getContent().getString())) {
                    LOGGER.info("Trivia question was answered correctly.");
                    quiz.processQuizWinner(sender, sender.server);}
            }
        });


    }

    public static Trivia getInstance() {
        return instance;
    }

}