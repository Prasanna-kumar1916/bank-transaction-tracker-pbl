import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class App {
    private final TransactionManager manager = new TransactionManager();
    private final List<Transaction> transactions = new ArrayList<>();
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new String[]{"Transaction ID", "Account ID", "Date and Time", "Type", "Amount", "Mode", "Description"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTextField idField = new JTextField();
    private final JTextField accountField = new JTextField();
    private final JTextField dateTimeField = new JTextField(LocalDateTime.now().withNano(0).toString());
    private final JComboBox<String> typeBox = new JComboBox<>(new String[]{"DEBIT", "CREDIT"});
    private final JTextField amountField = new JTextField();
    private final JComboBox<String> modeBox = new JComboBox<>(new String[]{"UPI", "CARD", "CASH", "NET_BANKING"});
    private final JTextField descriptionField = new JTextField();
    private final JTextField searchField = new JTextField(14);
    private final JLabel summaryLabel = new JLabel();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new App().showWindow());
    }

    public App() {
        // Expired records are removed from the CSV when the app starts.
        TransactionFile.deleteOlderThan365Days();
        for (Transaction transaction : TransactionFile.loadTransactions()) {
            transactions.add(transaction);
            manager.addTransaction(transaction);
        }
    }

    private void showWindow() {
        JFrame frame = new JFrame("Bank Transaction Tracker");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1100, 720);
        frame.setMinimumSize(new Dimension(900, 600));
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout(12, 12));

        JLabel title = new JLabel("Bank Transaction Tracker", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 24));
        JPanel topPanel = new JPanel(new BorderLayout(8, 8));
        topPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 0, 16));
        topPanel.add(title, BorderLayout.NORTH);
        summaryLabel.setHorizontalAlignment(SwingConstants.CENTER);
        topPanel.add(summaryLabel, BorderLayout.SOUTH);
        frame.add(topPanel, BorderLayout.NORTH);

        JPanel leftPanel = new JPanel(new BorderLayout(8, 8));
        leftPanel.setBorder(BorderFactory.createTitledBorder("Enter a Transaction"));
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Transaction ID")); form.add(idField);
        form.add(new JLabel("Account ID")); form.add(accountField);
        form.add(new JLabel("Date and time (yyyy-MM-ddTHH:mm:ss)")); form.add(dateTimeField);
        form.add(new JLabel("Type")); form.add(typeBox);
        form.add(new JLabel("Amount")); form.add(amountField);
        form.add(new JLabel("Mode")); form.add(modeBox);
        form.add(new JLabel("Description")); form.add(descriptionField);
        leftPanel.add(form, BorderLayout.NORTH);

        JButton addButton = new JButton("Save Transaction");
        addButton.addActionListener(event -> addTransaction());
        leftPanel.add(addButton, BorderLayout.SOUTH);
        leftPanel.setPreferredSize(new Dimension(330, 300));
        frame.add(leftPanel, BorderLayout.WEST);

        JTable table = new JTable(tableModel);
        table.setAutoCreateRowSorter(true);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Transaction Records"));
        frame.add(tableScroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.add(new JLabel("Search account ID:"));
        bottom.add(searchField);

        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(event -> showAccountTransactions());
        bottom.add(searchButton);

        JButton allButton = new JButton("Show All");
        allButton.addActionListener(event -> refreshTable(transactions));
        bottom.add(allButton);

        JButton chartsButton = new JButton("Open Python Charts");
        chartsButton.addActionListener(event -> openCharts(frame));
        bottom.add(chartsButton);

        frame.add(bottom, BorderLayout.SOUTH);
        refreshTable(transactions);
        frame.setVisible(true);
    }

    private void addTransaction() {
        try {
            String id = idField.getText().trim();
            String accountId = accountField.getText().trim();
            String description = descriptionField.getText().trim();
            if (id.isEmpty() || accountId.isEmpty() || description.isEmpty()) {
                throw new IllegalArgumentException("Fill in the ID, account ID, and description.");
            }
            for (Transaction existing : transactions) {
                if (existing.getTransactionId().equalsIgnoreCase(id)) {
                    throw new IllegalArgumentException("That transaction ID already exists.");
                }
            }

            LocalDateTime dateTime = LocalDateTime.parse(dateTimeField.getText().trim());
            double amount = Double.parseDouble(amountField.getText().trim());
            if (amount <= 0) {
                throw new IllegalArgumentException("Amount must be greater than zero.");
            }

            Transaction transaction = new Transaction(
                    id, accountId, dateTime, (String) typeBox.getSelectedItem(), amount,
                    (String) modeBox.getSelectedItem(), description);

            TransactionFile.appendTransaction(transaction);
            transactions.add(transaction);
            manager.addTransaction(transaction);
            refreshTable(transactions);
            idField.setText("");
            amountField.setText("");
            descriptionField.setText("");
            updateSummary();
            JOptionPane.showMessageDialog(null, "Transaction saved to transactions.csv.");
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), "Check transaction details", JOptionPane.WARNING_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Could not save transaction: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showAccountTransactions() {
        String accountId = searchField.getText().trim();
        if (accountId.isEmpty()) {
            JOptionPane.showMessageDialog(null, "Enter an account ID to search.");
            return;
        }
        refreshTable(manager.getTransactions(accountId));
    }

    private void refreshTable(List<Transaction> rows) {
        tableModel.setRowCount(0);
        for (Transaction transaction : rows) {
            tableModel.addRow(new Object[]{
                    transaction.getTransactionId(), transaction.getAccountId(),
                    transaction.getDateTime(), transaction.getType(),
                    transaction.getAmount(), transaction.getMode(),
                    transaction.getDescription()
            });
        }
        updateSummary();
    }

    private void updateSummary() {
        double credits = 0;
        double debits = 0;
        for (Transaction transaction : transactions) {
            if ("CREDIT".equalsIgnoreCase(transaction.getType())) {
                credits += transaction.getAmount();
            } else if ("DEBIT".equalsIgnoreCase(transaction.getType())) {
                debits += transaction.getAmount();
            }
        }
        summaryLabel.setText(String.format(
                "Transactions: %d     Total credits: %.2f     Total debits: %.2f",
                transactions.size(), credits, debits));
    }

    private void openCharts(JFrame parent) {
        java.nio.file.Path chart = java.nio.file.Path.of("transaction_dashboard.png");
        if (!java.nio.file.Files.exists(chart)) {
            JOptionPane.showMessageDialog(parent,
                    "Create the charts first by running: py src\\dashboard.py");
            return;
        }
        JFrame chartFrame = new JFrame("Python Transaction Charts");
        chartFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        JLabel image = new JLabel(new ImageIcon(chart.toString()));
        chartFrame.add(new JScrollPane(image));
        chartFrame.setSize(1000, 720);
        chartFrame.setLocationRelativeTo(parent);
        chartFrame.setVisible(true);
    }
}