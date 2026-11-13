import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransactionManager {
    private Map<String, List<Transaction>> transactionsByAccount =
            new HashMap<>();

    public void addTransaction(Transaction transaction) {
        transactionsByAccount
                .computeIfAbsent(transaction.getAccountId(),
                        id -> new ArrayList<>())
                .add(transaction);
    }

    public List<Transaction> getTransactions(String accountId) {
        return transactionsByAccount.getOrDefault(accountId, new ArrayList<>());
    }

    public void deleteOlderThan365Days() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(365);

        for (List<Transaction> transactions : transactionsByAccount.values()) {
            transactions.removeIf(
                    transaction -> transaction.getDateTime().isBefore(cutoff));
        }
    }
}
