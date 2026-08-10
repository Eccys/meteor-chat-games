package anticope.chatgames.utils;

import anticope.chatgames.ChatGamesAddon;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class TriviaSolver {
    private static final Map<String, String> TRIVIA_DB = new HashMap<>();
    private static final Map<String, String> SORT_DB = new HashMap<>();

    static {
        loadDatabase();
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
                            TRIVIA_DB.put(clean(obj.get("question").getAsString()), obj.get("answer").getAsString());
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

    private static String clean(String input) {
        return input.replaceAll("[^a-zA-Z0-9 ]", "").toLowerCase().trim();
    }

    public static String solveTrivia(String question) {
        if (question == null) return null;
        String cleanQ = clean(question);
        
        // Exact match
        if (TRIVIA_DB.containsKey(cleanQ)) {
            return TRIVIA_DB.get(cleanQ);
        }

        // Substring / Fuzzy match
        for (Map.Entry<String, String> entry : TRIVIA_DB.entrySet()) {
            if (cleanQ.contains(entry.getKey()) || entry.getKey().contains(cleanQ)) {
                return entry.getValue();
            }
        }

        return null;
    }

    public static String solveSort(String scrambled) {
        if (scrambled == null) return null;
        scrambled = scrambled.trim();
        if (SORT_DB.containsKey(scrambled)) {
            return SORT_DB.get(scrambled);
        }

        // Anagram matching
        char[] targetChars = scrambled.toLowerCase().toCharArray();
        java.util.Arrays.sort(targetChars);

        for (Map.Entry<String, String> entry : SORT_DB.entrySet()) {
            char[] entryChars = entry.getValue().toLowerCase().toCharArray();
            java.util.Arrays.sort(entryChars);
            if (java.util.Arrays.equals(targetChars, entryChars)) {
                return entry.getValue();
            }
        }

        return null;
    }
}
