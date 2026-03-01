package com.brgroup.cybotstar.agent.session;

import com.brgroup.cybotstar.core.connection.ConnectionManager;
import com.brgroup.cybotstar.core.model.common.ConnectionState;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import reactor.core.publisher.Mono;

import java.time.Duration;
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
     * 使用 Mono.cache() 实现会话复用，失败时自动清除缓存
     * 根据连接状态动态调整缓存时间
     *
     * @param sessionId 会话 ID
     * @return 会话上下文的 Mono
     */
    @NonNull
    public Mono<SessionContext> getContext(@NonNull String sessionId) {
        return contextCache.computeIfAbsent(sessionId, id -> {
            log.debug("Creating new session context for: {}", id);
            return createContext(id)
                    .cache(
                        value -> {
                            // 根据连接状态决定缓存时间
                            ConnectionState state = value.getConnection().getCurrentState();
                            if (state == ConnectionState.CONNECTED) {
                                return Duration.ofMinutes(30);  // 连接正常，缓存 30 分钟
                            } else {
                                return Duration.ofMinutes(5);   // 连接异常，缓存 5 分钟
                            }
                        },
                        error -> Duration.ZERO,            // 失败时不缓存
                        () -> Duration.ZERO                // 空值不缓存
                    );
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

    /**
     * 获取会话数量
     */
    public int getSessionCount() {
        return contextCache.size();
    }

    /**
     * 清理无效会话
     * 移除连接已关闭的会话
     */
    @NonNull
    public Mono<Integer> cleanupInvalidSessions() {
        return reactor.core.publisher.Flux.fromIterable(contextCache.entrySet())
                .filterWhen(entry -> entry.getValue()
                        .map(context -> {
                            ConnectionState state = context.getConnection().getCurrentState();
                            return state == ConnectionState.CLOSED || state == ConnectionState.DISCONNECTED;
                        })
                        .onErrorReturn(false))
                .flatMap(entry -> {
                    String sessionId = entry.getKey();
                    log.debug("Cleaning up invalid session: {}", sessionId);
                    return removeContext(sessionId).thenReturn(1);
                })
                .reduce(0, Integer::sum)
                .doOnSuccess(count -> {
                    if (count > 0) {
                        log.info("Cleaned up {} invalid sessions", count);
                    }
                });
    }
}
