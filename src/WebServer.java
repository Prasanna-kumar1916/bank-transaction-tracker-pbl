import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class WebServer {
    private static final List<Transaction> TRANSACTIONS = new CopyOnWriteArrayList<>();
    private static SupabaseStore remoteStore;

    public static void main(String[] args) throws Exception {
        remoteStore = SupabaseStore.fromEnvironment();
        if (remoteStore == null) {
            TransactionFile.deleteOlderThan365Days();
            TRANSACTIONS.addAll(TransactionFile.loadTransactions());
        } else {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(365);
            remoteStore.deleteOlderThan(cutoff);
            TRANSACTIONS.addAll(remoteStore.loadTransactions());
        }

        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        String host = System.getenv().getOrDefault("HOST", "127.0.0.1");
        HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/api/transactions", WebServer::handleTransactions);
        server.createContext("/charts/transaction_dashboard.png", WebServer::handleChart);
        server.createContext("/", WebServer::handlePage);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        ScheduledExecutorService cleanup = Executors.newSingleThreadScheduledExecutor();
        cleanup.scheduleAtFixedRate(WebServer::cleanupExpiredTransactions,
                1, 1, TimeUnit.DAYS);

        System.out.println("Bank Transaction Tracker is available at http://" + host + ":" + port);
    }

    private static boolean saveTransaction(Transaction transaction) {
        try {
            return remoteStore == null
                    ? TransactionFile.appendTransaction(transaction)
                    : remoteStore.appendTransaction(transaction);
        } catch (Exception e) {
            System.err.println("Could not save transaction: " + e.getMessage());
            return false;
        }
    }

    private static void cleanupExpiredTransactions() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(365);
            if (remoteStore == null) {
                TransactionFile.deleteOlderThan365Days();
                TRANSACTIONS.clear();
                TRANSACTIONS.addAll(TransactionFile.loadTransactions());
            } else {
                remoteStore.deleteOlderThan(cutoff);
                TRANSACTIONS.clear();
                TRANSACTIONS.addAll(remoteStore.loadTransactions());
            }
        } catch (Exception e) {
            System.err.println("365-day cleanup failed: " + e.getMessage());
        }
    }
    private static void handleTransactions(HttpExchange exchange) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            String accountId = queryValue(exchange.getRequestURI().getRawQuery(), "accountId");
            List<Transaction> selected = new ArrayList<>();
            for (Transaction transaction : TRANSACTIONS) {
                if (accountId.isEmpty() || transaction.getAccountId().equalsIgnoreCase(accountId)) {
                    selected.add(transaction);
                }
            }
            String json = selected.stream().map(WebServer::toJson)
                    .collect(Collectors.joining(",", "[", "]"));
            send(exchange, 200, "application/json; charset=utf-8", json);
            return;
        }

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            Map<String, String> fields = parseForm(new String(
                    exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                String id = required(fields, "transactionId");
                String accountId = required(fields, "accountId");
                String dateText = required(fields, "dateTime");
                String type = required(fields, "type").toUpperCase();
                String mode = required(fields, "mode").toUpperCase();
                String description = required(fields, "description");
                double amount = Double.parseDouble(required(fields, "amount"));
                LocalDateTime dateTime = LocalDateTime.parse(dateText);

                if (!type.equals("CREDIT") && !type.equals("DEBIT")) {
                    throw new IllegalArgumentException("Type must be CREDIT or DEBIT.");
                }
                if (amount <= 0) {
                    throw new IllegalArgumentException("Amount must be greater than zero.");
                }
                for (Transaction existing : TRANSACTIONS) {
                    if (existing.getTransactionId().equalsIgnoreCase(id)) {
                        throw new IllegalArgumentException("That transaction ID already exists.");
                    }
                }

                Transaction transaction = new Transaction(
                        id, accountId, dateTime, type, amount, mode, description);
                if (!saveTransaction(transaction)) {
                    send(exchange, 500, "application/json; charset=utf-8",
                            "{\"error\":\"Could not save the transaction.\"}");
                    return;
                }
                TRANSACTIONS.add(transaction);
                send(exchange, 201, "application/json; charset=utf-8", toJson(transaction));
            } catch (RuntimeException e) {
                send(exchange, 400, "application/json; charset=utf-8",
                        "{\"error\":\"" + jsonEscape(e.getMessage()) + "\"}");
            }
            return;
        }

        exchange.getResponseHeaders().set("Allow", "GET, POST");
        send(exchange, 405, "text/plain; charset=utf-8", "Method not allowed");
    }

    private static void handlePage(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "text/plain; charset=utf-8", "Method not allowed");
            return;
        }
        Path page = Path.of("web", "index.html");
        if (!Files.exists(page)) {
            send(exchange, 404, "text/plain; charset=utf-8", "web/index.html was not found");
            return;
        }
        byte[] content = Files.readAllBytes(page);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, content.length);
        exchange.getResponseBody().write(content);
        exchange.close();
    }

    private static void handleChart(HttpExchange exchange) throws IOException {
        Path image = Path.of("transaction_dashboard.png");
        if (!Files.exists(image)) {
            send(exchange, 404, "text/plain; charset=utf-8",
                    "Run the Python dashboard script to generate the chart image.");
            return;
        }
        byte[] content = Files.readAllBytes(image);
        exchange.getResponseHeaders().set("Content-Type", "image/png");
        exchange.sendResponseHeaders(200, content.length);
        exchange.getResponseBody().write(content);
        exchange.close();
    }

    private static String toJson(Transaction transaction) {
        return "{"
                + "\"transactionId\":\"" + jsonEscape(transaction.getTransactionId()) + "\","
                + "\"accountId\":\"" + jsonEscape(transaction.getAccountId()) + "\","
                + "\"dateTime\":\"" + jsonEscape(transaction.getDateTime().toString()) + "\","
                + "\"type\":\"" + jsonEscape(transaction.getType()) + "\","
                + "\"amount\":" + transaction.getAmount() + ","
                + "\"mode\":\"" + jsonEscape(transaction.getMode()) + "\","
                + "\"description\":\"" + jsonEscape(transaction.getDescription()) + "\"}";
    }

    private static String jsonEscape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private static String required(Map<String, String> fields, String name) {
        String value = fields.getOrDefault(name, "").trim();
        if (value.isEmpty()) throw new IllegalArgumentException(name + " is required.");
        return value;
    }

    private static Map<String, String> parseForm(String body) {
        return java.util.Arrays.stream(body.split("&"))
                .map(part -> part.split("=", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(
                        parts -> URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        parts -> URLDecoder.decode(parts[1], StandardCharsets.UTF_8),
                        (first, second) -> second));
    }

    private static String queryValue(String query, String key) {
        if (query == null || query.isEmpty()) return "";
        return parseForm(query).getOrDefault(key, "");
    }

    private static void send(HttpExchange exchange, int status,
                             String contentType, String body) throws IOException {
        byte[] content = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, content.length);
        exchange.getResponseBody().write(content);
        exchange.close();
    }
}