package com.github.darioajr.teuthis.integration;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Integration Tests")
@EnabledIfSystemProperty(named = "integration.tests", matches = "true")
class IntegrationTest {

    private static final String BASE_URL = "http://localhost:8080";
    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Test
    @DisplayName("Should publish message to valid topic")
    void shouldPublishMessageToValidTopic() throws IOException, InterruptedException {
        String jsonPayload = "{\"message\": \"Integration test\", \"timestamp\": " + System.currentTimeMillis() + "}";
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/publish/test-topic"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
            .timeout(Duration.ofSeconds(30))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        assertEquals(201, response.statusCode());
    }

    @Test
    @DisplayName("Should reject unauthorized topic")
    void shouldRejectUnauthorizedTopic() throws IOException, InterruptedException {
        String jsonPayload = "{\"message\": \"Unauthorized test\"}";
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/publish/unauthorized-topic"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
            .timeout(Duration.ofSeconds(30))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("Tópico não permitido"));
    }

    @Test
    @DisplayName("Should reject GET method")
    void shouldRejectGetMethod() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/publish/test-topic"))
            .GET()
            .timeout(Duration.ofSeconds(30))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        assertEquals(405, response.statusCode());
    }

    @Test
    @DisplayName("Should handle metrics endpoint")
    void shouldHandleMetricsEndpoint() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/metrics"))
            .GET()
            .timeout(Duration.ofSeconds(30))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        // Metrics endpoint should exist
        assertNotEquals(404, response.statusCode());
    }
}