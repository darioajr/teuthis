package com.github.darioajr.teuthis.performance;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@DisplayName("Performance Tests")
@EnabledIfSystemProperty(named = "performance.tests", matches = "true")
class PerformanceTest {

    private static final String BASE_URL = "http://localhost:8080";
    private HttpClient httpClient;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        executor = Executors.newFixedThreadPool(50);
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("Should handle 1000 concurrent requests")
    void shouldHandle1000ConcurrentRequests() throws Exception {
        int numberOfRequests = 1000;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        CompletableFuture<?>[] futures = new CompletableFuture<?>[numberOfRequests];
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < numberOfRequests; i++) {
            final int requestId = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    String payload = "{\"requestId\": " + requestId + ", \"timestamp\": " + System.currentTimeMillis() + "}";
                    
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/publish/test-topic"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .timeout(Duration.ofSeconds(10))
                        .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    
                    if (response.statusCode() == 201) {
                        successCount.incrementAndGet();
                    } else {
                        errorCount.incrementAndGet();
                    }
                } catch (IOException | InterruptedException e) {
                    errorCount.incrementAndGet();
                    System.err.println("Request " + requestId + " failed: " + e.getMessage());
                }
            }, executor);
        }
        
        CompletableFuture.allOf(futures).join();
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        System.out.println("Performance Test Results:");
        System.out.println("Total requests: " + numberOfRequests);
        System.out.println("Successful requests: " + successCount.get());
        System.out.println("Failed requests: " + errorCount.get());
        System.out.println("Total time: " + duration + "ms");
        System.out.println("Requests per second: " + (numberOfRequests * 1000.0 / duration));
        
        // Assert that at least 95% of requests succeeded
        double successRate = (double) successCount.get() / numberOfRequests;
        assertTrue(successRate >= 0.95, "Success rate should be at least 95%, was: " + (successRate * 100) + "%");
        
        // Assert that average response time is reasonable (less than 100ms per request)
        double avgResponseTime = (double) duration / numberOfRequests;
        assertTrue(avgResponseTime < 100, "Average response time should be less than 100ms, was: " + avgResponseTime + "ms");
    }

    @Test
    @DisplayName("Should handle large payload efficiently")
    void shouldHandleLargePayloadEfficiently() throws IOException, InterruptedException {
        // Create 1MB payload
        StringBuilder largePayload = new StringBuilder();
        largePayload.append("{\"data\": \"");
        for (int i = 0; i < 100000; i++) {
            largePayload.append("0123456789");
        }
        largePayload.append("\"}");
        
        long startTime = System.currentTimeMillis();
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/publish/test-topic"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(largePayload.toString()))
            .timeout(Duration.ofSeconds(30))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        assertEquals(201, response.statusCode());
        assertTrue(duration < 5000, "Large payload should be processed in less than 5 seconds, took: " + duration + "ms");
        
        System.out.println("Large payload test: " + largePayload.length() + " bytes processed in " + duration + "ms");
    }
}