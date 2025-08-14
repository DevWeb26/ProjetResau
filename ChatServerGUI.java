import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServerGUI {
    private JFrame frame;
    private JTextPane textPane;
    private DefaultListModel<String> clientsListModel;
    private JButton stopButton;

    private ServerSocket serverSocket;
    private ExecutorService pool;
    private final Map<String, PrintWriter> clients = new ConcurrentHashMap<>();

    public ChatServerGUI(int port) {
        frame = new JFrame("Serveur Chat (GUI, /w, /quit)");
        frame.setSize(640, 440);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        textPane = new JTextPane();
        textPane.setEditable(false);
        JScrollPane messageScroll = new JScrollPane(textPane);

        clientsListModel = new DefaultListModel<>();
        JList<String> clientsList = new JList<>(clientsListModel);
        JScrollPane clientsScroll = new JScrollPane(clientsList);
        clientsScroll.setPreferredSize(new Dimension(180, 0));

        stopButton = new JButton("Arrêter le serveur");
        stopButton.addActionListener(e -> stopServer());

        frame.setLayout(new BorderLayout());
        frame.add(messageScroll, BorderLayout.CENTER);
        frame.add(clientsScroll, BorderLayout.EAST);
        frame.add(stopButton, BorderLayout.SOUTH);

        pool = Executors.newCachedThreadPool();
        new Thread(() -> startServer(port)).start();

        frame.setVisible(true);
    }

    private void startServer(int port) {
        try {
            serverSocket = new ServerSocket(port);
            appendMessage("Serveur démarré sur le port " + port, new Color(0,128,0));

            while (!serverSocket.isClosed()) {
                Socket socket = serverSocket.accept();
                pool.execute(new ClientHandler(socket));
            }
        } catch (IOException e) {
            appendMessage("Serveur arrêté.", Color.RED);
        }
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String pseudo;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

                // Le client envoie son pseudo directement
                pseudo = in.readLine();
                if (pseudo == null || pseudo.trim().isEmpty()) {
                    pseudo = "Anonyme-" + socket.getPort();
                }

                pseudo = ensureUniquePseudo(pseudo);

                clients.put(pseudo, out);
                SwingUtilities.invokeLater(() -> clientsListModel.addElement(pseudo));

                broadcast("[Système] " + pseudo + " a rejoint le chat", new Color(0,128,0));
                sendUserListToAll();

                String line;
                while ((line = in.readLine()) != null) {
                    if (line.equalsIgnoreCase("/quit")) {
                        break;
                    } else if (line.startsWith("/w ")) {
                        handlePrivateMessage(pseudo, line);
                    } else {
                        broadcast(pseudo + ": " + line, Color.BLACK);
                    }
                }
            } catch (IOException ignored) {
            } finally {
                if (pseudo != null) {
                    clients.remove(pseudo);
                    SwingUtilities.invokeLater(() -> clientsListModel.removeElement(pseudo));
                    broadcast("[Système] " + pseudo + " a quitté le chat", Color.RED);
                    sendUserListToAll();
                }
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    private String ensureUniquePseudo(String base) {
        String p = base;
        int i = 1;
        while (clients.containsKey(p)) {
            p = base + "_" + i;
            i++;
        }
        if (!p.equals(base)) {
            appendMessage("Pseudo '" + base + "' déjà pris. Remplacé par '" + p + "'.", Color.ORANGE);
        }
        return p;
    }

    private void handlePrivateMessage(String sender, String fullMessage) {
        String[] parts = fullMessage.split(" ", 3);
        if (parts.length < 3) {
            sendToClient(sender, "[Système] Format: /w pseudo message");
            return;
        }
        String target = parts[1].trim();
        String msg = parts[2];

        PrintWriter targetOut = clients.get(target);
        if (targetOut != null) {
            targetOut.println("[Privé de " + sender + "]: " + msg);
            sendToClient(sender, "[Privé à " + target + "]: " + msg);
            appendMessage("[Privé] " + sender + " → " + target + ": " + msg, new Color(0,0,160));
        } else {
            sendToClient(sender, "[Système] Utilisateur introuvable : " + target);
        }
    }

    private void sendToClient(String pseudo, String message) {
        PrintWriter w = clients.get(pseudo);
        if (w != null) w.println(message);
    }

    private void broadcast(String msg, Color color) {
        appendMessage(msg, color);
        for (PrintWriter w : clients.values()) {
            w.println(msg);
        }
    }

    private void sendUserListToAll() {
        String list = String.join(",", clients.keySet());
        String payload = "__USERS__:" + list;
        for (PrintWriter w : clients.values()) {
            w.println(payload);
        }
    }

    private void appendMessage(String msg, Color color) {
        StyledDocument doc = textPane.getStyledDocument();
        Style style = textPane.addStyle("Style", null);
        StyleConstants.setForeground(style, color);
        try {
            doc.insertString(doc.getLength(), msg + "\n", style);
        } catch (BadLocationException ignored) {}
    }

    private void stopServer() {
        try {
            if (serverSocket != null) serverSocket.close();
            if (pool != null) pool.shutdownNow();
            appendMessage("Serveur arrêté manuellement.", Color.RED);
        } catch (IOException e) {
            appendMessage("Erreur à l'arrêt: " + e.getMessage(), Color.RED);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatServerGUI(1234));
    }
}
