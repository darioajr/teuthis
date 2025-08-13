package com.github.darioajr.teuthis.infra;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration utility class for Teuthis
 */
public class Config {
    
    private static final Logger logger = LoggerFactory.getLogger(Config.class);
    private static final Properties props = new Properties();
    
    static {
        loadConfiguration();
    }
    
    private static void loadConfiguration() {
        try {
            // Load from application.properties in classpath
            try (InputStream is = Config.class.getClassLoader().getResourceAsStream("application.properties")) {
                if (is != null) {
                    props.load(is);
                    logger.info("✅ Loaded configuration from application.properties");
                }
            }
            
            // Override with system properties
            Properties systemProps = System.getProperties();
            for (String key : systemProps.stringPropertyNames()) {
                props.setProperty(key, systemProps.getProperty(key));
            }
            
            // Override with environment variables (convert dots to underscores)
            System.getenv().forEach((key, value) -> {
                String propKey = key.toLowerCase().replace('_', '.');
                props.setProperty(propKey, value);
                props.setProperty(key, value); // Also keep original format
            });
            
            logger.info("✅ Configuration loaded successfully with {} properties", props.size());
            
        } catch (IOException e) {
            logger.warn("⚠️ Could not load application.properties: {}", e.getMessage());
        }
    }
    
    /**
     * Get string property with interpolation support
     */
    public static String str(String key) {
        String value = props.getProperty(key);
        if (value == null) {
            logger.warn("⚠️ Property not found: {}", key);
            return "";
        }
        return interpolate(value);
    }
    
    /**
     * Get integer property
     */
    public static int i(String key) {
        String value = str(key);
        if (value.isEmpty()) {
            logger.warn("⚠️ Integer property not found: {}, using default 0", key);
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.error("❌ Invalid integer value for {}: {}", key, value);
            throw new IllegalArgumentException("Invalid integer value for " + key + ": " + value, e);
        }
    }
    
    /**
     * Get double property
     */
    public static double d(String key) {
        String value = str(key);
        if (value.isEmpty()) {
            logger.warn("⚠️ Double property not found: {}, using default 0.0", key);
            return 0.0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            logger.error("❌ Invalid double value for {}: {}", key, value);
            throw new IllegalArgumentException("Invalid double value for " + key + ": " + value, e);
        }
    }
    
    /**
     * Get boolean property
     */
    public static boolean b(String key) {
        String value = str(key);
        if (value.isEmpty()) {
            logger.warn("⚠️ Boolean property not found: {}, using default false", key);
            return false;
        }
        return Boolean.parseBoolean(value);
    }
    
    /**
     * Get allowed topics list
     */
    public static List<String> allowedTopics() {
        String topics = str("allowed.topics");
        if (topics.isEmpty()) {
            logger.warn("⚠️ No allowed topics configured, using defaults");
            return Arrays.asList("test-topic", "events", "logs", "metrics", "health");
        }
        return Arrays.asList(topics.split(","));
    }
    
    /**
     * Interpolate environment variables and system properties
     * Supports ${VAR:default} syntax
     */
    private static String interpolate(String value) {
        if (value == null || !value.contains("${")) {
            return value;
        }
        
        StringBuilder result = new StringBuilder();
        int start = 0;
        int pos;
        
        while ((pos = value.indexOf("${", start)) != -1) {
            result.append(value, start, pos);
            
            int end = value.indexOf("}", pos + 2);
            if (end == -1) {
                result.append(value.substring(pos));
                break;
            }
            
            String varExpression = value.substring(pos + 2, end);
            String varName;
            String defaultValue = "";
            
            int colonPos = varExpression.indexOf(':');
            if (colonPos != -1) {
                varName = varExpression.substring(0, colonPos);
                defaultValue = varExpression.substring(colonPos + 1);
            } else {
                varName = varExpression;
            }
            
            // Try system property first, then environment variable
            String varValue = System.getProperty(varName);
            if (varValue == null) {
                varValue = System.getenv(varName);
            }
            if (varValue == null) {
                varValue = System.getenv(varName.toUpperCase().replace('.', '_'));
            }
            
            result.append(varValue != null ? varValue : defaultValue);
            start = end + 1;
        }
        
        result.append(value.substring(start));
        return result.toString();
    }
    
    /**
     * Get all properties (for debugging)
     */
    public static Properties getAllProperties() {
        return new Properties(props);
    }
    
    /**
     * Print configuration summary
     */
    public static void printSummary() {
        logger.info("📋 Configuration Summary:");
        logger.info("  Server Port: {}", str("server.port"));
        logger.info("  Kafka Bootstrap: {}", str("kafka.bootstrap.servers"));
        logger.info("  Schema Registry: {}", str("schema.registry.url"));
        logger.info("  Boss Threads: {}", str("netty.boss.threads"));
        logger.info("  Worker Threads: {}", str("netty.worker.threads"));
        logger.info("  Kafka Threads: {}", str("kafka.thread.pool.size"));
        logger.info("  Allowed Topics: {}", String.join(", ", allowedTopics()));
    }
}
