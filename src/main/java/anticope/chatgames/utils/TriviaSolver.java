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
        return input.replaceAll("[^a-zA-Z0-9 ]", "").toLowerCase().trim();
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

        // Substring match ONLY for long questions (>= 15 chars) to avoid false positives
        if (cleanQ.length() >= 15) {
            for (Map.Entry<String, String> entry : TRIVIA_DB.entrySet()) {
                String dbKey = entry.getKey();
                if (dbKey.length() >= 15) {
                    if (cleanQ.contains(dbKey) || dbKey.contains(cleanQ)) {
                        return entry.getValue();
                    }
                }
            }
        }

        return null;
    }

    public static String solveSort(String scrambled) {
        if (scrambled == null) return null;
        scrambled = scrambled.trim();
        if (scrambled.isEmpty()) return null;

        // 1. Direct DB lookup
        if (SORT_DB.containsKey(scrambled)) {
            return SORT_DB.get(scrambled);
        }

        // 2. Anagram solver using embedded word list
        char[] targetChars = scrambled.toLowerCase().toCharArray();
        Arrays.sort(targetChars);

        for (String word : WORDS_LIST) {
            if (word.length() == scrambled.length()) {
                char[] wordChars = word.toLowerCase().toCharArray();
                Arrays.sort(wordChars);
                if (Arrays.equals(targetChars, wordChars)) {
                    if (Character.isUpperCase(scrambled.charAt(0))) {
                        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
                    }
                    return word;
                }
            }
        }

        // 3. Fallback check inside SORT_DB values
        for (Map.Entry<String, String> entry : SORT_DB.entrySet()) {
            char[] entryChars = entry.getValue().toLowerCase().toCharArray();
            Arrays.sort(entryChars);
            if (Arrays.equals(targetChars, entryChars)) {
                return entry.getValue();
            }
        }

        return null;
    }
}
