package com.centralizesys.config;

import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Configuration;

@Configuration
@Profile("!test")
public class DatabaseConfig {

    // dataSource() and jdbcTemplate() beans have been removed.
    // Spring Boot automatically configures a HikariDataSource and JdbcTemplate
    // using the properties defined in application.properties (PostgreSQL).

    //TODO: If the beans have been removed, does this file even do anything? Whats its purpose now?
}