package anticope.chatgames.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VariableSolver {
    public static String solveVariable(List<String> lines) {
        if (lines == null || lines.isEmpty()) return null;
        try {
            // Pattern for equation lines like "X + X + X = 36"
            List<String> eqLines = new ArrayList<>();
            for (String line : lines) {
                if (line.contains("=") && (line.contains("+") || line.contains("-") || line.contains("*"))) {
                    eqLines.add(line);
                }
            }

            if (eqLines.size() >= 3) {
                // Line 1: 3 * A = Val1
                long val1 = parseEndVal(eqLines.get(0));
                long a = val1 / 3;

                // Line 2: 3 * B = Val2
                long val2 = parseEndVal(eqLines.get(1));
                long b = val2 / 3;

                // Line 3: A + B + C = Val3
                long val3 = parseEndVal(eqLines.get(2));
                long c = val3 - a - b;

                return String.valueOf(c);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static long parseEndVal(String line) {
        int idx = line.lastIndexOf('=');
        if (idx != -1) {
            String numStr = line.substring(idx + 1).replaceAll("[^0-9-]", "").trim();
            return Long.parseLong(numStr);
        }
        return 0;
    }
}
