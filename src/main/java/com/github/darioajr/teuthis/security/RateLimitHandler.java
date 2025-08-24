package com.github.darioajr.teuthis.security;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.darioajr.teuthis.infra.Config;
import com.google.common.util.concurrent.RateLimiter;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

/**
 * Rate limiting handler using Guava RateLimiter
 */
public class RateLimitHandler extends ChannelInboundHandlerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitHandler.class);
    private static final Logger securityLogger = LoggerFactory.getLogger("security");
    
    private static final double GLOBAL_RATE_LIMIT = Config.d("teuthis.security.rate.limit.global");
    private static final double PER_IP_RATE_LIMIT = Config.d("teuthis.security.rate.limit.per.ip");
    
    // Global rate limiter
    private static final RateLimiter globalLimiter = RateLimiter.create(GLOBAL_RATE_LIMIT);
    
    // Per-IP rate limiters
    private static final ConcurrentHashMap<String, RateLimiter> ipLimiters = new ConcurrentHashMap<>();
    
    // Cleanup scheduler for unused IP limiters
    private static final ScheduledExecutorService cleanupScheduler = 
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rate-limit-cleanup");
            t.setDaemon(true);
            return t;
        });
    
    static {
        // Clean up unused IP limiters every 5 minutes
        cleanupScheduler.scheduleAtFixedRate(RateLimitHandler::cleanupUnusedLimiters, 
                                           5, 5, TimeUnit.MINUTES);
        
        logger.info("✅ Rate limiting initialized - Global: {} req/s, Per-IP: {} req/s", 
                   GLOBAL_RATE_LIMIT, PER_IP_RATE_LIMIT);
    }
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest req) {
            String clientIp = extractClientIp(ctx);
            
            // Check global rate limit first
            if (!globalLimiter.tryAcquire()) {
                securityLogger.warn("Global rate limit exceeded from {}", clientIp);
                sendRateLimitResponse(ctx, "Global rate limit exceeded");
                return;
            }
            
            // Check per-IP rate limit
            if (!checkIpRateLimit(clientIp)) {
                securityLogger.warn("IP rate limit exceeded from {}", clientIp);
                sendRateLimitResponse(ctx, "IP rate limit exceeded");
                return;
            }
            
            logger.debug("✅ Rate limit check passed for {}", clientIp);
        }
        
        super.channelRead(ctx, msg);
    }
    
    private boolean checkIpRateLimit(String clientIp) {
        RateLimiter ipLimiter = ipLimiters.computeIfAbsent(clientIp, 
            k -> {
                logger.debug("🔧 Creating new rate limiter for IP: {}", k);
                return RateLimiter.create(PER_IP_RATE_LIMIT);
            });
        
        return ipLimiter.tryAcquire();
    }
    
    private String extractClientIp(ChannelHandlerContext ctx) {
        try {
            String address = ctx.channel().remoteAddress().toString();
            // Extract IP from address format like "/127.0.0.1:12345"
            if (address.startsWith("/")) {
                int colonIndex = address.indexOf(':', 1);
                return colonIndex > 0 ? address.substring(1, colonIndex) : address.substring(1);
            }
            return address;
        } catch (Exception e) {
            logger.warn("⚠️ Could not extract client IP: {}", e.getMessage());
            return "unknown";
        }
    }
    
    private void sendRateLimitResponse(ChannelHandlerContext ctx, String message) {
        byte[] bytes = message.getBytes();
        
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, 
            HttpResponseStatus.TOO_MANY_REQUESTS, 
            Unpooled.wrappedBuffer(bytes)
        );
        
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        response.headers().set(HttpHeaderNames.RETRY_AFTER, "60"); // Retry after 60 seconds
        
        ctx.writeAndFlush(response);
        ctx.close();
    }
    
    /**
     * Clean up unused IP rate limiters to prevent memory leaks
     */
    private static void cleanupUnusedLimiters() {
        int sizeBefore = ipLimiters.size();
        
        // Remove limiters that haven't been used recently
        // This is a simple cleanup - in production you might want more sophisticated logic
        if (sizeBefore > 1000) { // Only cleanup if we have many limiters
            ipLimiters.clear();
            logger.info("🧹 Cleaned up {} unused rate limiters", sizeBefore);
        }
    }
    
    /**
     * Get current rate limiting statistics
     */
    public static RateLimitStats getStats() {
        return new RateLimitStats(
            ipLimiters.size(),
            globalLimiter.getRate(),
            PER_IP_RATE_LIMIT
        );
    }
    
    /**
     * Rate limiting statistics
     */
    public record RateLimitStats(
        int activeIpLimiters,
        double globalRateLimit,
        double perIpRateLimit
    ) {}
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("❌ Rate limit handler error: {}", cause.getMessage(), cause);
        ctx.close();
    }
}
