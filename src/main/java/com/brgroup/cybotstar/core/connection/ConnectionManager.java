package com.brgroup.cybotstar.core.connection;

import com.brgroup.cybotstar.agent.config.AgentConfig;
import com.brgroup.cybotstar.core.exception.ErrorRecoveryStrategy;
import com.brgroup.cybotstar.core.util.CybotStarConstants;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 响应式连接管理器
 * 使用 Caffeine Cache 实现连接复用和自动淘汰
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class ConnectionManager {

    @NonNull
    private final AgentConfig config;

    // 连接缓存（使用 Caffeine 实现自动淘汰）
    private final Cache<String, Mono<WebSocketConnection>> connectionCache;

    // 连接计数器（用于监控）
    private final AtomicInteger activeConnectionCount = new AtomicInteger(0);

    public ConnectionManager(@NonNull AgentConfig config) {
        Objects.requireNonNull(config, "config cannot be null");
        this.config = config;

        // 配置 Caffeine 缓存
        this.connectionCache = Caffeine.newBuilder()
                .maximumSize(CybotStarConstants.CONNECTION_CACHE_MAX_SIZE)
                .expireAfterAccess(Duration.ofMinutes(CybotStarConstants.CONNECTION_CACHE_EXPIRE_MINUTES))
                .removalListener(this::onConnectionRemoved)
                .recordStats()  // 启用统计
                .build();

        log.debug("ConnectionManager initialized with Caffeine cache (max: {}, TTL: {}min)",
                CybotStarConstants.CONNECTION_CACHE_MAX_SIZE,
                CybotStarConstants.CONNECTION_CACHE_EXPIRE_MINUTES);
    }

    /**
     * 连接移除回调
     */
    private void onConnectionRemoved(String sessionId, Mono<WebSocketConnection> connectionMono, RemovalCause cause) {
        if (connectionMono == null) {
            return;
        }

        log.debug("Connection removed for session: {}, cause: {}", sessionId, cause);

        // 异步关闭连接
        connectionMono
                .doOnNext(connection -> {
                    try {
                        connection.close();
                        activeConnectionCount.decrementAndGet();
                        log.debug("Connection closed for session: {}, active connections: {}",
                            sessionId, activeConnectionCount.get());
                    } catch (Exception e) {
                        log.warn("Error closing connection for session: {}", sessionId, e);
                    }
                })
                .onErrorResume(error -> {
                    log.warn("Failed to close connection for session: {}, error: {}",
                        sessionId, error.getMessage());
                    return Mono.empty();
                })
                .subscribe();
    }

    /**
     * 获取或创建连接
     * 使用 Caffeine Cache 实现连接复用，多个订阅者共享同一个连接
     *
     * @param sessionId 会话 ID
     * @return 连接的 Mono
     */
    @NonNull
    public Mono<WebSocketConnection> getConnection(@NonNull String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId cannot be null");

        // 检查会话数量限制
        if (connectionCache.estimatedSize() >= CybotStarConstants.MAX_SESSION_COUNT) {
            log.warn("Session count limit reached: {}", CybotStarConstants.MAX_SESSION_COUNT);
            return Mono.error(new IllegalStateException(
                "Maximum session count reached: " + CybotStarConstants.MAX_SESSION_COUNT));
        }

        Mono<WebSocketConnection> cached = connectionCache.getIfPresent(sessionId);
        if (cached != null) {
            return cached;
        }

        // 创建新连接并缓存
        Mono<WebSocketConnection> newConnection = createConnection(sessionId)
                .retryWhen(ErrorRecoveryStrategy.connectionRetry())
                .cache();
        connectionCache.put(sessionId, newConnection);
        return newConnection;
    }

    /**
     * 创建新连接
     */
    @NonNull
    private Mono<WebSocketConnection> createConnection(@NonNull String sessionId) {
        log.debug("Creating new connection for session: {}", sessionId);
        return Mono.fromCallable(() -> new WebSocketConnection(config))
                .flatMap(connection ->
                        connection.connect()
                                .thenReturn(connection)
                )
                .doOnSuccess(conn -> {
                    activeConnectionCount.incrementAndGet();
                    log.debug("Connection created successfully for session: {}, active connections: {}",
                        sessionId, activeConnectionCount.get());
                })
                .doOnError(error -> {
                    log.error("Failed to create connection for session: {}, error: {}",
                        sessionId, error.getMessage());
                    // 移除失败的连接缓存
                    connectionCache.invalidate(sessionId);
                })
                .onErrorMap(error -> {
                    if (error instanceof com.brgroup.cybotstar.agent.exception.AgentException) {
                        return error;
                    }
                    return com.brgroup.cybotstar.agent.exception.AgentException.connectionFailed(
                        "Failed to create connection for session: " + sessionId,
                        error instanceof Exception ? (Exception) error : null);
                });
    }

    /**
     * 断开连接
     *
     * @param sessionId 会话 ID
     */
    @NonNull
    public Mono<Void> disconnect(@NonNull String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId cannot be null");
        return Mono.defer(() -> {
            Mono<WebSocketConnection> cachedConnection = connectionCache.getIfPresent(sessionId);
            connectionCache.invalidate(sessionId);  // 从缓存中移除

            if (cachedConnection == null) {
                return Mono.empty();
            }

            return cachedConnection
                    .doOnNext(WebSocketConnection::close)
                    .then()
                    .doOnSuccess(v -> log.debug("Connection closed for session: {}", sessionId));
        });
    }

    /**
     * 断开所有连接
     */
    @NonNull
    public Mono<Void> disconnectAll() {
        return Flux.fromIterable(connectionCache.asMap().keySet())
                .flatMap(this::disconnect)
                .then()
                .doOnSuccess(v -> {
                    connectionCache.invalidateAll();
                    log.debug("All connections closed");
                });
    }

    /**
     * 检查连接是否存在
     */
    public boolean hasConnection(@NonNull String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId cannot be null");
        return connectionCache.getIfPresent(sessionId) != null;
    }

    /**
     * 获取缓存统计信息
     */
    public String getCacheStats() {
        return String.format("Cache stats - size: %d, hits: %d, misses: %d, evictions: %d, active: %d",
                connectionCache.estimatedSize(),
                connectionCache.stats().hitCount(),
                connectionCache.stats().missCount(),
                connectionCache.stats().evictionCount(),
                activeConnectionCount.get());
    }

    /**
     * 获取活跃连接数
     */
    public int getActiveConnectionCount() {
        return activeConnectionCount.get();
    }

    /**
     * 获取缓存大小
     */
    public long getCacheSize() {
        return connectionCache.estimatedSize();
    }

    /**
     * 检查是否达到连接限制
     */
    public boolean isConnectionLimitReached() {
        return connectionCache.estimatedSize() >= CybotStarConstants.MAX_SESSION_COUNT;
    }
}
