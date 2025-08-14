import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class ChatClientGUI {
    private JFrame frame;
    private JTextPane textPane;
    private JTextField textField;
    private JButton sendButton, connectButton, disconnectButton;
    private JComboBox<String> recipientBox;
    private JTextField pseudoField;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String pseudo;

    public ChatClientGUI() {
        frame = new JFrame("Client Chat GUI");
        frame.setSize(600, 450);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        textPane = new JTextPane();
        textPane.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textPane);

        textField = new JTextField();

        sendButton = new JButton("Envoyer");
        sendButton.setEnabled(false);

        connectButton = new JButton("Connexion");
        disconnectButton = new JButton("Déconnexion");
        disconnectButton.setEnabled(false);

        recipientBox = new JComboBox<>();
        recipientBox.addItem("Tous");

        pseudoField = new JTextField(10);

        JPanel topPanel = new JPanel(new FlowLayout());
        topPanel.add(new JLabel("Pseudo :"));
        topPanel.add(pseudoField);
        topPanel.add(connectButton);
        topPanel.add(disconnectButton);

        JPanel bottomPanel = new JPanel(new BorderLayout(5,5));
        JPanel rightPanel = new JPanel(new BorderLayout(5,5));
        rightPanel.add(recipientBox, BorderLayout.NORTH);
        rightPanel.add(sendButton, BorderLayout.SOUTH);

        bottomPanel.add(textField, BorderLayout.CENTER);
        bottomPanel.add(rightPanel, BorderLayout.EAST);

        frame.setLayout(new BorderLayout());
        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        connectButton.addActionListener(e -> connect());
        disconnectButton.addActionListener(e -> disconnect());
        sendButton.addActionListener(e -> sendMessage());
        textField.addActionListener(e -> sendMessage());

        frame.setVisible(true);
    }

    private void connect() {
        pseudo = pseudoField.getText().trim();
        if (pseudo.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Entrez un pseudo.");
            return;
        }
        try {
            socket = new Socket("localhost", 1234);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Envoyer le pseudo dès la connexion
            out.println(pseudo);

            // Thread réception messages
            new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        if (line.startsWith("__USERS__:")) {
                            updateRecipients(line.substring("__USERS__:".length()));
                        } else if (line.startsWith("[Privé")) {
                            append(line, new Color(128, 0, 128)); // violet
                        } else if (line.startsWith(pseudo + ":")) {
                            append(line, Color.BLUE); // moi = bleu
                        } else if (line.contains(" a rejoint le chat") || line.contains(" a quitté le chat")) {
                            append(line, Color.RED); // système = rouge
                        } else {
                            append(line, Color.BLACK); // autres = noir
                        }
                    }
                } catch (IOException e) {
                    append("Déconnecté du serveur.\n", Color.RED);
                }
            }).start();

            connectButton.setEnabled(false);
            disconnectButton.setEnabled(true);
            sendButton.setEnabled(true);
            pseudoField.setEditable(false);
            textField.requestFocus();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Impossible de se connecter au serveur.");
        }
    }

    private void disconnect() {
        try {
            if (out != null) {
                out.println("/quit");
            }
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        connectButton.setEnabled(true);
        disconnectButton.setEnabled(false);
        sendButton.setEnabled(false);
        pseudoField.setEditable(true);
        append("Déconnecté.\n", Color.RED);
        recipientBox.removeAllItems();
        recipientBox.addItem("Tous");
    }

    private void sendMessage() {
        if (out == null) return;
        String msg = textField.getText().trim();
        if (msg.isEmpty()) return;

        String target = (String) recipientBox.getSelectedItem();
        if (target != null && !"Tous".equals(target)) {
            out.println("/w " + target + " " + msg);
        } else {
            out.println(msg);
        }
        textField.setText("");
    }

    private void updateRecipients(String csv) {
        SwingUtilities.invokeLater(() -> {
            Set<String> recipients = new LinkedHashSet<>();
            recipients.add("Tous");
            if (!csv.isEmpty()) {
                for (String name : csv.split(",")) {
                    if (!name.equals(pseudo)) {
                        recipients.add(name.trim());
                    }
                }
            }
            recipientBox.removeAllItems();
            for (String r : recipients) {
                recipientBox.addItem(r);
            }
        });
    }

    private void append(String msg, Color color) {
        StyledDocument doc = textPane.getStyledDocument();
        Style style = textPane.addStyle("Style", null);
        StyleConstants.setForeground(style, color);
        try {
            doc.insertString(doc.getLength(), msg + "\n", style);
            textPane.setCaretPosition(doc.getLength());
        } catch (BadLocationException ignored) {}
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatClientGUI::new);
    }
}
