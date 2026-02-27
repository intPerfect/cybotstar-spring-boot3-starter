package com.brgroup.cybotstar.connection;

import com.brgroup.cybotstar.config.AgentConfig;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 响应式连接管理器
 * 使用 Mono.cache() 实现连接复用
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class ConnectionManager {

    @NonNull
    private final AgentConfig config;

    // 连接缓存（使用 Mono.cache() 实现自动复用）
    private final ConcurrentHashMap<String, Mono<WebSocketConnection>> connectionCache =
            new ConcurrentHashMap<>();

    public ConnectionManager(@NonNull AgentConfig config) {
        this.config = config;
    }

    /**
     * 获取或创建连接
     * 使用 Mono.cache() 实现连接复用，多个订阅者共享同一个连接
     *
     * @param sessionId 会话 ID
     * @return 连接的 Mono
     */
    @NonNull
    public Mono<WebSocketConnection> getConnection(@NonNull String sessionId) {
        return connectionCache.computeIfAbsent(sessionId, id -> {
            log.debug("Creating new connection for session: {}", id);
            return createConnection(id)
                    .cache();  // 使用 cache() 实现连接复用
        });
    }

    /**
     * 创建新连接
     */
    @NonNull
    private Mono<WebSocketConnection> createConnection(@NonNull String sessionId) {
        return Mono.fromCallable(() -> {
            WebSocketConnection connection = new WebSocketConnection(config);
            return connection;
        })
        .flatMap(connection ->
            connection.connect()
                    .thenReturn(connection)
        )
        .doOnSuccess(conn -> log.debug("Connection created successfully for session: {}", sessionId))
        .doOnError(error -> {
            log.error("Failed to create connection for session: {}", sessionId, error);
            // 移除失败的连接缓存
            connectionCache.remove(sessionId);
        });
    }

    /**
     * 断开连接
     *
     * @param sessionId 会话 ID
     */
    @NonNull
    public Mono<Void> disconnect(@NonNull String sessionId) {
        return Mono.defer(() -> {
            Mono<WebSocketConnection> cachedConnection = connectionCache.remove(sessionId);
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
        return Flux.fromIterable(connectionCache.keySet())
                .flatMap(this::disconnect)
                .then()
                .doOnSuccess(v -> log.debug("All connections closed"));
    }

    /**
     * 检查连接是否存在
     */
    public boolean hasConnection(@NonNull String sessionId) {
        return connectionCache.containsKey(sessionId);
    }
}
