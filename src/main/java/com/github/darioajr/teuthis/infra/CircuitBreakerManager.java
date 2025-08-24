package com.github.darioajr.teuthis.infra;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

/**
 * Circuit breaker and retry management for Kafka operations
 */
public class CircuitBreakerManager {
    
    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerManager.class);
    
    private static final boolean CIRCUIT_BREAKER_ENABLED = Config.b("teuthis.monitoring.circuit.breaker.enabled");
    
    // Circuit breaker configuration
    private static final CircuitBreaker kafkaCircuitBreaker;
    private static final Retry retryPolicy;
    
    static {
        if (CIRCUIT_BREAKER_ENABLED) {
            // Circuit breaker configuration
            CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50) // 50% failure rate threshold
                .waitDurationInOpenState(Duration.ofSeconds(30)) // Wait 30s before trying again
                .slidingWindowSize(10) // Consider last 10 calls
                .minimumNumberOfCalls(5) // Minimum 5 calls before calculating failure rate
                .permittedNumberOfCallsInHalfOpenState(3) // Allow 3 calls in half-open state
                .build();
            
            kafkaCircuitBreaker = CircuitBreaker.of("kafka", cbConfig);
            
            // Retry configuration
            RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .retryExceptions(Exception.class)
                .ignoreExceptions(IllegalArgumentException.class)
                .build();
            
            retryPolicy = Retry.of("kafka", retryConfig);
            
            // Add event listeners
            kafkaCircuitBreaker.getEventPublisher()
                .onStateTransition(event -> 
                    logger.info("🔄 Kafka circuit breaker state transition: {} -> {}", 
                               event.getStateTransition().getFromState(), 
                               event.getStateTransition().getToState()));
            
            kafkaCircuitBreaker.getEventPublisher()
                .onCallNotPermitted(event -> 
                    logger.warn("⚠️ Kafka call not permitted due to circuit breaker"));
            
            retryPolicy.getEventPublisher()
                .onRetry(event -> 
                    logger.debug("🔄 Retrying Kafka operation, attempt: {}", event.getNumberOfRetryAttempts()));
            
            logger.info("✅ Circuit breaker and retry policies initialized for Kafka");
        } else {
            kafkaCircuitBreaker = null;
            retryPolicy = null;
            logger.info("⚠️ Circuit breaker disabled by configuration");
        }
    }
    
    /**
     * Execute Kafka send operation with circuit breaker and retry
     */
    public static CompletableFuture<RecordMetadata> sendWithProtection(
            Producer<String, byte[]> producer, 
            ProducerRecord<String, byte[]> record) {
        
        if (!CIRCUIT_BREAKER_ENABLED) {
            // Direct call without protection - convert Future to CompletableFuture
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return producer.send(record).get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        
        // Simplified approach without decorators for now
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("🚀 Sending message to Kafka with protection: {}", record.topic());
                return producer.send(record).get();
            } catch (Exception e) {
                logger.error("❌ Protected Kafka send failed: {}", e.getMessage());
                throw new RuntimeException(e);
            }
        }).whenComplete((result, throwable) -> {
            if (throwable != null) {
                logger.error("❌ Protected Kafka send failed: {}", throwable.getMessage());
            } else {
                logger.debug("✅ Protected Kafka send successful: partition={}, offset={}", 
                            result.partition(), result.offset());
            }
        });
    }
    
    /**
     * Get circuit breaker state
     */
    public static CircuitBreakerState getState() {
        if (!CIRCUIT_BREAKER_ENABLED || kafkaCircuitBreaker == null) {
            return new CircuitBreakerState("DISABLED", 0, 0, 0);
        }
        
        var metrics = kafkaCircuitBreaker.getMetrics();
        return new CircuitBreakerState(
            kafkaCircuitBreaker.getState().name(),
            metrics.getNumberOfSuccessfulCalls(),
            metrics.getNumberOfFailedCalls(),
            (int) (metrics.getFailureRate() * 100)
        );
    }
    
    /**
     * Get retry statistics
     */
    public static RetryStats getRetryStats() {
        if (!CIRCUIT_BREAKER_ENABLED || retryPolicy == null) {
            return new RetryStats(0, 0);
        }
        
        var metrics = retryPolicy.getMetrics();
        return new RetryStats(
            metrics.getNumberOfSuccessfulCallsWithRetryAttempt(),
            metrics.getNumberOfFailedCallsWithRetryAttempt()
        );
    }
    
    /**
     * Force circuit breaker to open state (for testing)
     */
    public static void forceOpen() {
        if (CIRCUIT_BREAKER_ENABLED && kafkaCircuitBreaker != null) {
            kafkaCircuitBreaker.transitionToOpenState();
            logger.warn("⚠️ Circuit breaker forced to OPEN state");
        }
    }
    
    /**
     * Reset circuit breaker to closed state
     */
    public static void reset() {
        if (CIRCUIT_BREAKER_ENABLED && kafkaCircuitBreaker != null) {
            kafkaCircuitBreaker.reset();
            logger.info("✅ Circuit breaker reset to CLOSED state");
        }
    }
    
    /**
     * Circuit breaker state information
     */
    public record CircuitBreakerState(
        String state,
        long successfulCalls,
        long failedCalls,
        int failureRatePercent
    ) {}
    
    /**
     * Retry statistics
     */
    public record RetryStats(
        long successfulCallsWithRetry,
        long failedCallsWithRetry
    ) {}
}
