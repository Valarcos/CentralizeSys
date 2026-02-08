package com.centralizesys.config;

import org.springframework.context.annotation.Profile;
import org.sqlite.SQLiteConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

@Configuration
@Profile("!test")
public class DatabaseConfig {

    /**
     * Configura el DataSource apuntando al archivo local.
     * Importante: Habilita explícitamente las Foreign Keys.
     * Uses DataPathConfig to resolve absolute path for consistent DB location.
     */
    @Bean
    public DataSource dataSource() {
        // Use DataPathConfig for consistent absolute path regardless of working
        // directory
        String absoluteDbUrl = DataPathConfig.getDatabaseUrl();

        // Configuraciones específicas de SQLite para integridad de datos
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true); // CRUCIAL: Habilita FKs para triggers y restricciones
        config.setJournalMode(SQLiteConfig.JournalMode.WAL); // Write-Ahead Logging para mejor concurrencia
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL); // Balance entre seguridad y velocidad

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl(absoluteDbUrl);
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