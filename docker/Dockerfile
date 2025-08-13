# Multi-stage Dockerfile for Teuthis - High-performance Kafka producer with Netty
FROM eclipse-temurin:21-jdk-alpine AS builder

# Install build dependencies
RUN apk add --no-cache curl bash git

WORKDIR /build

# Copy Maven wrapper and configuration files
COPY mvnw mvnw.cmd ./
COPY .mvn/ .mvn/
COPY pom.xml ./

# Make executable and download dependencies
RUN chmod +x ./mvnw && \
    ./mvnw dependency:go-offline -B --no-transfer-progress

# Copy source and build
COPY src/ src/
RUN ./mvnw clean package -DskipTests -B --no-transfer-progress

# Verify build and show JAR details
RUN ls -la target/ && \
    find target -name "*.jar" -not -name "*sources.jar" -not -name "*javadoc.jar" && \
    echo "=== JAR Manifest ===" && \
    unzip -l target/teuthis-*.jar | grep -E "(META-INF/MANIFEST.MF|\.class)" | head -5

# Production stage
FROM eclipse-temurin:21-jre-alpine AS production

LABEL maintainer="darioajr@github.com" \
      description="Teuthis - High-performance Kafka producer with Netty" \
      version="1.0.0"

# Install runtime dependencies
RUN apk add --no-cache \
    curl \
    bash \
    dumb-init \
    tzdata \
    tini \
    && rm -rf /var/cache/apk/*

# Create application directories as root first
RUN mkdir -p /app/bin && \
    mkdir -p /app/config && \
    mkdir -p /app/data && \
    mkdir -p /app/logs && \
    mkdir -p /app/tmp

# Copy JAR file as root to avoid permission issues
COPY --from=builder /build/target/teuthis-*.jar /app/bin/teuthis.jar

# Create default configuration file
RUN echo "# Default Teuthis Configuration" > /app/config/application.properties && \
    echo "server.port=8080" >> /app/config/application.properties && \
    echo "kafka.bootstrap.servers=kafka:29092" >> /app/config/application.properties && \
    echo "schema.registry.url=http://schema-registry:8081" >> /app/config/application.properties

# Create application user and set ownership
RUN addgroup -g 1000 -S teuthis && \
    adduser -D -S -s /bin/bash -u 1000 -G teuthis teuthis && \
    chown -R teuthis:teuthis /app && \
    chmod -R 755 /app

# Set timezone
ARG TIMEZONE=America/Sao_Paulo
ENV TZ=${TIMEZONE}
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# Switch to application user
USER teuthis
WORKDIR /app

# Environment variables
ENV JAVA_MAX_HEAP=512m \
    JAVA_MIN_HEAP=256m \
    JAVA_MAX_RAM_PERCENTAGE=75.0 \
    SERVER_PORT=8080 \
    KAFKA_BOOTSTRAP_SERVERS=kafka:29092 \
    SCHEMA_REGISTRY_URL=http://schema-registry:8081 \
    NETTY_BOSS_THREADS=1 \
    NETTY_WORKER_THREADS=0 \
    KAFKA_THREAD_POOL_SIZE=10 \
    JMX_ENABLED=false \
    JMX_PORT=9999 \
    DEBUG_ENABLED=false \
    DEBUG_PORT=5005 \
    DEBUG_SUSPEND=n

# Expose ports
EXPOSE 8080 9999 5005

# Volume for persistence
VOLUME ["/app/logs", "/app/data"]

# Health check
HEALTHCHECK --interval=30s \
            --timeout=10s \
            --start-period=60s \
            --retries=3 \
            CMD curl -f http://localhost:8080/health || \
                netstat -tlnp | grep :8080 || \
                exit 1

# Use tini for proper signal handling
ENTRYPOINT ["tini", "--"]
CMD ["sh", "-c", "java -server -Xmx${JAVA_MAX_HEAP:-512m} -Xms${JAVA_MIN_HEAP:-256m} -XX:+UseG1GC -XX:+UseContainerSupport -jar /app/bin/teuthis.jar"]