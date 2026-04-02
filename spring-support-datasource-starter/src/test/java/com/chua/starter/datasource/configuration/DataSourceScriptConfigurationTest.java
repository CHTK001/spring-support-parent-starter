package com.chua.starter.datasource.configuration;

import com.chua.starter.datasource.properties.DataSourceScriptProperties;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceScriptConfigurationTest {

    @Test
    void shouldExecuteInitMigrationAndInitdataScriptsFromDefaultLocations() throws Exception {
        JdbcDataSource dataSource = createDataSource("script-init-" + UUID.randomUUID());

        DataSourceScriptProperties properties = new DataSourceScriptProperties();
        properties.setEnable(true);
        properties.setVerbose(false);
        properties.setBaselineOnMigrate(false);
        properties.setContinueOnError(false);

        DataSourceScriptConfiguration configuration = new DataSourceScriptConfiguration(properties);
        configuration.postProcessAfterInitialization(dataSource, "testDataSource");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        waitUntil(Duration.ofSeconds(5),
                () -> jdbcTemplate.queryForObject("SELECT COUNT(*) FROM script_test_user", Integer.class) == 1);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT nickname FROM script_test_user WHERE id = 1",
                String.class)).isEqualTo("alpha");

        Map<String, Object> migrationRow = jdbcTemplate.queryForMap(
                "SELECT sys_database_version_version AS version, sys_database_version_success AS success " +
                        "FROM sys_database_version WHERE sys_database_version_script_name = ?",
                "V1.1__add_script_test_user_nickname.sql");
        assertThat(migrationRow.get("version")).isEqualTo("1.1");
        assertThat(migrationRow.get("success")).isEqualTo("true");

        Map<String, Object> initdataRow = jdbcTemplate.queryForMap(
                "SELECT sys_database_version_version AS version, sys_database_version_success AS success " +
                        "FROM sys_database_version WHERE sys_database_version_script_name = ?",
                "V1.0__initdata_script_test_user.sql");
        assertThat(initdataRow.get("version")).isEqualTo("1.0");
        assertThat(initdataRow.get("success")).isEqualTo("true");
    }

    @Test
    void shouldRecordFailedScriptAsFalseWhenContinueOnErrorEnabled() {
        JdbcDataSource dataSource = createDataSource("script-failure-" + UUID.randomUUID());

        DataSourceScriptProperties properties = createProperties();
        properties.setContinueOnError(true);
        properties.setScriptPath("classpath*:db/failure/*.sql");

        DataSourceScriptConfiguration configuration = new DataSourceScriptConfiguration(properties);
        configuration.postProcessAfterInitialization(dataSource, "failingDataSource");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Map<String, Object> failedRow = jdbcTemplate.queryForMap(
                "SELECT sys_database_version_success AS success, sys_database_version_version AS version " +
                        "FROM sys_database_version WHERE sys_database_version_script_name = ?",
                "V1.0__init_broken_script.sql");

        assertThat(failedRow.get("success")).isEqualTo("false");
        assertThat(failedRow.get("version")).isEqualTo("1.0");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM broken_script",
                Integer.class)).isZero();
    }

    @Test
    void shouldRetryFailedScriptAndUpdateExecutionRecordOnNextStartup() {
        JdbcDataSource dataSource = createDataSource("script-retry-" + UUID.randomUUID());

        DataSourceScriptProperties failingProperties = createProperties();
        failingProperties.setContinueOnError(true);
        failingProperties.setScriptPath("classpath*:db/retry/failure/*.sql");

        new DataSourceScriptConfiguration(failingProperties)
                .postProcessAfterInitialization(dataSource, "retryFailureDataSource");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Map<String, Object> failedRow = jdbcTemplate.queryForMap(
                "SELECT sys_database_version_success AS success FROM sys_database_version " +
                        "WHERE sys_database_version_script_name = ?",
                "V1.0__retry_script.sql");
        assertThat(failedRow.get("success")).isEqualTo("false");

        DataSourceScriptProperties successProperties = createProperties();
        successProperties.setScriptPath("classpath*:db/retry/success/*.sql");

        new DataSourceScriptConfiguration(successProperties)
                .postProcessAfterInitialization(dataSource, "retrySuccessDataSource");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM retry_script", Integer.class)).isEqualTo(1);

        Map<String, Object> retriedRow = jdbcTemplate.queryForMap(
                "SELECT sys_database_version_success AS success, sys_database_version_version AS version " +
                        "FROM sys_database_version WHERE sys_database_version_script_name = ?",
                "V1.0__retry_script.sql");
        assertThat(retriedRow.get("success")).isEqualTo("true");
        assertThat(retriedRow.get("version")).isEqualTo("1.0");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_database_version WHERE sys_database_version_script_name = ?",
                Integer.class,
                "V1.0__retry_script.sql")).isEqualTo(1);
    }

    @Test
    void shouldExecuteMigrationAndInitdataWithIndependentScanModes() throws Exception {
        JdbcDataSource dataSource = createDataSource("script-independent-modes-" + UUID.randomUUID());
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE script_test_user (
                    id INT PRIMARY KEY,
                    username VARCHAR(64) NOT NULL
                )
                """);

        DataSourceScriptProperties properties = createProperties();
        properties.setScanMode(DataSourceScriptProperties.ScanMode.ONCE);
        properties.setMigrationScanMode(DataSourceScriptProperties.RepeatableScanMode.ALWAYS);
        properties.setDataScanMode(DataSourceScriptProperties.ScanMode.ONCE);

        new DataSourceScriptConfiguration(properties)
                .postProcessAfterInitialization(dataSource, "independentModeDataSource");

        waitUntil(Duration.ofSeconds(5),
                () -> jdbcTemplate.queryForObject("SELECT COUNT(*) FROM script_test_user", Integer.class) == 1);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT nickname FROM script_test_user WHERE id = 1",
                String.class)).isEqualTo("alpha");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_database_version WHERE sys_database_version_script_name = ?",
                Integer.class,
                "V1.0__init_script_test_user.sql")).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_database_version WHERE sys_database_version_script_name = ?",
                Integer.class,
                "V1.1__add_script_test_user_nickname.sql")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_database_version WHERE sys_database_version_script_name = ?",
                Integer.class,
                "V1.0__initdata_script_test_user.sql")).isEqualTo(1);
    }

    @Test
    void shouldSkipInitdataWhenBusinessDataAlreadyExists() {
        JdbcDataSource dataSource = createDataSource("script-initdata-skip-" + UUID.randomUUID());
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE script_test_user (
                    id INT PRIMARY KEY,
                    username VARCHAR(64) NOT NULL,
                    nickname VARCHAR(64)
                )
                """);
        jdbcTemplate.update(
                "INSERT INTO script_test_user (id, username, nickname) VALUES (1, ?, ?)",
                "existing",
                "seed");

        DataSourceScriptProperties properties = createProperties();
        properties.setScanMode(DataSourceScriptProperties.ScanMode.NONE);
        properties.setMigrationScanMode(DataSourceScriptProperties.RepeatableScanMode.NONE);
        properties.setDataScanMode(DataSourceScriptProperties.ScanMode.ONCE);
        properties.setScriptPath("classpath*:db/init/V1.0__initdata_script_test_user.sql");

        new DataSourceScriptConfiguration(properties)
                .postProcessAfterInitialization(dataSource, "initdataSkipDataSource");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM script_test_user", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_database_version WHERE sys_database_version_script_name = ?",
                Integer.class,
                "V1.0__initdata_script_test_user.sql")).isZero();
    }

    private JdbcDataSource createDataSource(String name) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private DataSourceScriptProperties createProperties() {
        DataSourceScriptProperties properties = new DataSourceScriptProperties();
        properties.setEnable(true);
        properties.setVerbose(false);
        properties.setBaselineOnMigrate(false);
        properties.setContinueOnError(false);
        return properties;
    }

    private void waitUntil(Duration timeout, BooleanSupplier condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("Condition was not met within " + timeout);
    }
}
