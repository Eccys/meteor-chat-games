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

public class AutoChatGame extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSolvers = settings.createGroup("Solvers");

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

    private final Random random = new Random();
    private final List<String> messageBuffer = new ArrayList<>();
    private long lastBufferTime = 0;
    private String lastAnswerSent = "";
    private long lastAnswerTime = 0;

    public AutoChatGame() {
        super(ChatGamesAddon.CATEGORY, "auto-chat-game", "Automatically solves server chat games with customizable delay and humanized randomness.");
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (mc.player == null) return;
        String text = event.getMessage().getString();
        if (text == null || text.trim().isEmpty()) return;

        long now = System.currentTimeMillis();

        // Reset buffer if more than 3 seconds elapsed
        if (now - lastBufferTime > 3000) {
            messageBuffer.clear();
        }
        lastBufferTime = now;
        messageBuffer.add(text);

        // Process single line and buffer
        String answer = null;
        String gameType = null;

        // 1. TRIVIA
        if (solveTrivia.get() && text.contains("\"")) {
            int firstQuote = text.indexOf('"');
            int lastQuote = text.lastIndexOf('"');
            if (firstQuote != -1 && lastQuote > firstQuote) {
                String question = text.substring(firstQuote + 1, lastQuote).trim();
                if (question.length() > 5 && !question.contains("seconds to")) {
                    answer = TriviaSolver.solveTrivia(question);
                    if (answer != null) gameType = "Trivia";
                }
            }
        }

        // 2. SORT / UNSCRAMBLE
        if (answer == null && solveSort.get() && text.toLowerCase().contains("sort the word")) {
            int firstQuote = text.indexOf('"');
            int lastQuote = text.lastIndexOf('"');
            if (firstQuote != -1 && lastQuote > firstQuote) {
                String scrambled = text.substring(firstQuote + 1, lastQuote).trim();
                answer = TriviaSolver.solveSort(scrambled);
                if (answer != null) gameType = "Sort";
            }
        }

        // 3. REVERSE
        if (answer == null && solveReverse.get() && (text.toLowerCase().contains("backwards") || text.toLowerCase().contains("reverse"))) {
            int firstQuote = text.indexOf('"');
            int lastQuote = text.lastIndexOf('"');
            if (firstQuote != -1 && lastQuote > firstQuote) {
                String word = text.substring(firstQuote + 1, lastQuote).trim();
                answer = ReverseSolver.solveReverse(word);
                if (answer != null) gameType = "Reverse";
            }
        }

        // 4. MATH
        if (answer == null && solveMath.get() && text.toLowerCase().contains("calculate")) {
            int firstQuote = text.indexOf('"');
            int lastQuote = text.lastIndexOf('"');
            if (firstQuote != -1 && lastQuote > firstQuote) {
                String expr = text.substring(firstQuote + 1, lastQuote).trim();
                answer = MathSolver.solveMath(expr);
                if (answer != null) gameType = "Math";
            }
        }

        // 5. VARIABLE
        if (answer == null && solveVariable.get() && (text.contains("CHATGAMES") || text.contains("VARIABLE"))) {
            answer = VariableSolver.solveVariable(messageBuffer);
            if (answer != null) gameType = "Variable";
        }

        // 6. WRITE / TYPE
        if (answer == null && (text.toLowerCase().contains("type the word") || text.toLowerCase().contains("type the sentence"))) {
            int firstQuote = text.indexOf('"');
            int lastQuote = text.lastIndexOf('"');
            if (firstQuote != -1 && lastQuote > firstQuote) {
                answer = text.substring(firstQuote + 1, lastQuote).trim();
                if (answer != null) gameType = "Write";
            }
        }

        // Dispatch Answer
        if (answer != null && !answer.isEmpty()) {
            // Prevent duplicate spam within 5 seconds
            if (answer.equalsIgnoreCase(lastAnswerSent) && (now - lastAnswerTime) < 5000) {
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
