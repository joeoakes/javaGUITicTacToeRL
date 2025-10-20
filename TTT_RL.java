import javax.swing.*;

// Import only the AWT classes actually needed (no java.awt.*)
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.event.*;

// Import Java utility classes individually
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * TTT_RL: Tic-Tac-Toe with a tabular Q-learning agent (O) vs human (X).
 * - Click cells to play as X.
 * - "Train 50k" runs 50,000 self-play episodes (no UI).
 * - "Reset" clears the current game.
 * - Q-table persisted to ttt_qtable.ser (auto-save on exit).
 */
public class TTT_RL extends JFrame {

    // ---- GUI ----
    private final JButton[] cells = new JButton[9];
    private final JLabel status = new JLabel("Human (X) vs RL (O). Your move.");
    private final JButton resetBtn = new JButton("Reset");
    private final JButton trainBtn = new JButton("Train 50k");
    private final JButton saveBtn  = new JButton("Save Q");
    private final JButton loadBtn  = new JButton("Load Q");

    // ---- Game state ----
    private char[] board = new char[9]; // 'X','O',' ' (space)
    private boolean gameOver = false;
    private char currentPlayer = 'X';   // Human starts

    // ---- RL Agent (plays 'O') ----
    private final QLearningAgent agent = new QLearningAgent('O', 'X');

    public TTT_RL() {
        super("Tic Tac Toe — Q-Learning");
        java.util.Arrays.fill(board, ' ');

        // Top bar
        JPanel top = new JPanel(new BorderLayout(8,8));
        status.setBorder(BorderFactory.createEmptyBorder(6,10,6,10));
        top.add(status, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        buttons.add(resetBtn);
        buttons.add(trainBtn);
        buttons.add(saveBtn);
        buttons.add(loadBtn);
        top.add(buttons, BorderLayout.EAST);

        resetBtn.addActionListener(e -> resetGame());
        trainBtn.addActionListener(e -> trainAndToast());
        saveBtn.addActionListener(e -> saveQ());
        loadBtn.addActionListener(e -> loadQ());

        // Grid
        JPanel grid = new JPanel(new java.awt.GridLayout(3,3,6,6));
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

        setLayout(new BorderLayout());
        add(top, BorderLayout.NORTH);
        add(grid, BorderLayout.CENTER);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(420, 500);
        setLocationRelativeTo(null);
        setVisible(true);

        // Try loading existing Q-table
        loadQ(); // ignore if file absent
        // On close, persist learned Q
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { saveQ(); }
        });
    }

    /* ====== Human turn ====== */
    private void humanMove(int idx) {
        if (gameOver || currentPlayer != 'X') return;
        if (board[idx] != ' ') return;

        applyMove(idx, 'X');
        if (checkEndAndReport()) return;

        // Agent (O) replies immediately
        agentMove();
    }

    /* ====== Agent turn (ε-greedy on the current board) ====== */
    private void agentMove() {
        if (gameOver || currentPlayer != 'O') return;

        String state = stringify(board);
        int action = agent.chooseAction(state, legalActions(board), /*epsilon=*/0.0); // exploit at play-time
        applyMove(action, 'O');
        checkEndAndReport();
    }

    /* Apply move to state + UI */
    private void applyMove(int idx, char p) {
        board[idx] = p;
        cells[idx].setText(String.valueOf(p));
        currentPlayer = (p == 'X') ? 'O' : 'X';
    }

    /* Win/draw detection + status UI */
    private boolean checkEndAndReport() {
        Character winner = winnerOf(board);
        if (winner != null) {
            gameOver = true;
            status.setText((winner == 'X' ? "Human (X)" : "RL (O)") + " wins!");
            highlightWin(winner);
            return true;
        }
        if (isDraw(board)) {
            gameOver = true;
            status.setText("Draw.");
            return true;
        }
        status.setText(currentPlayer == 'X' ? "Your move (X)" : "RL thinking (O)...");
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
        currentPlayer = 'X';
        gameOver = false;
        status.setText("Human (X) vs RL (O). Your move.");
    }

    /* ====== Training (self-play) ====== */
    private void trainAndToast() {
        int episodes = 50_000;
        agent.trainSelfPlay(episodes);
        JOptionPane.showMessageDialog(this, "Training complete: " + episodes + " episodes.");
        resetGame();
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
            status.setText("Loaded Q-table. Human (X) vs RL (O).");
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

        void trainSelfPlay(int episodes) {
            for (int ep = 0; ep < episodes; ep++) {
                char[] b = new char[9];
                java.util.Arrays.fill(b, ' ');
                char cur = 'X';

                String s = stringify(b);
                Integer aO = null;
                String sO = null;

                while (true) {
                    if (cur == 'X') {
                        java.util.List<Integer> legal = legalActions(b);
                        if (legal.isEmpty()) break;
                        int a = legal.get(rnd.nextInt(legal.size()));
                        b[a] = 'X';
                        cur = 'O';
                    } else {
                        java.util.List<Integer> legal = legalActions(b);
                        if (legal.isEmpty()) break;

                        s = stringify(b);
                        int a = chooseAction(s, legal, -1);
                        b[a] = me;

                        sO = s;
                        aO = a;
                        cur = 'X';
                    }

                    Character w = winnerOf(b);
                    if (w != null || isDraw(b)) {
                        double r;
                        if (w == null) r = 0.0;
                        else if (w == me) r = +1.0;
                        else r = -1.0;

                        if (sO != null && aO != null) {
                            updateQTerminal(sO, aO, r);
                        }
                        break;
                    }

                    if (sO != null && aO != null && cur == 'X') {
                        String sPrime = stringify(b);
                        updateQ(sO, aO, 0.0, sPrime);
                        sO = null; aO = null;
                    }
                }

                if ((ep+1) % 5000 == 0) epsilon = Math.max(0.05, epsilon * 0.9);
            }
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
