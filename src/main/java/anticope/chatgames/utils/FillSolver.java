package anticope.chatgames.utils;

import anticope.chatgames.ChatGamesAddon;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class FillSolver {
    private static final List<String> WORDS = new ArrayList<>();

    static {
        loadWords();
    }

    private static void loadWords() {
        try (InputStream is = ChatGamesAddon.class.getResourceAsStream("/words.txt")) {
            if (is != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty()) {
                            WORDS.add(line);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public static String solveFill(String input) {
        if (input == null || !input.contains("_")) return null;

        // Clean input to get the blank pattern (e.g. "__dybug" or "b_n_n_")
        String[] tokens = input.split("\\s+");
        String pattern = null;
        for (String token : tokens) {
            String cleanToken = token.replaceAll("[^a-zA-Z_]", "");
            if (cleanToken.contains("_") && cleanToken.length() >= 3) {
                pattern = cleanToken;
                break;
            }
        }

        if (pattern == null) return null;
        pattern = pattern.toLowerCase();

        for (String word : WORDS) {
            if (word.length() == pattern.length()) {
                boolean match = true;
                for (int i = 0; i < pattern.length(); i++) {
                    char pc = pattern.charAt(i);
                    if (pc != '_' && pc != word.toLowerCase().charAt(i)) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    // Match capitalization
                    if (Character.isUpperCase(pattern.charAt(0)) || (input.length() > 0 && Character.isUpperCase(input.charAt(0)))) {
                        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
                    }
                    return Character.toUpperCase(word.charAt(0)) + word.substring(1);
                }
            }
        }

        return null;
    }
}
