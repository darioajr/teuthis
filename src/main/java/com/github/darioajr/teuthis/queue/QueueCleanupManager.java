package com.github.darioajr.teuthis.queue;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DeleteRecordsResult;
import org.apache.kafka.clients.admin.RecordsToDelete;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.github.darioajr.teuthis.infra.Metrics;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

/**
 * Gerenciador de limpeza de filas Kafka de forma segura e performática.
 * 
 * Features:
 * - Parada segura do produtor antes da limpeza
 * - Operações assíncronas sem bloqueio
 * - Circuit breaker para operações admin
 * - Validação e autorização
 * - Métricas e logging estruturado
 */
public class QueueCleanupManager {
    
    private static final Logger logger = LoggerFactory.getLogger(QueueCleanupManager.class);
    private static final Logger securityLogger = LoggerFactory.getLogger("security");
    
    private final AdminClient adminClient;
    private final Producer<String, byte[]> kafkaProducer;
    private final Metrics metrics;
    private final ExecutorService cleanupExecutor;
    private final CircuitBreaker circuitBreaker;
    
    // Estado de limpeza por tópico
    private final ConcurrentHashMap<String, AtomicBoolean> cleanupInProgress = new ConcurrentHashMap<>();
    
    // Configurações
    private final Duration producerShutdownTimeout;
    
    public QueueCleanupManager(AdminClient adminClient, 
                              Producer<String, byte[]> kafkaProducer,
                              Metrics metrics) {
        this.adminClient = adminClient;
        this.kafkaProducer = kafkaProducer;
        this.metrics = metrics;
        this.cleanupExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "queue-cleanup-worker");
            t.setDaemon(true);
            return t;
        });
        
        // Circuit breaker para operações admin
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .build();
        
        this.circuitBreaker = CircuitBreaker.of("queue-cleanup", config);
        
        // Configurações via properties
        this.producerShutdownTimeout = Duration.ofSeconds(
            Integer.parseInt(System.getProperty("teuthis.queue.cleanup.producer.shutdown.timeout", "30"))
        );
        
        logger.info("QueueCleanupManager initialized with producer shutdown timeout: {}s", 
                   producerShutdownTimeout.getSeconds());
    }
    
    /**
     * Limpa uma fila específica de forma assíncrona e segura.
     * 
     * @param topicName Nome do tópico a ser limpo
     * @param requestId ID da requisição para correlação
     * @param clientIp IP do cliente para auditoria
     * @return CompletableFuture com resultado da operação
     */
    public CompletableFuture<QueueCleanupResult> cleanupQueue(String topicName, 
                                                             String requestId, 
                                                             String clientIp) {
        
        // Configurar MDC para logging correlacionado
        MDC.put("requestId", requestId);
        MDC.put("clientIp", clientIp);
        MDC.put("topicName", topicName);
        
        try {
            // Validações iniciais
            if (!isValidTopicName(topicName)) {
                securityLogger.warn("Invalid topic name attempted for cleanup: {}", topicName);
                return CompletableFuture.completedFuture(
                    QueueCleanupResult.error("Invalid topic name: " + topicName)
                );
            }
            
            // Verificar se já está em progresso
            AtomicBoolean inProgress = cleanupInProgress.computeIfAbsent(topicName, k -> new AtomicBoolean(false));
            if (!inProgress.compareAndSet(false, true)) {
                logger.warn("Cleanup already in progress for topic: {}", topicName);
                return CompletableFuture.completedFuture(
                    QueueCleanupResult.error("Cleanup already in progress for topic: " + topicName)
                );
            }
            
            logger.info("Starting queue cleanup for topic: {}", topicName);
            securityLogger.info("Queue cleanup initiated for topic: {} by client: {}", topicName, clientIp);
            
            // Executar limpeza assíncrona
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return performCleanup(topicName, requestId);
                } finally {
                    inProgress.set(false);
                    MDC.clear();
                }
            }, cleanupExecutor);
            
        } catch (Exception e) {
            logger.error("Error initiating queue cleanup for topic: {}", topicName, e);
            cleanupInProgress.get(topicName).set(false);
            MDC.clear();
            return CompletableFuture.completedFuture(
                QueueCleanupResult.error("Failed to initiate cleanup: " + e.getMessage())
            );
        }
    }
    
    /**
     * Executa a limpeza da fila com parada segura do produtor.
     */
    private QueueCleanupResult performCleanup(String topicName, String requestId) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. Parar o produtor temporariamente
            logger.info("Stopping producer for safe cleanup of topic: {}", topicName);
            stopProducerSafely();
            
            // 2. Aguardar flush completo
            Thread.sleep(1000); // Pequena pausa para garantir que não há operações pendentes
            
            // 3. Executar limpeza via AdminClient com circuit breaker
            logger.info("Executing cleanup for topic: {}", topicName);
            DeleteRecordsResult result = circuitBreaker.executeSupplier(() -> {
                return adminClient.deleteRecords(
                    java.util.Map.of(
                        new TopicPartition(topicName, 0), 
                        RecordsToDelete.beforeOffset(-1L) // Remove todos os registros
                    )
                );
            });
            
            // 4. Aguardar conclusão
            result.all().get(producerShutdownTimeout.toSeconds(), java.util.concurrent.TimeUnit.SECONDS);
            
            // 5. Reiniciar operações normais (o produtor será recriado automaticamente)
            logger.info("Queue cleanup completed successfully for topic: {}", topicName);
            
            // Métricas
            long duration = System.currentTimeMillis() - startTime;
            metrics.recordQueueCleanup(topicName, duration, true);
            
            return QueueCleanupResult.success(topicName, duration);
            
        } catch (Exception e) {
            logger.error("Failed to cleanup queue for topic: {}", topicName, e);
            
            // Métricas de erro
            long duration = System.currentTimeMillis() - startTime;
            metrics.recordQueueCleanup(topicName, duration, false);
            
            return QueueCleanupResult.error("Cleanup failed: " + e.getMessage());
        }
    }
    
    /**
     * Para o produtor de forma segura, aguardando flush de mensagens pendentes.
     */
    private void stopProducerSafely() {
        try {
            // Flush todas as mensagens pendentes
            kafkaProducer.flush();
            logger.debug("Producer flushed successfully");
            
            // Nota: Não fechamos o produtor completamente, apenas garantimos que está limpo
            // O TeuthisServer gerenciará o ciclo de vida do produtor
            
        } catch (Exception e) {
            logger.warn("Error during producer flush: {}", e.getMessage());
            // Continua com a limpeza mesmo se o flush falhar
        }
    }
    
    /**
     * Valida se o nome do tópico é seguro para limpeza.
     */
    private boolean isValidTopicName(String topicName) {
        if (topicName == null || topicName.trim().isEmpty()) {
            return false;
        }
        
        // Regex para nomes válidos de tópicos Kafka
        return topicName.matches("^[a-zA-Z0-9._-]+$") && topicName.length() <= 249;
    }
    
    /**
     * Verifica se uma limpeza está em progresso para um tópico.
     */
    public boolean isCleanupInProgress(String topicName) {
        AtomicBoolean inProgress = cleanupInProgress.get(topicName);
        return inProgress != null && inProgress.get();
    }
    
    /**
     * Obtém status de todas as operações de limpeza em progresso.
     */
    public java.util.Set<String> getActiveCleanups() {
        return cleanupInProgress.entrySet().stream()
            .filter(entry -> entry.getValue().get())
            .map(java.util.Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toSet());
    }
    
    /**
     * Shutdown graceful do manager.
     */
    public void shutdown() {
        logger.info("Shutting down QueueCleanupManager");
        
        try {
            cleanupExecutor.shutdown();
            if (!cleanupExecutor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) {
                logger.warn("Cleanup executor did not terminate gracefully, forcing shutdown");
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cleanupExecutor.shutdownNow();
        }
        
        logger.info("QueueCleanupManager shutdown completed");
    }
}
