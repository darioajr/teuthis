package com.github.darioajr.teuthis;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.github.darioajr.teuthis.avro.Message;
import com.github.darioajr.teuthis.infra.Config;
import com.github.darioajr.teuthis.infra.Metrics;
import com.github.darioajr.teuthis.infra.MetricsHandler;
import com.sun.management.OperatingSystemMXBean;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;

public class TeuthisServer {

    private static final Logger logger = LoggerFactory.getLogger(TeuthisServer.class);
    private static final Logger performanceLogger = LoggerFactory.getLogger("performance");
    private static final Logger securityLogger = LoggerFactory.getLogger("security");
    
    private static final double THRESHOLD = Config.d("resources.threshold");
    private static final int RETRY_AFTER = Config.i("retry.after.seconds");
    private static final String PARTITION_KEY = Config.str("kafka.partition.key");
    private static final List<String> ALLOWED_TOPICS = Config.allowedTopics();
    private static final int KAFKA_THREADS = Config.i("kafka.thread.pool.size");
    
    private static final ExecutorService kafkaExecutor =
        Executors.newFixedThreadPool(KAFKA_THREADS, r -> {
            Thread t = new Thread(r, "kafka-sender");
            t.setDaemon(true);
            return t;
        });
    private static Producer<String, byte[]> producer;

    public static void main(String[] args) throws InterruptedException {
        logger.info("🚀 Starting Teuthis Server...");
        
        // Print configuration summary
        Config.printSummary();
        
        // Set resource threshold metric for monitoring
        Metrics.resourceThreshold.set(THRESHOLD);
        logger.info("📊 Resource threshold set to: {:.1f}%", THRESHOLD * 100);
        
        int port = Config.i("server.port");
        int bossThreads = Config.i("netty.boss.threads");
        int workerThreads = Config.i("netty.worker.threads");

        logger.info("Starting TeuthisServer on port {} with {} boss threads and {} worker threads", 
                   port, bossThreads, workerThreads);
        
        EventLoopGroup bossGroup = new NioEventLoopGroup(bossThreads, r -> {
            Thread t = new Thread(r, "netty-boss"); 
            t.setDaemon(true); 
            return t;
        });
        
        EventLoopGroup workerGroup = (workerThreads > 0
            ? new NioEventLoopGroup(workerThreads, r -> {
                Thread t = new Thread(r, "netty-worker"); 
                t.setDaemon(true); 
                return t;
              })
            : new NioEventLoopGroup());

        try (Producer<String, byte[]> kafkaProducer = createProducer()) {
            producer = kafkaProducer;
            
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel ch) {
                     logger.debug("Initializing channel: {}", ch.remoteAddress());
                     ch.pipeline().addLast(
                       new HttpServerCodec(),
                       new HttpObjectAggregator(64 * 1024),
                       new MetricsHandler(),
                       new PublishHandler()
                     );
                 }
             });

            ChannelFuture f = b.bind(port).sync();
            logger.info("✅ Server successfully started on http://localhost:{}", port);
            f.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            logger.error("❌ Server interrupted: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
            throw e;
        } catch (RuntimeException e) {
            logger.error("❌ Failed to start server: {}", e.getMessage(), e);
            throw e;
        } finally {
            logger.info("🔄 Shutting down server gracefully...");
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            kafkaExecutor.shutdown();
            logger.info("✅ Server shutdown completed");
        }
    }

    private static Producer<String, byte[]> createProducer() {
        logger.debug("Creating Kafka producer with bootstrap servers: {}", Config.str("kafka.bootstrap.servers"));
        
        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, Config.str("kafka.bootstrap.servers"));
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, Config.str("kafka.key.serializer"));
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, Config.str("kafka.value.serializer"));
        p.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, Boolean.toString(Config.b("kafka.enable.idempotence")));
        p.put(ProducerConfig.ACKS_CONFIG, Config.str("kafka.acks"));
        p.put(ProducerConfig.RETRIES_CONFIG, Config.str("kafka.retries"));
        p.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, Config.str("kafka.max.in.flight.requests.per.connection"));
        p.put(ProducerConfig.BATCH_SIZE_CONFIG, Config.str("kafka.batch.size"));
        p.put(ProducerConfig.LINGER_MS_CONFIG, Config.str("kafka.linger.ms"));
        p.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, Config.str("kafka.compression.type"));
        
        try {
            Producer<String, byte[]> kafkaProducer = new KafkaProducer<>(p);
            logger.info("✅ Kafka producer created successfully");
            return kafkaProducer;
        } catch (IllegalArgumentException e) {
            logger.error("❌ Invalid Kafka producer configuration: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize Kafka producer due to invalid configuration", e);
        } catch (org.apache.kafka.common.KafkaException e) {
            logger.error("❌ Kafka exception while creating producer: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize Kafka producer due to Kafka exception", e);
        }
    }

    private static class PublishHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
        
        private static String getClientIp(ChannelHandlerContext ctx) {
            try {
                return ctx.channel().remoteAddress() != null ? 
                       ctx.channel().remoteAddress().toString() : "unknown";
            } catch (Exception e) {
                logger.warn("⚠️ Could not get client IP: {}", e.getMessage());
                return "unknown";
            }
        }
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
            String requestId = UUID.randomUUID().toString().substring(0, 8);
            String clientIp = "unknown";
            
            try {
                clientIp = ctx.channel().remoteAddress() != null ? 
                          ctx.channel().remoteAddress().toString() : "unknown";
            } catch (Exception e) {
                logger.warn("⚠️ Could not get client IP: {}", e.getMessage());
            }
            
            // MDC para correlação de logs
            MDC.put("requestId", requestId);
            MDC.put("clientIp", clientIp);
            
            try {
                if (req.method() == null || req.uri() == null) {
                    logger.warn("⚠️ Invalid request - null method or URI for request {}", requestId);
                    sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Invalid request");
                    return;
                }
                
                MDC.put("method", req.method().name());
                MDC.put("uri", req.uri());
                
                logger.debug("📥 Processing request: {} {} from {}", req.method(), req.uri(), clientIp);
                
                // Record request metrics
                try {
                    Metrics.requestsTotal.labels(req.method().name(), "received").inc();
                } catch (Exception e) {
                    logger.warn("⚠️ Error recording metrics: {}", e.getMessage());
                }
                
                QueryStringDecoder dec = new QueryStringDecoder(req.uri());
                String[] segs = dec.path().split("/");
                
                // Handle health check endpoint (GET)
                if (dec.path().equals("/health")) {
                    if (req.method().equals(HttpMethod.GET)) {
                        logger.debug("🏥 Health check request from {}", clientIp);
                        sendHealthResponse(ctx);
                        return;
                    } else {
                        sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED, "Health endpoint only accepts GET requests");
                        return;
                    }
                }
                
                // Handle metrics endpoint (GET)
                if (dec.path().equals("/metrics")) {
                    if (req.method().equals(HttpMethod.GET)) {
                        logger.debug("📊 Metrics request from {}", clientIp);
                        sendMetricsResponse(ctx);
                        return;
                    } else {
                        sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED, "Metrics endpoint only accepts GET requests");
                        return;
                    }
                }
                
                // Only POST requests are allowed for publish endpoints
                if (!req.method().equals(HttpMethod.POST)) {
                    logger.warn("⚠️ Method not allowed: {} for request {}", req.method(), requestId);
                    securityLogger.warn("Method not allowed attempt from {}: {}", clientIp, req.method());
                    sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED, "Only POST requests are allowed for publish endpoints");
                    return;
                }
                
                if (segs.length != 3 || !segs[1].equals("publish")) {
                    logger.warn("⚠️ Invalid path: {} for request {}", dec.path(), requestId);
                    securityLogger.warn("Invalid path attempt from {}: {}", clientIp, dec.path());
                    sendError(ctx, HttpResponseStatus.NOT_FOUND);
                    return;
                }
                
                String topic = segs[2];
                MDC.put("topic", topic);
                
                if (!ALLOWED_TOPICS.contains(topic)) {
                    logger.warn("⚠️ Topic not allowed: {} for request {}", topic, requestId);
                    securityLogger.warn("Unauthorized topic access attempt from {}: {}", clientIp, topic);
                    sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Tópico não permitido: " + topic);
                    return;
                }
                
                String resourceLimitMessage = checkResourceUsage();
                if (resourceLimitMessage != null) {
                    logger.warn("⚠️ Resource usage high, rejecting request {} for topic {}: {}", requestId, topic, resourceLimitMessage);
                    sendError(ctx, HttpResponseStatus.TOO_MANY_REQUESTS, resourceLimitMessage, RETRY_AFTER);
                    return;
                }
                
                byte[] body = new byte[req.content().readableBytes()];
                req.content().readBytes(body);
                
                logger.debug("📦 Message body size: {} bytes for request {}", body.length, requestId);
                
                String format = detectFormat(new String(body, StandardCharsets.UTF_8));
                MDC.put("format", format);
                
                Message msg;
                try {
                    msg = Message.newBuilder()
                                         .setFormat(format)
                                         .setPayload(ByteBuffer.wrap(body))
                                         .setTimestamp(System.currentTimeMillis())
                                         .setHostname(InetAddress.getLocalHost().getHostName())
                                         .build();
                    
                    logger.debug("✅ Avro message built successfully for request {}", requestId);
                } catch (UnknownHostException e) {
                    logger.error("❌ Failed to get hostname for request {}: {}", requestId, e.getMessage(), e);
                    sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "Failed to build message");
                    return;
                }
                
                if (msg == null) {
                    logger.error("❌ Failed to build message for request {}", requestId);
                    sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "Failed to build message");
                    return;
                }
                
                byte[] avroBytes;
                try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    DatumWriter<Message> w = new SpecificDatumWriter<>(Message.class);
                    BinaryEncoder enc = EncoderFactory.get().binaryEncoder(out, null);
                    w.write(msg, enc);
                    enc.flush();
                    avroBytes = out.toByteArray();
                    
                    logger.debug("📋 Avro serialization completed: {} bytes for request {}", avroBytes.length, requestId);
                } catch (java.io.IOException e) {
                    logger.error("❌ IO error during serialization for request {}: {}", requestId, e.getMessage(), e);
                    sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "IO error during serialization");
                    return;
                } catch (org.apache.avro.AvroRuntimeException e) {
                    logger.error("❌ Avro runtime error for request {}: {}", requestId, e.getMessage(), e);
                    sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "Avro runtime error during serialization");
                    return;
                }

                ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, PARTITION_KEY, avroBytes);
                
                logger.info("🚀 Sending message to Kafka topic: {} for request {}", topic, requestId);
                
                kafkaExecutor.submit(() -> {
                    // Manter MDC no thread do Kafka  
                    MDC.put("requestId", requestId);
                    MDC.put("topic", topic);
                    // clientIp já está no MDC principal
                    
                    long requestStartTime = System.nanoTime();
                    
                    try {
                        logger.debug("🚀 Submitting message to Kafka producer for request {}", requestId);
                        
                        RecordMetadata md = producer.send(record).get();
                        long duration = System.nanoTime() - requestStartTime;
                        
                        // Record metrics safely
                        try {
                            Metrics.publishLatency.observe((double) duration / 1_000_000_000.0);
                            Metrics.messagesTotal.inc();
                        } catch (Exception metricsError) {
                            logger.warn("⚠️ Failed to record metrics for request {}: {}", requestId, metricsError.getMessage());
                        }
                        
                        logger.info("✅ Message sent successfully to partition {} offset {} for request {} in {}ms", 
                                   md.partition(), md.offset(), requestId, duration / 1_000_000);
                        
                        performanceLogger.info("Message processing completed: requestId={}, topic={}, partition={}, offset={}, duration={}ms", 
                                              requestId, topic, md.partition(), md.offset(), duration / 1_000_000);
                        
                        // Send success response in the Netty event loop
                        ctx.executor().execute(() -> {
                            try {
                                sendSuccess(ctx, HttpResponseStatus.CREATED);
                            } catch (Exception e) {
                                logger.error("❌ Error sending success response for request {}: {}", requestId, e.getMessage(), e);
                            }
                        });
                        
                    } catch (InterruptedException ex) {
                        long duration = System.nanoTime() - requestStartTime;

                        // Record metrics safely
                        try {
                            Metrics.publishLatency.observe((double) duration / 1_000_000_000.0);
                            Metrics.messagesErrors.inc();
                        } catch (Exception metricsError) {
                            logger.warn("⚠️ Failed to record error metrics for request {}: {}", requestId, metricsError.getMessage());
                        }

                        logger.error("❌ Interrupted while sending message to Kafka for request {} after {}ms: {}",
                                    requestId, duration / 1_000_000, ex.getMessage(), ex);

                        Thread.currentThread().interrupt();
                        ctx.executor().execute(() -> {
                            try {
                                sendError(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE,
                                          "Kafka indisponível: " + ex.getMessage(), RETRY_AFTER);
                            } catch (Exception e) {
                                logger.error("❌ Error sending interrupted response for request {}: {}", requestId, e.getMessage(), e);
                            }
                        });
                    } catch (java.util.concurrent.ExecutionException ex) {
                        long duration = System.nanoTime() - requestStartTime;

                        // Record metrics safely
                        try {
                            Metrics.publishLatency.observe((double) duration / 1_000_000_000.0);
                            Metrics.messagesErrors.inc();
                        } catch (Exception metricsError) {
                            logger.warn("⚠️ Failed to record error metrics for request {}: {}", requestId, metricsError.getMessage());
                        }

                        logger.error("❌ Execution error while sending message to Kafka for request {} after {}ms: {}",
                                    requestId, duration / 1_000_000, ex.getMessage(), ex);

                        ctx.executor().execute(() -> {
                            try {
                                sendError(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE,
                                          "Kafka indisponível: " + ex.getMessage(), RETRY_AFTER);
                            } catch (Exception e) {
                                logger.error("❌ Error sending execution error response for request {}: {}", requestId, e.getMessage(), e);
                            }
                        });
                    } catch (Exception ex) {
                        long duration = System.nanoTime() - requestStartTime;
                        
                        // Record metrics safely
                        try {
                            Metrics.publishLatency.observe((double) duration / 1_000_000_000.0);
                            Metrics.messagesErrors.inc();
                        } catch (Exception metricsError) {
                            logger.warn("⚠️ Failed to record error metrics for request {}: {}", requestId, metricsError.getMessage());
                        }
                        
                        logger.error("❌ Unexpected error while sending message to Kafka for request {} after {}ms: {}",
                                    requestId, duration / 1_000_000, ex.getMessage(), ex);
                        
                        ctx.executor().execute(() -> {
                            try {
                                sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                                          "Erro interno: " + ex.getMessage());
                            } catch (Exception e) {
                                logger.error("❌ Error sending unexpected error response for request {}: {}", requestId, e.getMessage(), e);
                            }
                        });
                    } finally {
                        MDC.clear();
                    }
                });
                
            } catch (org.apache.avro.AvroRuntimeException e) {
                logger.error("❌ Avro runtime error for request {}: {}", requestId, e.getMessage(), e);
                sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "Avro runtime error during serialization");
            } catch (RuntimeException e) {
                logger.error("❌ Runtime error processing request {}: {}", requestId, e.getMessage(), e);
                sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "Runtime error");
            } catch (Exception e) {
                logger.error("❌ Unexpected error processing request {}: {}", requestId, e.getMessage(), e);
                sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "Internal server error");
            } finally {
                MDC.clear();
            }
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.error("❌ Exception in PublishHandler: {}", cause.getMessage(), cause);
            try {
                sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "Internal server error");
            } catch (Exception e) {
                logger.error("❌ Error sending error response: {}", e.getMessage());
                ctx.close();
            }
        }

        private static String checkResourceUsage() {
            try {
                OperatingSystemMXBean os = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
                double cpu = os.getCpuLoad();
                long totRam = os.getTotalMemorySize(), freeRam = os.getFreeMemorySize();
                double ramRatio = (double)(totRam - freeRam)/totRam;
                File root = new File("/");
                long totDisk = root.getTotalSpace(), freeDisk = root.getUsableSpace();
                double diskRatio = (double)(totDisk - freeDisk)/totDisk;
                
                Metrics.cpuUsage.set(cpu);
                Metrics.memoryUsage.set(ramRatio);
                Metrics.diskUsage.set(diskRatio);
                
                StringBuilder limitedResources = new StringBuilder();
                boolean isHigh = false;
                
                if (cpu > THRESHOLD) {
                    limitedResources.append("CPU: ").append(String.format("%.1f%%", cpu * 100));
                    isHigh = true;
                }
                
                if (ramRatio > THRESHOLD) {
                    if (limitedResources.length() > 0) limitedResources.append(", ");
                    limitedResources.append("RAM: ").append(String.format("%.1f%%", ramRatio * 100));
                    isHigh = true;
                }
                
                if (diskRatio > THRESHOLD) {
                    if (limitedResources.length() > 0) limitedResources.append(", ");
                    limitedResources.append("Disco: ").append(String.format("%.1f%%", diskRatio * 100));
                    isHigh = true;
                }
                
                if (isHigh) {
                    String message = "Recursos limitados: " + limitedResources.toString() + " ≥ 85%";
                    logger.warn("⚠️ High resource usage detected - {}", message);
                    return message;
                } else {
                    logger.debug("📊 Resource usage - CPU: {:.2f}%, RAM: {:.2f}%, Disk: {:.2f}%", 
                                cpu * 100, ramRatio * 100, diskRatio * 100);
                    return null;
                }
                
            } catch (SecurityException | NullPointerException | IllegalArgumentException e) {
                logger.error("❌ Error checking resource usage: {}", e.getMessage(), e);
                return null;
            }
        }

        private static String detectFormat(String txt) {
            String t = txt.trim();
            String format;
            if (t.startsWith("{") && t.endsWith("}")) {
                format = "json";
            } else if (t.startsWith("<")) {
                format = t.contains("Envelope") ? "soap" : "xml";
            } else {
                format = "txt";
            }
            
            logger.debug("🔍 Detected format: {} for content starting with: {}", format, 
                        t.length() > 50 ? t.substring(0, 50) + "..." : t);
            
            return format;
        }

        private static void sendSuccess(ChannelHandlerContext ctx, HttpResponseStatus status) {
            logger.debug("✅ Sending success response: {}", status);
            
            // Record success metrics
            try {
                Metrics.requestsTotal.labels("POST", String.valueOf(status.code())).inc();
            } catch (Exception e) {
                logger.warn("⚠️ Error recording success metrics: {}", e.getMessage());
            }
            
            ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status))
               .addListener(ChannelFutureListener.CLOSE);
        }
        
        private static void sendHealthResponse(ChannelHandlerContext ctx) {
            logger.debug("🏥 Sending health response");
            String healthJson = "{\"status\":\"UP\",\"timestamp\":" + System.currentTimeMillis() + "}";
            byte[] bytes = healthJson.getBytes(StandardCharsets.UTF_8);
            
            FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(bytes));
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
            response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
            
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
        
        private static void sendMetricsResponse(ChannelHandlerContext ctx) {
            logger.debug("📊 Sending metrics response");
            try {
                // Basic metrics in Prometheus format
                StringBuilder metrics = new StringBuilder();
                metrics.append("# HELP teuthis_requests_total Total HTTP requests\n");
                metrics.append("# TYPE teuthis_requests_total counter\n");
                metrics.append("teuthis_requests_total ").append(System.currentTimeMillis()).append("\n");
                
                byte[] bytes = metrics.toString().getBytes(StandardCharsets.UTF_8);
                FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(bytes));
                response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; version=0.0.4; charset=utf-8");
                response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
                
                ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
            } catch (Exception e) {
                logger.error("❌ Error generating metrics: {}", e.getMessage());
                sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            }
        }

        private static void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
            sendError(ctx, status, status.reasonPhrase(), null);
        }

        private static void sendError(ChannelHandlerContext ctx, HttpResponseStatus status, String msg) {
            sendError(ctx, status, msg, null);
        }

        private static void sendError(ChannelHandlerContext ctx, HttpResponseStatus status, String msg, Integer retryAfter) {
            logger.debug("❌ Sending error response: {} - {}", status, msg);
            
            // Record error metrics
            try {
                Metrics.requestsTotal.labels("POST", String.valueOf(status.code())).inc();
            } catch (Exception e) {
                logger.warn("⚠️ Error recording error metrics: {}", e.getMessage());
            }
            
            byte[] b = msg.getBytes(StandardCharsets.UTF_8);
            FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(b));
            resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
            resp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, b.length);
            if (retryAfter != null) {
                resp.headers().set(HttpHeaderNames.RETRY_AFTER, retryAfter.toString());
            }
            
            ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
