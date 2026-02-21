package dev.roanoke.trivia.Quiz;

import java.util.List;
import java.util.Locale;

// contains a question string, a list of possible answers, and a function to check if its the correct answer
public class Question {

    public String question;
    public List<String> answers;
    public String difficulty;

    public Question(String question, List<String> answers, String difficulty) {
        this.question = question;
        this.answers = answers;
        this.difficulty = difficulty;
        // PokeTrivia.LOGGER.info("Loaded question: " + question);
        this.answers = answers.stream()
                .map(a -> a.toLowerCase(Locale.ROOT).trim())
                .distinct()
                .toList();
    }

}
