package anticope.chatgames.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VariableSolver {
    private static final Pattern RHS_INT_PATTERN = Pattern.compile("=\\s*(-?\\d+)\\s*$");

    public static String solveVariable(List<String> lines) {
        if (lines == null || lines.isEmpty()) return null;
        try {
            List<String> eqLines = new ArrayList<>();
            for (String line : lines) {
                if (isEquationLine(line)) {
                    eqLines.add(line);
                }
            }

            if (eqLines.size() >= 3) {
                int base = eqLines.size() - 3;
                // Line 1: A + A + A = Val1 (or A * A * A = Val1)
                long a = solveUniformLine(eqLines.get(base));
                String symA = firstToken(eqLines.get(base));

                // Line 2: B + B + B = Val2 (or B * B * B = Val2)
                long b = solveUniformLine(eqLines.get(base + 1));
                String symB = firstToken(eqLines.get(base + 1));

                // Line 3: A op1 B op2 x = Val3 (or 2-term / reordered unknown)
                String line3 = eqLines.get(base + 2);
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

                if (tokens.size() == 2 && ops.size() == 1) {
                    return solveTwoTermLine(tokens, ops.get(0), val3, symA, a, symB, b);
                }

                long t1 = a;
                long t2 = b;
                int unknownIdx = 2;
                if (tokens.size() >= 3 && !symA.isEmpty() && !symB.isEmpty() && !symA.equals(symB)) {
                    Long v0 = resolveKnown(tokens.get(0), symA, a, symB, b);
                    Long v1 = resolveKnown(tokens.get(1), symA, a, symB, b);
                    Long v2 = resolveKnown(tokens.get(2), symA, a, symB, b);

                    if (v0 != null && v1 != null && v2 == null) {
                        t1 = v0;
                        t2 = v1;
                        unknownIdx = 2;
                    } else if (v0 != null && v1 == null && v2 != null) {
                        return solveMiddleUnknown(v0, ops.get(0), ops.get(1), v2, val3);
                    } else if (v0 == null && v1 != null && v2 != null) {
                        return solveFirstUnknown(ops.get(0), v1, ops.get(1), v2, val3);
                    } else {
                        if (v0 != null) t1 = v0;
                        if (v1 != null) t2 = v1;
                    }
                }

                char op1 = ops.size() >= 1 ? ops.get(0) : '+';
                char op2 = ops.size() >= 2 ? ops.get(1) : '+';

                long c;
                switch (op2) {
                    case '+' -> c = val3 - evalOp(t1, op1, t2);
                    case '-' -> c = evalOp(t1, op1, t2) - val3;
                    case '*' -> {
                        if (op1 == '+') {
                            if (t2 == 0) return null;
                            c = (val3 - t1) / t2;
                        } else if (op1 == '-') {
                            if (t2 == 0) return null;
                            c = (t1 - val3) / t2;
                        } else {
                            long prod = evalOp(t1, op1, t2);
                            if (prod == 0) return null;
                            c = val3 / prod;
                        }
                    }
                    case '/' -> {
                        if (op1 == '+') {
                            long denom = val3 - t1;
                            if (denom == 0) return null;
                            c = t2 / denom;
                        } else if (op1 == '-') {
                            long denom = t1 - val3;
                            if (denom == 0) return null;
                            c = t2 / denom;
                        } else {
                            if (val3 == 0) return null;
                            c = evalOp(t1, op1, t2) / val3;
                        }
                    }
                    default -> c = val3 - a - b;
                }

                return String.valueOf(c);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean isEquationLine(String line) {
        if (line == null) return false;
        int eqIdx = line.lastIndexOf('=');
        if (eqIdx <= 0) return false;
        if (!RHS_INT_PATTERN.matcher(line.substring(eqIdx)).matches()) return false;
        String lhs = line.substring(0, eqIdx);
        return lhs.contains("+") || lhs.contains("-") || lhs.contains("*") || lhs.contains("/");
    }

    private static Long resolveKnown(String token, String symA, long a, String symB, long b) {
        if (token.equals(symA)) return a;
        if (token.equals(symB)) return b;
        try {
            return Long.parseLong(token);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String solveTwoTermLine(List<String> tokens, char op, long val3, String symA, long a, String symB, long b) {
        Long v0 = resolveKnown(tokens.get(0), symA, a, symB, b);
        Long v1 = resolveKnown(tokens.get(1), symA, a, symB, b);
        if (v0 != null && v1 == null) {
            // v0 op x = val3
            return switch (op) {
                case '+' -> String.valueOf(val3 - v0);
                case '-' -> String.valueOf(v0 - val3);
                case '*' -> v0 != 0 ? String.valueOf(val3 / v0) : null;
                case '/' -> val3 != 0 ? String.valueOf(v0 / val3) : null;
                default -> null;
            };
        } else if (v0 == null && v1 != null) {
            // x op v1 = val3
            return switch (op) {
                case '+' -> String.valueOf(val3 - v1);
                case '-' -> String.valueOf(val3 + v1);
                case '*' -> v1 != 0 ? String.valueOf(val3 / v1) : null;
                case '/' -> String.valueOf(val3 * v1);
                default -> null;
            };
        }
        // Fallback when symbols are identical (e.g. '?')
        return switch (op) {
            case '+' -> String.valueOf(val3 - a);
            case '-' -> String.valueOf(a - val3);
            case '*' -> a != 0 ? String.valueOf(val3 / a) : null;
            case '/' -> val3 != 0 ? String.valueOf(a / val3) : null;
            default -> null;
        };
    }

    private static String solveMiddleUnknown(long t1, char op1, char op2, long t3, long val3) {
        // t1 op1 x op2 t3 = val3
        if (op2 == '+' || op2 == '-') {
            long rhs = (op2 == '+') ? (val3 - t3) : (val3 + t3);
            return switch (op1) {
                case '+' -> String.valueOf(rhs - t1);
                case '-' -> String.valueOf(t1 - rhs);
                case '*' -> t1 != 0 ? String.valueOf(rhs / t1) : null;
                case '/' -> rhs != 0 ? String.valueOf(t1 / rhs) : null;
                default -> null;
            };
        }
        return null;
    }

    private static String solveFirstUnknown(char op1, long t2, char op2, long t3, long val3) {
        // x op1 t2 op2 t3 = val3
        if ((op1 == '+' || op1 == '-') && (op2 == '*' || op2 == '/')) {
            long tail = evalOp(t2, op2, t3);
            return String.valueOf(op1 == '+' ? val3 - tail : val3 + tail);
        }
        if (op2 == '+' || op2 == '-') {
            long rhs = (op2 == '+') ? (val3 - t3) : (val3 + t3);
            return switch (op1) {
                case '+' -> String.valueOf(rhs - t2);
                case '-' -> String.valueOf(rhs + t2);
                case '*' -> t2 != 0 ? String.valueOf(rhs / t2) : null;
                case '/' -> String.valueOf(rhs * t2);
                default -> null;
            };
        }
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
            if (val < 0 && terms % 2 == 1) {
                return -Math.round(Math.pow(-val, 1.0 / terms));
            }
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
        Matcher m = RHS_INT_PATTERN.matcher(line);
        if (m.find()) {
            return Long.parseLong(m.group(1));
        }
        return 0;
    }
}

