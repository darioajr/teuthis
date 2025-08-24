package com.github.darioajr.teuthis.infra;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;

/**
 * Prometheus metrics for Teuthis
 */
public class Metrics {
    
    // Request metrics
    public static final Counter requestsTotal = Counter.build()
            .name("teuthis_requests_total")
            .help("Total number of HTTP requests")
            .labelNames("method", "status")
            .register();
    
        // Latency and performance metrics
    public static final Histogram publishLatency = Histogram.build()
            .name("teuthis_publish_latency_seconds")
            .help("Latency of publish operations in seconds")
            .buckets(0.001, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0)
            .register();

    public static final Counter messagesTotal = Counter.build()
            .name("teuthis_messages_total")
            .help("Total number of messages processed")
            .register();

    public static final Counter messagesErrors = Counter.build()
            .name("teuthis_messages_errors_total")
            .help("Total number of message processing errors")
            .register();
    
    public static final Histogram requestDuration = Histogram.build()
            .name("teuthis_request_duration_seconds")
            .help("Time taken to process HTTP request")
            .labelNames("method", "endpoint", "status")
            .register();
    
    // Resource usage metrics
    public static final Gauge cpuUsage = Gauge.build()
            .name("teuthis_cpu_usage")
            .help("Current CPU usage ratio (0.0 to 1.0)")
            .register();

    public static final Gauge memoryUsage = Gauge.build()
            .name("teuthis_memory_usage")
            .help("Current memory usage ratio (0.0 to 1.0)")
            .register();

    public static final Gauge diskUsage = Gauge.build()
            .name("teuthis_disk_usage")
            .help("Current disk usage ratio (0.0 to 1.0)")
            .register();
    
    // Resource threshold configuration (for dynamic monitoring)
    public static final Gauge resourceThreshold = Gauge.build()
            .name("teuthis_resource_threshold")
            .help("Configured resource threshold for rejecting requests (0.0 to 1.0)")
            .register();    // Connection metrics
    public static final Gauge activeConnections = Gauge.build()
            .name("teuthis_active_connections")
            .help("Number of active HTTP connections")
            .register();
    
    // Kafka metrics
    public static final Gauge kafkaConnectionStatus = Gauge.build()
            .name("teuthis_kafka_connection_status")
            .help("Kafka connection status (1=connected, 0=disconnected)")
            .register();
    
    // Queue cleanup metrics
    public static final Counter queueCleanupTotal = Counter.build()
            .name("teuthis_queue_cleanup_total")
            .help("Total number of queue cleanup operations")
            .labelNames("topic", "status")
            .register();
    
    public static final Histogram queueCleanupDuration = Histogram.build()
            .name("teuthis_queue_cleanup_duration_seconds")
            .help("Duration of queue cleanup operations in seconds")
            .labelNames("topic")
            .buckets(1.0, 5.0, 10.0, 30.0, 60.0, 120.0, 300.0)
            .register();
    
    public static final Gauge activeCleanups = Gauge.build()
            .name("teuthis_active_cleanups")
            .help("Number of active queue cleanup operations")
            .register();
    
    /**
     * Records a queue cleanup operation metric.
     */
    public void recordQueueCleanup(String topicName, long durationMs, boolean success) {
        String status = success ? "success" : "error";
        queueCleanupTotal.labels(topicName, status).inc();
        
        if (success) {
            queueCleanupDuration.labels(topicName).observe(durationMs / 1000.0);
        }
    }
}
