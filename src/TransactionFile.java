import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TransactionFile {
    private static final Path FILE = Path.of("transactions.csv");
    private static final String HEADER =
            "transactionId,accountId,dateTime,type,amount,mode,description";

    public static List<Transaction> loadTransactions() {
        List<Transaction> transactions = new ArrayList<>();

        if (!Files.exists(FILE)) {
            return transactions;
        }

        try (BufferedReader reader =
                     Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            String line = reader.readLine();

            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",", -1);
                if (fields.length == 7) {
                    transactions.add(new Transaction(
                            fields[0], fields[1], LocalDateTime.parse(fields[2]),
                            fields[3], Double.parseDouble(fields[4]),
                            fields[5], fields[6]));
                }
            }
        } catch (IOException | RuntimeException e) {
            System.out.println("Could not read transactions.csv: " + e.getMessage());
        }

        return transactions;
    }

    public static boolean appendTransaction(Transaction transaction) {
        try {
            boolean needsHeader = !Files.exists(FILE) || Files.size(FILE) == 0;
            try (BufferedWriter writer = Files.newBufferedWriter(
                    FILE, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE)) {
                if (needsHeader) {
                    writer.write(HEADER);
                    writer.newLine();
                } else {
                    String currentContents = Files.readString(FILE, StandardCharsets.UTF_8);
                    if (!currentContents.endsWith("\n") && !currentContents.endsWith("\r")) {
                        writer.newLine();
                    }
                }
                writer.write(toCsvRow(transaction));
                writer.newLine();
            }
            return true;
        } catch (IOException e) {
            System.out.println("Could not save transaction: " + e.getMessage());
            return false;
        }
    }
    public static void deleteOlderThan365Days() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(365);
        List<Transaction> recentTransactions = new ArrayList<>();

        for (Transaction transaction : loadTransactions()) {
            if (!transaction.getDateTime().isBefore(cutoff)) {
                recentTransactions.add(transaction);
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(
                FILE, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            writer.write(HEADER);
            writer.newLine();
            for (Transaction transaction : recentTransactions) {
                writer.write(toCsvRow(transaction));
                writer.newLine();
            }
            System.out.println("365-day transaction cleanup completed.");
        } catch (IOException e) {
            System.out.println("Could not clean old transactions: " + e.getMessage());
        }
    }

    private static String toCsvRow(Transaction transaction) {
        return String.join(",",
                clean(transaction.getTransactionId()),
                clean(transaction.getAccountId()),
                transaction.getDateTime().toString(),
                clean(transaction.getType()),
                Double.toString(transaction.getAmount()),
                clean(transaction.getMode()),
                clean(transaction.getDescription()));
    }

    private static String clean(String value) {
        return value.replace(",", " ");
    }
}
