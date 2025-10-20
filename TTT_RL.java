import javax.swing.*;

// Import specific AWT classes to avoid java.awt.List clash
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.*;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class TTT_RL extends JFrame {

    // ---- GUI (board) ----
    private final JButton[] cells = new JButton[9];
    private final JLabel status = new JLabel("Human (X) vs RL (O). Your move.");
    private final JButton resetBtn = new JButton("Reset");
    private final JButton trainBtn = new JButton("Train…");
    private final JButton saveBtn  = new JButton("Save Q");
    private final JButton loadBtn  = new JButton("Load Q");

    // New controls: training mode + side + stepping
    private final JComboBox<String> trainModeCombo = new JComboBox<>(new String[]{
            "Self-Play (strong)",
            "Random X vs Agent O (legacy)"
    });
    private final JCheckBox playAsOCheck = new JCheckBox("Human plays O");
    private final JButton stepEpisodeBtn = new JButton("Step Episode");

    // Progress UI
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel progressLabel = new JLabel("Idle");
    private final JCheckBox liveOverlayDuringTrain = new JCheckBox("Update overlay during training", true);

    // ---- Q Overlay (3x3 labels for Q-values) ----
    private final JLabel overlayTitle = new JLabel("Q Overlay (state = current board)");
    private final JPanel qOverlayGrid = new JPanel(new GridLayout(3,3,4,4));
    private final JLabel[] qCells = new JLabel[9];
    private final JButton showQCurrentBtn = new JButton("Show Q (Current Board)");
    private final JButton showQEmptyBtn   = new JButton("Show Q (Empty Board)");
    private char[] overlayState = null; // null => current board; else a 9-char array state

    // ---- Game state ----
    private final char[] board = new char[9]; // 'X','O',' '
    private boolean gameOver = false;

    // Live play sides
    private char humanMark = 'X';
    private char aiMark    = 'O';
    private char currentPlayer = 'X';

    // RL Agent
    private final QLearningAgent agent = new QLearningAgent('O', 'X');

    public TTT_RL() {
        super("Tic Tac Toe — Q-Learning (Q Overlay + Step Episodes)");
        java.util.Arrays.fill(board, ' ');

        // ===== Top bar =====
        JPanel top = new JPanel(new BorderLayout(8,8));
        status.setBorder(BorderFactory.createEmptyBorder(6,10,6,10));
        top.add(status, BorderLayout.CENTER);

        JPanel leftControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        leftControls.add(new JLabel("Train Mode:"));
        leftControls.add(trainModeCombo);
        leftControls.add(playAsOCheck);
        leftControls.add(stepEpisodeBtn);
        top.add(leftControls, BorderLayout.WEST);

        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        rightControls.add(resetBtn);
        rightControls.add(trainBtn);
        rightControls.add(saveBtn);
        rightControls.add(loadBtn);
        top.add(rightControls, BorderLayout.EAST);

        // ===== Board grid =====
        JPanel grid = new JPanel(new GridLayout(3,3,6,6));
        grid.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        Font big = new Font(Font.SANS_SERIF, Font.BOLD, 42);
        for (int i = 0; i < 9; i++) {
            JButton b = new JButton("");
            b.setFont(big);
            b.setFocusPainted(false);
            b.setBackground(Color.WHITE);
            final int idx = i;
            b.addActionListener(evt -> humanMove(idx));
            cells[i] = b;
            grid.add(b);
        }

        // ===== Bottom: progress + overlay controls =====
        JPanel bottom = new JPanel(new BorderLayout(8,8));
        progressBar.setStringPainted(true);
        JPanel progressRow = new JPanel(new BorderLayout(8,8));
        progressRow.add(progressLabel, BorderLayout.WEST);
        progressRow.add(progressBar, BorderLayout.CENTER);
        progressRow.add(liveOverlayDuringTrain, BorderLayout.EAST);

        // Q overlay grid
        for (int i = 0; i < 9; i++) {
            JLabel ql = new JLabel("–", SwingConstants.CENTER);
            ql.setOpaque(true);
            ql.setBackground(new Color(245,245,245));
            ql.setBorder(BorderFactory.createLineBorder(new Color(220,220,220)));
            ql.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
            qCells[i] = ql;
            qOverlayGrid.add(ql);
        }
        JPanel overlayControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        overlayControls.add(showQCurrentBtn);
        overlayControls.add(showQEmptyBtn);

        JPanel overlayBlock = new JPanel(new BorderLayout(6,6));
        overlayBlock.setBorder(BorderFactory.createEmptyBorder(6,10,10,10));
        overlayBlock.add(overlayTitle, BorderLayout.NORTH);
        overlayBlock.add(qOverlayGrid, BorderLayout.CENTER);
        overlayBlock.add(overlayControls, BorderLayout.SOUTH);

        JPanel bottomStack = new JPanel(new BorderLayout(8,8));
        bottomStack.add(progressRow, BorderLayout.NORTH);
        bottomStack.add(overlayBlock, BorderLayout.CENTER);

        bottom.add(bottomStack, BorderLayout.CENTER);

        // ===== Frame layout =====
        setLayout(new BorderLayout());
        add(top, BorderLayout.NORTH);
        add(grid, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        // ===== Events =====
        resetBtn.addActionListener(e -> { resetGame(); maybeAgentAutoOpen(); });
        trainBtn.addActionListener(e -> promptAndStartTraining());
        saveBtn.addActionListener(e -> saveQ());
        loadBtn.addActionListener(e -> { loadQ(); updateStatusForTurn(); refreshOverlayForCurrentSelection(); });
        playAsOCheck.addActionListener(e -> {
            humanMark = playAsOCheck.isSelected() ? 'O' : 'X';
            aiMark    = (humanMark == 'X') ? 'O' : 'X';
            resetGame();
            maybeAgentAutoOpen();
            refreshOverlayForCurrentSelection();
        });
        stepEpisodeBtn.addActionListener(e -> stepOneEpisode());
        showQCurrentBtn.addActionListener(e -> { overlayState = null; overlayTitle.setText("Q Overlay (state = current board)"); refreshOverlayForCurrentSelection(); });
        showQEmptyBtn.addActionListener(e -> { overlayState = makeEmptyBoard(); overlayTitle.setText("Q Overlay (state = empty board)"); refreshOverlayForCurrentSelection(); });

        // ===== Finish window =====
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(640, 740);
        setLocationRelativeTo(null);
        setVisible(true);

        // Try load existing Q-table
        loadQ();

        // Persist on close
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { saveQ(); }
        });

        // Initialize overlay and status
        refreshOverlayForCurrentSelection();
        updateStatusForTurn();
    }

    // ========= Live Play =========
    private void humanMove(int idx) {
        if (gameOver || currentPlayer != humanMark) return;
        if (board[idx] != ' ') return;
        applyMove(idx, humanMark);
        if (checkEndAndReport()) return;
        agentMoveIfAITurn();
    }

    private void agentMoveIfAITurn() {
        if (gameOver || currentPlayer != aiMark) return;
        String state = stringify(board);
        int action = agent.chooseAction(state, legalActions(board), 0.0); // pure exploit
        applyMove(action, aiMark);
        checkEndAndReport();
    }

    private void applyMove(int idx, char p) {
        board[idx] = p;
        cells[idx].setText(String.valueOf(p));
        currentPlayer = (p == 'X') ? 'O' : 'X';
        updateStatusForTurn();
        // update Q tooltips for current board
        updateCellTooltipsForState(board);
    }

    private void updateStatusForTurn() {
        if (gameOver) return;
        String humanSide = String.valueOf(humanMark);
        String aiSide    = String.valueOf(aiMark);
        status.setText("Human (" + humanSide + ") vs RL (" + aiSide + "). " +
                (currentPlayer == humanMark ? "Your move." : "RL thinking..."));
    }

    private boolean checkEndAndReport() {
        Character winner = winnerOf(board);
        if (winner != null) {
            gameOver = true;
            boolean humanWon = (winner == humanMark);
            status.setText((humanWon ? "Human (" : "RL (") + winner + ") wins!");
            highlightWin(winner);
            return true;
        }
        if (isDraw(board)) {
            gameOver = true;
            status.setText("Draw.");
            return true;
        }
        return false;
    }

    private void highlightWin(char p) {
        for (int[] L : lines()) {
            if (board[L[0]] == p && board[L[1]] == p && board[L[2]] == p) {
                for (int i : L) cells[i].setBackground(new Color(220,255,220));
                break;
            }
        }
    }

    private void resetGame() {
        java.util.Arrays.fill(board, ' ');
        for (JButton b : cells) { b.setText(""); b.setBackground(Color.WHITE); b.setToolTipText(null); }
        currentPlayer = 'X';
        gameOver = false;
        updateStatusForTurn();
        refreshOverlayForCurrentSelection();
    }

    private void maybeAgentAutoOpen() {
        if (!gameOver && humanMark == 'O' && currentPlayer == 'X') {
            agentMoveIfAITurn();
        }
    }

    // ========= Training =========
    private void promptAndStartTraining() {
        String input = JOptionPane.showInputDialog(this, "Episodes to train:", "50000");
        if (input == null) return;
        int episodes;
        try { episodes = Math.max(1, Integer.parseInt(input.trim())); }
        catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Please enter a valid integer.", "Invalid Input", JOptionPane.WARNING_MESSAGE);
            return;
        }
        startTrainingWorker(episodes);
    }

    private void setTrainingUIEnabled(boolean enabled) {
        trainBtn.setEnabled(enabled);
        resetBtn.setEnabled(enabled);
        saveBtn.setEnabled(enabled);
        loadBtn.setEnabled(enabled);
        trainModeCombo.setEnabled(enabled);
        playAsOCheck.setEnabled(enabled);
        stepEpisodeBtn.setEnabled(enabled);
        showQCurrentBtn.setEnabled(enabled);
        showQEmptyBtn.setEnabled(enabled);
        for (JButton b : cells) b.setEnabled(enabled);
    }

    private void startTrainingWorker(int episodes) {
        setTrainingUIEnabled(false);
        progressBar.setValue(0);
        progressLabel.setText("Training… 0/" + episodes);
        status.setText("Training in progress. Please wait…");

        final String selectedMode = (String) trainModeCombo.getSelectedItem();

        SwingWorker<Void, Integer> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                final int reportEvery = Math.max(1, episodes / 200);
                for (int i = 1; i <= episodes; i++) {
                    if ("Self-Play (strong)".equals(selectedMode)) {
                        agent.trainSelfPlaySmartOneEpisode();
                    } else {
                        agent.trainOneEpisode();
                    }
                    if (i % reportEvery == 0 || i == episodes) {
                        int percent = (int) Math.round(i * 100.0 / episodes);
                        setProgress(percent);
                        publish(i);
                    }
                }
                return null;
            }

            @Override
            protected void process(java.util.List<Integer> chunks) {
                int done = chunks.get(chunks.size() - 1);
                progressLabel.setText("Training… " + done + "/" + episodes);

                // Live overlay update (either current board or empty board)
                if (liveOverlayDuringTrain.isSelected()) {
                    refreshOverlayForCurrentSelection();
                }
            }

            @Override
            protected void done() {
                setTrainingUIEnabled(true);
                progressLabel.setText("Done (" + episodes + " episodes).");
                status.setText("Training complete.");
                resetGame();
                maybeAgentAutoOpen();
                JOptionPane.showMessageDialog(TTT_RL.this, "Training complete: " + episodes + " episodes.");
            }
        };

        worker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                int p = (Integer) evt.getNewValue();
                progressBar.setValue(p);
            }
        });

        worker.execute();
    }

    /** Run exactly ONE episode in the selected training mode (blocks briefly). */
    private void stepOneEpisode() {
        String mode = (String) trainModeCombo.getSelectedItem();
        if ("Self-Play (strong)".equals(mode)) {
            agent.trainSelfPlaySmartOneEpisode();
        } else {
            agent.trainOneEpisode();
        }
        refreshOverlayForCurrentSelection();
        JOptionPane.showMessageDialog(this, "Stepped 1 episode (" + mode + ").");
    }

    private void saveQ() {
        try {
            agent.saveTo("ttt_qtable.ser");
            JOptionPane.showMessageDialog(this, "Q-table saved.");
        } catch (Exception ex) {
            System.err.println("Save failed: " + ex.getMessage());
        }
    }

    private void loadQ() {
        try {
            agent.loadFrom("ttt_qtable.ser");
            status.setText("Loaded Q-table.");
        } catch (Exception ignored) {}
    }

    // ========= Q Overlay helpers =========
    private void refreshOverlayForCurrentSelection() {
        char[] state = (overlayState == null) ? board : overlayState;
        updateQOverlayForState(state);
        updateCellTooltipsForState(board); // tooltips always reflect current board
    }

    private void updateQOverlayForState(char[] state) {
        String s = stringify(state);
        double[] row = agent.peekQRow(s); // may be null if unseen
        for (int i = 0; i < 9; i++) {
            String txt;
            if (row == null) {
                txt = "–";
            } else {
                if (state[i] == ' ') {
                    double v = row[i];
                    if (v == Double.NEGATIVE_INFINITY) txt = "–";
                    else txt = formatQ(v);
                } else {
                    txt = "█"; // occupied
                }
            }
            qCells[i].setText(txt);
        }
    }

    private void updateCellTooltipsForState(char[] state) {
        double[] row = agent.peekQRow(stringify(state));
        for (int i = 0; i < 9; i++) {
            if (row == null) { cells[i].setToolTipText(null); continue; }
            if (state[i] == ' ') {
                double v = row[i];
                if (v == Double.NEGATIVE_INFINITY) cells[i].setToolTipText(null);
                else cells[i].setToolTipText("Q=" + formatQ(v));
            } else {
                cells[i].setToolTipText(null);
            }
        }
    }

    private static String formatQ(double v) {
        // compact 3 sig figs, centered around 0
        return String.format("%+.3f", v);
    }

    private static char[] makeEmptyBoard() {
        char[] b = new char[9];
        java.util.Arrays.fill(b, ' ');
        return b;
    }

    // ========= Static game helpers =========
    private static boolean isDraw(char[] b) {
        return winnerOf(b) == null && new String(b).indexOf(' ') == -1;
    }
    private static Character winnerOf(char[] b) {
        for (int[] L : lines()) {
            if (b[L[0]] != ' ' && b[L[0]] == b[L[1]] && b[L[1]] == b[L[2]]) return b[L[0]];
        }
        return null;
    }
    private static int[][] lines() {
        return new int[][]{
                {0,1,2},{3,4,5},{6,7,8},
                {0,3,6},{1,4,7},{2,5,8},
                {0,4,8},{2,4,6}
        };
    }
    private static java.util.List<Integer> legalActions(char[] b) {
        java.util.List<Integer> a = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) if (b[i] == ' ') a.add(i);
        return a;
    }
    private static String stringify(char[] b) { return new String(b); }

    // ========= Q-Learning Agent =========
    static class QLearningAgent implements Serializable {
        private static final long serialVersionUID = 1L;

        private final char me;
        private final char opp;
        private double alpha = 0.5;
        private double gamma = 0.9;
        private double epsilon = 0.2;

        private final Map<String, double[]> q = new HashMap<>();
        private final Random rnd = new Random();

        QLearningAgent(char me, char opp) { this.me = me; this.opp = opp; }

        int chooseAction(String state, java.util.List<Integer> legal, double epsilonOverride) {
            ensureState(state);
            double eps = (epsilonOverride >= 0) ? epsilonOverride : epsilon;
            if (!legal.isEmpty() && rnd.nextDouble() < eps) {
                return legal.get(rnd.nextInt(legal.size()));
            }
            double[] row = q.get(state);
            int best = legal.get(0);
            double bestQ = -1e9;
            for (int a : legal) if (row[a] > bestQ) { bestQ = row[a]; best = a; }
            return best;
        }

        /** Legacy trainer: Random X vs Agent O */
        void trainOneEpisode() {
            char[] b = new char[9];
            java.util.Arrays.fill(b, ' ');
            char cur = 'X';
            String sO = null; Integer aO = null;

            while (true) {
                if (cur == 'X') {
                    java.util.List<Integer> legal = legalActions(b);
                    if (legal.isEmpty()) break;
                    int a = legal.get(rnd.nextInt(legal.size()));
                    b[a] = 'X'; cur = 'O';
                } else {
                    java.util.List<Integer> legal = legalActions(b);
                    if (legal.isEmpty()) break;
                    String s = stringify(b);
                    int a = chooseAction(s, legal, -1);
                    b[a] = 'O';
                    sO = s; aO = a; cur = 'X';
                }
                Character w = winnerOf(b);
                if (w != null || isDraw(b)) {
                    double r = (w == null) ? 0.0 : (w == 'O' ? +1.0 : -1.0);
                    if (sO != null && aO != null) updateQTerminal(sO, aO, r);
                    break;
                }
                if (sO != null && aO != null && cur == 'X') {
                    String sPrime = stringify(b);
                    updateQ(sO, aO, 0.0, sPrime);
                    sO = null; aO = null;
                }
            }
            epsilon = Math.max(0.05, epsilon * 0.99995);
        }

        /** Strong trainer: Self-play with one shared Q-table (X and O). */
        void trainSelfPlaySmartOneEpisode() {
            char[] b = new char[9];
            java.util.Arrays.fill(b, ' ');
            char cur = 'X';
            String sPrevX = null, sPrevO = null;
            Integer aPrevX = null, aPrevO = null;

            while (true) {
                java.util.List<Integer> legal = legalActions(b);
                if (legal.isEmpty()) break;

                String s = stringify(b);
                int a = chooseAction(s, legal, -1);
                b[a] = cur;

                Character w = winnerOf(b);
                boolean terminal = (w != null) || isDraw(b);

                if (cur == 'X') {
                    if (sPrevX != null && aPrevX != null && !terminal) {
                        updateQ(sPrevX, aPrevX, 0.0, stringify(b));
                    }
                    sPrevX = s; aPrevX = a;
                } else {
                    if (sPrevO != null && aPrevO != null && !terminal) {
                        updateQ(sPrevO, aPrevO, 0.0, stringify(b));
                    }
                    sPrevO = s; aPrevO = a;
                }

                if (terminal) {
                    double rX = 0.0, rO = 0.0;
                    if (w != null) {
                        if (w == 'X') { rX = +1.0; rO = -1.0; } else { rX = -1.0; rO = +1.0; }
                    }
                    if (sPrevX != null && aPrevX != null) updateQTerminal(sPrevX, aPrevX, rX);
                    if (sPrevO != null && aPrevO != null) updateQTerminal(sPrevO, aPrevO, rO);
                    break;
                }
                cur = (cur == 'X') ? 'O' : 'X';
            }
            epsilon = Math.max(0.05, epsilon * 0.99995);
        }

        // ---- Q-learning updates ----
        private void updateQ(String s, int a, double r, String sPrime) {
            ensureState(s);
            ensureState(sPrime);
            double[] rowS = q.get(s);
            double[] rowSp = q.get(sPrime);
            double maxNext = maxOverLegal(rowSp, sPrime);
            double td = r + gamma * maxNext - rowS[a];
            rowS[a] += alpha * td;
        }
        private void updateQTerminal(String s, int a, double r) {
            ensureState(s);
            double[] row = q.get(s);
            double td = r - row[a];
            row[a] += alpha * td;
        }

        // ---- Q-table helpers ----
        private void ensureState(String s) {
            if (!q.containsKey(s)) {
                double[] row = new double[9];
                java.util.Arrays.fill(row, Double.NEGATIVE_INFINITY);
                char[] b = s.toCharArray();
                for (int i = 0; i < 9; i++) if (b[i] == ' ') row[i] = 0.0;
                q.put(s, row);
            }
        }
        private double maxOverLegal(double[] row, String sPrime) {
            double best = -1e9;
            char[] b = sPrime.toCharArray();
            for (int i = 0; i < 9; i++) if (b[i] == ' ') best = Math.max(best, row[i]);
            if (best == -1e9) best = 0.0; // no legal actions (terminal)
            return best;
        }

        // PUBLIC: read-only access for overlays / tooltips
        double[] peekQRow(String state) {
            if (!q.containsKey(state)) return null;
            return q.get(state).clone();
        }

        // Persistence
        void saveTo(String path) throws IOException {
            try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(path))) {
                out.writeObject(this);
            }
        }
        void loadFrom(String path) throws IOException, ClassNotFoundException {
            File f = new File(path);
            if (!f.exists()) return;
            try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(path))) {
                QLearningAgent loaded = (QLearningAgent) in.readObject();
                this.q.clear();
                this.q.putAll(loaded.q);
                this.alpha = loaded.alpha;
                this.gamma = loaded.gamma;
                this.epsilon = loaded.epsilon;
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
            new TTT_RL();
        });
    }
}
