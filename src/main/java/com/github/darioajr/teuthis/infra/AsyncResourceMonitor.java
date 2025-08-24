package com.github.darioajr.teuthis.infra;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.management.OperatingSystemMXBean;

/**
 * Asynchronous resource monitoring to avoid blocking request processing
 */
public class AsyncResourceMonitor {
    
    private static final Logger logger = LoggerFactory.getLogger(AsyncResourceMonitor.class);
    
    private static final double THRESHOLD = Config.d("resources.threshold");
    private static final long CHECK_INTERVAL = Config.i("teuthis.monitoring.resource.check.interval");
    
    // Cached resource status
    private static final AtomicReference<ResourceStatus> cachedStatus = 
        new AtomicReference<>(ResourceStatus.OK);
    
    private static final AtomicLong lastUpdateTime = new AtomicLong(0);
    
    // Background monitoring thread
    private static final ScheduledExecutorService resourceMonitor = 
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "resource-monitor");
            t.setDaemon(true);
            return t;
        });
    
    static {
        // Start background monitoring
        resourceMonitor.scheduleAtFixedRate(
            AsyncResourceMonitor::updateResourceStatus, 
            0, CHECK_INTERVAL, TimeUnit.MILLISECONDS
        );
        
        logger.info("✅ Async resource monitor started with {}ms interval", CHECK_INTERVAL);
    }
    
    /**
     * Get current resource status (non-blocking)
     */
    public static ResourceStatus getResourceStatus() {
        return cachedStatus.get();
    }
    
    /**
     * Check if resources are within acceptable limits
     */
    public static String checkResourceLimits() {
        ResourceStatus status = cachedStatus.get();
        
        if (status.isOverThreshold()) {
            StringBuilder limitedResources = new StringBuilder();
            boolean first = true;
            
            if (status.cpuUsage() > THRESHOLD) {
                limitedResources.append("CPU: ").append(String.format("%.1f%%", status.cpuUsage() * 100));
                first = false;
            }
            
            if (status.memoryUsage() > THRESHOLD) {
                if (!first) limitedResources.append(", ");
                limitedResources.append("RAM: ").append(String.format("%.1f%%", status.memoryUsage() * 100));
                first = false;
            }
            
            if (status.diskUsage() > THRESHOLD) {
                if (!first) limitedResources.append(", ");
                limitedResources.append("Disco: ").append(String.format("%.1f%%", status.diskUsage() * 100));
            }
            
            return "Recursos limitados: " + limitedResources.toString() + " ≥ " + 
                   String.format("%.0f%%", THRESHOLD * 100);
        }
        
        return null; // Resources are OK
    }
    
    /**
     * Background task to update resource status
     */
    private static void updateResourceStatus() {
        try {
            OperatingSystemMXBean os = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
            
            double cpu = os.getCpuLoad();
            if (cpu < 0) cpu = 0.0; // Handle case where CPU load is not available
            
            long totalRam = os.getTotalMemorySize();
            long freeRam = os.getFreeMemorySize();
            double memoryUsage = totalRam > 0 ? (double)(totalRam - freeRam) / totalRam : 0.0;
            
            File root = new File("/");
            long totalDisk = root.getTotalSpace();
            long freeDisk = root.getUsableSpace();
            double diskUsage = totalDisk > 0 ? (double)(totalDisk - freeDisk) / totalDisk : 0.0;
            
            // Update metrics
            Metrics.cpuUsage.set(cpu);
            Metrics.memoryUsage.set(memoryUsage);
            Metrics.diskUsage.set(diskUsage);
            
            // Determine if any resource is over threshold
            boolean overThreshold = cpu > THRESHOLD || memoryUsage > THRESHOLD || diskUsage > THRESHOLD;
            
            ResourceStatus newStatus = new ResourceStatus(
                cpu, memoryUsage, diskUsage, overThreshold, System.currentTimeMillis()
            );
            
            ResourceStatus oldStatus = cachedStatus.getAndSet(newStatus);
            lastUpdateTime.set(System.currentTimeMillis());
            
            // Log status changes
            if (oldStatus.isOverThreshold() != newStatus.isOverThreshold()) {
                if (newStatus.isOverThreshold()) {
                    logger.warn("⚠️ Resource usage exceeded threshold - CPU: {:.2f}%, RAM: {:.2f}%, Disk: {:.2f}%", 
                               cpu * 100, memoryUsage * 100, diskUsage * 100);
                } else {
                    logger.info("✅ Resource usage back to normal - CPU: {:.2f}%, RAM: {:.2f}%, Disk: {:.2f}%", 
                               cpu * 100, memoryUsage * 100, diskUsage * 100);
                }
            } else {
                logger.debug("📊 Resource status - CPU: {:.2f}%, RAM: {:.2f}%, Disk: {:.2f}%", 
                            cpu * 100, memoryUsage * 100, diskUsage * 100);
            }
            
        } catch (Exception e) {
            logger.error("❌ Error updating resource status: {}", e.getMessage(), e);
            // Keep the last known status in case of error
        }
    }
    
    /**
     * Get monitoring statistics
     */
    public static MonitorStats getStats() {
        return new MonitorStats(
            lastUpdateTime.get(),
            CHECK_INTERVAL,
            THRESHOLD,
            cachedStatus.get()
        );
    }
    
    /**
     * Shutdown the resource monitor
     */
    public static void shutdown() {
        resourceMonitor.shutdown();
        try {
            if (!resourceMonitor.awaitTermination(5, TimeUnit.SECONDS)) {
                resourceMonitor.shutdownNow();
            }
            logger.info("✅ Resource monitor shutdown completed");
        } catch (InterruptedException e) {
            resourceMonitor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Resource status record
     */
    public record ResourceStatus(
        double cpuUsage,
        double memoryUsage, 
        double diskUsage,
        boolean isOverThreshold,
        long timestamp
    ) {
        public static final ResourceStatus OK = new ResourceStatus(0.0, 0.0, 0.0, false, 0L);
    }
    
    /**
     * Monitoring statistics record
     */
    public record MonitorStats(
        long lastUpdateTime,
        long checkInterval,
        double threshold,
        ResourceStatus currentStatus
    ) {}
}
