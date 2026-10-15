package anticope.chatgames.utils;

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
        try (InputStream is = FillSolver.class.getResourceAsStream("/words.txt")) {
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

        String[] rawTokens = input.trim().split("\\s+");
        List<String> cleanTokens = new ArrayList<>();
        for (String raw : rawTokens) {
            // Skip speaker prefix tokens (e.g. "Player_1:") or tokens containing digits
            if (raw.endsWith(":") || raw.matches(".*\\d.*")) {
                cleanTokens.add("");
                continue;
            }
            cleanTokens.add(raw.replaceAll("[^a-zA-Z_]", ""));
        }

        // 1. Check multi-word fill patterns first (e.g. "? _ile ca_i_et" -> "File cabinet")
        for (int i = 0; i < cleanTokens.size() - 1; i++) {
            String t1 = cleanTokens.get(i);
            String t2 = cleanTokens.get(i + 1);
            if (!t1.isEmpty() && !t2.isEmpty() && (t1.contains("_") || t2.contains("_"))) {
                String combined = t1 + " " + t2;
                if (hasLetterAndUnderscore(combined)) {
                    String match = matchPattern(combined);
                    if (match != null) return match;
                }
            }
        }

        // 2. Check individual tokens containing both '_' and at least one known letter
        for (String cleanToken : cleanTokens) {
            if (cleanToken.length() >= 3 && hasLetterAndUnderscore(cleanToken)) {
                String match = matchPattern(cleanToken);
                if (match != null) return match;
            }
        }

        return null;
    }

    private static boolean hasLetterAndUnderscore(String s) {
        boolean hasUnderscore = false;
        boolean hasLetter = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '_') hasUnderscore = true;
            else if (Character.isLetter(c)) hasLetter = true;
        }
        return hasUnderscore && hasLetter;
    }

    private static String matchPattern(String pattern) {
        String lowerPattern = pattern.toLowerCase();
        for (String word : WORDS) {
            if (word.length() == lowerPattern.length()) {
                boolean match = true;
                String lowerWord = word.toLowerCase();
                for (int i = 0; i < lowerPattern.length(); i++) {
                    char pc = lowerPattern.charAt(i);
                    char wc = lowerWord.charAt(i);
                    if (pc == ' ' && wc != ' ') {
                        match = false;
                        break;
                    }
                    if (pc != '_' && pc != ' ' && pc != wc) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    // Always capitalize first letter while preserving uppercase acronyms like DVD
                    return Character.toUpperCase(word.charAt(0)) + word.substring(1);
                }
            }
        }
        return null;
    }
}

