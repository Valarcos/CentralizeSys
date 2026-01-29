plugins {
    id("java")
    id("org.sonarqube") version "6.0.1.5171"
    id("org.springframework.boot") version "3.3.3"
    id("io.spring.dependency-management") version "1.1.5"
    jacoco
}

group = "com.centralizesys"
version = "0.0.2-SNAPSHOT"

// CRITICAL FIX: Ensure we are strictly using Java 21 Toolchain
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")

    // SQLite JDBC driver
    // FIX: Uncommented specific version to ensure DatabaseConfig finds "SQLiteConfig"
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")

    implementation("org.hibernate.validator:hibernate-validator")

    // Security
    implementation("org.springframework.boot:spring-boot-starter-security")

    // CRITICAL FIX: Force Lombok 1.18.36 to prevent "ExceptionInInitializerError" on Java 21+
    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Updated to 5.4.0 to resolve CVE-2025-31672, CVE-2024-25710, CVE-2024-26308
    // Excel (Apache POI)
    implementation("org.apache.poi:poi-ooxml:5.4.0")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }
    maxHeapSize = "2048m"
}

// CRITICAL FIX: Handle Spanish accents in source code
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

sonar {
    properties {
        property("sonar.projectKey", "Valarcos_sinpen_thesis")
        property("sonar.organization", "valarcos")
        property("sonar.host.url", "https://sonarcloud.io")
    }
}