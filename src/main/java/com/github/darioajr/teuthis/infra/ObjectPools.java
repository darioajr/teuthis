package com.github.darioajr.teuthis.infra;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.darioajr.teuthis.avro.Message;

/**
 * Object pools for performance optimization
 */
public class ObjectPools {
    
    private static final Logger logger = LoggerFactory.getLogger(ObjectPools.class);
    
    // ThreadLocal caches for frequently used objects
    private static final ThreadLocal<ByteArrayOutputStream> BUFFER_CACHE = 
        ThreadLocal.withInitial(() -> new ByteArrayOutputStream(8192));
    
    private static final ThreadLocal<DatumWriter<Message>> AVRO_WRITER_CACHE = 
        ThreadLocal.withInitial(() -> new SpecificDatumWriter<>(Message.class));
    
    private static final ThreadLocal<BinaryEncoder> ENCODER_CACHE = 
        ThreadLocal.withInitial(() -> EncoderFactory.get().binaryEncoder(null, null));
    
    // Pool for ByteArrayOutputStream objects
    private static final ConcurrentLinkedQueue<ByteArrayOutputStream> bufferPool = 
        new ConcurrentLinkedQueue<>();
    
    private static final int MAX_POOL_SIZE = Config.i("teuthis.performance.buffer.pool.size");
    private static final int INITIAL_BUFFER_SIZE = 8192;
    
    static {
        // Pre-populate buffer pool
        for (int i = 0; i < Math.min(MAX_POOL_SIZE, 10); i++) {
            bufferPool.offer(new ByteArrayOutputStream(INITIAL_BUFFER_SIZE));
        }
        logger.info("✅ Object pools initialized with {} pre-allocated buffers", bufferPool.size());
    }
    
    /**
     * Get a reusable ByteArrayOutputStream from ThreadLocal cache
     */
    public static ByteArrayOutputStream getBuffer() {
        ByteArrayOutputStream buffer = BUFFER_CACHE.get();
        buffer.reset(); // Clear previous content
        return buffer;
    }
    
    /**
     * Get a pooled ByteArrayOutputStream (alternative to ThreadLocal)
     */
    public static ByteArrayOutputStream borrowBuffer() {
        ByteArrayOutputStream buffer = bufferPool.poll();
        if (buffer == null) {
            buffer = new ByteArrayOutputStream(INITIAL_BUFFER_SIZE);
            logger.debug("🔧 Created new buffer, pool was empty");
        } else {
            buffer.reset();
        }
        return buffer;
    }
    
    /**
     * Return a buffer to the pool
     */
    public static void returnBuffer(ByteArrayOutputStream buffer) {
        if (buffer != null && bufferPool.size() < MAX_POOL_SIZE) {
            buffer.reset();
            bufferPool.offer(buffer);
        }
    }
    
    /**
     * Get cached Avro writer
     */
    public static DatumWriter<Message> getAvroWriter() {
        return AVRO_WRITER_CACHE.get();
    }
    
    /**
     * Get cached binary encoder
     */
    public static BinaryEncoder getEncoder(ByteArrayOutputStream output) {
        BinaryEncoder encoder = ENCODER_CACHE.get();
        return EncoderFactory.get().binaryEncoder(output, encoder);
    }
    
    /**
     * Get pool statistics
     */
    public static PoolStats getStats() {
        return new PoolStats(bufferPool.size(), MAX_POOL_SIZE);
    }
    
    /**
     * Pool statistics record
     */
    public record PoolStats(int availableBuffers, int maxPoolSize) {
        public double utilizationRatio() {
            return maxPoolSize > 0 ? (double) (maxPoolSize - availableBuffers) / maxPoolSize : 0.0;
        }
    }
    
    /**
     * Clear ThreadLocal caches (for cleanup)
     */
    public static void clearThreadLocalCaches() {
        BUFFER_CACHE.remove();
        AVRO_WRITER_CACHE.remove();
        ENCODER_CACHE.remove();
    }
}
