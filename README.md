# 🚀 Teuthis

A high-performance Kafka producer with HTTP endpoint using Netty, designed for non-blocking operations and cluster support.

## 📋 Overview

Teuthis is a lightweight, high-performance HTTP-to-Kafka bridge that accepts HTTP POST requests and forwards them to Kafka topics. Built with Netty for maximum throughput and minimal latency, it supports multiple message formats (JSON, XML, SOAP, plain text) and includes comprehensive monitoring capabilities.

## ✨ Features

- **High Performance**: Built on Netty NIO for maximum throughput
- **Non-blocking**: Asynchronous message processing
- **Multi-format Support**: JSON, XML, SOAP, and plain text
- **Resource Monitoring**: CPU, memory, and disk usage monitoring
- **Metrics**: Prometheus-compatible metrics endpoint
- **Topic Authorization**: Configurable allowed topics
- **Avro Serialization**: Efficient message serialization
- **Structured Logging**: Comprehensive logging with request correlation
- **Health Checks**: Built-in resource threshold monitoring

## 🏗️ Architecture

- **HTTP Server**: Netty-based HTTP server
- **Message Processing**: Asynchronous Kafka producer
- **Serialization**: Apache Avro for efficient message encoding
- **Monitoring**: Prometheus metrics and structured logging

## 🚀 Quick Start

### Prerequisites

- Java 21+
- Apache Kafka cluster
- Maven 3.9+

### Build

```bash
# Clone the repository
git clone <repository-url>
cd teuthis

# Build with Maven
./mvnw clean compile

# Run tests
./mvnw test

# Package
./mvnw package
```

### Run

```bash
# Run directly with Maven
./mvnw exec:java -Dexec.mainClass="com.github.darioajr.teuthis.TeuthisServer"

# Or run the packaged JAR
java -jar target/teuthis-1.0.0-SNAPSHOT.jar
```

## ⚙️ Configuration

Configuration is managed through [`application.properties`](src/main/resources/application.properties):

```properties
# Server Configuration
server.port=8080
netty.boss.threads=1
netty.worker.threads=0

# Kafka Configuration
kafka.bootstrap.servers=localhost:9092
kafka.key.serializer=org.apache.kafka.common.serialization.StringSerializer
kafka.value.serializer=org.apache.kafka.common.serialization.ByteArraySerializer
kafka.enable.idempotence=true
kafka.acks=all
kafka.retries=2147483647
kafka.max.in.flight.requests.per.connection=1

# Topic Security
kafka.allowed.topics=orders,users,transactions
kafka.partition.key=ordering-key

# Resource Monitoring
resources.threshold=0.85
retry.after.seconds=30

# Thread Pool
kafka.thread.pool.size=8
```

## 🔌 API Usage

### Publish Message

Send messages to Kafka topics via HTTP POST:

```bash
# JSON message
curl -X POST http://localhost:8080/publish/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId": 12345, "amount": 99.99, "timestamp": 1640995200000}'

# XML message
curl -X POST http://localhost:8080/publish/users \
  -H "Content-Type: application/xml" \
  -d '<user><id>123</id><name>John Doe</name></user>'

# Plain text
curl -X POST http://localhost:8080/publish/logs \
  -H "Content-Type: text/plain" \
  -d 'Application started successfully'
```

### Metrics

Access Prometheus-compatible metrics:

```bash
curl http://localhost:8080/metrics
```

## 📊 Monitoring

The application provides comprehensive monitoring:

- **Request Metrics**: Total requests, success/error rates
- **Kafka Metrics**: Messages sent, errors, latency
- **System Metrics**: CPU, memory, disk usage
- **Structured Logging**: Request correlation with MDC

### Log Files

- `logs/teuthis.log` - Application logs
- `logs/performance.log` - Performance metrics
- `logs/security.log` - Security events

## 🧪 Testing

### Unit Tests

```bash
# Run unit tests
./mvnw test
```

### Integration Tests

```bash
# Run integration tests (requires running Kafka)
./mvnw test -Dintegration.tests=true
```

### Performance Tests

```bash
# Run performance tests
./mvnw test -Dperformance.tests=true
```

## 🏗️ Development

### Project Structure

```
src/
├── main/
│   ├── java/com/github/darioajr/teuthis/
│   │   ├── TeuthisServer.java           # Main application
│   │   ├── avro/Message.java               # Avro generated classes
│   │   └── infra/
│   │       ├── Config.java                 # Configuration management
│   │       ├── Metrics.java                # Prometheus metrics
│   │       └── MetricsHandler.java         # Metrics HTTP handler
│   └── resources/
│       ├── application.properties          # Configuration
│       ├── logback.xml                     # Logging configuration
│       └── avro/message.avsc              # Avro schema
└── test/
    └── java/com/github/darioajr/teuthis/
        ├── BasicTest.java                  # Basic functionality tests
        ├── TeuthisServerTest.java       # Server unit tests
        ├── TeuthisApplicationTests.java    # Application tests
        ├── integration/IntegrationTest.java # Integration tests
        └── performance/PerformanceTest.java # Performance tests
```

### Key Components

- **[`TeuthisServer`](src/main/java/com/github/darioajr/teuthis/TeuthisServer.java)**: Main application class
- **[`Config`](src/main/java/com/github/darioajr/teuthis/infra/Config.java)**: Configuration management
- **[`Metrics`](src/main/java/com/github/darioajr/teuthis/infra/Metrics.java)**: Prometheus metrics
- **[`Message`](src/main/java/com/github/darioajr/teuthis/avro/Message.java)**: Avro message schema

### Building from Source

```bash
# Generate Avro classes
./mvnw avro:schema

# Compile
./mvnw compile

# Run tests
./mvnw test

# Package
./mvnw package -DskipTests
```

## 🐳 Docker Support

The project includes dev container support with [`Dockerfile`](.devcontainer/Dockerfile):

```bash
# Build image
docker build -f .devcontainer/Dockerfile -t teuthis .

# Run container
docker run -p 8080:8080 teuthis
```

## 🐳 Docker Compose

# Build e start tudo
```bash
docker-compose up -d
```

# Ver logs do Teuthis
```bash
docker-compose logs -f teuthis-app
```

# Testar endpoints
```bash
curl http://localhost:8080/health
curl http://localhost:8080/metrics
```

# Test publish
```bash
curl -X POST http://localhost:8080/publish/test-topic \
  -H "Content-Type: application/json" \
  -d '{"message": "Hello Teuthis!"}'
```

## URLs importantes:

🌐 Teuthis: http://localhost:8080
📊 Kafka UI: http://localhost:8090
📈 Prometheus: http://localhost:9090
📊 Grafana: http://localhost:3000 (admin/teuthis123)

## 📈 Performance

Designed for high throughput:

- **Non-blocking I/O**: Netty NIO event loops
- **Async Processing**: Kafka operations in thread pool
- **Resource Monitoring**: Automatic throttling at 85% resource usage
- **Efficient Serialization**: Apache Avro binary format

Performance test results (see [`PerformanceTest`](src/test/java/com/github/darioajr/teuthis/performance/PerformanceTest.java)):
- Handles 1000+ concurrent requests
- Sub-100ms average response time
- Large payload support (1MB+)

## 🔒 Security

- **Topic Authorization**: Configurable allowed topics
- **Request Validation**: Method and path validation
- **Resource Protection**: Automatic throttling under high load
- **Security Logging**: Comprehensive audit trail

## 📄 License

Licensed under the [Apache License 2.0](LICENSE).



## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## 📞 Support

For questions and support, please open an issue in the repository.
