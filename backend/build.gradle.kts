plugins {
    id("java")
    id("org.sonarqube") version "6.0.1.5171" // Add this line (check for the latest version)
    id("org.springframework.boot") version "3.3.3"
    id("io.spring.dependency-management") version "1.1.5"
}

group = "com.centralizesys"
version = "0.0.2-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")

    // SQLite JDBC driver
    implementation("org.xerial:sqlite-jdbc")

    implementation("org.hibernate.validator:hibernate-validator")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

sonar {
    properties {
        property("sonar.projectKey", "Valarcos_sinpen_thesis")
        property("sonar.organization", "valarcos")
        property("sonar.host.url", "https://sonarcloud.io")
    }
}