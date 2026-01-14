package com.centralizesys.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.sqlite.SQLiteConfig;

import javax.sql.DataSource;

@TestConfiguration
public class TestDatabaseConfig {

    @Bean
    public DataSource dataSource() {
        // 1. Configure SQLite specific settings
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true); // Critical for testing constraints
        // Using ::memory: creates a volatile DB in RAM
        config.setJournalMode(SQLiteConfig.JournalMode.MEMORY);

        // 2. USE SingleConnectionDataSource
        // This holds the connection open so the in-memory DB isn't wiped
        // when the Initializer finishes running the scripts.
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl("jdbc:sqlite::memory:"); // <--- The Magic String
        dataSource.setSuppressClose(true); // <--- CRITICAL: Prevents data loss

        dataSource.setConnectionProperties(config.toProperties());

        return dataSource;
    }

    @Bean
    public DataSourceInitializer dataSourceInitializer(DataSource dataSource) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();

        // 2. Load your REAL schema.
        // This ensures tests fail if your production SQL has syntax errors.
        populator.addScript(new ClassPathResource("schema.sql"));

        // IMPORTANT: Must match the separator used in schema.sql for Triggers
        populator.setSeparator(";;");

        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setDatabasePopulator(populator);
        return initializer;
    }
}