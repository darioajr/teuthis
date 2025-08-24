package com.github.darioajr.teuthis.queue;

import java.time.Instant;

/**
 * Resultado de uma operação de limpeza de fila.
 */
public class QueueCleanupResult {
    
    private final boolean success;
    private final String topicName;
    private final String message;
    private final long durationMs;
    private final Instant timestamp;
    private final String errorCode;
    
    private QueueCleanupResult(boolean success, String topicName, String message, 
                              long durationMs, String errorCode) {
        this.success = success;
        this.topicName = topicName;
        this.message = message;
        this.durationMs = durationMs;
        this.timestamp = Instant.now();
        this.errorCode = errorCode;
    }
    
    public static QueueCleanupResult success(String topicName, long durationMs) {
        return new QueueCleanupResult(true, topicName, 
            "Queue cleanup completed successfully", durationMs, null);
    }
    
    public static QueueCleanupResult error(String message) {
        return new QueueCleanupResult(false, null, message, 0, "CLEANUP_ERROR");
    }
    
    public static QueueCleanupResult error(String topicName, String message, String errorCode) {
        return new QueueCleanupResult(false, topicName, message, 0, errorCode);
    }
    
    // Getters
    public boolean isSuccess() { return success; }
    public String getTopicName() { return topicName; }
    public String getMessage() { return message; }
    public long getDurationMs() { return durationMs; }
    public Instant getTimestamp() { return timestamp; }
    public String getErrorCode() { return errorCode; }
    
    @Override
    public String toString() {
        return String.format("QueueCleanupResult{success=%s, topic='%s', message='%s', duration=%dms, timestamp=%s}", 
                           success, topicName, message, durationMs, timestamp);
    }
}
