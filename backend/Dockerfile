# Stage 1: Build the application
FROM gradle:8.10-jdk21-alpine AS build
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY gradle ./gradle
COPY src ./src
# Build the application, skipping tests to speed up the docker build
RUN chmod +x ./gradlew
RUN ./gradlew build -x test

# Stage 2: Run the application
FROM eclipse-temurin:21-jre

ENV LANG=C.UTF-8

# Add official PostgreSQL APT repository using modern keyring to ensure immutable access to postgresql-client-16
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates gnupg2 lsb-release wget && \
    wget --quiet -O /usr/share/keyrings/postgresql.asc https://www.postgresql.org/media/keys/ACCC4CF8.asc && \
    echo "deb [signed-by=/usr/share/keyrings/postgresql.asc] http://apt.postgresql.org/pub/repos/apt/ $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list && \
    apt-get update && \
    apt-get install -y --no-install-recommends postgresql-client-16 && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app
# Copy the built jar from the build stage
COPY --from=build /app/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
