package com.centralizesys.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.sqlite.SQLiteConfig;                                             // Requiere la librería xerial/sqlite-jdbc"

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
        resourceDatabasePopulator.addScript(new ClassPathResource("schema.sql"));

        // Set the separator to double semicolon (;;)
        // This prevents Spring from splitting the Trigger definitions in half.
        resourceDatabasePopulator.setSeparator(";;");

        DataSourceInitializer dataSourceInitializer = new DataSourceInitializer();
        dataSourceInitializer.setDataSource(dataSource);
        dataSourceInitializer.setDatabasePopulator(resourceDatabasePopulator);
        return dataSourceInitializer;
    }
}