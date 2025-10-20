import javax.swing.*;

// Import only the AWT classes we actually need (avoid java.awt.* to prevent List clash)
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

/**
 * TTT_RL: Tic-Tac-Toe with a tabular Q-learning agent and GUI.
 * - Training progress bar + percentage
 * - Training mode selector: Self-Play (strong) OR Random X vs Agent O (legacy)
 * - "Human plays O" toggle (lets AI start as X)
 */
public class TTT_RL extends JFrame {

    // ---- GUI ----
    private final JButton[] cells = new JButton[9];
    private final JLabel status = new JLabel("Human (X) vs RL (O). Your move.");
    private final JButton resetBtn = new JButton("Reset");
    private final JButton trainBtn = new JButton("Train…");
    private final JButton saveBtn  = new JButton("Save Q");
    private final JButton loadBtn  = new JButton("Load Q");

    // New controls
    private final JComboBox<String> trainModeCombo = new JComboBox<>(new String[]{
            "Self-Play (strong)",
            "Random X vs Agent O (legacy)"
    });
    private final JCheckBox playAsOCheck = new JCheckBox("Human plays O");

    // Progress UI
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel progressLabel = new JLabel("Idle");

    // ---- Game state ----
    private final char[] board = new char[9]; // 'X','O',' ' (space)
    private boolean gameOver = false;

    // Who is who for LIVE PLAY (not training)
    private char humanMark = 'X';
    private char aiMark    = 'O';
    private char currentPlayer = 'X';   // X always starts the actual game

    // ---- RL Agent (Q-table lives here) ----
    private final QLearningAgent agent = new QLearningAgent('O', 'X');

    public TTT_RL() {
        super("Tic Tac Toe — Q-Learning (+ Modes & Sides)");
        java.util.Arrays.fill(board, ' ');

        // Top bar: status + control buttons + mode/side controls
        JPanel top = new JPanel(new BorderLayout(8,8));
        status.setBorder(BorderFactory.createEmptyBorder(6,10,6,10));
        top.add(status, BorderLayout.CENTER);

        JPanel controlsLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        controlsLeft.add(new JLabel("Train Mode:"));
        controlsLeft.add(trainModeCombo);
        controlsLeft.add(playAsOCheck);
        top.add(controlsLeft, BorderLayout.WEST);

        JPanel controlsRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        controlsRight.add(resetBtn);
        controlsRight.add(trainBtn);
        controlsRight.add(saveBtn);
        controlsRight.add(loadBtn);
        top.add(controlsRight, BorderLayout.EAST);

        // Events
        resetBtn.addActionListener(e -> {
            resetGame();
            // If human plays O, AI (X) opens
            maybeAgentAutoOpen();
        });
        trainBtn.addActionListener(e -> promptAndStartTraining());
        saveBtn.addActionListener(e -> saveQ());
        loadBtn.addActionListener(e -> {
            loadQ();
            // keep status consistent with current side
            updateStatusForTurn();
        });
        playAsOCheck.addActionListener(e -> {
            humanMark = playAsOCheck.isSelected() ? 'O' : 'X';
            aiMark    = (humanMark == 'X') ? 'O' : 'X';
            resetGame();
            maybeAgentAutoOpen();
        });

        // Board grid
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

        // Bottom: progress bar + label
        JPanel bottom = new JPanel(new BorderLayout(8,8));
        progressBar.setStringPainted(true);
        bottom.setBorder(BorderFactory.createEmptyBorder(6,10,10,10));
        bottom.add(progressLabel, BorderLayout.WEST);
        bottom.add(progressBar, BorderLayout.CENTER);

        // Frame layout
        setLayout(new BorderLayout());
        add(top, BorderLayout.NORTH);
        add(grid, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(520, 560);
        setLocationRelativeTo(null);
        setVisible(true);

        // Try loading existing Q-table silently
        loadQ();
        // Persist Q on window close
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { saveQ(); }
        });

        // Initial side + opening move if needed
        humanMark = 'X';
        aiMark    = 'O';
        updateStatusForTurn();
    }

    /* ====== Human turn (LIVE PLAY) ====== */
    private void humanMove(int idx) {
        if (gameOver || currentPlayer != humanMark) return;
        if (board[idx] != ' ') return;

        applyMove(idx, humanMark);
        if (checkEndAndReport()) return;

        // Agent replies if it's AI's turn now
        agentMoveIfAITurn();
    }

    /* ====== Agent turn (LIVE PLAY, ε=0 exploit) ====== */
    private void agentMoveIfAITurn() {
        if (gameOver || currentPlayer != aiMark) return;

        String state = stringify(board);
        int action = agent.chooseAction(state, legalActions(board), /*epsilon=*/0.0); // exploit only
        applyMove(action, aiMark);
        checkEndAndReport();
    }

    /* Apply move to state + UI */
    private void applyMove(int idx, char p) {
        board[idx] = p;
        cells[idx].setText(String.valueOf(p));
        currentPlayer = (p == 'X') ? 'O' : 'X';
        updateStatusForTurn();
    }

    private void updateStatusForTurn() {
        if (gameOver) return;
        String humanSide = (humanMark == 'X') ? "X" : "O";
        String aiSide    = (aiMark == 'X') ? "X" : "O";
        if (currentPlayer == humanMark) {
            status.setText("Human (" + humanSide + ") vs RL (" + aiSide + "). Your move.");
        } else {
            status.setText("Human (" + humanSide + ") vs RL (" + aiSide + "). RL thinking...");
        }
    }

    /* Win/draw detection + status UI */
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
        int[][] lines = lines();
        for (int[] L : lines) {
            if (board[L[0]] == p && board[L[1]] == p && board[L[2]] == p) {
                for (int i : L) cells[i].setBackground(new Color(220,255,220));
                break;
            }
        }
    }

    private void resetGame() {
        java.util.Arrays.fill(board, ' ');
        for (JButton b : cells) { b.setText(""); b.setBackground(Color.WHITE); }
        currentPlayer = 'X';          // X always opens
        gameOver = false;
        updateStatusForTurn();
    }

    private void maybeAgentAutoOpen() {
        // If human is O, AI is X and should open with one move
        if (!gameOver && humanMark == 'O' && currentPlayer == 'X') {
            agentMoveIfAITurn();
        }
    }

    /* ====== Training (non-blocking with progress bar) ====== */
    private void promptAndStartTraining() {
        String input = JOptionPane.showInputDialog(this, "Episodes to train:", "50000");
        if (input == null) return; // cancelled
        int episodes;
        try {
            episodes = Math.max(1, Integer.parseInt(input.trim()));
        } catch (NumberFormatException ex) {
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
        for (JButton b : cells) b.setEnabled(enabled); // prevent clicks during training
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
                // Train in small batches to keep UI responsive
                final int reportEvery = Math.max(1, episodes / 200); // ~200 progress updates
                for (int i = 1; i <= episodes; i++) {

                    if ("Self-Play (strong)".equals(selectedMode)) {
                        agent.trainSelfPlaySmartOneEpisode();
                    } else {
                        agent.trainOneEpisode(); // Random X vs Agent O (legacy)
                    }

                    if (i % reportEvery == 0 || i == episodes) {
                        int percent = (int) Math.round(i * 100.0 / episodes);
                        setProgress(percent);       // updates progressBar because we bind it below
                        publish(i);                 // send current episode count for label
                    }
                }
                return null;
            }

            @Override
            protected void process(java.util.List<Integer> chunks) {
                int done = chunks.get(chunks.size() - 1);
                progressLabel.setText("Training… " + done + "/" + episodes);
            }

            @Override
            protected void done() {
                setTrainingUIEnabled(true);
                progressLabel.setText("Done (" + episodes + " episodes).");
                status.setText("Training complete.");
                resetGame();            // start fresh with learned policy
                maybeAgentAutoOpen();   // if AI is X, it will open
                JOptionPane.showMessageDialog(TTT_RL.this, "Training complete: " + episodes + " episodes.");
            }
        };

        // Bind progress property to the progress bar
        worker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                int p = (Integer) evt.getNewValue();
                progressBar.setValue(p);
            }
        });

        worker.execute();
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

    /* ====== Static helpers for game rules ====== */
    private static boolean isDraw(char[] b) {
        return winnerOf(b) == null && new String(b).indexOf(' ') == -1;
    }

    private static Character winnerOf(char[] b) {
        for (int[] L : lines()) {
            if (b[L[0]] != ' ' && b[L[0]] == b[L[1]] && b[L[1]] == b[L[2]]) {
                return b[L[0]];
            }
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

    private static String stringify(char[] b) {
        return new String(b);
    }

    /* ====== Q-Learning Agent ====== */
    static class QLearningAgent implements Serializable {
        private static final long serialVersionUID = 1L;

        // 'me' & 'opp' are kept for historical usage (human-play context),
        // training methods below ignore these and learn both sides.
        private final char me;
        private final char opp;
        private double alpha = 0.5;
        private double gamma = 0.9;
        private double epsilon = 0.2;

        private final Map<String, double[]> q = new HashMap<>();
        private final Random rnd = new Random();

        QLearningAgent(char me, char opp) {
            this.me = me;
            this.opp = opp;
        }

        int chooseAction(String state, java.util.List<Integer> legal, double epsilonOverride) {
            ensureState(state);
            double eps = (epsilonOverride >= 0) ? epsilonOverride : epsilon;
            if (!legal.isEmpty() && rnd.nextDouble() < eps) {
                return legal.get(rnd.nextInt(legal.size()));
            }
            double[] row = q.get(state);
            int best = legal.get(0);
            double bestQ = -1e9;
            for (int a : legal) {
                if (row[a] > bestQ) { bestQ = row[a]; best = a; }
            }
            return best;
        }

        /** Legacy trainer: random X vs agent as O (kept for comparison). */
        void trainOneEpisode() {
            char[] b = new char[9];
            java.util.Arrays.fill(b, ' ');
            char cur = 'X';

            String sO = null;
            Integer aO = null;

            while (true) {
                if (cur == 'X') {
                    // Random opponent move
                    java.util.List<Integer> legal = legalActions(b);
                    if (legal.isEmpty()) break;
                    int a = legal.get(rnd.nextInt(legal.size()));
                    b[a] = 'X';
                    cur = 'O';
                } else { // cur == 'O' (the agent)
                    java.util.List<Integer> legal = legalActions(b);
                    if (legal.isEmpty()) break;

                    String s = stringify(b);
                    int a = chooseAction(s, legal, -1); // use agent.epsilon
                    b[a] = 'O';

                    sO = s;
                    aO = a;

                    cur = 'X';
                }

                Character w = winnerOf(b);
                if (w != null || isDraw(b)) {
                    double r;
                    if (w == null) r = 0.0;
                    else if (w == 'O') r = +1.0;
                    else r = -1.0;

                    if (sO != null && aO != null) {
                        updateQTerminal(sO, aO, r);
                    }
                    break;
                }

                // After our move (now X's turn) -> intermediate update
                if (sO != null && aO != null && cur == 'X') {
                    String sPrime = stringify(b);
                    updateQ(sO, aO, 0.0, sPrime);
                    sO = null; aO = null;
                }
            }

            // Mild epsilon decay each episode for convergence
            epsilon = Math.max(0.05, epsilon * 0.99995);
        }

        /** Self-play trainer: SAME Q-table plays both X and O. */
        void trainSelfPlaySmartOneEpisode() {
            char[] b = new char[9];
            java.util.Arrays.fill(b, ' ');
            char cur = 'X';

            // Track the last (state, action) for EACH side separately
            String sPrevX = null, sPrevO = null;
            Integer aPrevX = null, aPrevO = null;

            while (true) {
                java.util.List<Integer> legal = legalActions(b);
                if (legal.isEmpty()) break;

                String s = stringify(b);
                int a = chooseAction(s, legal, -1); // ε-greedy using this.epsilon
                b[a] = cur;

                // After the move, check terminal
                Character w = winnerOf(b);
                boolean terminal = (w != null) || isDraw(b);

                // Mover's intermediate bootstrap update
                if (cur == 'X') {
                    if (sPrevX != null && aPrevX != null && !terminal) {
                        String sPrime = stringify(b);
                        updateQ(sPrevX, aPrevX, 0.0, sPrime);
                    }
                    sPrevX = s; aPrevX = a;
                } else { // cur == 'O'
                    if (sPrevO != null && aPrevO != null && !terminal) {
                        String sPrime = stringify(b);
                        updateQ(sPrevO, aPrevO, 0.0, sPrime);
                    }
                    sPrevO = s; aPrevO = a;
                }

                if (terminal) {
                    // Terminal rewards from each player's perspective
                    double rX = 0.0, rO = 0.0;
                    if (w != null) {
                        if (w == 'X') { rX = +1.0; rO = -1.0; }
                        else          { rX = -1.0; rO = +1.0; }
                    } // draw stays 0/0

                    if (sPrevX != null && aPrevX != null) updateQTerminal(sPrevX, aPrevX, rX);
                    if (sPrevO != null && aPrevO != null) updateQTerminal(sPrevO, aPrevO, rO);
                    break;
                }

                // Switch player
                cur = (cur == 'X') ? 'O' : 'X';
            }

            // Mild epsilon decay
            epsilon = Math.max(0.05, epsilon * 0.99995);
        }

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
            for (int i = 0; i < 9; i++) {
                if (b[i] == ' ') best = Math.max(best, row[i]);
            }
            if (best == -1e9) best = 0.0;
            return best;
        }

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
