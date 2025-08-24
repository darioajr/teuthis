package com.github.darioajr.teuthis.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;

/**
 * Handler to add security headers to all HTTP responses
 */
public class SecurityHeadersHandler extends ChannelOutboundHandlerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityHeadersHandler.class);
    
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof FullHttpResponse response) {
            addSecurityHeaders(response);
            logger.debug("✅ Security headers added to response");
        }
        
        super.write(ctx, msg, promise);
    }
    
    private void addSecurityHeaders(FullHttpResponse response) {
        // Prevent MIME type sniffing
        response.headers().set("X-Content-Type-Options", "nosniff");
        
        // Prevent clickjacking
        response.headers().set("X-Frame-Options", "DENY");
        
        // XSS protection
        response.headers().set("X-XSS-Protection", "1; mode=block");
        
        // HSTS (HTTP Strict Transport Security)
        response.headers().set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        
        // Content Security Policy
        response.headers().set("Content-Security-Policy", "default-src 'self'");
        
        // Referrer Policy
        response.headers().set("Referrer-Policy", "strict-origin-when-cross-origin");
        
        // Permissions Policy (formerly Feature Policy)
        response.headers().set("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
        
        // Remove server information
        response.headers().remove(HttpHeaderNames.SERVER);
        
        // Add custom server header
        response.headers().set(HttpHeaderNames.SERVER, "Teuthis");
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("❌ Security headers handler error: {}", cause.getMessage(), cause);
        super.exceptionCaught(ctx, cause);
    }
}
