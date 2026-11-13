# Bank Transaction Tracker (PBL Demo)

A browser-based Java application that records demo transactions and builds charts from the saved data. It combines topics from the supplied syllabi:

- **Java OOP:** classes, objects, methods, exception handling, collections, and file I/O.
- **Advanced Data Structures:** hash-based account lookup and transaction sorting/searching concepts.
- **Python Data Visualization:** Pandas, Matplotlib, and Seaborn charts from the CSV data.

## Important demo-data rule

This project is a classroom demonstration. Use only fictional names, account IDs, and transaction details. The public demo has no user login or access control: anyone with its public URL can view and add records. Do not enter real bank or customer information.

## Run locally in VS Code

Open a terminal at the project root and run:

```powershell
javac -d bin src\Transaction.java src\TransactionManager.java src\TransactionFile.java src\SupabaseStore.java src\WebServer.java
java -cp bin WebServer
```

Open `http://localhost:8080` in your browser. Local mode stores records in `transactions.csv`. Stop the server with `Ctrl+C`.

Generate the Python dashboard image with:

```powershell
py src\dashboard.py
```

## Prepare persistent storage for a public demo

The public deployment uses Supabase so added records survive web-service restarts. Do this before deploying:

1. Create a Supabase project.
2. In Supabase **SQL Editor**, run the contents of `supabase_schema.sql`. It creates the transaction table and inserts three fictional sample transactions.
3. In Supabase project settings, copy the project URL and the **service_role** key. Keep the key private; never put it in `web/index.html`, source code, GitHub, or a screenshot.

The Java server uses the service key only on the server. The browser never receives it.

## Create a public URL with Render

1. Put this project in a GitHub repository. Keep it public only if you are comfortable sharing the source code; never commit database credentials.
2. Sign in to Render and choose **New > Blueprint**. Connect the repository containing `render.yaml`.
3. When Render prompts for `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY`, enter the values from your Supabase project in Render's secret fields.
4. Deploy the service. When the deployment finishes, open its `onrender.com` URL.

Keep a private copy of the Supabase key and database settings. If you rotate the key, update the Render environment variable and redeploy.

## Record retention and charts

Transactions older than 365 days are deleted by the server at startup and then once per day. The dashboard charts are generated in the browser from the saved transaction records; `src/dashboard.py` can also create a static chart image from `transactions.csv` for the Python visualization part of the PBL.

## Free-hosting behavior

Render's free web services can spin down after 15 minutes without inbound traffic, and local file changes are lost when a service restarts or redeploys. This is why public mode stores records in Supabase. Free Supabase projects can pause after a period of low activity, so check its current free-plan limits before the presentation.

## Project files

- `src/WebServer.java` — Java HTTP server and transaction API.
- `src/Transaction.java` — transaction data class.
- `src/TransactionFile.java` — CSV storage and 365-day retention for local mode.
- `src/SupabaseStore.java` — Supabase REST storage for public mode.
- `web/index.html` — browser UI, transaction form, table, search, summary cards, and charts.
- `src/dashboard.py` — Python dashboard generation from CSV.
- `supabase_schema.sql` — public demo database table and sample rows.
- `Dockerfile`, `render.yaml` — Render deployment configuration.
