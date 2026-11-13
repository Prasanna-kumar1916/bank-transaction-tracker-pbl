import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SupabaseStore {
    private static final String SELECT =
            "transaction_id,account_id,date_time,type,amount,mode,description";
    private final String baseUrl;
    private final String serviceKey;
    private final HttpClient client = HttpClient.newHttpClient();

    private SupabaseStore(String baseUrl, String serviceKey) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.serviceKey = serviceKey;
    }

    public static SupabaseStore fromEnvironment() {
        String url = System.getenv("SUPABASE_URL");
        String key = System.getenv("SUPABASE_SERVICE_ROLE_KEY");
        if (url == null || url.isBlank()) return null;
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("SUPABASE_SERVICE_ROLE_KEY is required when SUPABASE_URL is set.");
        }
        return new SupabaseStore(url, key);
    }

    public List<Transaction> loadTransactions() throws IOException, InterruptedException {
        String endpoint = baseUrl + "/rest/v1/transactions?select=" + SELECT + "&order=date_time.asc";
        HttpResponse<String> response = client.send(request(endpoint).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        requireSuccess(response.statusCode(), response.body());
        return parseRows(response.body());
    }

    public boolean appendTransaction(Transaction transaction) throws IOException, InterruptedException {
        String body = "{"
                + "\"transaction_id\":\"" + escape(transaction.getTransactionId()) + "\","
                + "\"account_id\":\"" + escape(transaction.getAccountId()) + "\","
                + "\"date_time\":\"" + escape(transaction.getDateTime().toString()) + "\","
                + "\"type\":\"" + escape(transaction.getType()) + "\","
                + "\"amount\":" + transaction.getAmount() + ","
                + "\"mode\":\"" + escape(transaction.getMode()) + "\","
                + "\"description\":\"" + escape(transaction.getDescription()) + "\"}";
        HttpRequest request = request(baseUrl + "/rest/v1/transactions")
                .header("Content-Type", "application/json")
                .header("Prefer", "return=minimal")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }

    public void deleteOlderThan(LocalDateTime cutoff) throws IOException, InterruptedException {
        String encoded = URLEncoder.encode(cutoff.toString(), StandardCharsets.UTF_8);
        String endpoint = baseUrl + "/rest/v1/transactions?date_time=lt." + encoded;
        HttpRequest request = request(endpoint)
                .header("Prefer", "return=minimal")
                .DELETE().build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        requireSuccess(response.statusCode(), response.body());
    }

    private HttpRequest.Builder request(String endpoint) {
        return HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(java.time.Duration.ofSeconds(20))
                .header("apikey", serviceKey)
                .header("Authorization", "Bearer " + serviceKey)
                .header("Accept", "application/json");
    }

    private static void requireSuccess(int status, String body) throws IOException {
        if (status < 200 || status >= 300) {
            throw new IOException("Database request failed (" + status + "): " + body);
        }
    }

    private static List<Transaction> parseRows(String json) {
        List<Transaction> result = new ArrayList<>();
        int start = -1;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') inString = true;
            else if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    result.add(fromObject(json.substring(start, i + 1)));
                    start = -1;
                }
            }
        }
        return result;
    }

    private static Transaction fromObject(String json) {
        Map<String, String> fields = new HashMap<>();
        int[] index = {0};
        skipSpace(json, index);
        index[0]++;
        while (index[0] < json.length()) {
            skipSpace(json, index);
            if (json.charAt(index[0]) == '}') break;
            String key = readString(json, index);
            skipSpace(json, index);
            index[0]++;
            skipSpace(json, index);
            String value;
            if (json.charAt(index[0]) == '"') {
                value = readString(json, index);
            } else {
                int start = index[0];
                while (index[0] < json.length() && json.charAt(index[0]) != ',' && json.charAt(index[0]) != '}') index[0]++;
                value = json.substring(start, index[0]).trim();
            }
            fields.put(key, value);
            skipSpace(json, index);
            if (json.charAt(index[0]) == ',') index[0]++;
        }
        return new Transaction(fields.get("transaction_id"), fields.get("account_id"),
                LocalDateTime.parse(fields.get("date_time")), fields.get("type"),
                Double.parseDouble(fields.get("amount")), fields.get("mode"), fields.get("description"));
    }

    private static String readString(String json, int[] index) {
        StringBuilder result = new StringBuilder();
        index[0]++;
        while (index[0] < json.length()) {
            char c = json.charAt(index[0]++);
            if (c == '"') return result.toString();
            if (c == '\\' && index[0] < json.length()) {
                char escaped = json.charAt(index[0]++);
                switch (escaped) {
                    case '"', '\\', '/' -> result.append(escaped);
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'u' -> {
                        result.append((char) Integer.parseInt(json.substring(index[0], index[0] + 4), 16));
                        index[0] += 4;
                    }
                    default -> result.append(escaped);
                }
            } else {
                result.append(c);
            }
        }
        throw new IllegalArgumentException("Invalid JSON string from database.");
    }

    private static void skipSpace(String json, int[] index) {
        while (index[0] < json.length() && Character.isWhitespace(json.charAt(index[0]))) index[0]++;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }
}