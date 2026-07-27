package com.wattsmart.backend.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.wattsmart.backend.integration.PostgresIntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class FlywayMigrationIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesAllMigrationsAndCreatesCoreTables() {
        String latestVersion = jdbcTemplate.queryForObject(
                """
                        SELECT version
                        FROM wattsmart.flyway_schema_history
                        WHERE success = TRUE
                        ORDER BY installed_rank DESC
                        LIMIT 1
                        """,
                String.class);

        List<String> tableNames = jdbcTemplate.queryForList(
                """
                        SELECT table_name
                        FROM information_schema.tables
                        WHERE table_schema = 'wattsmart'
                        """,
                String.class);

        Integer tariffPlanCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wattsmart.tariff_plans",
                Integer.class);
        Integer applianceTypeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wattsmart.appliance_types",
                Integer.class);
        Integer applianceModelProfileCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wattsmart.appliance_model_profiles",
                Integer.class);
        Integer scheduledJobCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wattsmart.scheduled_jobs",
                Integer.class);
        Integer demoHomeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wattsmart.homes WHERE external_key LIKE 'DEMO-HOME-%'",
                Integer.class);
        Integer demoMembershipCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM wattsmart.home_user_memberships membership
                        JOIN wattsmart.homes home ON home.id = membership.home_id
                        WHERE home.external_key LIKE 'DEMO-HOME-%'
                        """,
                Integer.class);
        Integer demoBillingAccountCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM wattsmart.home_billing_accounts billing_account
                        JOIN wattsmart.homes home ON home.id = billing_account.home_id
                        WHERE home.external_key LIKE 'DEMO-HOME-%'
                        """,
                Integer.class);
        Integer demoApplianceCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM wattsmart.appliances appliance
                        JOIN wattsmart.homes home ON home.id = appliance.home_id
                        WHERE home.external_key LIKE 'DEMO-HOME-%'
                        """,
                Integer.class);

        assertThat(latestVersion).isEqualTo("7");
        assertThat(tableNames).contains(
                "users",
                "user_sessions",
                "homes",
                "tariff_plans",
                "appliance_types",
                "appliance_model_profiles",
                "appliances",
                "home_billing_accounts",
                "home_usage_daily",
                "appliance_usage_daily",
                "home_billing_cycles",
                "scheduled_jobs",
                "llm_recommendations",
                "email_notifications");
        assertThat(tariffPlanCount).isGreaterThanOrEqualTo(3);
        assertThat(applianceTypeCount).isGreaterThanOrEqualTo(27);
        assertThat(applianceModelProfileCount).isGreaterThanOrEqualTo(60);
        assertThat(scheduledJobCount).isGreaterThanOrEqualTo(9);
        assertThat(demoHomeCount).isEqualTo(6);
        assertThat(demoMembershipCount).isEqualTo(6);
        assertThat(demoBillingAccountCount).isEqualTo(6);
        assertThat(demoApplianceCount).isEqualTo(25);
    }
}
