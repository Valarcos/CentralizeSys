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
COPY build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
