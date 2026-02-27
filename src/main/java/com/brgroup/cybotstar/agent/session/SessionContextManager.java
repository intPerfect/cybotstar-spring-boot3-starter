package com.brgroup.cybotstar.agent.session;

import com.brgroup.cybotstar.connection.ConnectionManager;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 响应式会话上下文管理器
 * 管理所有会话上下文的生命周期
 *
 * @author zhiyuan.xi
 */
@Slf4j
public class SessionContextManager {

    @NonNull
    private final ConnectionManager connectionManager;

    // 会话上下文缓存
    private final ConcurrentHashMap<String, Mono<SessionContext>> contextCache =
            new ConcurrentHashMap<>();

    public SessionContextManager(@NonNull ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * 获取或创建会话上下文
     * 使用 Mono.cache() 实现会话复用
     *
     * @param sessionId 会话 ID
     * @return 会话上下文的 Mono
     */
    @NonNull
    public Mono<SessionContext> getContext(@NonNull String sessionId) {
        return contextCache.computeIfAbsent(sessionId, id -> {
            log.debug("Creating new session context for: {}", id);
            return createContext(id).cache();
        });
    }

    /**
     * 创建新会话上下文
     */
    @NonNull
    private Mono<SessionContext> createContext(@NonNull String sessionId) {
        return connectionManager.getConnection(sessionId)
                .map(connection -> new SessionContext(sessionId, connection))
                .doOnSuccess(context -> log.debug("Session context created: {}", sessionId))
                .doOnError(error -> {
                    log.error("Failed to create session context: {}", sessionId, error);
                    contextCache.remove(sessionId);
                });
    }

    /**
     * 移除会话上下文
     */
    @NonNull
    public Mono<Void> removeContext(@NonNull String sessionId) {
        return Mono.defer(() -> {
            Mono<SessionContext> cachedContext = contextCache.remove(sessionId);
            if (cachedContext == null) {
                return Mono.empty();
            }

            return cachedContext
                    .doOnNext(SessionContext::close)
                    .then()
                    .doOnSuccess(v -> log.debug("Session context removed: {}", sessionId));
        });
    }

    /**
     * 移除所有会话上下文
     */
    @NonNull
    public Mono<Void> removeAll() {
        return reactor.core.publisher.Flux.fromIterable(contextCache.keySet())
                .flatMap(this::removeContext)
                .then()
                .doOnSuccess(v -> log.debug("All session contexts removed"));
    }

    /**
     * 检查会话上下文是否存在
     */
    public boolean hasContext(@NonNull String sessionId) {
        return contextCache.containsKey(sessionId);
    }
}
