package com.github.darioajr.teuthis.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;

/**
 * Simple connection metrics handler
 */
public class MetricsHandler extends ChannelDuplexHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsHandler.class);
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        try {
            Metrics.activeConnections.inc();
            logger.debug("🔗 Connection established: {}", ctx.channel().remoteAddress());
        } catch (Exception e) {
            logger.warn("⚠️ Error tracking connection: {}", e.getMessage());
        }
        super.channelActive(ctx);
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        try {
            Metrics.activeConnections.dec();
            logger.debug("🔌 Connection closed: {}", ctx.channel().remoteAddress());
        } catch (Exception e) {
            logger.warn("⚠️ Error tracking disconnection: {}", e.getMessage());
        }
        super.channelInactive(ctx);
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.warn("⚠️ Connection exception: {}", cause.getMessage());
        ctx.close();
    }
}
