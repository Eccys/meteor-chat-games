package anticope.chatgames.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MathSolver {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\s*(-?\\d+)\\s*([+\\-*/xX])\\s*");
    private static final Pattern TRAILING_INT = Pattern.compile("^\\s*(-?\\d+)\\s*$");

    public static String solveMath(String expression) {
        if (expression == null) return null;
        try {
            // Normalize Unicode operators (×, ÷, −, –) before stripping non-math characters
            String normalized = expression
                .replace('\u00D7', '*')
                .replace('\u00F7', '/')
                .replace('\u2212', '-')
                .replace('\u2013', '-');

            // Clean non-math characters (preserve x/X for multiplication)
            String clean = normalized.replaceAll("[^0-9+\\-*/xX\\s]", "").trim();
            if (clean.isEmpty()) return null;

            List<Long> numbers = new ArrayList<>();
            List<Character> ops = new ArrayList<>();

            int pos = 0;
            Matcher m = TOKEN_PATTERN.matcher(clean);
            while (m.find(pos) && m.start() == pos) {
                numbers.add(Long.parseLong(m.group(1)));
                ops.add(m.group(2).charAt(0));
                pos = m.end();
            }

            if (ops.isEmpty()) return null;

            Matcher tail = TRAILING_INT.matcher(clean.substring(pos));
            if (!tail.matches()) return null;
            numbers.add(Long.parseLong(tail.group(1)));

            // Pass 1: *, /, x, X
            List<Long> addTerms = new ArrayList<>();
            List<Character> addOps = new ArrayList<>();
            long current = numbers.get(0);
            for (int i = 0; i < ops.size(); i++) {
                char op = ops.get(i);
                long next = numbers.get(i + 1);
                if (op == '*' || op == 'x' || op == 'X') {
                    current = current * next;
                } else if (op == '/') {
                    if (next == 0) return null;
                    current = current / next;
                } else {
                    addTerms.add(current);
                    addOps.add(op);
                    current = next;
                }
            }
            addTerms.add(current);

            // Pass 2: +, -
            long result = addTerms.get(0);
            for (int i = 0; i < addOps.size(); i++) {
                char op = addOps.get(i);
                long next = addTerms.get(i + 1);
                if (op == '+') result += next;
                else if (op == '-') result -= next;
            }

            return String.valueOf(result);
        } catch (Exception ignored) {}
        return null;
    }
}