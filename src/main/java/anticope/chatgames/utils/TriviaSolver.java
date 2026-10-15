package anticope.chatgames.utils;

import anticope.chatgames.ChatGamesAddon;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class TriviaSolver {
    private static final Map<String, String> TRIVIA_DB = new HashMap<>();
    private static final Map<String, String> SORT_DB = new HashMap<>();
    private static final List<String> WORDS_LIST = new ArrayList<>();

    static {
        loadDatabase();
        loadWordList();
    }

    private static void loadDatabase() {
        try (InputStream is = ChatGamesAddon.class.getResourceAsStream("/trivia_db.json")) {
            if (is != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
                if (root.has("trivia")) {
                    JsonArray triviaArray = root.getAsJsonArray("trivia");
                    for (JsonElement elem : triviaArray) {
                        JsonObject obj = elem.getAsJsonObject();
                        if (obj.has("question") && obj.has("answer")) {
                            String q = clean(obj.get("question").getAsString());
                            if (!q.isEmpty()) {
                                TRIVIA_DB.put(q, obj.get("answer").getAsString());
                            }
                        }
                    }
                }
                if (root.has("sort")) {
                    JsonArray sortArray = root.getAsJsonArray("sort");
                    for (JsonElement elem : sortArray) {
                        JsonObject obj = elem.getAsJsonObject();
                        if (obj.has("scrambled") && obj.has("solved")) {
                            SORT_DB.put(obj.get("scrambled").getAsString().trim(), obj.get("solved").getAsString().trim());
                        }
                    }
                }
            }
        } catch (Exception e) {
            ChatGamesAddon.LOG.error("Failed to load trivia_db.json", e);
        }
    }

    private static void loadWordList() {
        try (InputStream is = ChatGamesAddon.class.getResourceAsStream("/words.txt")) {
            if (is != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty()) {
                            WORDS_LIST.add(line);
                        }
                    }
                }
            }
        } catch (Exception e) {
            ChatGamesAddon.LOG.error("Failed to load words.txt", e);
        }
    }

    private static String clean(String input) {
        if (input == null) return "";
        return input.replaceAll("[^a-zA-Z0-9 ]", "").replaceAll("\\s+", " ").toLowerCase().trim();
    }

    public static String solveTrivia(String question) {
        if (question == null) return null;
        String cleanQ = clean(question);

        // Prevent false positive matching on short/empty strings
        if (cleanQ.length() < 10) return null;

        // Exact match
        if (TRIVIA_DB.containsKey(cleanQ)) {
            return TRIVIA_DB.get(cleanQ);
        }

        // Substring match ONLY for long questions (>= 15 chars) to avoid false positives,
        // preferring the longest matching key for specificity
        if (cleanQ.length() >= 15) {
            String bestAnswer = null;
            int bestKeyLen = -1;
            for (Map.Entry<String, String> entry : TRIVIA_DB.entrySet()) {
                String dbKey = entry.getKey();
                if (dbKey.length() >= 15) {
                    if (cleanQ.contains(dbKey) || dbKey.contains(cleanQ)) {
                        if (dbKey.length() > bestKeyLen) {
                            bestKeyLen = dbKey.length();
                            bestAnswer = entry.getValue();
                        }
                    }
                }
            }
            if (bestAnswer != null) return bestAnswer;
        }

        return null;
    }

    public static String solveSort(String scrambled) {
        if (scrambled == null) return null;
        scrambled = scrambled.trim();
        if (scrambled.startsWith("\"") && scrambled.endsWith("\"")) {
            if (scrambled.length() < 2) return null;
            scrambled = scrambled.substring(1, scrambled.length() - 1).trim();
        }
        if (scrambled.isEmpty()) return null;

        // 1. Direct DB lookup
        if (SORT_DB.containsKey(scrambled)) {
            return SORT_DB.get(scrambled);
        }

        // Detect uppercase character hint (server scrambles capitalized words so the original first letter remains uppercase)
        Character upperHint = null;
        boolean anyUpper = false;
        for (int i = 0; i < scrambled.length(); i++) {
            char c = scrambled.charAt(i);
            if (Character.isUpperCase(c)) {
                anyUpper = true;
                if (upperHint == null) {
                    upperHint = Character.toLowerCase(c);
                }
            }
        }

        // 2. Anagram solver using embedded word list
        char[] targetChars = scrambled.toLowerCase().toCharArray();
        Arrays.sort(targetChars);

        String fallbackMatch = null;
        for (String word : WORDS_LIST) {
            if (word.length() == scrambled.length()) {
                char[] wordChars = word.toLowerCase().toCharArray();
                Arrays.sort(wordChars);
                if (Arrays.equals(targetChars, wordChars)) {
                    String formatted = (anyUpper || Character.isUpperCase(word.charAt(0)))
                        ? Character.toUpperCase(word.charAt(0)) + word.substring(1)
                        : word;
                    if (upperHint != null && Character.toLowerCase(word.charAt(0)) == upperHint) {
                        return formatted;
                    }
                    if (fallbackMatch == null) {
                        fallbackMatch = formatted;
                    }
                }
            }
        }
        if (fallbackMatch != null) return fallbackMatch;

        // 3. Fallback check inside SORT_DB values
        for (Map.Entry<String, String> entry : SORT_DB.entrySet()) {
            String val = entry.getValue();
            if (val.length() == scrambled.length()) {
                char[] entryChars = val.toLowerCase().toCharArray();
                Arrays.sort(entryChars);
                if (Arrays.equals(targetChars, entryChars)) {
                    return val;
                }
            }
        }

        return null;
    }
}

