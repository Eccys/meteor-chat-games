package anticope.chatgames.utils;

import java.util.ArrayList;
import java.util.List;

public class VariableSolver {
    public static String solveVariable(List<String> lines) {
        if (lines == null || lines.isEmpty()) return null;
        try {
            List<String> eqLines = new ArrayList<>();
            for (String line : lines) {
                if (line.contains("=") && (line.contains("+") || line.contains("-") || line.contains("*") || line.contains("/"))) {
                    eqLines.add(line);
                }
            }

            if (eqLines.size() >= 3) {
                // Line 1: A + A + A = Val1 (or A * A * A = Val1)
                long a = solveUniformLine(eqLines.get(0));
                String symA = firstToken(eqLines.get(0));

                // Line 2: B + B + B = Val2 (or B * B * B = Val2)
                long b = solveUniformLine(eqLines.get(1));
                String symB = firstToken(eqLines.get(1));

                // Line 3: A op1 B op2 x = Val3
                String line3 = eqLines.get(2);
                long val3 = parseEndVal(line3);
                String lhs = line3.substring(0, line3.lastIndexOf('=')).trim();

                List<Character> ops = new ArrayList<>();
                List<String> tokens = new ArrayList<>();
                StringBuilder cur = new StringBuilder();
                for (int i = 0; i < lhs.length(); i++) {
                    char ch = lhs.charAt(i);
                    if ((ch == '+' || ch == '-' || ch == '*' || ch == '/') && cur.toString().trim().length() > 0) {
                        tokens.add(cur.toString().trim());
                        ops.add(ch);
                        cur.setLength(0);
                    } else {
                        cur.append(ch);
                    }
                }
                if (cur.toString().trim().length() > 0) {
                    tokens.add(cur.toString().trim());
                }

                long t1 = a;
                long t2 = b;
                if (tokens.size() >= 2 && !symA.isEmpty() && !symB.isEmpty() && !symA.equals(symB)) {
                    if (tokens.get(0).equals(symB)) t1 = b;
                    else if (tokens.get(0).equals(symA)) t1 = a;
                    if (tokens.get(1).equals(symA)) t2 = a;
                    else if (tokens.get(1).equals(symB)) t2 = b;
                }

                char op1 = ops.size() >= 1 ? ops.get(0) : '+';
                char op2 = ops.size() >= 2 ? ops.get(1) : '+';

                long c;
                switch (op2) {
                    case '+' -> c = val3 - evalOp(t1, op1, t2);
                    case '-' -> c = evalOp(t1, op1, t2) - val3;
                    case '*' -> {
                        if (op1 == '+') c = (val3 - t1) / t2;
                        else if (op1 == '-') c = (t1 - val3) / t2;
                        else c = val3 / evalOp(t1, op1, t2);
                    }
                    case '/' -> {
                        if (op1 == '+') c = t2 / (val3 - t1);
                        else if (op1 == '-') c = t2 / (t1 - val3);
                        else c = evalOp(t1, op1, t2) / val3;
                    }
                    default -> c = val3 - a - b;
                }

                return String.valueOf(c);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static long evalOp(long x, char op, long y) {
        return switch (op) {
            case '+' -> x + y;
            case '-' -> x - y;
            case '*' -> x * y;
            case '/' -> y != 0 ? x / y : 0;
            default -> x + y;
        };
    }

    private static long solveUniformLine(String line) {
        long val = parseEndVal(line);
        int eqIdx = line.lastIndexOf('=');
        String lhs = eqIdx != -1 ? line.substring(0, eqIdx) : line;
        int plusCount = 0;
        int mulCount = 0;
        for (int i = 0; i < lhs.length(); i++) {
            char c = lhs.charAt(i);
            if (c == '+') plusCount++;
            else if (c == '*') mulCount++;
        }
        if (mulCount > 0 && plusCount == 0) {
            int terms = mulCount + 1;
            return Math.round(Math.pow(val, 1.0 / terms));
        }
        int terms = plusCount > 0 ? plusCount + 1 : 3;
        return val / terms;
    }

    private static String firstToken(String line) {
        int eqIdx = line.lastIndexOf('=');
        String lhs = eqIdx != -1 ? line.substring(0, eqIdx) : line;
        String[] parts = lhs.split("[+\\-*/]");
        return parts.length > 0 ? parts[0].trim() : "";
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
