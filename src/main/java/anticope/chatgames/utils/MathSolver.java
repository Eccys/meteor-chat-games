package anticope.chatgames.utils;

public class MathSolver {
    public static String solveMath(String expression) {
        if (expression == null) return null;
        try {
            // Clean non-math characters
            String clean = expression.replaceAll("[^0-9+\\-*/().\\s]", "").trim();
            if (clean.isEmpty()) return null;

            // Simple two-operand evaluator
            if (clean.contains("+")) {
                String[] parts = clean.split("\\+");
                long result = parseLong(parts[0]) + parseLong(parts[1]);
                return String.valueOf(result);
            } else if (clean.contains("-")) {
                int dashIndex = clean.indexOf('-', 1);
                if (dashIndex > 0) {
                    long first = parseLong(clean.substring(0, dashIndex));
                    long second = parseLong(clean.substring(dashIndex + 1));
                    return String.valueOf(first - second);
                }
            } else if (clean.contains("*") || clean.contains("x")) {
                String[] parts = clean.split("[*x]");
                long result = parseLong(parts[0]) * parseLong(parts[1]);
                return String.valueOf(result);
            } else if (clean.contains("/")) {
                String[] parts = clean.split("/");
                long second = parseLong(parts[1]);
                if (second != 0) {
                    long result = parseLong(parts[0]) / second;
                    return String.valueOf(result);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static long parseLong(String val) {
        return Long.parseLong(val.replaceAll("[^0-9-]", "").trim());
    }
}
