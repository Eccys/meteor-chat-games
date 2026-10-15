package anticope.chatgames;

import anticope.chatgames.utils.*;
import java.util.*;
import java.util.regex.*;

/**
 * Standalone test harness for all chat game solvers.
 * Run with: java -cp . anticope.chatgames.SolverTest
 * (Or via ./gradlew test)
 */
public class SolverTest {

    // Patterns synced with AutoChatGame.java to verify they match
    private static final Pattern SORT_PATTERN = Pattern.compile("(?:sort|unscramble)\\s+(?:the\\s+)?(?:word)?\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern REVERSE_PATTERN = Pattern.compile("(?:type|write|reverse)\\s+(?:the\\s+)?(?:word)?\\s*\"([^\"]+)\"\\s+backwards|(?:reverse)\\s+(?:the\\s+)?(?:word)?\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern MATH_PATTERN = Pattern.compile("\"(-?\\d+\\s*[+\\-*/xX\u00D7\u00F7]\\s*-?\\d+(?:\\s*[+\\-*/xX\u00D7\u00F7]\\s*-?\\d+)*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern WRITE_PATTERN = Pattern.compile("(?:to\\s+)?(?:type|write)\\s+(?:the\\s+)?(?:word\\s+)?\"([^\"]+)\"(?!\\s+backwards\\b)", Pattern.CASE_INSENSITIVE);

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  Chat Game Solver Test Suite");
        System.out.println("========================================\n");

        testMathSolver();
        testMathSolverEdgeCases();
        testWriteRegex();
        testSortRegex();
        testReverseRegex();
        testMathRegex();
        testFillSolver();
        testReverseSolver();
        testSortSolver();
        testTriviaSolver();
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
        assertRegexMatch(MATH_PATTERN, "solve \"-12 * -3\"", "-12 * -3");
        assertRegexMatch(MATH_PATTERN, "solve \"10 + 20 * 3\"", "10 + 20 * 3");
        assertRegexMatch(MATH_PATTERN, "solve \"12 \u00D7 5\"", "12 \u00D7 5");

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
        assertRegexMatch(WRITE_PATTERN, "20 seconds to write the word \"Walnut\"", "Walnut");
        assertRegexMatch(WRITE_PATTERN, "to write \"Balloon\"", "Balloon");
        assertRegexMatch(WRITE_PATTERN, "write \"Test\"", "Test");

        // Should NOT match regular chat or reverse prompts
        assertRegexNoMatch(WRITE_PATTERN, "Player123: hey guys");
        assertRegexNoMatch(WRITE_PATTERN, "CHATGAMES WRITE");
        assertRegexNoMatch(WRITE_PATTERN, "write \"Balloon\" backwards");
        assertRegexNoMatch(WRITE_PATTERN, "write the word \"Balloon\" backwards");
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
        assertRegexMatch(REVERSE_PATTERN, "20 seconds to reverse the word \"dap gniruocS\"", "dap gniruocS", 2);

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
        assertEqual("Multiply with x", MathSolver.solveMath("12 x 12"), "144");
        assertEqual("Multiply with X", MathSolver.solveMath("12 X 5"), "60");
        assertEqual("Unicode multiply \u00D7", MathSolver.solveMath("12 \u00D7 5"), "60");
        assertEqual("Unicode divide \u00F7", MathSolver.solveMath("45 \u00F7 9"), "5");
        assertEqual("Unicode minus \u2212", MathSolver.solveMath("20 \u2212 8"), "12");
        assertEqual("Negative add", MathSolver.solveMath("-12 + 5"), "-7");
        assertEqual("Negative subtract", MathSolver.solveMath("-12 - 5"), "-17");
        assertEqual("Subtract negative", MathSolver.solveMath("12 - -5"), "17");
        assertEqual("Negative multiply", MathSolver.solveMath("-12 * -3"), "36");
        assertEqual("Negative divide", MathSolver.solveMath("-45 / 9"), "-5");
        assertEqual("Multi-operand precedence + *", MathSolver.solveMath("10 + 2 * 3"), "16");
        assertEqual("Multi-operand precedence - / +", MathSolver.solveMath("100 - 20 / 4 + 5"), "100");
        assertEqual("Divide by zero", MathSolver.solveMath("45 / 0"), null);
        assertEqual("Single number without operator", MathSolver.solveMath("12345"), null);
        assertEqual("Trailing operator", MathSolver.solveMath("12 + "), null);
        assertEqual("Empty string", MathSolver.solveMath(""), null);
        assertEqual("Non-math text", MathSolver.solveMath("hello world"), null);
    }

    static void testReverseSolver() {
        section("ReverseSolver");

        assertEqual("Reverse Mortar", ReverseSolver.solveReverse("Mortar"), "ratroM");
        assertEqual("Reverse Balloon", ReverseSolver.solveReverse("Balloon"), "noollaB");
        assertEqual("Reverse Room", ReverseSolver.solveReverse("Room"), "mooR");
        assertEqual("Reverse multi-word phrase", ReverseSolver.solveReverse("dap gniruocS"), "Scouring pad");
        assertEqual("Reverse quoted phrase", ReverseSolver.solveReverse("\"moor gniviL\""), "Living room");
        assertEqual("Reverse null", ReverseSolver.solveReverse(null), null);
        assertEqual("Reverse empty", ReverseSolver.solveReverse(""), null);
        assertEqual("Reverse whitespace only", ReverseSolver.solveReverse("   "), null);
        assertEqual("Reverse single quote", ReverseSolver.solveReverse("\""), null);
        assertEqual("Reverse empty quotes", ReverseSolver.solveReverse("\"\""), null);
        assertEqual("Reverse whitespace quotes", ReverseSolver.solveReverse("\"   \""), null);
    }

    static void testFillSolver() {
        section("FillSolver");

        // Real server patterns
        assertEqual("Fill __dybug => Ladybug", FillSolver.solveFill("__dybug"), "Ladybug");
        assertEqual("Fill _VD => DVD", FillSolver.solveFill("_VD"), "DVD");
        assertEqual("Fill D_D => DVD", FillSolver.solveFill("? D_D"), "DVD");
        assertEqual("Fill __rtar => Mortar", FillSolver.solveFill("? __rtar"), "Mortar");
        assertEqual("Fill E__e_vo_r => Endeavour", FillSolver.solveFill("? E__e_vo_r"), "Endeavour");
        assertEqual("Fill _m___tte => Omelette", FillSolver.solveFill("? _m___tte"), "Omelette");
        assertEqual("Fill multi-word _ile ca_i_et => File cabinet", FillSolver.solveFill("? _ile ca_i_et"), "File cabinet");
        assertEqual("Fill with non-matching underscore prefix token", FillSolver.solveFill("[Chat_Games] ? __dybug"), "Ladybug");
        assertEqual("Fill with speaker username prefix", FillSolver.solveFill("Player_One: ? __rtar"), "Mortar");

        // Edge cases that should return null
        assertEqual("Fill all underscores ___", FillSolver.solveFill("___"), null);
        assertEqual("Fill all underscores ______", FillSolver.solveFill("______"), null);
        assertEqual("Fill username with digits D_D123", FillSolver.solveFill("D_D123"), null);
        assertEqual("Fill speaker prefix only Box_:", FillSolver.solveFill("Box_: hello"), null);
        assertEqual("Fill no underscores", FillSolver.solveFill("Ladybug"), null);
        assertEqual("Fill null", FillSolver.solveFill(null), null);
    }

    static void testSortSolver() {
        section("TriviaSolver.solveSort (Unscramble)");

        assertEqual("Sort Amr => Arm", TriviaSolver.solveSort("Amr"), "Arm");
        assertEqual("Sort Hlil => Hill", TriviaSolver.solveSort("Hlil"), "Hill");
        assertEqual("Sort eaOnrg => Orange", TriviaSolver.solveSort("eaOnrg"), "Orange");
        assertEqual("Sort oDro => Door", TriviaSolver.solveSort("oDro"), "Door");
        assertEqual("Sort toneS => Stone (uppercase S hint)", TriviaSolver.solveSort("toneS"), "Stone");
        assertEqual("Sort rkBea => Brake (uppercase B hint)", TriviaSolver.solveSort("rkBea"), "Brake");
        assertEqual("Sort ceiD => Dice (uppercase D hint)", TriviaSolver.solveSort("ceiD"), "Dice");
        assertEqual("Sort alteMn => Mantle (uppercase M hint)", TriviaSolver.solveSort("alteMn"), "Mantle");
        assertEqual("Sort omySphyn => Symphony", TriviaSolver.solveSort("omySphyn"), "Symphony");
        assertEqual("Sort tpaerysT => Tapestry", TriviaSolver.solveSort("tpaerysT"), "Tapestry");
        assertEqual("Sort quoted \"Hlil\" => Hill", TriviaSolver.solveSort("\"Hlil\""), "Hill");
        assertEqual("Sort null", TriviaSolver.solveSort(null), null);
        assertEqual("Sort empty", TriviaSolver.solveSort(""), null);
        assertEqual("Sort empty quotes", TriviaSolver.solveSort("\"\""), null);
    }

    static void testTriviaSolver() {
        section("TriviaSolver.solveTrivia");

        assertEqual("Trivia UK capital", TriviaSolver.solveTrivia("What is the capital of the United Kingdom?"), "London");
        assertEqual("Trivia extra spaces", TriviaSolver.solveTrivia("What is   the capital of the   United Kingdom?"), "London");
        assertEqual("Trivia red panda", TriviaSolver.solveTrivia("What type of animal is the \"red panda\"?"), "Mammal");
        assertEqual("Trivia Don Quixote", TriviaSolver.solveTrivia("Who wrote \"Don Quixote\"?"), "Miguel de Cervantes");
        assertEqual("Trivia FIFA 2010", TriviaSolver.solveTrivia("Which country won the FIFA World Cup in 2010?"), "Spain");
        assertEqual("Trivia J.K. Rowling", TriviaSolver.solveTrivia("What is the name of the book series written by J.K. Rowling that follows the adventures of a young wizard?"), "Harry Potter");

        // False positive guards
        assertEqual("Trivia unrelated animal question", TriviaSolver.solveTrivia("What type of animal is the Komodo dragon?"), null);
        assertEqual("Trivia unrelated Who wrote question", TriviaSolver.solveTrivia("Who wrote the play Hamlet in 1600?"), null);
        assertEqual("Trivia too short", TriviaSolver.solveTrivia("short?"), null);
        assertEqual("Trivia null", TriviaSolver.solveTrivia(null), null);
    }

    static void testVariableSolver() {
        section("VariableSolver");

        // Standard 3-equation system
        List<String> lines = new ArrayList<>();
        lines.add("A + A + A = 15");
        lines.add("B + B + B = 9");
        lines.add("A + B + x = 12");
        assertEqual("Variable 5+3+x=12", VariableSolver.solveVariable(lines), "4");

        // Multiplication/subtraction/division in line 3
        assertEqual("Variable 5*3+x=20", VariableSolver.solveVariable(List.of("A + A + A = 15", "B + B + B = 9", "A * B + x = 20")), "5");
        assertEqual("Variable 5+3*x=17", VariableSolver.solveVariable(List.of("A + A + A = 15", "B + B + B = 9", "A + B * x = 17")), "4");
        assertEqual("Variable 12/3+x=10", VariableSolver.solveVariable(List.of("A + A + A = 36", "B + B + B = 9", "A / B + x = 10")), "6");
        assertEqual("Variable 5+3-x=2", VariableSolver.solveVariable(List.of("A + A + A = 15", "B + B + B = 9", "A + B - x = 2")), "6");
        assertEqual("Variable with stale buffer line", VariableSolver.solveVariable(List.of("Z + Z + Z = 99", "A + A + A = 15", "B + B + B = 9", "A + B + x = 12")), "4");

        // Additional edge cases
        assertEqual("Variable with non-equation chat noise", VariableSolver.solveVariable(List.of(
            "A + A + A = 15",
            "[me -> friend] bait = good",
            "B + B + B = 9",
            "A + B + x = 12"
        )), "4");
        assertEqual("Variable negative cube root", VariableSolver.solveVariable(List.of(
            "A * A * A = -27",
            "B + B + B = 9",
            "A + B + x = 10"
        )), "10");
        assertEqual("Variable 2-term third line A + x = 12", VariableSolver.solveVariable(List.of(
            "A + A + A = 15",
            "B + B + B = 9",
            "A + x = 12"
        )), "7");
        assertEqual("Variable middle unknown A + x + B = 12", VariableSolver.solveVariable(List.of(
            "A + A + A = 15",
            "B + B + B = 9",
            "A + x + B = 12"
        )), "4");
        assertEqual("Variable first unknown x + A + B = 12", VariableSolver.solveVariable(List.of(
            "A + A + A = 15",
            "B + B + B = 9",
            "x + A + B = 12"
        )), "4");
        assertEqual("Variable divide by zero guard", VariableSolver.solveVariable(List.of(
            "A + A + A = 15",
            "B + B + B = 0",
            "A + B * x = 12"
        )), null);

        // Edge: null / empty / insufficient lines
        assertEqual("Variable null", VariableSolver.solveVariable(null), null);
        assertEqual("Variable empty", VariableSolver.solveVariable(new ArrayList<>()), null);
        assertEqual("Variable only 2 lines", VariableSolver.solveVariable(List.of("A + A + A = 15", "B + B + B = 9")), null);
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
