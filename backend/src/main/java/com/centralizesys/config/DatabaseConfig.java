package com.centralizesys.config;

import org.sqlite.SQLiteConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

@Configuration
public class DatabaseConfig {

    // Read the URL from application.properties
    @Value("${spring.datasource.url}")
    private String dbUrl;

    /**
     * Configura el DataSource apuntando al archivo local.
     * Importante: Habilita explícitamente las Foreign Keys.
     */
    @Bean
    public DataSource dataSource() {
        // Configuraciones específicas de SQLite para integridad de datos
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true); // CRUCIAL: Habilita FKs para triggers y restricciones
        config.setJournalMode(SQLiteConfig.JournalMode.WAL); // Write-Ahead Logging para mejor concurrencia
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL); // Balance entre seguridad y velocidad

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");

        // Use the injected variable instead of hardcoded string
        dataSource.setUrl(dbUrl);
        dataSource.setConnectionProperties(config.toProperties());
        return dataSource;
    }

    /**
     * Expone el JdbcTemplate para que los Repositories lo usen.
     * Esta es la herramienta principal para tus consultas SQL manuales.
     */
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * Inicializador de Base de Datos.
     * Ejecuta el archivo 'schema.sql' al iniciar la app si las tablas no existen.
     */
    @Bean
    public DataSourceInitializer dataSourceInitializer(DataSource dataSource) {
        ResourceDatabasePopulator resourceDatabasePopulator = new ResourceDatabasePopulator();

        // 1. Load the Structure
        resourceDatabasePopulator.addScript(new ClassPathResource("schema.sql"));

        // 2. Load the Data (THIS WAS MISSING)
        resourceDatabasePopulator.addScript(new ClassPathResource("data.sql"));

        // CRITICAL: Since schema.sql uses ";;" for triggers,
        // data.sql MUST ALSO use ";;" as the separator.
        resourceDatabasePopulator.setSeparator(";;");

        DataSourceInitializer dataSourceInitializer = new DataSourceInitializer();
        dataSourceInitializer.setDataSource(dataSource);
        dataSourceInitializer.setDatabasePopulator(resourceDatabasePopulator);
        return dataSourceInitializer;
    }
}