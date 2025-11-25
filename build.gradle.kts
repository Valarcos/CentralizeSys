plugins {
    id("java")
    id("org.sonarqube") version "6.0.1.5171" // Add this line (check for the latest version)
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

sonar {
    properties {
        property("sonar.projectKey", "Valarcos_sinpen_thesis") // Replace it with your actual key from SonarCloud
        property("sonar.organization", "valarcos")        // Replace with your actual org
        property("sonar.host.url", "https://sonarcloud.io")
    }
}