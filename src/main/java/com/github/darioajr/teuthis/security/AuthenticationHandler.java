package com.github.darioajr.teuthis.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

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
 * JWT Authentication handler for incoming requests
 */
public class AuthenticationHandler extends ChannelInboundHandlerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationHandler.class);
    private static final Logger securityLogger = LoggerFactory.getLogger("security");
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest req) {
            
            // Skip authentication for health and metrics endpoints
            if (isPublicEndpoint(req.uri())) {
                super.channelRead(ctx, msg);
                return;
            }
            
            // Skip authentication if disabled
            if (!JwtValidator.isAuthEnabled()) {
                super.channelRead(ctx, msg);
                return;
            }
            
            String authHeader = req.headers().get(HttpHeaderNames.AUTHORIZATION);
            String clientIp = getClientIp(ctx);
            
            if (!validateAuth(authHeader, clientIp)) {
                sendUnauthorized(ctx);
                return;
            }
            
            // Extract user info and add to MDC for logging
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                String subject = JwtValidator.getSubject(token);
                if (subject != null) {
                    MDC.put("user", subject);
                }
            }
        }
        
        super.channelRead(ctx, msg);
    }
    
    private boolean isPublicEndpoint(String uri) {
        return uri.equals("/health") || 
               uri.equals("/metrics") ||
               uri.startsWith("/auth/"); // Future auth endpoints
    }
    
    private boolean validateAuth(String authHeader, String clientIp) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            securityLogger.warn("Missing or invalid Authorization header from {}", clientIp);
            return false;
        }
        
        String token = authHeader.substring(7);
        boolean isValid = JwtValidator.validate(token);
        
        if (!isValid) {
            securityLogger.warn("Invalid JWT token from {}", clientIp);
        } else {
            logger.debug("✅ Authentication successful for {}", clientIp);
        }
        
        return isValid;
    }
    
    private String getClientIp(ChannelHandlerContext ctx) {
        try {
            return ctx.channel().remoteAddress() != null ? 
                   ctx.channel().remoteAddress().toString() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    private void sendUnauthorized(ChannelHandlerContext ctx) {
        String message = "Authentication required";
        byte[] bytes = message.getBytes();
        
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, 
            HttpResponseStatus.UNAUTHORIZED, 
            Unpooled.wrappedBuffer(bytes)
        );
        
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        response.headers().set(HttpHeaderNames.WWW_AUTHENTICATE, "Bearer");
        
        ctx.writeAndFlush(response);
        ctx.close();
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("❌ Authentication handler error: {}", cause.getMessage(), cause);
        ctx.close();
    }
}
