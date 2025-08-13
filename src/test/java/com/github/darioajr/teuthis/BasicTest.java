package com.github.darioajr.teuthis;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Basic Tests")
class BasicTest {

    @Test
    @DisplayName("Simple test that should pass")
    void simpleTest() {
        assertTrue(true);
        assertEquals(2, 1 + 1);
    }
    
    @Test
    @DisplayName("Test Java version")
    void testJavaVersion() {
        String javaVersion = System.getProperty("java.version");
        assertNotNull(javaVersion);
        assertTrue(javaVersion.startsWith("21"));
    }
    
    @Test
    @DisplayName("Test classpath dependencies")
    void testDependencies() {
        assertDoesNotThrow(() -> {
            // Test JUnit
            Class.forName("org.junit.jupiter.api.Test");
            
            // Test Netty
            Class.forName("io.netty.bootstrap.ServerBootstrap");
            
            // Test Kafka
            Class.forName("org.apache.kafka.clients.producer.KafkaProducer");
            
            // Test SLF4J
            Class.forName("org.slf4j.Logger");
        });
    }
}