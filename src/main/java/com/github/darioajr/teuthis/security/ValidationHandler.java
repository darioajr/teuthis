package com.github.darioajr.teuthis.security;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.darioajr.teuthis.infra.Config;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;

/**
 * Input validation and sanitization handler
 */
public class ValidationHandler extends ChannelInboundHandlerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(ValidationHandler.class);
    private static final Logger securityLogger = LoggerFactory.getLogger("security");
    
    private static final int MAX_PAYLOAD_SIZE = Config.i("teuthis.security.max.payload.size");
    private static final Pattern TOPIC_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");
    private static final Pattern SAFE_PATH_PATTERN = Pattern.compile("^[a-zA-Z0-9/_.-]+$");
    
    // Allowed content types
    private static final String[] ALLOWED_CONTENT_TYPES = {
        "application/json",
        "application/xml", 
        "text/xml",
        "application/soap+xml",
        "text/plain"
    };
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest req) {
            String clientIp = getClientIp(ctx);
            
            try {
                // Validate HTTP method
                if (!isValidMethod(req.method())) {
                    securityLogger.warn("Invalid HTTP method {} from {}", req.method(), clientIp);
                    sendValidationError(ctx, "Method not allowed", HttpResponseStatus.METHOD_NOT_ALLOWED);
                    return;
                }
                
                // Validate URI path
                if (!isValidPath(req.uri())) {
                    securityLogger.warn("Invalid URI path {} from {}", req.uri(), clientIp);
                    sendValidationError(ctx, "Invalid path", HttpResponseStatus.BAD_REQUEST);
                    return;
                }
                
                // For POST requests to publish endpoints
                if (req.method().equals(HttpMethod.POST) && req.uri().startsWith("/publish/")) {
                    if (!validatePublishRequest(req, clientIp, ctx)) {
                        return; // Error response already sent
                    }
                }
                
                logger.debug("✅ Request validation passed for {}", clientIp);
                
            } catch (Exception e) {
                logger.error("❌ Validation error for request from {}: {}", clientIp, e.getMessage(), e);
                sendValidationError(ctx, "Validation error", HttpResponseStatus.BAD_REQUEST);
                return;
            }
        }
        
        super.channelRead(ctx, msg);
    }
    
    private boolean validatePublishRequest(FullHttpRequest req, String clientIp, ChannelHandlerContext ctx) {
        // Validate payload size
        int contentLength = req.content().readableBytes();
        if (contentLength > MAX_PAYLOAD_SIZE) {
            securityLogger.warn("Payload too large ({} bytes) from {}", contentLength, clientIp);
            sendValidationError(ctx, 
                String.format("Payload too large. Maximum allowed: %d bytes", MAX_PAYLOAD_SIZE),
                HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE);
            return false;
        }
        
        // Validate Content-Type
        String contentType = req.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (!isValidContentType(contentType)) {
            securityLogger.warn("Invalid content type '{}' from {}", contentType, clientIp);
            sendValidationError(ctx, "Unsupported content type", HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE);
            return false;
        }
        
        // Extract and validate topic name
        QueryStringDecoder decoder = new QueryStringDecoder(req.uri());
        String[] segments = decoder.path().split("/");
        if (segments.length >= 3) {
            String topic = segments[2];
            if (!isValidTopicName(topic)) {
                securityLogger.warn("Invalid topic name '{}' from {}", topic, clientIp);
                sendValidationError(ctx, "Invalid topic name", HttpResponseStatus.BAD_REQUEST);
                return false;
            }
        }
        
        // Validate payload content based on content type
        if (contentLength > 0) {
            byte[] content = new byte[contentLength];
            req.content().getBytes(0, content);
            
            if (!isValidPayload(content, contentType)) {
                securityLogger.warn("Invalid payload content from {}", clientIp);
                sendValidationError(ctx, "Invalid payload format", HttpResponseStatus.BAD_REQUEST);
                return false;
            }
        }
        
        return true;
    }
    
    private boolean isValidMethod(HttpMethod method) {
        return method.equals(HttpMethod.GET) || 
               method.equals(HttpMethod.POST) ||
               method.equals(HttpMethod.HEAD) ||
               method.equals(HttpMethod.OPTIONS);
    }
    
    private boolean isValidPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        
        // Check for path traversal attempts
        if (path.contains("..") || path.contains("//")) {
            return false;
        }
        
        // Check against safe path pattern
        return SAFE_PATH_PATTERN.matcher(path).matches();
    }
    
    private boolean isValidContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        
        // Extract main content type (ignore charset and other parameters)
        String mainType = contentType.split(";")[0].trim().toLowerCase();
        
        for (String allowedType : ALLOWED_CONTENT_TYPES) {
            if (mainType.equals(allowedType)) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean isValidTopicName(String topic) {
        if (topic == null || topic.isEmpty() || topic.length() > 255) {
            return false;
        }
        
        return TOPIC_PATTERN.matcher(topic).matches();
    }
    
    private boolean isValidPayload(byte[] content, String contentType) {
        if (content.length == 0) {
            return true; // Empty payload is valid
        }
        
        try {
            String payload = new String(content, StandardCharsets.UTF_8);
            String mainType = contentType.split(";")[0].trim().toLowerCase();
            
            switch (mainType) {
                case "application/json":
                    return isValidJson(payload);
                case "application/xml":
                case "text/xml":
                case "application/soap+xml":
                    return isValidXml(payload);
                case "text/plain":
                    return isValidText(payload);
                default:
                    return false;
            }
        } catch (Exception e) {
            logger.warn("⚠️ Error validating payload: {}", e.getMessage());
            return false;
        }
    }
    
    private boolean isValidJson(String payload) {
        String trimmed = payload.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
               (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }
    
    private boolean isValidXml(String payload) {
        String trimmed = payload.trim();
        return trimmed.startsWith("<") && trimmed.endsWith(">");
    }
    
    private boolean isValidText(String payload) {
        // Basic text validation - no control characters except newlines and tabs
        for (char c : payload.toCharArray()) {
            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') {
                return false;
            }
        }
        return true;
    }
    
    private String getClientIp(ChannelHandlerContext ctx) {
        try {
            return ctx.channel().remoteAddress() != null ? 
                   ctx.channel().remoteAddress().toString() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    private void sendValidationError(ChannelHandlerContext ctx, String message, HttpResponseStatus status) {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, 
            status, 
            Unpooled.wrappedBuffer(bytes)
        );
        
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        
        ctx.writeAndFlush(response);
        ctx.close();
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("❌ Validation handler error: {}", cause.getMessage(), cause);
        ctx.close();
    }
}
