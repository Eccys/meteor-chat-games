package anticope.chatgames.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MathSolver {
    private static final Pattern BINARY_EXPR = Pattern.compile("^\\s*(-?\\d+)\\s*([+\\-*/xX])\\s*(-?\\d+)\\s*$");

    public static String solveMath(String expression) {
        if (expression == null) return null;
        try {
            // Clean non-math characters (preserve x/X for multiplication)
            String clean = expression.replaceAll("[^0-9+\\-*/xX().\\s]", "").trim();
            if (clean.isEmpty()) return null;

            Matcher m = BINARY_EXPR.matcher(clean);
            if (m.matches()) {
                long first = Long.parseLong(m.group(1));
                char op = m.group(2).charAt(0);
                long second = Long.parseLong(m.group(3));

                return switch (op) {
                    case '+' -> String.valueOf(first + second);
                    case '-' -> String.valueOf(first - second);
                    case '*', 'x', 'X' -> String.valueOf(first * second);
                    case '/' -> second != 0 ? String.valueOf(first / second) : null;
                    default -> null;
                };
            }
        } catch (Exception ignored) {}
        return null;
    }
}