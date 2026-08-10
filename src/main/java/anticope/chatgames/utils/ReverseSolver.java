package anticope.chatgames.utils;

public class ReverseSolver {
    public static String solveReverse(String input) {
        if (input == null || input.isEmpty()) return null;
        String trimmed = input.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return new StringBuilder(trimmed).reverse().toString();
    }
}
