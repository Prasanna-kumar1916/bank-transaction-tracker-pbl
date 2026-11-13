from pathlib import Path

import matplotlib.pyplot as plt
import pandas as pd
import seaborn as sns

# The script is in src; the CSV and dashboard image are in the project folder.
PROJECT_DIR = Path(__file__).resolve().parent.parent
DATA_FILE = PROJECT_DIR / "transactions.csv"
OUTPUT_FILE = PROJECT_DIR / "transaction_dashboard.png"

if not DATA_FILE.exists():
    raise FileNotFoundError(f"Transaction file was not found: {DATA_FILE}")

transactions = pd.read_csv(DATA_FILE)
if transactions.empty:
    raise ValueError("No transaction rows are available for the dashboard.")

transactions["dateTime"] = pd.to_datetime(
    transactions["dateTime"], errors="coerce"
)
transactions["amount"] = pd.to_numeric(
    transactions["amount"], errors="coerce"
)
transactions = transactions.dropna(subset=["dateTime", "amount"]).copy()
transactions["type"] = transactions["type"].str.upper()
transactions["mode"] = transactions["mode"].str.upper()

if transactions.empty:
    raise ValueError("No valid transaction rows were found in the CSV file.")

sns.set_theme(style="whitegrid")
figure, axes = plt.subplots(2, 2, figsize=(13, 8))
figure.suptitle("Bank Transaction Dashboard", fontsize=20, fontweight="bold")

credit_total = transactions.loc[transactions["type"] == "CREDIT", "amount"].sum()
debit_total = transactions.loc[transactions["type"] == "DEBIT", "amount"].sum()
figure.text(
    0.5, 0.91,
    f"Transactions: {len(transactions)}     Credits: {credit_total:,.2f}     Debits: {debit_total:,.2f}",
    ha="center", fontsize=12
)

# Monthly credit and debit totals.
transactions["month"] = transactions["dateTime"].dt.to_period("M").astype(str)
monthly = transactions.pivot_table(
    index="month", columns="type", values="amount",
    aggfunc="sum", fill_value=0
)
monthly.plot(kind="bar", ax=axes[0, 0], color={"CREDIT": "#2a9d8f", "DEBIT": "#e76f51"})
axes[0, 0].set_title("Monthly Credits and Debits")
axes[0, 0].set_xlabel("Month")
axes[0, 0].set_ylabel("Amount")
axes[0, 0].tick_params(axis="x", rotation=30)

# Share of transactions by payment mode.
mode_counts = transactions["mode"].value_counts()
axes[0, 1].pie(
    mode_counts.values, labels=mode_counts.index, autopct="%1.1f%%",
    startangle=90, wedgeprops={"edgecolor": "white"}
)
axes[0, 1].set_title("Transactions by Payment Mode")

# Daily transaction count over time.
daily_count = transactions.set_index("dateTime").resample("D").size()
axes[1, 0].plot(daily_count.index, daily_count.values, marker="o", color="#457b9d")
axes[1, 0].set_title("Daily Transaction Activity")
axes[1, 0].set_xlabel("Date")
axes[1, 0].set_ylabel("Number of Transactions")
axes[1, 0].tick_params(axis="x", rotation=30)

# Total transaction amount by payment mode.
mode_amounts = transactions.groupby("mode")["amount"].sum().sort_values()
mode_amounts.plot(kind="barh", ax=axes[1, 1], color="#6c63ff")
axes[1, 1].set_title("Total Amount by Payment Mode")
axes[1, 1].set_xlabel("Amount")
axes[1, 1].set_ylabel("Mode")

figure.tight_layout(rect=[0, 0, 1, 0.87])
figure.savefig(OUTPUT_FILE, dpi=160, bbox_inches="tight")
print(f"Dashboard image saved to: {OUTPUT_FILE}")
plt.show()
