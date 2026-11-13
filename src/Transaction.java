import java.time.LocalDateTime;

public class Transaction {
    private String transactionId;
    private String accountId;
    private LocalDateTime dateTime;
    private String type;
    private double amount;
    private String mode;
    private String description;

    public Transaction(String transactionId, String accountId,
                       LocalDateTime dateTime, String type,
                       double amount, String mode, String description) {
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.dateTime = dateTime;
        this.type = type;
        this.amount = amount;
        this.mode = mode;
        this.description = description;
    }

    public String getTransactionId() { return transactionId; }
    public String getAccountId() { return accountId; }
    public LocalDateTime getDateTime() { return dateTime; }
    public String getType() { return type; }
    public double getAmount() { return amount; }
    public String getMode() { return mode; }
    public String getDescription() { return description; }

    @Override
    public String toString() {
        return transactionId + " | " + accountId + " | " + dateTime
                + " | " + type + " | " + amount + " | " + mode
                + " | " + description;
    }
}