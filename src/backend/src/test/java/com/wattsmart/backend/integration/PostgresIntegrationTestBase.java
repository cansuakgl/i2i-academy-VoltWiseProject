package com.wattsmart.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
public abstract class PostgresIntegrationTestBase {

    private static final String DB_HOST = System.getProperty("it.postgres.host", "localhost");
    private static final String DB_PORT = System.getProperty("it.postgres.port", "5432");
    private static final String DB_NAME = System.getProperty("it.postgres.database", "wattsmart_it");
    private static final String DB_USERNAME = System.getProperty("it.postgres.username", "postgres");
    private static final String DB_PASSWORD = System.getProperty("it.postgres.password", "postgres");
    private static boolean databasePrepared;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        prepareDatabase();
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://%s:%s/%s".formatted(DB_HOST, DB_PORT, DB_NAME));
        registry.add("spring.datasource.username", () -> DB_USERNAME);
        registry.add("spring.datasource.password", () -> DB_PASSWORD);
        registry.add("app.kafka.enabled", () -> "false");
        registry.add("app.kafka.listener.auto-startup", () -> "false");
        registry.add("app.telemetry-ingestion.enabled", () -> "false");
        registry.add("app.telemetry-simulator.enabled", () -> "false");
        registry.add("app.persistence.enabled", () -> "true");
        registry.add("app.persistence.readings-interval-ms", () -> "3600000");
        registry.add("app.persistence.events-interval-ms", () -> "3600000");
        registry.add("app.jobs.enabled", () -> "false");
        registry.add("app.ignite.enabled", () -> "false");
        registry.add("app.live-state.store", () -> "in-memory");
        registry.add("app.idempotency.store", () -> "in-memory");
        registry.add("app.llm.enabled", () -> "false");
        registry.add("app.email.provider", () -> "logging");
    }

    private static synchronized void prepareDatabase() {
        if (databasePrepared) {
            return;
        }

        try (Connection connection = DriverManager.getConnection(
                "jdbc:postgresql://%s:%s/postgres".formatted(DB_HOST, DB_PORT),
                DB_USERNAME,
                DB_PASSWORD);
             Statement statement = connection.createStatement()) {
            if (!DB_NAME.matches("[a-zA-Z0-9_]+")) {
                throw new IllegalStateException("Integration test database name must be alphanumeric/underscore only.");
            }
            statement.execute("""
                    SELECT pg_terminate_backend(pid)
                    FROM pg_stat_activity
                    WHERE datname = '%s'
                    """.formatted(DB_NAME));
            statement.executeUpdate("DROP DATABASE IF EXISTS " + DB_NAME);
            statement.executeUpdate("CREATE DATABASE " + DB_NAME);
            databasePrepared = true;
        } catch (Exception exception) {
            throw new IllegalStateException("""
                    Could not prepare integration test database '%s'.
                    Start the local Docker stack first:
                    docker compose -f src\\docker\\docker-compose.yml up -d
                    """.formatted(DB_NAME), exception);
        }
    }

    protected JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
