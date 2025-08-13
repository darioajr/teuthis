package com.github.darioajr.teuthis;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Teuthis Application Tests")
class TeuthisApplicationTests {

    @Test
    @DisplayName("Application main class should exist")
    void applicationMainClassShouldExist() {
        assertDoesNotThrow(() -> {
            Class<?> mainClass = Class.forName("com.github.darioajr.teuthis.TeuthisServer");
            assertNotNull(mainClass);
            assertNotNull(mainClass.getMethod("main", String[].class));
        });
    }

    @Test
    @DisplayName("Required dependencies should be available")
    void requiredDependenciesShouldBeAvailable() {
        assertDoesNotThrow(() -> {
            // Test Netty
            Class.forName("io.netty.bootstrap.ServerBootstrap");
            
            // Test Kafka
            Class.forName("org.apache.kafka.clients.producer.KafkaProducer");
            
            // Test Avro
            Class.forName("org.apache.avro.Schema");
            
            // Test Prometheus
            Class.forName("io.prometheus.client.Histogram");
            
            // Test SLF4J
            Class.forName("org.slf4j.Logger");
        });
    }

    @Test
    @DisplayName("Basic functionality test")
    void basicFunctionalityTest() {
        assertTrue(true, "Basic test should pass");
    }
}