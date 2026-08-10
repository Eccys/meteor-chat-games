package anticope.chatgames;

import anticope.chatgames.utils.*;
import java.util.*;
import java.util.regex.*;

/**
 * Standalone test harness for all chat game solvers.
 * Run with: java -cp . anticope.chatgames.SolverTest
 * (Or just build the mod and check the output in latest.log)
 */
public class SolverTest {

    // Patterns copied from AutoChatGame.java to verify they match
    private static final Pattern SORT_PATTERN = Pattern.compile("(?:sort|unscramble)\\s+(?:the\\s+)?(?:word)?\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern REVERSE_PATTERN = Pattern.compile("(?:type|write|reverse)\\s+(?:the\\s+)?(?:word)?\\s*\"([^\"]+)\"\\s+backwards|(?:reverse)\\s+(?:the\\s+)?(?:word)?\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern MATH_PATTERN = Pattern.compile("\"(\\d+\\s*[+\\-*/xX]\\s*\\d+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern WRITE_PATTERN = Pattern.compile("(?:to\\s+)?(?:type|write)\\s+\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  Chat Game Solver Test Suite");
        System.out.println("========================================\n");

        testMathSolver();
        testWriteRegex();
        testSortRegex();
        testReverseRegex();
        testMathRegex();
        testFillSolver();
        testReverseSolver();
        testMathSolverEdgeCases();
        testVariableSolver();

        System.out.println("\n========================================");
        System.out.printf("  Results: %d PASSED, %d FAILED%n", passed, failed);
        System.out.println("========================================");

        if (failed > 0) System.exit(1);
    }

    // =========== REGEX MATCHING TESTS ===========

    static void testMathRegex() {
        section("MATH Regex");

        // Actual server messages from latest.log
        assertRegexMatch(MATH_PATTERN, "You have 20 seconds to solve \"12 + 54\"", "12 + 54");
        assertRegexMatch(MATH_PATTERN, "20 seconds to calculate \"88 - 23\"", "88 - 23");
        assertRegexMatch(MATH_PATTERN, "solve \"100 * 3\"", "100 * 3");
        assertRegexMatch(MATH_PATTERN, "\"45 / 9\"", "45 / 9");

        // With formatting noise stripped by getString()
        assertRegexMatch(MATH_PATTERN, "You have 20 seconds to  \"12 + 54\"", "12 + 54");
        assertRegexMatch(MATH_PATTERN, "some random text \"99 + 1\" more text", "99 + 1");

        // Should NOT match
        assertRegexNoMatch(MATH_PATTERN, "Player123 said hello");
        assertRegexNoMatch(MATH_PATTERN, "CHATGAMES MATH");
    }

    static void testWriteRegex() {
        section("WRITE Regex");

        // Real server format: "20 seconds to write \"Cheek\""
        assertRegexMatch(WRITE_PATTERN, "20 seconds to write \"Cheek\"", "Cheek");
        assertRegexMatch(WRITE_PATTERN, "You have 20 seconds to type \"Hello World\"", "Hello World");
        assertRegexMatch(WRITE_PATTERN, "to write \"Balloon\"", "Balloon");
        assertRegexMatch(WRITE_PATTERN, "write \"Test\"", "Test");

        // Should NOT match regular chat
        assertRegexNoMatch(WRITE_PATTERN, "Player123: hey guys");
        assertRegexNoMatch(WRITE_PATTERN, "CHATGAMES WRITE");
    }

    static void testSortRegex() {
        section("SORT Regex");

        assertRegexMatch(SORT_PATTERN, "sort the word \"oodro\"", "oodro");
        assertRegexMatch(SORT_PATTERN, "unscramble the word \"elhdo\"", "elhdo");
        assertRegexMatch(SORT_PATTERN, "sort \"Hgede\"", "Hgede");

        assertRegexNoMatch(SORT_PATTERN, "Player says sort this out");
    }

    static void testReverseRegex() {
        section("REVERSE Regex");

        assertRegexMatch(REVERSE_PATTERN, "type \"Mortar\" backwards", "Mortar", 1);
        assertRegexMatch(REVERSE_PATTERN, "write \"Balloon\" backwards", "Balloon", 1);
        assertRegexMatch(REVERSE_PATTERN, "reverse \"Room\"", "Room", 2);

        assertRegexNoMatch(REVERSE_PATTERN, "Player says reverse this");
    }

    // =========== SOLVER LOGIC TESTS ===========

    static void testMathSolver() {
        section("MathSolver");

        assertEqual("MathSolver 12+54", MathSolver.solveMath("12 + 54"), "66");
        assertEqual("MathSolver 88-23", MathSolver.solveMath("88 - 23"), "65");
        assertEqual("MathSolver 100*3", MathSolver.solveMath("100 * 3"), "300");
        assertEqual("MathSolver 45/9", MathSolver.solveMath("45 / 9"), "5");
        assertEqual("MathSolver 0+0", MathSolver.solveMath("0 + 0"), "0");
        assertEqual("MathSolver null", MathSolver.solveMath(null), null);
    }

    static void testMathSolverEdgeCases() {
        section("MathSolver Edge Cases");

        assertEqual("Large numbers", MathSolver.solveMath("999 + 999"), "1998");
        assertEqual("Single digits", MathSolver.solveMath("5 + 3"), "8");
        assertEqual("Multiply", MathSolver.solveMath("12 * 12"), "144");
    }

    static void testReverseSolver() {
        section("ReverseSolver");

        assertEqual("Reverse Mortar", ReverseSolver.solveReverse("Mortar"), "ratroM");
        assertEqual("Reverse Balloon", ReverseSolver.solveReverse("Balloon"), "noollaB");
        assertEqual("Reverse Room", ReverseSolver.solveReverse("Room"), "mooR");
        assertEqual("Reverse null", ReverseSolver.solveReverse(null), null);
        assertEqual("Reverse empty", ReverseSolver.solveReverse(""), null);
    }

    static void testFillSolver() {
        section("FillSolver");

        // Test from real log: "__dybug" -> "Ladybug"
        String result = FillSolver.solveFill("__dybug");
        assertNotNull("Fill __dybug", result);
        if (result != null) {
            assertEqual("Fill __dybug => Ladybug", result.toLowerCase(), "ladybug");
        }
    }

    static void testVariableSolver() {
        section("VariableSolver");

        // A + A + A = 15 => A = 5
        // B + B + B = 9 => B = 3
        // A + B + x = 12 => x = 4
        List<String> lines = new ArrayList<>();
        lines.add("A + A + A = 15");
        lines.add("B + B + B = 9");
        lines.add("A + B + x = 12");
        assertEqual("Variable 5+3+x=12", VariableSolver.solveVariable(lines), "4");

        // Edge: null
        assertEqual("Variable null", VariableSolver.solveVariable(null), null);
        assertEqual("Variable empty", VariableSolver.solveVariable(new ArrayList<>()), null);
    }

    // =========== TEST HELPERS ===========

    static void section(String name) {
        System.out.println("\n--- " + name + " ---");
    }

    static void assertEqual(String label, String actual, String expected) {
        if (Objects.equals(actual, expected)) {
            System.out.println("  ✓ " + label + " = " + actual);
            passed++;
        } else {
            System.out.println("  ✗ " + label + " => expected: " + expected + ", got: " + actual);
            failed++;
        }
    }

    static void assertNotNull(String label, Object value) {
        if (value != null) {
            System.out.println("  ✓ " + label + " is not null: " + value);
            passed++;
        } else {
            System.out.println("  ✗ " + label + " is null (expected non-null)");
            failed++;
        }
    }

    static void assertRegexMatch(Pattern pattern, String input, String expectedGroup) {
        assertRegexMatch(pattern, input, expectedGroup, 1);
    }

    static void assertRegexMatch(Pattern pattern, String input, String expectedGroup, int groupIdx) {
        Matcher m = pattern.matcher(input);
        if (m.find()) {
            String found = m.group(groupIdx);
            if (Objects.equals(found, expectedGroup)) {
                System.out.println("  ✓ Regex matched: \"" + input + "\" => group(" + groupIdx + ")=\"" + found + "\"");
                passed++;
            } else {
                System.out.println("  ✗ Regex matched but wrong group: \"" + input + "\" => expected \"" + expectedGroup + "\", got \"" + found + "\"");
                failed++;
            }
        } else {
            System.out.println("  ✗ Regex did NOT match: \"" + input + "\" (expected match)");
            failed++;
        }
    }

    static void assertRegexNoMatch(Pattern pattern, String input) {
        Matcher m = pattern.matcher(input);
        if (!m.find()) {
            System.out.println("  ✓ Correctly no match: \"" + input + "\"");
            passed++;
        } else {
            System.out.println("  ✗ Unexpected match: \"" + input + "\" => \"" + m.group() + "\"");
            failed++;
        }
    }
}
