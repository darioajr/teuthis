package com.github.darioajr.teuthis.queue;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.darioajr.teuthis.security.JwtValidator;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

/**
 * Handler HTTP para operações de limpeza de filas.
 * 
 * Endpoints:
 * - DELETE /queue/{topicName} - Limpa uma fila específica
 * - GET /queue/status - Status das operações de limpeza
 */
public class QueueCleanupHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    
    private static final Logger logger = LoggerFactory.getLogger(QueueCleanupHandler.class);
    private static final Logger securityLogger = LoggerFactory.getLogger("security");
    
    private final QueueCleanupManager cleanupManager;
    private final ObjectMapper objectMapper;
    private final boolean authRequired;
    
    public QueueCleanupHandler(QueueCleanupManager cleanupManager) {
        this.cleanupManager = cleanupManager;
        this.objectMapper = new ObjectMapper();
        this.authRequired = Boolean.parseBoolean(
            System.getProperty("teuthis.security.auth.enabled", "false")
        );
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        String uri = request.uri();
        HttpMethod method = request.method();
        String clientIp = getClientIp(ctx);
        String requestId = generateRequestId();
        
        // Configurar MDC
        MDC.put("requestId", requestId);
        MDC.put("clientIp", clientIp);
        MDC.put("method", method.name());
        MDC.put("uri", uri);
        
        try {
            // Verificar se é um endpoint de queue cleanup
            if (!uri.startsWith("/queue")) {
                ctx.fireChannelRead(request.retain());
                return;
            }
            
            // Autenticação (se habilitada)
            if (authRequired && !isAuthenticated(request)) {
                securityLogger.warn("Unauthorized queue cleanup attempt from {}", clientIp);
                sendResponse(ctx, HttpResponseStatus.UNAUTHORIZED, 
                           "{\"error\":\"Authentication required for queue operations\"}");
                return;
            }
            
            // Roteamento
            if (method == HttpMethod.DELETE && uri.matches("/queue/[a-zA-Z0-9._-]+")) {
                handleQueueCleanup(ctx, request, requestId, clientIp);
            } else if (method == HttpMethod.GET && uri.equals("/queue/status")) {
                handleQueueStatus(ctx, requestId);
            } else {
                sendResponse(ctx, HttpResponseStatus.NOT_FOUND, 
                           "{\"error\":\"Queue endpoint not found\"}");
            }
            
        } catch (Exception e) {
            logger.error("Error processing queue request", e);
            sendResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, 
                       "{\"error\":\"Internal server error\"}");
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Processa requisição de limpeza de fila.
     */
    private void handleQueueCleanup(ChannelHandlerContext ctx, FullHttpRequest request, 
                                   String requestId, String clientIp) {
        
        // Extrair nome do tópico da URI
        String uri = request.uri();
        String topicName = uri.substring("/queue/".length());
        
        logger.info("Queue cleanup requested for topic: {}", topicName);
        
        // Executar limpeza assíncrona
        CompletableFuture<QueueCleanupResult> cleanupFuture = 
            cleanupManager.cleanupQueue(topicName, requestId, clientIp);
        
        cleanupFuture.thenAccept(result -> {
            try {
                if (result.isSuccess()) {
                    String response = objectMapper.writeValueAsString(new CleanupResponse(
                        true, 
                        "Queue cleanup completed successfully",
                        topicName,
                        result.getDurationMs(),
                        result.getTimestamp().toString()
                    ));
                    sendResponse(ctx, HttpResponseStatus.OK, response);
                } else {
                    String response = objectMapper.writeValueAsString(new CleanupResponse(
                        false,
                        result.getMessage(),
                        topicName,
                        0,
                        result.getTimestamp().toString()
                    ));
                    sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, response);
                }
            } catch (Exception e) {
                logger.error("Error serializing cleanup response", e);
                sendResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, 
                           "{\"error\":\"Error processing response\"}");
            }
        }).exceptionally(throwable -> {
            logger.error("Cleanup operation failed", throwable);
            sendResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, 
                       "{\"error\":\"Cleanup operation failed\"}");
            return null;
        });
    }
    
    /**
     * Processa requisição de status das operações de limpeza.
     */
    private void handleQueueStatus(ChannelHandlerContext ctx, String requestId) {
        try {
            var activeCleanups = cleanupManager.getActiveCleanups();
            
            String response = objectMapper.writeValueAsString(new StatusResponse(
                activeCleanups.size(),
                activeCleanups,
                System.currentTimeMillis()
            ));
            
            sendResponse(ctx, HttpResponseStatus.OK, response);
            
        } catch (Exception e) {
            logger.error("Error getting queue status", e);
            sendResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, 
                       "{\"error\":\"Error getting status\"}");
        }
    }
    
    /**
     * Verifica autenticação JWT.
     */
    private boolean isAuthenticated(FullHttpRequest request) {
        String authHeader = request.headers().get(HttpHeaderNames.AUTHORIZATION);
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }
        
        String token = authHeader.substring(7);
        return JwtValidator.validate(token);
    }
    
    /**
     * Envia resposta HTTP.
     */
    private void sendResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String content) {
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, 
            status,
            Unpooled.copiedBuffer(content, StandardCharsets.UTF_8)
        );
        
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        response.headers().set(HttpHeaderNames.CONNECTION, "close");
        
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
    
    /**
     * Obtém IP do cliente.
     */
    private String getClientIp(ChannelHandlerContext ctx) {
        return ctx.channel().remoteAddress().toString();
    }
    
    /**
     * Gera ID único para a requisição.
     */
    private String generateRequestId() {
        return "cleanup-" + System.currentTimeMillis() + "-" + 
               Integer.toHexString((int)(Math.random() * 0x10000));
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Exception in QueueCleanupHandler", cause);
        ctx.close();
    }
    
    // Classes para serialização JSON
    public static class CleanupResponse {
        public final boolean success;
        public final String message;
        public final String topicName;
        public final long durationMs;
        public final String timestamp;
        
        public CleanupResponse(boolean success, String message, String topicName, 
                              long durationMs, String timestamp) {
            this.success = success;
            this.message = message;
            this.topicName = topicName;
            this.durationMs = durationMs;
            this.timestamp = timestamp;
        }
    }
    
    public static class StatusResponse {
        public final int activeCleanups;
        public final java.util.Set<String> topicsBeingCleaned;
        public final long timestamp;
        
        public StatusResponse(int activeCleanups, java.util.Set<String> topicsBeingCleaned, 
                             long timestamp) {
            this.activeCleanups = activeCleanups;
            this.topicsBeingCleaned = topicsBeingCleaned;
            this.timestamp = timestamp;
        }
    }
}
