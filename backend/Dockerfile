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
FROM eclipse-temurin:21-jre-alpine

# Install postgresql-client dynamically based on OS to support pg_dump and psql
RUN if [ -x "$(command -v apk)" ]; then \
      apk add --no-cache postgresql-client; \
    elif [ -x "$(command -v apt-get)" ]; then \
      apt-get update && apt-get install -y --no-install-recommends postgresql-client && rm -rf /var/lib/apt/lists/*; \
    else \
      echo "Unsupported OS for postgresql-client installation"; exit 1; \
    fi

WORKDIR /app
# Copy the built jar from the build stage
COPY --from=build /app/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
