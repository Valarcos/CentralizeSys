package com.centralizesys.config;

import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration
public class TestDatabaseConfig {

    // dataSource() bean has been removed.
    // Spring Boot automatically configures the Testcontainers DataSource
    // because we specified the jdbc:tc:postgresql URL in application-test.properties.

    //TODO: why even keep this empty class? Analyze the documentation files in the project when considering if deletion is necessary
    // after the migration to testcontainers

}