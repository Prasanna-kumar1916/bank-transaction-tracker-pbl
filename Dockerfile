FROM eclipse-temurin:17-jdk

WORKDIR /app
COPY src ./src
COPY web ./web
COPY transactions.csv ./transactions.csv
COPY transaction_dashboard.png ./transaction_dashboard.png
RUN mkdir -p bin && javac -d bin src/Transaction.java src/TransactionManager.java src/TransactionFile.java src/SupabaseStore.java src/WebServer.java

ENV HOST=0.0.0.0
CMD ["java", "-cp", "bin", "WebServer"]
