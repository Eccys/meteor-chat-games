package anticope.chatgames.modules;

import anticope.chatgames.ChatGamesAddon;
import anticope.chatgames.utils.*;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoChatGame extends Module {
    private static final Pattern SORT_PATTERN = Pattern.compile("(?:sort|unscramble)\\s+(?:the\\s+)?(?:word)?\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern REVERSE_PATTERN = Pattern.compile("(?:type|write|reverse)\\s+(?:the\\s+)?(?:word)?\\s*\"([^\"]+)\"\\s+backwards|(?:reverse)\\s+(?:the\\s+)?(?:word)?\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern MATH_PATTERN = Pattern.compile("\"(\\d+\\s*[+\\-*/xX]\\s*\\d+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern WRITE_PATTERN = Pattern.compile("(?:to\\s+)?(?:type|write)\\s+\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern FILL_PATTERN = Pattern.compile("([a-zA-Z_]{3,})", Pattern.CASE_INSENSITIVE);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSolvers = settings.createGroup("Solvers");
    private final SettingGroup sgDebug = settings.createGroup("Debug");

    // General Settings
    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Base delay in milliseconds before answering.")
        .defaultValue(1500)
        .min(0)
        .sliderMax(5000)
        .build()
    );

    private final Setting<Integer> randomness = sgGeneral.add(new IntSetting.Builder()
        .name("randomness")
        .description("Random variation in delay (±ms).")
        .defaultValue(300)
        .min(0)
        .sliderMax(2000)
        .build()
    );

    private final Setting<Boolean> autoSend = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-send")
        .description("Automatically send the answer to public chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> chatPrefix = sgGeneral.add(new StringSetting.Builder()
        .name("chat-prefix")
        .description("Optional prefix before answer (e.g. /chat ).")
        .defaultValue("")
        .build()
    );

    private final Setting<Boolean> feedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Log solved answers to Meteor client chat.")
        .defaultValue(true)
        .build()
    );

    // Solver Toggles
    private final Setting<Boolean> solveTrivia = sgSolvers.add(new BoolSetting.Builder()
        .name("solve-trivia")
        .description("Solve Trivia questions.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> solveMath = sgSolvers.add(new BoolSetting.Builder()
        .name("solve-math")
        .description("Solve Math equations.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> solveSort = sgSolvers.add(new BoolSetting.Builder()
        .name("solve-sort")
        .description("Solve Sort / Unscramble games.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> solveReverse = sgSolvers.add(new BoolSetting.Builder()
        .name("solve-reverse")
        .description("Solve Reverse word games.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> solveVariable = sgSolvers.add(new BoolSetting.Builder()
        .name("solve-variable")
        .description("Solve Variable equation systems.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> solveFill = sgSolvers.add(new BoolSetting.Builder()
        .name("solve-fill")
        .description("Solve Fill-in-the-blank games.")
        .defaultValue(true)
        .build()
    );

    // Debug Settings
    private final Setting<Boolean> debugMode = sgDebug.add(new BoolSetting.Builder()
        .name("debug-mode")
        .description("Log ALL incoming chat messages and solver attempts to Meteor chat and game log.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> debugRegex = sgDebug.add(new BoolSetting.Builder()
        .name("debug-regex")
        .description("Log regex match results for each solver pattern.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> dryRun = sgDebug.add(new BoolSetting.Builder()
        .name("dry-run")
        .description("When enabled, solve and log answers but do NOT send them to chat. Useful for testing.")
        .defaultValue(false)
        .build()
    );

    private final Random random = new Random();
    private final List<String> messageBuffer = new ArrayList<>();
    private long lastBufferTime = 0;
    private String lastAnswerSent = "";
    private long lastAnswerTime = 0;
    private String lastProcessedText = "";
    private long lastProcessedTime = 0;
    private boolean chatGameActive = false;
    private String currentGameType = "";

    public AutoChatGame() {
        super(ChatGamesAddon.CATEGORY, "auto-chat-game", "Automatically solves server chat games with customizable delay and humanized randomness.");
    }

    private void debug(String msg) {
        if (debugMode.get()) {
            ChatGamesAddon.LOG.info("[AutoChatGame:Debug] " + msg);
        }
    }

    private void debugRegex(String solver, String input, boolean matched, String result) {
        if (debugRegex.get()) {
            ChatGamesAddon.LOG.info("[AutoChatGame:Regex] " + solver + ": " + (matched ? "MATCHED" : "no match") + (result != null ? " => " + result : "") + " | input: " + input);
        }
    }

    private String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (mc.player == null) return;
        String text = event.getMessage().getString();
        if (text == null || text.trim().isEmpty()) return;

        // CRITICAL: Ignore our own messages to prevent infinite feedback loop
        if (text.contains("[Meteor]") || text.contains("[AutoChatGame]") || text.contains("[Debug]") || text.contains("[Regex]")) return;

        long now = System.currentTimeMillis();


        // De-duplicate rapid calls for identical text within 800ms
        if (text.equals(lastProcessedText) && (now - lastProcessedTime) < 800) {
            if (debugMode.get()) debug("§8[DEDUP] Skipped duplicate: " + truncate(text, 80));
            return;
        }
        lastProcessedText = text;
        lastProcessedTime = now;

        // Detect CHATGAMES header to activate tracking
        if (text.contains("CHATGAMES")) {
            if (text.contains("WINNER") || text.contains("TIME EXPIRED")) {
                debug("§6[GAME END] " + truncate(text, 80));
                chatGameActive = false;
                return;
            }
            // Game type header (e.g. "CHATGAMES  MATH", "CHATGAMES  FILL")
            chatGameActive = true;
            if (text.contains("MATH")) currentGameType = "MATH";
            else if (text.contains("FILL")) currentGameType = "FILL";
            else if (text.contains("WRITE")) currentGameType = "WRITE";
            else if (text.contains("SORT")) currentGameType = "SORT";
            else if (text.contains("REVERSE")) currentGameType = "REVERSE";
            else if (text.contains("TRIVIA")) currentGameType = "TRIVIA";
            else if (text.contains("VARIABLE")) currentGameType = "VARIABLE";
            else currentGameType = "UNKNOWN";
            debug("§b[GAME START] Type: §e" + currentGameType + " §7| header: " + truncate(text, 80));
            return;
        }

        // Ignore server outcome broadcasts & system noise
        if (text.contains("TIME EXPIRED") || text.contains("WINNER") ||
            text.contains("CORRECT ANSWER WAS") || text.contains("AWARDED A PRIZE") ||
            text.contains("FIRST TO RESPOND") || text.contains("Crates") || text.contains("WARPS")) {
            debug("§8[FILTERED] " + truncate(text, 80));
            return;
        }

        // Log every message in debug mode
        debug("§f[MSG] " + truncate(text, 100));

        // Reset buffer if more than 3 seconds elapsed
        if (now - lastBufferTime > 3000) {
            messageBuffer.clear();
        }
        lastBufferTime = now;
        messageBuffer.add(text);

        String answer = null;
        String gameType = null;

        // A. VARIABLE
        if (solveVariable.get() && (text.contains("=") && (text.contains("+") || text.contains("x")))) {
            String result = VariableSolver.solveVariable(messageBuffer);
            debugRegex("VARIABLE", text, result != null, result);
            if (result != null) { answer = result; gameType = "Variable"; }
        }

        // B. SORT / UNSCRAMBLE
        if (answer == null && solveSort.get()) {
            Matcher m = SORT_PATTERN.matcher(text);
            boolean found = m.find();
            if (found) {
                String scrambled = m.group(1);
                String result = TriviaSolver.solveSort(scrambled);
                debugRegex("SORT", text, result != null, result);
                if (result != null) { answer = result; gameType = "Sort"; }
            } else {
                debugRegex("SORT", text, false, null);
            }
        }

        // C. REVERSE
        if (answer == null && solveReverse.get()) {
            Matcher m = REVERSE_PATTERN.matcher(text);
            boolean found = m.find();
            if (found) {
                String word = m.group(1) != null ? m.group(1) : m.group(2);
                String result = ReverseSolver.solveReverse(word);
                debugRegex("REVERSE", text, result != null, result);
                if (result != null) { answer = result; gameType = "Reverse"; }
            } else {
                debugRegex("REVERSE", text, false, null);
            }
        }

        // D. MATH — match "12 + 54" anywhere in text
        if (answer == null && solveMath.get()) {
            Matcher m = MATH_PATTERN.matcher(text);
            boolean found = m.find();
            if (found) {
                String expr = m.group(1);
                String result = MathSolver.solveMath(expr);
                debugRegex("MATH", text, result != null, result);
                if (result != null) { answer = result; gameType = "Math"; }
            } else {
                debugRegex("MATH", text, false, null);
            }
        }

        // E. WRITE / TYPE — match `to write "Cheek"` or `type "word"`
        if (answer == null) {
            Matcher m = WRITE_PATTERN.matcher(text);
            boolean found = m.find();
            if (found) {
                String result = m.group(1).trim();
                debugRegex("WRITE", text, true, result);
                if (!result.isEmpty()) { answer = result; gameType = "Write"; }
            } else {
                debugRegex("WRITE", text, false, null);
            }
        }

        // F. FILL IN THE BLANK
        if (answer == null && solveFill.get() && text.contains("_")) {
            String result = FillSolver.solveFill(text);
            debugRegex("FILL", text, result != null, result);
            if (result != null) { answer = result; gameType = "Fill"; }
        }

        // G. TRIVIA
        if (answer == null && solveTrivia.get()) {
            if (text.contains("\"")) {
                int firstQuote = text.indexOf('"');
                int lastQuote = text.lastIndexOf('"');
                if (firstQuote != -1 && lastQuote > firstQuote) {
                    String question = text.substring(firstQuote + 1, lastQuote).trim();
                    if (question.length() >= 10 && !question.contains("seconds to") && !question.contains("calculate") && !question.contains("sort") && !question.contains("write")) {
                        String result = TriviaSolver.solveTrivia(question);
                        debugRegex("TRIVIA", question, result != null, result);
                        if (result != null) { answer = result; gameType = "Trivia"; }
                    } else {
                        debugRegex("TRIVIA", question, false, "§7(skipped: too short or contains solver keyword)");
                    }
                }
            }
        }

        // Dispatch Answer
        if (answer != null && !answer.isEmpty()) {
            // Prevent duplicate answer spam within 5 seconds
            if (answer.equalsIgnoreCase(lastAnswerSent) && (now - lastAnswerTime) < 5000) {
                debug("§8[DEDUP-ANS] Suppressed duplicate answer: " + answer);
                return;
            }

            final String finalAnswer = answer;
            final String finalType = gameType;

            // Calculate Delay
            int baseDelayMs = Math.max(0, delay.get());
            int randMs = randomness.get() > 0 ? (random.nextInt(randomness.get() * 2) - randomness.get()) : 0;
            int totalDelayMs = Math.max(0, baseDelayMs + randMs);

            lastAnswerSent = finalAnswer;
            lastAnswerTime = now;

            if (feedback.get()) {
                ChatUtils.info("[AutoChatGame] Solved " + finalType + ": §a" + finalAnswer + " §7(Delay: " + totalDelayMs + "ms)");
            }

            if (dryRun.get()) {
                ChatUtils.info("§e[DRY RUN] Would send: §f" + finalAnswer + " §7(not sent)");
                ChatGamesAddon.LOG.info("[AutoChatGame:DryRun] Would send: " + finalAnswer);
                return;
            }

            if (autoSend.get()) {
                MeteorExecutor.execute(() -> {
                    try {
                        Thread.sleep(totalDelayMs);
                    } catch (InterruptedException ignored) {}

                    mc.execute(() -> {
                        if (mc.player != null) {
                            String fullMessage = chatPrefix.get().trim().isEmpty()
                                ? finalAnswer
                                : chatPrefix.get().trim() + " " + finalAnswer;
                            ChatUtils.sendPlayerMsg(fullMessage);
                        }
                    });
                });
            }
        }
    }
}
