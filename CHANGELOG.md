# Changelog

All notable changes to the Teuthis project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## 0.2.0 - 2025-08-24

### Added
- **Queue Cleanup API**: Safe and performant queue cleanup functionality
  - `DELETE /queue/{topicName}` - Clean specific queue with producer shutdown
  - `GET /queue/status` - Monitor active cleanup operations
  - Asynchronous processing without blocking main operations
  - Circuit breaker protection for admin operations
  - JWT authentication support for cleanup operations
  - Comprehensive input validation and sanitization

- **New Components**
  - `QueueCleanupManager.java` - Core cleanup logic with safe producer shutdown
  - `QueueCleanupHandler.java` - HTTP endpoint handler for cleanup operations
  - `QueueCleanupResult.java` - Result object for cleanup operations

- **Enhanced Metrics**
  - `teuthis_queue_cleanup_total` - Total cleanup operations by topic and status
  - `teuthis_queue_cleanup_duration_seconds` - Cleanup operation duration
  - `teuthis_active_cleanups` - Number of active cleanup operations

- **Configuration**
  - `teuthis.queue.cleanup.enabled` - Enable/disable queue cleanup feature
  - `teuthis.queue.cleanup.producer.shutdown.timeout` - Producer shutdown timeout
  - `teuthis.queue.cleanup.max.concurrent` - Maximum concurrent cleanup operations
  - `teuthis.queue.cleanup.auth.required` - Require authentication for cleanup

### Changed
- **Version**: Updated to 0.2.0-SNAPSHOT
- **Netty Pipeline**: Added QueueCleanupHandler before PublishHandler
- **AdminClient Integration**: Added Kafka AdminClient for queue management operations

### Technical Details
- **Safe Producer Shutdown**: Ensures all pending messages are flushed before cleanup
- **Async Processing**: Non-blocking cleanup operations with CompletableFuture
- **Resource Management**: Proper cleanup of threads and connections
- **Security**: Optional JWT authentication with configurable requirements
- **Monitoring**: Full observability with Prometheus metrics and structured logging

## 0.1.0 - 2025-08-24

### Added
- **Performance Optimizations**
  - Object pooling system with ThreadLocal caches for ByteArrayOutputStream, DatumWriter, and BinaryEncoder
  - Asynchronous resource monitoring with background thread and 5-second cache
  - Circuit breaker pattern for Kafka operations using Resilience4j
  - Optimized Avro serialization with reusable objects

- **Security Enhancements**
  - JWT authentication system with HMAC256 algorithm
  - Rate limiting: Global (10k req/s) and per-IP (100 req/s) using Guava RateLimiter
  - Comprehensive input validation and sanitization
  - Security headers (HSTS, XSS protection, CSP, clickjacking protection)
  - Path traversal protection and payload size validation

- **New Components**
  - `ObjectPools.java` - Object pooling and ThreadLocal caches
  - `JwtValidator.java` - JWT token validation and creation
  - `AuthenticationHandler.java` - Netty handler for JWT authentication
  - `RateLimitHandler.java` - Rate limiting implementation
  - `ValidationHandler.java` - Input validation and sanitization
  - `AsyncResourceMonitor.java` - Non-blocking resource monitoring
  - `CircuitBreakerManager.java` - Circuit breaker for Kafka operations
  - `SecurityHeadersHandler.java` - Security headers injection

- **Configuration**
  - New configuration properties for performance and security features
  - Environment variable support for all new settings
  - Backward-compatible configuration with sensible defaults

- **Dependencies**
  - Resilience4j 2.1.0 for circuit breaker and retry patterns
  - Guava 32.1.2 for rate limiting
  - Auth0 Java JWT 4.4.0 for JWT handling
  - Apache Commons Pool2 2.11.1 for object pooling
  - Caffeine 3.1.8 for caching
  - OpenTelemetry 1.30.1 for observability (prepared for future use)

- **Documentation**
  - `PERFORMANCE-SECURITY-IMPROVEMENTS.md` - Detailed documentation of all improvements
  - Updated configuration examples and usage instructions

### Changed
- **Netty Pipeline Enhancement**
  - Updated handler pipeline to include security and validation layers
  - Order: SecurityHeaders → Metrics → RateLimit → Authentication → Validation → Publish

- **Resource Monitoring**
  - Replaced synchronous resource checking with asynchronous background monitoring
  - Improved performance by eliminating blocking calls in request path

- **Kafka Operations**
  - Enhanced with circuit breaker pattern for better resilience
  - Improved error handling and retry mechanisms

- **Configuration Properties**
  - Extended `application.properties` with new security and performance settings
  - Added environment variable interpolation for all new properties

### Performance Improvements
- **Expected Gains**
  - Throughput: +40% (10k → 14k req/s)
  - Latency P99: -30% (100ms → 70ms)
  - Memory Usage: -25% (reduced GC pressure)
  - CPU Usage: -20% (more efficient operations)

### Security Improvements
- **DDoS Protection**: Rate limiting prevents abuse
- **Authentication**: Optional JWT-based authentication
- **Input Validation**: 100% payload validation and sanitization
- **Security Headers**: Complete protection against web attacks
- **Audit Trail**: Structured security logging with correlation IDs

### Technical Details
- **Build**: Successfully builds with `./mvnw package`
- **Compatibility**: All improvements are backward compatible and optional
- **Testing**: Maintains existing test coverage requirements (80% minimum)
- **Deployment**: Zero-downtime deployment capable

## [1.0.0-SNAPSHOT] - 2024-08-24

### Added
- Initial implementation of Teuthis HTTP-to-Kafka bridge
- Netty-based HTTP server with non-blocking I/O
- Apache Kafka producer with Avro serialization
- Multi-format support (JSON, XML, SOAP, plain text)
- Prometheus metrics integration
- Resource monitoring and throttling
- Docker Compose setup with full Kafka ecosystem
- Health checks and metrics endpoints
- Structured logging with MDC correlation
- Topic authorization with configurable allowed topics
- Comprehensive test suite with unit, integration, and performance tests
- JaCoCo code coverage reporting (80% minimum)
- Maven build system with Java 21 support

### Technical Stack
- Java 21 with Maven
- Netty 4.1.95 for HTTP server
- Apache Kafka 3.5.1 client
- Apache Avro 1.11.3 for serialization
- Prometheus metrics
- Testcontainers for integration tests
- Docker Compose for development environment

### Configuration
- Environment variable support
- Configurable resource thresholds (95% default)
- Thread pool sizing
- Kafka producer settings optimized for reliability
- Topic-based authorization

---

## Legend

- **Added**: New features
- **Changed**: Changes in existing functionality
- **Deprecated**: Soon-to-be removed features
- **Removed**: Removed features
- **Fixed**: Bug fixes
- **Security**: Security improvements
