package com.brgroup.cybotstar.core.connection;

import com.brgroup.cybotstar.agent.config.AgentConfig;
import com.brgroup.cybotstar.core.util.CybotStarConstants;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket 连接池
 * 提供连接的获取、释放和管理功能
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class WebSocketConnectionPool {

    @NonNull
    private final AgentConfig config;

    // 连接池
    private final BlockingQueue<WebSocketConnection> availableConnections;

    // 池配置
    private final int maxPoolSize;
    private final int minPoolSize;

    // 当前连接数统计
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    private final AtomicInteger activeConnections = new AtomicInteger(0);

    /**
     * 创建连接池
     *
     * @param config 配置
     * @param minPoolSize 最小连接数
     * @param maxPoolSize 最大连接数
     */
    public WebSocketConnectionPool(@NonNull AgentConfig config, int minPoolSize, int maxPoolSize) {
        Objects.requireNonNull(config, "config cannot be null");
        if (minPoolSize < 0) {
            throw new IllegalArgumentException("minPoolSize must be >= 0");
        }
        if (maxPoolSize < minPoolSize) {
            throw new IllegalArgumentException("maxPoolSize must be >= minPoolSize");
        }

        this.config = config;
        this.minPoolSize = minPoolSize;
        this.maxPoolSize = maxPoolSize;
        this.availableConnections = new LinkedBlockingQueue<>(maxPoolSize);

        log.info("WebSocketConnectionPool initialized (min: {}, max: {})", minPoolSize, maxPoolSize);

        // 预创建最小连接数
        initializeMinConnections();
    }

    /**
     * 使用默认配置创建连接池
     */
    public WebSocketConnectionPool(@NonNull AgentConfig config) {
        this(config, 2, 10);
    }

    /**
     * 初始化最小连接数（异步）
     */
    private void initializeMinConnections() {
        for (int i = 0; i < minPoolSize; i++) {
            int index = i;
            createConnectionAsync()
                .subscribe(
                    connection -> {
                        availableConnections.offer(connection);
                        totalConnections.incrementAndGet();
                        log.debug("Initialized connection #{}", index);
                    },
                    error -> log.warn("Failed to initialize connection #{}", index, error)
                );
        }
    }

    /**
     * 获取连接（响应式）
     */
    @NonNull
    public Mono<WebSocketConnection> acquire() {
        return Mono.defer(() -> {
            // 尝试从池中获取可用连接
            WebSocketConnection connection = availableConnections.poll();

            if (connection != null) {
                // 检查连接是否有效
                if (connection.isConnected()) {
                    activeConnections.incrementAndGet();
                    log.debug("Acquired connection from pool (active: {}, total: {})",
                            activeConnections.get(), totalConnections.get());
                    return Mono.just(connection);
                } else {
                    // 连接已失效，关闭并创建新连接
                    connection.close();
                    totalConnections.decrementAndGet();
                }
            }

            // 如果池中没有可用连接且未达到最大连接数，创建新连接
            if (totalConnections.get() < maxPoolSize) {
                return createConnectionAsync()
                    .doOnSuccess(conn -> {
                        totalConnections.incrementAndGet();
                        activeConnections.incrementAndGet();
                        log.debug("Created new connection (active: {}, total: {})",
                                activeConnections.get(), totalConnections.get());
                    });
            }

            // 达到最大连接数，等待可用连接（使用响应式方式）
            log.debug("Pool exhausted, waiting for available connection...");
            return Mono.fromCallable(() -> {
                WebSocketConnection conn = availableConnections.take();  // 阻塞等待
                activeConnections.incrementAndGet();
                return conn;
            });
        });
    }

    /**
     * 释放连接回池中
     */
    public void release(@NonNull WebSocketConnection connection) {
        Objects.requireNonNull(connection, "connection cannot be null");

        activeConnections.decrementAndGet();

        // 检查连接是否有效
        if (!connection.isConnected()) {
            connection.close();
            totalConnections.decrementAndGet();
            log.debug("Released invalid connection (active: {}, total: {})",
                    activeConnections.get(), totalConnections.get());
            return;
        }

        // 如果池未满，归还连接
        if (availableConnections.offer(connection)) {
            log.debug("Released connection to pool (active: {}, total: {})",
                    activeConnections.get(), totalConnections.get());
        } else {
            // 池已满，关闭连接
            connection.close();
            totalConnections.decrementAndGet();
            log.debug("Pool full, closed connection (active: {}, total: {})",
                    activeConnections.get(), totalConnections.get());
        }
    }

    /**
     * 创建新连接（异步）
     */
    @NonNull
    private Mono<WebSocketConnection> createConnectionAsync() {
        WebSocketConnection connection = new WebSocketConnection(config);
        return connection.connect()
            .thenReturn(connection);
    }

    /**
     * 关闭连接池
     */
    public void shutdown() {
        log.info("Shutting down connection pool...");

        // 关闭所有可用连接
        WebSocketConnection connection;
        while ((connection = availableConnections.poll()) != null) {
            try {
                connection.close();
            } catch (Exception e) {
                log.warn("Error closing connection during shutdown", e);
            }
        }

        totalConnections.set(0);
        activeConnections.set(0);
        log.info("Connection pool shut down");
    }

    /**
     * 获取池统计信息
     */
    public PoolStats getStats() {
        return new PoolStats(
                totalConnections.get(),
                activeConnections.get(),
                availableConnections.size(),
                minPoolSize,
                maxPoolSize
        );
    }

    /**
     * 连接池统计信息
     */
    public record PoolStats(
            int totalConnections,
            int activeConnections,
            int availableConnections,
            int minPoolSize,
            int maxPoolSize
    ) {
        @Override
        public String toString() {
            return String.format("PoolStats[total=%d, active=%d, available=%d, min=%d, max=%d]",
                    totalConnections, activeConnections, availableConnections, minPoolSize, maxPoolSize);
        }
    }
}
